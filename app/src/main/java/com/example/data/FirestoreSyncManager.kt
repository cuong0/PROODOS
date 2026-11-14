package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.firestoreSettings
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.room.withTransaction
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

enum class SyncState {
    OFFLINE,    // Chưa đăng nhập (Chế độ lưu cục bộ)
    SYNCING,    // Đang đồng bộ...
    SYNCED      // Đã đồng bộ trực tuyến
}

class FirestoreSyncManager(
    private val context: Context,
    private val db: AppDatabase
) {
    private val TAG = "FirestoreSync"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val firestore: FirebaseFirestore = try {
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ).apply {
            try {
                firestoreSettings = firestoreSettings {
                    setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                }
            } catch (e: Exception) {
                // Ignore if settings already set
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error initializing custom firestore instance", e)
        throw e
    }

    private val _syncState = MutableStateFlow(SyncState.OFFLINE)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    var activeUserId: String? = null
        private set

    fun syncAllDataNow() {
        val uid = activeUserId ?: return
        scope.launch {
            syncAllDataToCloud(uid)
        }
    }

    private val listeners = mutableListOf<ListenerRegistration>()
    private var isApplyingCloudSnapshot = false
    private val activeSyncOperations = java.util.concurrent.atomic.AtomicInteger(0)
    private var syncDebounceJob: kotlinx.coroutines.Job? = null
    private var startSyncJob: kotlinx.coroutines.Job? = null
    private var isSnapshotPendingWrites = false

    fun markSyncOperationStart() {
        if (activeUserId == null) return
        activeSyncOperations.incrementAndGet()
        syncDebounceJob?.cancel()
        _syncState.value = SyncState.SYNCING
    }

    fun markSyncOperationEnd() {
        val remaining = activeSyncOperations.decrementAndGet().coerceAtLeast(0)
        if (activeUserId == null) {
            _syncState.value = SyncState.OFFLINE
            return
        }
        if (remaining == 0 && !isSnapshotPendingWrites) {
            // Chờ 600ms ổn định: chắc chắn hoàn tất toàn bộ thao tác đồng bộ mới chuyển sang màu xanh
            syncDebounceJob?.cancel()
            syncDebounceJob = scope.launch {
                kotlinx.coroutines.delay(800)
                if (activeUserId != null && activeSyncOperations.get() == 0 && !isSnapshotPendingWrites) {
                    _syncState.value = SyncState.SYNCED
                }
            }
        }
    }

    var onSettingsUpdated: ((storeName: String?, isDarkMode: Boolean?, isGridView: Boolean?, currentLanguage: String?, manualCash: Double?, manualAccount: Double?) -> Unit)? = null

    fun uriToBase64(context: Context, uriString: String?): String? {
        if (uriString.isNullOrBlank()) return null
        if (uriString.startsWith("data:image/")) return uriString

        return try {
            val uri = Uri.parse(uriString)
            val stream = when {
                uri.scheme == "file" -> {
                    val path = uri.path ?: uriString.removePrefix("file://")
                    val f = File(path)
                    if (f.exists()) FileInputStream(f) else {
                        val fInFiles = File(context.filesDir, f.name)
                        if (fInFiles.exists()) FileInputStream(fInFiles) else {
                            val fInSynced = File(File(context.filesDir, "synced_images"), f.name)
                            if (fInSynced.exists()) FileInputStream(fInSynced) else null
                        }
                    }
                }
                uri.scheme == "content" -> {
                    try {
                        context.contentResolver.openInputStream(uri)
                    } catch (e: Exception) {
                        val name = uri.lastPathSegment?.substringAfterLast("/") ?: ""
                        val fInFiles = File(context.filesDir, name)
                        if (fInFiles.exists()) FileInputStream(fInFiles) else null
                    }
                }
                else -> {
                    val direct = File(uriString)
                    if (direct.exists()) FileInputStream(direct) else {
                        val fileName = uriString.substringAfterLast("/")
                        val fInFiles = File(context.filesDir, fileName)
                        if (fInFiles.exists()) FileInputStream(fInFiles) else {
                            val syncedDir = File(context.filesDir, "synced_images")
                            val fInSynced = File(syncedDir, fileName)
                            if (fInSynced.exists()) FileInputStream(fInSynced) else null
                        }
                    }
                }
            } ?: return null

            val originalBitmap = stream.use { BitmapFactory.decodeStream(it) } ?: return null

            val maxDim = 800
            val width = originalBitmap.width
            val height = originalBitmap.height
            val scaledBitmap = if (width > maxDim || height > maxDim) {
                val ratio = minOf(maxDim.toFloat() / width, maxDim.toFloat() / height)
                val newW = (width * ratio).toInt().coerceAtLeast(1)
                val newH = (height * ratio).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(originalBitmap, newW, newH, true)
            } else {
                originalBitmap
            }

            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val bytes = outputStream.toByteArray()
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            "data:image/jpeg;base64,$base64"
        } catch (e: Throwable) {
            Log.e(TAG, "Error encoding image to base64 for uri: $uriString", e)
            null
        }
    }

    fun base64ToLocalUri(context: Context, dataString: String?, prefix: String, id: Int): String? {
        if (dataString.isNullOrBlank()) return null
        if (!dataString.startsWith("data:image/")) {
            val uri = Uri.parse(dataString)
            if (uri.scheme == "file") {
                val f = File(uri.path ?: "")
                if (f.exists()) return dataString
            }
            return dataString
        }

        return try {
            val base64Part = dataString.substringAfter("base64,")
            val imageBytes = Base64.decode(base64Part, Base64.DEFAULT)
            val dir = File(context.filesDir, "synced_images").apply { if (!exists()) mkdirs() }
            val file = File(dir, "${prefix}_$id.jpg")
            FileOutputStream(file).use { out ->
                out.write(imageBytes)
                out.flush()
            }
            Uri.fromFile(file).toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving base64 to local file for $prefix $id", e)
            null
        }
    }

    suspend fun pullAllDataFromCloud(userId: String) = withContext(Dispatchers.IO) {
        try {
            val userDoc = firestore.collection("users").document(userId)
            val prefs = context.getSharedPreferences("proodos_prefs", Context.MODE_PRIVATE)

            // 1. Settings (storeName, financial adjustments, theme, language, etc.)
            try {
                val setDoc = userDoc.collection("settings").document("general").get().await()
                if (setDoc != null && setDoc.exists()) {
                    val storeName = setDoc.getString("storeName")
                    val isDarkMode = setDoc.getBoolean("isDarkMode")
                    val isGridView = setDoc.getBoolean("isGridView")
                    val currentLanguage = setDoc.getString("currentLanguage")
                    val isAutoBackup = setDoc.getBoolean("isAutoBackup")
                    val manualCash = setDoc.getDouble("manualCashAdjustment") ?: setDoc.getLong("manualCashAdjustment")?.toDouble() ?: 0.0
                    val manualAccount = setDoc.getDouble("manualAccountAdjustment") ?: setDoc.getLong("manualAccountAdjustment")?.toDouble() ?: 0.0

                    val editor = prefs.edit()
                    storeName?.let {
                        if (it.isNotBlank()) {
                            editor.putString("saved_store_name", it)
                        }
                    }
                    isDarkMode?.let { editor.putBoolean("is_dark_mode", it) }
                    isGridView?.let { editor.putBoolean("is_grid_view", it) }
                    currentLanguage?.let { editor.putString("current_language", it) }
                    isAutoBackup?.let { editor.putBoolean("auto_backup", it) }
                    editor.putFloat("manual_cash_adjustment", manualCash.toFloat())
                    editor.putFloat("manual_account_adjustment", manualAccount.toFloat())
                    editor.apply()
                    onSettingsUpdated?.invoke(storeName, isDarkMode, isGridView, currentLanguage, manualCash, manualAccount)
                } else {
                    prefs.edit()
                        .putFloat("manual_cash_adjustment", 0f)
                        .putFloat("manual_account_adjustment", 0f)
                        .apply()
                    onSettingsUpdated?.invoke(null, null, null, null, 0.0, 0.0)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching cloud settings", e)
            }

            // 2. Categories
            try {
                val catsSnap = userDoc.collection("categories").get().await()
                val cloudCats = catsSnap.documents.mapNotNull { doc ->
                    val id = doc.getLong("id")?.toInt() ?: doc.id.toIntOrNull() ?: return@mapNotNull null
                    val name = doc.getString("name") ?: ""
                    Category(id = id, name = name)
                }
                isApplyingCloudSnapshot = true
                try {
                    db.withTransaction {
                        db.categoryDao().deleteAllCategories()
                        if (cloudCats.isNotEmpty()) {
                            db.categoryDao().insertCategories(cloudCats)
                        }
                    }
                } finally {
                    isApplyingCloudSnapshot = false
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching cloud categories", e)
            }

            // 3. Products
            try {
                val prodsSnap = userDoc.collection("products").get().await()
                val cloudProds = prodsSnap.documents.mapNotNull { doc ->
                    val id = doc.getLong("id")?.toInt() ?: doc.id.toIntOrNull() ?: return@mapNotNull null
                    val name = doc.getString("name") ?: ""
                    val categoryId = doc.getLong("categoryId")?.toInt()
                    val importPrice = doc.getDouble("importPrice") ?: doc.getLong("importPrice")?.toDouble() ?: 0.0
                    val sellPrice = doc.getDouble("sellPrice") ?: doc.getLong("sellPrice")?.toDouble() ?: 0.0
                    val trackInventory = doc.getBoolean("trackInventory") ?: true
                    val stockQuantity = doc.getLong("stockQuantity")?.toInt() ?: 0
                    val rawImageUri = doc.getString("imageUri").takeIf { !it.isNullOrBlank() }
                    val imageUri = base64ToLocalUri(context, rawImageUri, "prod", id) ?: rawImageUri
                    val supplierName = doc.getString("supplierName") ?: ""
                    Product(
                        id = id,
                        name = name,
                        categoryId = categoryId,
                        importPrice = importPrice,
                        sellPrice = sellPrice,
                        trackInventory = trackInventory,
                        stockQuantity = stockQuantity,
                        imageUri = imageUri,
                        supplierName = supplierName
                    )
                }
                isApplyingCloudSnapshot = true
                try {
                    db.withTransaction {
                        db.productDao().deleteAllProducts()
                        if (cloudProds.isNotEmpty()) {
                            db.productDao().insertProducts(cloudProds)
                        }
                    }
                } finally {
                    isApplyingCloudSnapshot = false
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching cloud products", e)
            }

            // 4. Invoices
            try {
                val invSnap = userDoc.collection("invoices").get().await()
                val invoices = mutableListOf<Invoice>()
                val invoiceItems = mutableListOf<InvoiceItem>()
                val editor = prefs.edit()
                prefs.all.keys.filter { it.startsWith("invoice_") }.forEach { editor.remove(it) }
                for (doc in invSnap.documents) {
                    val id = doc.getLong("id")?.toInt() ?: doc.id.toIntOrNull() ?: continue
                    val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                    val storeName = doc.getString("storeName") ?: ""
                    val totalAmount = doc.getDouble("totalAmount") ?: doc.getLong("totalAmount")?.toDouble() ?: 0.0
                    val profit = doc.getDouble("profit") ?: doc.getLong("profit")?.toDouble() ?: 0.0
                    val customerName = doc.getString("customerName").takeIf { !it.isNullOrBlank() }
                    val customerPhone = doc.getString("customerPhone").takeIf { !it.isNullOrBlank() }

                    doc.getString("paymentMethod")?.let { editor.putString("invoice_payment_method_$id", it) }
                    doc.getBoolean("isDebt")?.let { editor.putBoolean("invoice_debt_$id", it) }
                    doc.getLong("debtTime")?.let { editor.putLong("invoice_debt_time_$id", it) }
                    val debtPaid = doc.getDouble("debtPaid") ?: doc.getLong("debtPaid")?.toDouble()
                    debtPaid?.let { editor.putFloat("invoice_debt_paid_$id", it.toFloat()) }
                    val debtPaidTm = doc.getDouble("debtPaidTm") ?: doc.getLong("debtPaidTm")?.toDouble()
                    debtPaidTm?.let { editor.putFloat("invoice_debt_paid_tm_$id", it.toFloat()) }
                    val debtPaidCk = doc.getDouble("debtPaidCk") ?: doc.getLong("debtPaidCk")?.toDouble()
                    debtPaidCk?.let { editor.putFloat("invoice_debt_paid_ck_$id", it.toFloat()) }
                    doc.getBoolean("costActive")?.let { editor.putBoolean("invoice_cost_active_$id", it) }
                    doc.getString("costAmount")?.let { editor.putString("invoice_cost_amount_$id", it) }
                    doc.getLong("calendarEventId")?.let { editor.putLong("invoice_calendar_event_$id", it) }

                    invoices.add(Invoice(id, timestamp, storeName, totalAmount, profit, customerName, customerPhone))
                    val rawItems = doc.get("items") as? List<Map<String, Any>> ?: emptyList()
                    for (raw in rawItems) {
                        val itemId = (raw["id"] as? Number)?.toInt() ?: 0
                        val prodId = (raw["productId"] as? Number)?.toInt() ?: 0
                        val prodName = raw["productName"] as? String ?: ""
                        val qty = (raw["quantity"] as? Number)?.toInt() ?: 1
                        val sellPrice = (raw["sellPrice"] as? Number)?.toDouble() ?: 0.0
                        val importPrice = (raw["importPrice"] as? Number)?.toDouble() ?: 0.0
                        invoiceItems.add(InvoiceItem(itemId, id, prodId, prodName, qty, sellPrice, importPrice))
                    }
                }
                editor.apply()
                isApplyingCloudSnapshot = true
                try {
                    db.withTransaction {
                        db.invoiceDao().deleteAllInvoices()
                        db.invoiceDao().deleteAllInvoiceItems()
                        if (invoices.isNotEmpty()) {
                            db.invoiceDao().insertInvoices(invoices)
                            db.invoiceDao().insertInvoiceItems(invoiceItems)
                        }
                    }
                } finally {
                    isApplyingCloudSnapshot = false
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching cloud invoices", e)
            }

            // 5. Import Orders
            try {
                val ordSnap = userDoc.collection("importOrders").get().await()
                val orders = mutableListOf<ImportOrder>()
                val orderItems = mutableListOf<ImportOrderItem>()
                val ordEditor = prefs.edit()
                prefs.all.keys.filter { it.startsWith("import_order_") }.forEach { ordEditor.remove(it) }
                for (doc in ordSnap.documents) {
                    val id = doc.getLong("id")?.toInt() ?: doc.id.toIntOrNull() ?: continue
                    val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                    val supplierName = doc.getString("supplierName") ?: ""
                    val totalAmount = doc.getDouble("totalAmount") ?: doc.getLong("totalAmount")?.toDouble() ?: 0.0
                    val isDraft = doc.getBoolean("isDraft") ?: false
                    val rawInvoiceImageUri = doc.getString("invoiceImageUri").takeIf { !it.isNullOrBlank() }
                    val invoiceImageUri = base64ToLocalUri(context, rawInvoiceImageUri, "invoice", id) ?: rawInvoiceImageUri

                    val cashPaid = doc.getDouble("cashPaid") ?: doc.getLong("cashPaid")?.toDouble()
                    cashPaid?.let { ordEditor.putFloat("import_order_cash_paid_$id", it.toFloat()) }
                    val transferPaid = doc.getDouble("transferPaid") ?: doc.getLong("transferPaid")?.toDouble()
                    transferPaid?.let { ordEditor.putFloat("import_order_transfer_paid_$id", it.toFloat()) }

                    orders.add(ImportOrder(id, timestamp, supplierName, totalAmount, isDraft, invoiceImageUri))
                    val rawItems = doc.get("items") as? List<Map<String, Any>> ?: emptyList()
                    for (raw in rawItems) {
                        val itemId = (raw["id"] as? Number)?.toInt() ?: 0
                        val prodId = (raw["productId"] as? Number)?.toInt() ?: 0
                        val prodName = raw["productName"] as? String ?: ""
                        val qty = (raw["quantity"] as? Number)?.toInt() ?: 1
                        val importPrice = (raw["importPrice"] as? Number)?.toDouble() ?: 0.0
                        orderItems.add(ImportOrderItem(itemId, id, prodId, prodName, qty, importPrice))
                    }
                }
                ordEditor.apply()
                isApplyingCloudSnapshot = true
                try {
                    db.withTransaction {
                        db.importOrderDao().deleteAllImportOrders()
                        db.importOrderDao().deleteAllImportOrderItems()
                        if (orders.isNotEmpty()) {
                            db.importOrderDao().insertImportOrders(orders)
                            db.importOrderDao().insertImportOrderItems(orderItems)
                        }
                    }
                } finally {
                    isApplyingCloudSnapshot = false
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching cloud import orders", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in pullAllDataFromCloud", e)
        }
    }

    suspend fun wipeResidualCloudUserData(userId: String) = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext
        try {
            withTimeoutOrNull(6000L) {
                val userDoc = firestore.collection("users").document(userId)
                val collectionsToClear = listOf(
                    "settings",
                    "importOrders",
                    "import_orders",
                    "products",
                    "categories",
                    "invoices",
                    "debts",
                    "backups"
                )
                coroutineScope {
                    val jobs = mutableListOf<Deferred<Any?>>()
                    jobs.add(async {
                        try { userDoc.collection("settings").document("general").delete().await() } catch (_: Exception) {}
                    })
                    jobs.add(async {
                        try { userDoc.collection("backups").document("latest").delete().await() } catch (_: Exception) {}
                    })
                    for (col in collectionsToClear) {
                        jobs.add(async {
                            try {
                                val snap = withTimeoutOrNull(2500L) { userDoc.collection(col).get().await() }
                                snap?.documents?.map { doc ->
                                    async {
                                        try { doc.reference.delete().await() } catch (_: Exception) {}
                                    }
                                }?.awaitAll()
                            } catch (_: Exception) {}
                        })
                    }
                    jobs.awaitAll()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error wiping residual cloud data for $userId", e)
        }
    }

    fun startSync(userId: String, shouldMigrateLocalData: Boolean = false) {
        if (activeUserId == userId && (listeners.isNotEmpty() || startSyncJob?.isActive == true)) {
            return
        }
        stopSync()
        activeUserId = userId
        markSyncOperationStart()

        startSyncJob = scope.launch {
            try {
                if (shouldMigrateLocalData) {
                    // 1. Data Migration: Push any existing local data that's not on cloud (chỉ khi tạo tài khoản mới từ khách)
                    migrateLocalDataToCloud(userId)
                }

                // 2. Tải toàn bộ dữ liệu từ đám mây về máy để mọi thiết bị đăng nhập đều thấy chung dữ liệu ngay lập tức
                pullAllDataFromCloud(userId)

                // 3. Kết nối bộ lắng nghe thời gian thực (Real-time snapshot listeners) để đồng bộ hai chiều liên tục giữa các thiết bị
                withContext(Dispatchers.Main) {
                    if (activeUserId == userId) {
                        attachRealtimeListeners(userId)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error during startSync", e)
            } finally {
                markSyncOperationEnd()
            }
        }
    }

    fun stopSync() {
        activeUserId = null
        startSyncJob?.cancel()
        startSyncJob = null
        syncDebounceJob?.cancel()
        activeSyncOperations.set(0)
        isSnapshotPendingWrites = false
        for (reg in listeners) {
            try {
                reg.remove()
            } catch (e: Exception) {
                Log.w(TAG, "Error removing listener", e)
            }
        }
        listeners.clear()
        _syncState.value = SyncState.OFFLINE
    }

    private suspend fun migrateLocalDataToCloud(userId: String) = withContext(Dispatchers.IO) {
        try {
            val userDoc = firestore.collection("users").document(userId)
            val prefs = context.getSharedPreferences("proodos_prefs", Context.MODE_PRIVATE)

            // 1. Categories migration
            val localCats = db.categoryDao().getAllCategoriesList()
            for (cat in localCats) {
                userDoc.collection("categories").document(cat.id.toString()).set(
                    mapOf(
                        "id" to cat.id,
                        "name" to cat.name,
                        "userId" to userId,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                ).await()
            }

            // 2. Products migration (sync all products and convert local images to Base64)
            val localProds = db.productDao().getAllProductsList()
            for (prod in localProds) {
                val imageBase64 = uriToBase64(context, prod.imageUri) ?: (prod.imageUri ?: "")
                userDoc.collection("products").document(prod.id.toString()).set(
                    mapOf(
                        "id" to prod.id,
                        "name" to prod.name,
                        "categoryId" to prod.categoryId,
                        "importPrice" to prod.importPrice,
                        "sellPrice" to prod.sellPrice,
                        "trackInventory" to prod.trackInventory,
                        "stockQuantity" to prod.stockQuantity,
                        "imageUri" to imageBase64,
                        "supplierName" to prod.supplierName,
                        "userId" to userId,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                ).await()
            }

            // 3. Invoices migration (including payment methods, debts, debt payments, costs)
            val localInvoices = db.invoiceDao().getAllInvoicesList()
            val localItems = db.invoiceDao().getAllInvoiceItemsList()
            val itemsByInvoice = localItems.groupBy { it.invoiceId }

            for (inv in localInvoices) {
                val invItems = itemsByInvoice[inv.id] ?: emptyList()
                val paymentMethod = prefs.getString("invoice_payment_method_${inv.id}", "TM") ?: "TM"
                val isDebt = prefs.getBoolean("invoice_debt_${inv.id}", false)
                val debtTime = prefs.getLong("invoice_debt_time_${inv.id}", 0L)
                val debtPaid = prefs.getFloat("invoice_debt_paid_${inv.id}", 0f).toDouble()
                val debtPaidTm = prefs.getFloat("invoice_debt_paid_tm_${inv.id}", 0f).toDouble()
                val debtPaidCk = prefs.getFloat("invoice_debt_paid_ck_${inv.id}", 0f).toDouble()
                val costActive = prefs.getBoolean("invoice_cost_active_${inv.id}", false)
                val costAmount = prefs.getString("invoice_cost_amount_${inv.id}", "") ?: ""
                val calendarEventId = prefs.getLong("invoice_calendar_event_${inv.id}", -1L)

                userDoc.collection("invoices").document(inv.id.toString()).set(
                    mapOf(
                        "id" to inv.id,
                        "timestamp" to inv.timestamp,
                        "storeName" to inv.storeName,
                        "totalAmount" to inv.totalAmount,
                        "profit" to inv.profit,
                        "customerName" to (inv.customerName ?: ""),
                        "customerPhone" to (inv.customerPhone ?: ""),
                        "userId" to userId,
                        "paymentMethod" to paymentMethod,
                        "isDebt" to isDebt,
                        "debtTime" to debtTime,
                        "debtPaid" to debtPaid,
                        "debtPaidTm" to debtPaidTm,
                        "debtPaidCk" to debtPaidCk,
                        "costActive" to costActive,
                        "costAmount" to costAmount,
                        "calendarEventId" to calendarEventId,
                        "items" to invItems.map { item ->
                            mapOf(
                                "id" to item.id,
                                "productId" to item.productId,
                                "productName" to item.productName,
                                "quantity" to item.quantity,
                                "sellPrice" to item.sellPrice,
                                "importPrice" to item.importPrice
                            )
                        },
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                ).await()
            }

            // 4. Import Orders migration (including original invoice image & draft state & payment)
            val localOrders = db.importOrderDao().getAllImportOrdersList()
            val localOrderItems = db.importOrderDao().getAllImportOrderItemsList()
            val itemsByOrder = localOrderItems.groupBy { it.importOrderId }

            for (order in localOrders) {
                val ordItems = itemsByOrder[order.id] ?: emptyList()
                val invoiceImageBase64 = uriToBase64(context, order.invoiceImageUri) ?: (order.invoiceImageUri ?: "")
                val cashPaid = prefs.getFloat("import_order_cash_paid_${order.id}", 0f).toDouble()
                val transferPaid = prefs.getFloat("import_order_transfer_paid_${order.id}", 0f).toDouble()

                userDoc.collection("importOrders").document(order.id.toString()).set(
                    mapOf(
                        "id" to order.id,
                        "timestamp" to order.timestamp,
                        "supplierName" to order.supplierName,
                        "totalAmount" to order.totalAmount,
                        "isDraft" to order.isDraft,
                        "invoiceImageUri" to invoiceImageBase64,
                        "cashPaid" to cashPaid,
                        "transferPaid" to transferPaid,
                        "userId" to userId,
                        "items" to ordItems.map { item ->
                            mapOf(
                                "id" to item.id,
                                "productId" to item.productId,
                                "productName" to item.productName,
                                "quantity" to item.quantity,
                                "importPrice" to item.importPrice
                            )
                        },
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                ).await()
            }

            // 5. Settings & configurations migration
            val storeName = prefs.getString("saved_store_name", "") ?: ""
            val isAutoBackup = prefs.getBoolean("auto_backup", true)
            val hasAnyOrdersOrProducts = localProds.isNotEmpty() || localInvoices.isNotEmpty() || localOrders.isNotEmpty()
            val manualCash = if (hasAnyOrdersOrProducts) prefs.getFloat("manual_cash_adjustment", 0f).toDouble() else 0.0
            val manualAccount = if (hasAnyOrdersOrProducts) prefs.getFloat("manual_account_adjustment", 0f).toDouble() else 0.0
            if (!hasAnyOrdersOrProducts) {
                prefs.edit()
                    .putFloat("manual_cash_adjustment", 0f)
                    .putFloat("manual_account_adjustment", 0f)
                    .apply()
                onSettingsUpdated?.invoke(null, null, null, null, 0.0, 0.0)
            }
            val isDarkMode = prefs.getBoolean("is_dark_mode", false)
            val isGridView = prefs.getBoolean("is_grid_view", false)
            val currentLanguage = prefs.getString("current_language", "Tiếng Việt") ?: "Tiếng Việt"

            userDoc.collection("settings").document("general").set(
                mapOf(
                    "storeName" to storeName,
                    "isAutoBackup" to isAutoBackup,
                    "manualCashAdjustment" to manualCash,
                    "manualAccountAdjustment" to manualAccount,
                    "isDarkMode" to isDarkMode,
                    "isGridView" to isGridView,
                    "currentLanguage" to currentLanguage,
                    "userId" to userId,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()

            try {
                val localAcc = com.example.ui.AccountAuthManager.getLocalAccountByUserId(context, userId)
                if (localAcc != null) {
                    userDoc.collection("settings").document("account_auth").set(
                        mapOf(
                            "email" to localAcc.email,
                            "storeName" to localAcc.storeName,
                            "passwordHash" to localAcc.passwordHash,
                            "salt" to localAcc.salt,
                            "userId" to userId,
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    ).await()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not sync account_auth in migrateLocalDataToCloud", e)
            }

            Log.i(TAG, "All data and images successfully synced to cloud for user $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Error in migrateLocalDataToCloud", e)
        }
    }

    suspend fun backupAllDataToCloud(
        userId: String,
        onProgress: ((Float, String) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        markSyncOperationStart()
        try {
            val userDoc = firestore.collection("users").document(userId)
            val prefs = context.getSharedPreferences("proodos_prefs", Context.MODE_PRIVATE)

            onProgress?.invoke(0.05f, "Đang kết nối tài khoản đám mây...")

            // 1. Categories
            val localCats = db.categoryDao().getAllCategoriesList()
            onProgress?.invoke(0.12f, "Đang đồng bộ danh mục (${localCats.size})...")
            for (cat in localCats) {
                userDoc.collection("categories").document(cat.id.toString()).set(
                    mapOf(
                        "id" to cat.id,
                        "name" to cat.name,
                        "userId" to userId,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                ).await()
            }

            // 2. Products & All Product Images
            val localProds = db.productDao().getAllProductsList()
            val totalProds = localProds.size.coerceAtLeast(1)
            for ((idx, prod) in localProds.withIndex()) {
                val pct = 0.15f + (idx.toFloat() / totalProds) * 0.30f
                val hasImg = !prod.imageUri.isNullOrBlank()
                val label = if (hasImg) "Đang tải ảnh & sản phẩm: ${prod.name}..." else "Đang đồng bộ sản phẩm: ${prod.name}..."
                onProgress?.invoke(pct, label)
                val imageBase64 = uriToBase64(context, prod.imageUri) ?: (prod.imageUri ?: "")
                userDoc.collection("products").document(prod.id.toString()).set(
                    mapOf(
                        "id" to prod.id,
                        "name" to prod.name,
                        "categoryId" to prod.categoryId,
                        "importPrice" to prod.importPrice,
                        "sellPrice" to prod.sellPrice,
                        "trackInventory" to prod.trackInventory,
                        "stockQuantity" to prod.stockQuantity,
                        "imageUri" to imageBase64,
                        "supplierName" to prod.supplierName,
                        "userId" to userId,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                ).await()
            }

            // 3. Invoices & Items
            val localInvoices = db.invoiceDao().getAllInvoicesList()
            val localItems = db.invoiceDao().getAllInvoiceItemsList()
            val itemsByInvoice = localItems.groupBy { it.invoiceId }
            val totalInvs = localInvoices.size.coerceAtLeast(1)
            for ((idx, inv) in localInvoices.withIndex()) {
                val pct = 0.45f + (idx.toFloat() / totalInvs) * 0.20f
                onProgress?.invoke(pct, "Đang đồng bộ hóa đơn #${inv.id}...")
                val invItems = itemsByInvoice[inv.id] ?: emptyList()
                val paymentMethod = prefs.getString("invoice_payment_method_${inv.id}", "TM") ?: "TM"
                val isDebt = prefs.getBoolean("invoice_debt_${inv.id}", false)
                val debtTime = prefs.getLong("invoice_debt_time_${inv.id}", 0L)
                val debtPaid = prefs.getFloat("invoice_debt_paid_${inv.id}", 0f).toDouble()
                val debtPaidTm = prefs.getFloat("invoice_debt_paid_tm_${inv.id}", 0f).toDouble()
                val debtPaidCk = prefs.getFloat("invoice_debt_paid_ck_${inv.id}", 0f).toDouble()
                val costActive = prefs.getBoolean("invoice_cost_active_${inv.id}", false)
                val costAmount = prefs.getString("invoice_cost_amount_${inv.id}", "") ?: ""
                val calendarEventId = prefs.getLong("invoice_calendar_event_${inv.id}", -1L)

                userDoc.collection("invoices").document(inv.id.toString()).set(
                    mapOf(
                        "id" to inv.id,
                        "timestamp" to inv.timestamp,
                        "storeName" to inv.storeName,
                        "totalAmount" to inv.totalAmount,
                        "profit" to inv.profit,
                        "customerName" to (inv.customerName ?: ""),
                        "customerPhone" to (inv.customerPhone ?: ""),
                        "userId" to userId,
                        "paymentMethod" to paymentMethod,
                        "isDebt" to isDebt,
                        "debtTime" to debtTime,
                        "debtPaid" to debtPaid,
                        "debtPaidTm" to debtPaidTm,
                        "debtPaidCk" to debtPaidCk,
                        "costActive" to costActive,
                        "costAmount" to costAmount,
                        "calendarEventId" to calendarEventId,
                        "items" to invItems.map { item ->
                            mapOf(
                                "id" to item.id,
                                "productId" to item.productId,
                                "productName" to item.productName,
                                "quantity" to item.quantity,
                                "sellPrice" to item.sellPrice,
                                "importPrice" to item.importPrice
                            )
                        },
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                ).await()
            }

            // 4. Import Orders & Invoice Images
            val localOrders = db.importOrderDao().getAllImportOrdersList()
            val localOrderItems = db.importOrderDao().getAllImportOrderItemsList()
            val itemsByOrder = localOrderItems.groupBy { it.importOrderId }
            val totalOrders = localOrders.size.coerceAtLeast(1)
            for ((idx, order) in localOrders.withIndex()) {
                val pct = 0.65f + (idx.toFloat() / totalOrders) * 0.20f
                val hasImg = !order.invoiceImageUri.isNullOrBlank()
                val label = if (hasImg) "Đang tải ảnh hóa đơn gốc & đơn nhập #${order.id}..." else "Đang đồng bộ đơn nhập #${order.id}..."
                onProgress?.invoke(pct, label)
                val ordItems = itemsByOrder[order.id] ?: emptyList()
                val invoiceImageBase64 = uriToBase64(context, order.invoiceImageUri) ?: (order.invoiceImageUri ?: "")
                val cashPaid = prefs.getFloat("import_order_cash_paid_${order.id}", 0f).toDouble()
                val transferPaid = prefs.getFloat("import_order_transfer_paid_${order.id}", 0f).toDouble()

                userDoc.collection("importOrders").document(order.id.toString()).set(
                    mapOf(
                        "id" to order.id,
                        "timestamp" to order.timestamp,
                        "supplierName" to order.supplierName,
                        "totalAmount" to order.totalAmount,
                        "isDraft" to order.isDraft,
                        "invoiceImageUri" to invoiceImageBase64,
                        "cashPaid" to cashPaid,
                        "transferPaid" to transferPaid,
                        "userId" to userId,
                        "items" to ordItems.map { item ->
                            mapOf(
                                "id" to item.id,
                                "productId" to item.productId,
                                "productName" to item.productName,
                                "quantity" to item.quantity,
                                "importPrice" to item.importPrice
                            )
                        },
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                ).await()
            }

            // 5. Settings & Configurations
            onProgress?.invoke(0.88f, "Đang đồng bộ cấu hình & cài đặt hệ thống...")
            val storeName = prefs.getString("saved_store_name", "") ?: ""
            val isAutoBackup = prefs.getBoolean("auto_backup", true)
            val manualCash = prefs.getFloat("manual_cash_adjustment", 0f).toDouble()
            val manualAccount = prefs.getFloat("manual_account_adjustment", 0f).toDouble()
            val isDarkMode = prefs.getBoolean("is_dark_mode", false)
            val isGridView = prefs.getBoolean("is_grid_view", false)
            val currentLanguage = prefs.getString("current_language", "Tiếng Việt") ?: "Tiếng Việt"

            userDoc.collection("settings").document("general").set(
                mapOf(
                    "storeName" to storeName,
                    "isAutoBackup" to isAutoBackup,
                    "manualCashAdjustment" to manualCash,
                    "manualAccountAdjustment" to manualAccount,
                    "isDarkMode" to isDarkMode,
                    "isGridView" to isGridView,
                    "currentLanguage" to currentLanguage,
                    "userId" to userId,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()

            try {
                val localAcc = com.example.ui.AccountAuthManager.getLocalAccountByUserId(context, userId)
                if (localAcc != null) {
                    userDoc.collection("settings").document("account_auth").set(
                        mapOf(
                            "email" to localAcc.email,
                            "storeName" to localAcc.storeName,
                            "passwordHash" to localAcc.passwordHash,
                            "salt" to localAcc.salt,
                            "userId" to userId,
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    ).await()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not sync account_auth in backupAllDataToCloud", e)
            }

            // 6. Manifest sync
            onProgress?.invoke(0.96f, "Đang hoàn tất đồng bộ an toàn...")
            userDoc.collection("backups").document("latest").set(
                mapOf(
                    "backupTimestamp" to FieldValue.serverTimestamp(),
                    "totalCategories" to localCats.size,
                    "totalProducts" to localProds.size,
                    "totalInvoices" to localInvoices.size,
                    "totalImportOrders" to localOrders.size,
                    "status" to "SUCCESS"
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()

            onProgress?.invoke(1.0f, "Đã đồng bộ hóa thành công toàn bộ dữ liệu & hình ảnh!")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error in backupAllDataToCloud", e)
            onProgress?.invoke(0f, "Lỗi đồng bộ: ${e.localizedMessage}")
            false
        } finally {
            markSyncOperationEnd()
        }
    }

    suspend fun syncAllDataToCloud(
        userId: String,
        onProgress: ((Float, String) -> Unit)? = null
    ): Boolean = backupAllDataToCloud(userId, onProgress)

    private fun attachRealtimeListeners(userId: String) {
        val userDoc = firestore.collection("users").document(userId)

        // 1. Categories listener
        val catReg = userDoc.collection("categories").addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error in categories listener", error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                updateSyncStatusFromSnapshot(snapshot.metadata.hasPendingWrites())
                scope.launch(Dispatchers.IO) {
                    val cloudCats = snapshot.documents.mapNotNull { doc ->
                        val id = doc.getLong("id")?.toInt() ?: doc.id.toIntOrNull() ?: return@mapNotNull null
                        val name = doc.getString("name") ?: ""
                        Category(id = id, name = name)
                    }
                    if (cloudCats.isNotEmpty() || snapshot.documents.isEmpty()) {
                        isApplyingCloudSnapshot = true
                        try {
                            db.withTransaction {
                                db.categoryDao().deleteAllCategories()
                                db.categoryDao().insertCategories(cloudCats)
                            }
                        } finally {
                            isApplyingCloudSnapshot = false
                        }
                    }
                }
            }
        }
        listeners.add(catReg)

        // 2. Products listener
        val prodReg = userDoc.collection("products").addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error in products listener", error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                updateSyncStatusFromSnapshot(snapshot.metadata.hasPendingWrites())
                scope.launch(Dispatchers.IO) {
                    val cloudProds = snapshot.documents.mapNotNull { doc ->
                        val id = doc.getLong("id")?.toInt() ?: doc.id.toIntOrNull() ?: return@mapNotNull null
                        val name = doc.getString("name") ?: ""
                        val categoryId = doc.getLong("categoryId")?.toInt()
                        val importPrice = doc.getDouble("importPrice") ?: doc.getLong("importPrice")?.toDouble() ?: 0.0
                        val sellPrice = doc.getDouble("sellPrice") ?: doc.getLong("sellPrice")?.toDouble() ?: 0.0
                        val trackInventory = doc.getBoolean("trackInventory") ?: true
                        val stockQuantity = doc.getLong("stockQuantity")?.toInt() ?: 0
                        val rawImageUri = doc.getString("imageUri").takeIf { !it.isNullOrBlank() }
                        val imageUri = base64ToLocalUri(context, rawImageUri, "prod", id) ?: rawImageUri
                        val supplierName = doc.getString("supplierName") ?: ""
                        Product(
                            id = id,
                            name = name,
                            categoryId = categoryId,
                            importPrice = importPrice,
                            sellPrice = sellPrice,
                            trackInventory = trackInventory,
                            stockQuantity = stockQuantity,
                            imageUri = imageUri,
                            supplierName = supplierName
                        )
                    }
                    if (cloudProds.isNotEmpty() || snapshot.documents.isEmpty()) {
                        isApplyingCloudSnapshot = true
                        try {
                            db.withTransaction {
                                db.productDao().deleteAllProducts()
                                db.productDao().insertProducts(cloudProds)
                            }
                        } finally {
                            isApplyingCloudSnapshot = false
                        }
                    }
                }
            }
        }
        listeners.add(prodReg)

        // 3. Invoices listener
        val invReg = userDoc.collection("invoices").addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error in invoices listener", error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                updateSyncStatusFromSnapshot(snapshot.metadata.hasPendingWrites())
                scope.launch(Dispatchers.IO) {
                    val invoices = mutableListOf<Invoice>()
                    val invoiceItems = mutableListOf<InvoiceItem>()

                    val prefs = context.getSharedPreferences("proodos_prefs", Context.MODE_PRIVATE)
                    val editor = prefs.edit()
                    prefs.all.keys.filter { it.startsWith("invoice_") }.forEach { editor.remove(it) }

                    for (doc in snapshot.documents) {
                        val id = doc.getLong("id")?.toInt() ?: doc.id.toIntOrNull() ?: continue
                        val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        val storeName = doc.getString("storeName") ?: ""
                        val totalAmount = doc.getDouble("totalAmount") ?: doc.getLong("totalAmount")?.toDouble() ?: 0.0
                        val profit = doc.getDouble("profit") ?: doc.getLong("profit")?.toDouble() ?: 0.0
                        val customerName = doc.getString("customerName").takeIf { !it.isNullOrBlank() }
                        val customerPhone = doc.getString("customerPhone").takeIf { !it.isNullOrBlank() }

                        // Sync invoice metadata into local SharedPreferences
                        doc.getString("paymentMethod")?.let { editor.putString("invoice_payment_method_$id", it) }
                        doc.getBoolean("isDebt")?.let { editor.putBoolean("invoice_debt_$id", it) }
                        doc.getLong("debtTime")?.let { editor.putLong("invoice_debt_time_$id", it) }
                        val debtPaid = doc.getDouble("debtPaid") ?: doc.getLong("debtPaid")?.toDouble()
                        debtPaid?.let { editor.putFloat("invoice_debt_paid_$id", it.toFloat()) }
                        val debtPaidTm = doc.getDouble("debtPaidTm") ?: doc.getLong("debtPaidTm")?.toDouble()
                        debtPaidTm?.let { editor.putFloat("invoice_debt_paid_tm_$id", it.toFloat()) }
                        val debtPaidCk = doc.getDouble("debtPaidCk") ?: doc.getLong("debtPaidCk")?.toDouble()
                        debtPaidCk?.let { editor.putFloat("invoice_debt_paid_ck_$id", it.toFloat()) }
                        doc.getBoolean("costActive")?.let { editor.putBoolean("invoice_cost_active_$id", it) }
                        doc.getString("costAmount")?.let { editor.putString("invoice_cost_amount_$id", it) }
                        doc.getLong("calendarEventId")?.let { editor.putLong("invoice_calendar_event_$id", it) }

                        invoices.add(
                            Invoice(
                                id = id,
                                timestamp = timestamp,
                                storeName = storeName,
                                totalAmount = totalAmount,
                                profit = profit,
                                customerName = customerName,
                                customerPhone = customerPhone
                            )
                        )

                        val rawItems = doc.get("items") as? List<Map<String, Any>> ?: emptyList()
                        for (raw in rawItems) {
                            val itemId = (raw["id"] as? Number)?.toInt() ?: 0
                            val prodId = (raw["productId"] as? Number)?.toInt() ?: 0
                            val prodName = raw["productName"] as? String ?: ""
                            val qty = (raw["quantity"] as? Number)?.toInt() ?: 1
                            val sellPrice = (raw["sellPrice"] as? Number)?.toDouble() ?: 0.0
                            val importPrice = (raw["importPrice"] as? Number)?.toDouble() ?: 0.0
                            invoiceItems.add(
                                InvoiceItem(
                                    id = itemId,
                                    invoiceId = id,
                                    productId = prodId,
                                    productName = prodName,
                                    quantity = qty,
                                    sellPrice = sellPrice,
                                    importPrice = importPrice
                                )
                            )
                        }
                    }
                    editor.apply()

                    isApplyingCloudSnapshot = true
                    try {
                        db.withTransaction {
                            db.invoiceDao().deleteAllInvoices()
                            db.invoiceDao().deleteAllInvoiceItems()
                            db.invoiceDao().insertInvoices(invoices)
                            db.invoiceDao().insertInvoiceItems(invoiceItems)
                        }
                    } finally {
                        isApplyingCloudSnapshot = false
                    }
                }
            }
        }
        listeners.add(invReg)

        // 4. Import Orders listener
        val ordReg = userDoc.collection("importOrders").addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error in importOrders listener", error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                updateSyncStatusFromSnapshot(snapshot.metadata.hasPendingWrites())
                scope.launch(Dispatchers.IO) {
                    val orders = mutableListOf<ImportOrder>()
                    val orderItems = mutableListOf<ImportOrderItem>()
                    val prefs = context.getSharedPreferences("proodos_prefs", Context.MODE_PRIVATE)
                    val ordEditor = prefs.edit()
                    prefs.all.keys.filter { it.startsWith("import_order_") }.forEach { ordEditor.remove(it) }

                    for (doc in snapshot.documents) {
                        val id = doc.getLong("id")?.toInt() ?: doc.id.toIntOrNull() ?: continue
                        val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        val supplierName = doc.getString("supplierName") ?: ""
                        val totalAmount = doc.getDouble("totalAmount") ?: doc.getLong("totalAmount")?.toDouble() ?: 0.0
                        val isDraft = doc.getBoolean("isDraft") ?: false
                        val rawInvoiceImageUri = doc.getString("invoiceImageUri").takeIf { !it.isNullOrBlank() }
                        val invoiceImageUri = base64ToLocalUri(context, rawInvoiceImageUri, "invoice", id) ?: rawInvoiceImageUri

                        val cashPaid = doc.getDouble("cashPaid") ?: doc.getLong("cashPaid")?.toDouble()
                        cashPaid?.let { ordEditor.putFloat("import_order_cash_paid_$id", it.toFloat()) }
                        val transferPaid = doc.getDouble("transferPaid") ?: doc.getLong("transferPaid")?.toDouble()
                        transferPaid?.let { ordEditor.putFloat("import_order_transfer_paid_$id", it.toFloat()) }

                        orders.add(
                            ImportOrder(
                                id = id,
                                timestamp = timestamp,
                                supplierName = supplierName,
                                totalAmount = totalAmount,
                                isDraft = isDraft,
                                invoiceImageUri = invoiceImageUri
                            )
                        )

                        val rawItems = doc.get("items") as? List<Map<String, Any>> ?: emptyList()
                        for (raw in rawItems) {
                            val itemId = (raw["id"] as? Number)?.toInt() ?: 0
                            val prodId = (raw["productId"] as? Number)?.toInt() ?: 0
                            val prodName = raw["productName"] as? String ?: ""
                            val qty = (raw["quantity"] as? Number)?.toInt() ?: 1
                            val importPrice = (raw["importPrice"] as? Number)?.toDouble() ?: 0.0
                            orderItems.add(
                                ImportOrderItem(
                                    id = itemId,
                                    importOrderId = id,
                                    productId = prodId,
                                    productName = prodName,
                                    quantity = qty,
                                    importPrice = importPrice
                                )
                            )
                        }
                    }
                    ordEditor.apply()

                    isApplyingCloudSnapshot = true
                    try {
                        db.withTransaction {
                            db.importOrderDao().deleteAllImportOrders()
                            db.importOrderDao().deleteAllImportOrderItems()
                            db.importOrderDao().insertImportOrders(orders)
                            db.importOrderDao().insertImportOrderItems(orderItems)
                        }
                    } finally {
                        isApplyingCloudSnapshot = false
                    }
                }
            }
        }
        listeners.add(ordReg)

        // 5. Settings listener
        val setReg = userDoc.collection("settings").document("general").addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error in settings listener", error)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                updateSyncStatusFromSnapshot(snapshot.metadata.hasPendingWrites())
                val cloudStoreName = snapshot.getString("storeName")
                val isAutoBackup = snapshot.getBoolean("isAutoBackup")
                val manualCash = snapshot.getDouble("manualCashAdjustment") ?: snapshot.getLong("manualCashAdjustment")?.toDouble() ?: 0.0
                val manualAccount = snapshot.getDouble("manualAccountAdjustment") ?: snapshot.getLong("manualAccountAdjustment")?.toDouble() ?: 0.0
                val isDarkMode = snapshot.getBoolean("isDarkMode")
                val isGridView = snapshot.getBoolean("isGridView")
                val currentLanguage = snapshot.getString("currentLanguage")

                val prefs = context.getSharedPreferences("proodos_prefs", Context.MODE_PRIVATE)
                val editor = prefs.edit()
                if (!cloudStoreName.isNullOrBlank()) {
                    editor.putString("saved_store_name", cloudStoreName)
                }
                if (isAutoBackup != null) {
                    editor.putBoolean("auto_backup", isAutoBackup)
                }
                editor.putFloat("manual_cash_adjustment", manualCash.toFloat())
                editor.putFloat("manual_account_adjustment", manualAccount.toFloat())
                if (isDarkMode != null) {
                    editor.putBoolean("is_dark_mode", isDarkMode)
                }
                if (isGridView != null) {
                    editor.putBoolean("is_grid_view", isGridView)
                }
                if (!currentLanguage.isNullOrBlank()) {
                    editor.putString("current_language", currentLanguage)
                }
                editor.apply()

                onSettingsUpdated?.invoke(cloudStoreName, isDarkMode, isGridView, currentLanguage, manualCash, manualAccount)
            } else if (snapshot != null && !snapshot.exists()) {
                val prefs = context.getSharedPreferences("proodos_prefs", Context.MODE_PRIVATE)
                prefs.edit()
                    .putFloat("manual_cash_adjustment", 0f)
                    .putFloat("manual_account_adjustment", 0f)
                    .apply()
                onSettingsUpdated?.invoke(null, null, null, null, 0.0, 0.0)
            }
        }
        listeners.add(setReg)
    }

    private fun updateSyncStatusFromSnapshot(hasPendingWrites: Boolean) {
        if (activeUserId == null) {
            _syncState.value = SyncState.OFFLINE
            return
        }
        isSnapshotPendingWrites = hasPendingWrites
        if (hasPendingWrites || activeSyncOperations.get() > 0) {
            syncDebounceJob?.cancel()
            _syncState.value = SyncState.SYNCING
        } else {
            // Khi không còn pending writes và không còn tác vụ nào đang ghi:
            // Chờ 600ms ổn định chắc chắn thành công mới chuyển sang màu xanh, tránh chuyển đổi nhấp nháy
            if (_syncState.value != SyncState.SYNCED) {
                syncDebounceJob?.cancel()
                syncDebounceJob = scope.launch {
                    kotlinx.coroutines.delay(800)
                    if (activeUserId != null && activeSyncOperations.get() == 0 && !isSnapshotPendingWrites) {
                        _syncState.value = SyncState.SYNCED
                    }
                }
            }
        }
    }

    // --- Write Synchronization Methods ---

    fun syncSaveProduct(product: Product) {
        val uid = activeUserId ?: return
        if (isApplyingCloudSnapshot) return
        markSyncOperationStart()
        scope.launch {
            try {
                val cloudImage = uriToBase64(context, product.imageUri) ?: (product.imageUri ?: "")
                firestore.collection("users").document(uid)
                    .collection("products").document(product.id.toString())
                    .set(
                        mapOf(
                            "id" to product.id,
                            "name" to product.name,
                            "categoryId" to product.categoryId,
                            "importPrice" to product.importPrice,
                            "sellPrice" to product.sellPrice,
                            "trackInventory" to product.trackInventory,
                            "stockQuantity" to product.stockQuantity,
                            "imageUri" to cloudImage,
                            "supplierName" to product.supplierName,
                            "userId" to uid,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to syncSaveProduct", e)
            } finally {
                markSyncOperationEnd()
            }
        }
    }

    fun syncDeleteProduct(productId: Int) {
        val uid = activeUserId ?: return
        if (isApplyingCloudSnapshot) return
        markSyncOperationStart()
        scope.launch {
            try {
                firestore.collection("users").document(uid)
                    .collection("products").document(productId.toString())
                    .delete().await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to syncDeleteProduct", e)
            } finally {
                markSyncOperationEnd()
            }
        }
    }

    fun syncSaveCategory(category: Category) {
        val uid = activeUserId ?: return
        if (isApplyingCloudSnapshot) return
        markSyncOperationStart()
        scope.launch {
            try {
                firestore.collection("users").document(uid)
                    .collection("categories").document(category.id.toString())
                    .set(
                        mapOf(
                            "id" to category.id,
                            "name" to category.name,
                            "userId" to uid,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to syncSaveCategory", e)
            } finally {
                markSyncOperationEnd()
            }
        }
    }

    fun syncDeleteCategory(categoryId: Int) {
        val uid = activeUserId ?: return
        if (isApplyingCloudSnapshot) return
        markSyncOperationStart()
        scope.launch {
            try {
                firestore.collection("users").document(uid)
                    .collection("categories").document(categoryId.toString())
                    .delete().await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to syncDeleteCategory", e)
            } finally {
                markSyncOperationEnd()
            }
        }
    }

    fun syncSaveInvoice(invoice: Invoice, items: List<InvoiceItem>) {
        val uid = activeUserId ?: return
        if (isApplyingCloudSnapshot) return
        markSyncOperationStart()
        scope.launch {
            try {
                val prefs = context.getSharedPreferences("proodos_prefs", Context.MODE_PRIVATE)
                val paymentMethod = prefs.getString("invoice_payment_method_${invoice.id}", "TM") ?: "TM"
                val isDebt = prefs.getBoolean("invoice_debt_${invoice.id}", false)
                val debtTime = prefs.getLong("invoice_debt_time_${invoice.id}", 0L)
                val debtPaid = prefs.getFloat("invoice_debt_paid_${invoice.id}", 0f).toDouble()
                val debtPaidTm = prefs.getFloat("invoice_debt_paid_tm_${invoice.id}", 0f).toDouble()
                val debtPaidCk = prefs.getFloat("invoice_debt_paid_ck_${invoice.id}", 0f).toDouble()
                val costActive = prefs.getBoolean("invoice_cost_active_${invoice.id}", false)
                val costAmount = prefs.getString("invoice_cost_amount_${invoice.id}", "") ?: ""

                firestore.collection("users").document(uid)
                    .collection("invoices").document(invoice.id.toString())
                    .set(
                        mapOf(
                            "id" to invoice.id,
                            "timestamp" to invoice.timestamp,
                            "storeName" to invoice.storeName,
                            "totalAmount" to invoice.totalAmount,
                            "profit" to invoice.profit,
                            "customerName" to (invoice.customerName ?: ""),
                            "customerPhone" to (invoice.customerPhone ?: ""),
                            "userId" to uid,
                            "paymentMethod" to paymentMethod,
                            "isDebt" to isDebt,
                            "debtTime" to debtTime,
                            "debtPaid" to debtPaid,
                            "debtPaidTm" to debtPaidTm,
                            "debtPaidCk" to debtPaidCk,
                            "costActive" to costActive,
                            "costAmount" to costAmount,
                            "items" to items.map { item ->
                                mapOf(
                                    "id" to item.id,
                                    "productId" to item.productId,
                                    "productName" to item.productName,
                                    "quantity" to item.quantity,
                                    "sellPrice" to item.sellPrice,
                                    "importPrice" to item.importPrice
                                )
                            },
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to syncSaveInvoice", e)
            } finally {
                markSyncOperationEnd()
            }
        }
    }

    fun syncUpdateInvoiceMetadata(invoiceId: Int, updates: Map<String, Any>) {
        val uid = activeUserId ?: return
        if (isApplyingCloudSnapshot) return
        markSyncOperationStart()
        scope.launch {
            try {
                val map = updates.toMutableMap()
                map["updatedAt"] = FieldValue.serverTimestamp()
                firestore.collection("users").document(uid)
                    .collection("invoices").document(invoiceId.toString())
                    .set(map, com.google.firebase.firestore.SetOptions.merge()).await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to syncUpdateInvoiceMetadata", e)
            } finally {
                markSyncOperationEnd()
            }
        }
    }

    fun syncDeleteInvoice(invoiceId: Int) {
        val uid = activeUserId ?: return
        if (isApplyingCloudSnapshot) return
        markSyncOperationStart()
        scope.launch {
            try {
                firestore.collection("users").document(uid)
                    .collection("invoices").document(invoiceId.toString())
                    .delete().await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to syncDeleteInvoice", e)
            } finally {
                markSyncOperationEnd()
            }
        }
    }

    fun syncSaveImportOrder(order: ImportOrder, items: List<ImportOrderItem>) {
        val uid = activeUserId ?: return
        if (isApplyingCloudSnapshot) return
        markSyncOperationStart()
        scope.launch {
            try {
                val prefs = context.getSharedPreferences("proodos_prefs", Context.MODE_PRIVATE)
                val cashPaid = prefs.getFloat("import_order_cash_paid_${order.id}", 0f).toDouble()
                val transferPaid = prefs.getFloat("import_order_transfer_paid_${order.id}", 0f).toDouble()
                val cloudInvoiceImage = uriToBase64(context, order.invoiceImageUri) ?: (order.invoiceImageUri ?: "")
                firestore.collection("users").document(uid)
                    .collection("importOrders").document(order.id.toString())
                    .set(
                        mapOf(
                            "id" to order.id,
                            "timestamp" to order.timestamp,
                            "supplierName" to order.supplierName,
                            "totalAmount" to order.totalAmount,
                            "isDraft" to order.isDraft,
                            "invoiceImageUri" to cloudInvoiceImage,
                            "cashPaid" to cashPaid,
                            "transferPaid" to transferPaid,
                            "userId" to uid,
                            "items" to items.map { item ->
                                mapOf(
                                    "id" to item.id,
                                    "productId" to item.productId,
                                    "productName" to item.productName,
                                    "quantity" to item.quantity,
                                    "importPrice" to item.importPrice
                                )
                            },
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    ).await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to syncSaveImportOrder", e)
            } finally {
                markSyncOperationEnd()
            }
        }
    }

    fun syncDeleteImportOrder(orderId: Int) {
        val uid = activeUserId ?: return
        if (isApplyingCloudSnapshot) return
        markSyncOperationStart()
        scope.launch {
            try {
                firestore.collection("users").document(uid)
                    .collection("importOrders").document(orderId.toString())
                    .delete().await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to syncDeleteImportOrder", e)
            } finally {
                markSyncOperationEnd()
            }
        }
    }

    fun syncSaveSettings(
        storeName: String? = null,
        isAutoBackup: Boolean? = null,
        manualCashAdjustment: Double? = null,
        manualAccountAdjustment: Double? = null,
        isDarkMode: Boolean? = null,
        isGridView: Boolean? = null,
        currentLanguage: String? = null
    ) {
        val uid = activeUserId ?: return
        if (isApplyingCloudSnapshot) return
        markSyncOperationStart()
        scope.launch {
            try {
                val map = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                storeName?.let { map["storeName"] = it }
                isAutoBackup?.let { map["isAutoBackup"] = it }
                manualCashAdjustment?.let { map["manualCashAdjustment"] = it }
                manualAccountAdjustment?.let { map["manualAccountAdjustment"] = it }
                isDarkMode?.let { map["isDarkMode"] = it }
                isGridView?.let { map["isGridView"] = it }
                currentLanguage?.let { map["currentLanguage"] = it }
                firestore.collection("users").document(uid)
                    .collection("settings").document("general")
                    .set(map, com.google.firebase.firestore.SetOptions.merge()).await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to syncSaveSettings", e)
            } finally {
                markSyncOperationEnd()
            }
        }
    }

    fun clearCloudData(userId: String? = activeUserId) {
        val uid = userId ?: activeUserId ?: return
        scope.launch(Dispatchers.IO) {
            try {
                markSyncOperationStart()
                val userDoc = firestore.collection("users").document(uid)
                val cats = userDoc.collection("categories").get().await()
                cats.documents.forEach { try { it.reference.delete().await() } catch (_: Exception) {} }
                val prods = userDoc.collection("products").get().await()
                prods.documents.forEach { try { it.reference.delete().await() } catch (_: Exception) {} }
                val invs = userDoc.collection("invoices").get().await()
                invs.documents.forEach { try { it.reference.delete().await() } catch (_: Exception) {} }
                val ords = userDoc.collection("importOrders").get().await()
                ords.documents.forEach { try { it.reference.delete().await() } catch (_: Exception) {} }
                userDoc.collection("settings").document("general").set(
                    mapOf(
                        "manualCashAdjustment" to 0.0,
                        "manualAccountAdjustment" to 0.0,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                ).await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear cloud data", e)
            } finally {
                markSyncOperationEnd()
            }
        }
    }
}
