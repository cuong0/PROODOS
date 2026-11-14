package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.auth.FirebaseUser
import java.io.File
import java.io.FileOutputStream
import android.net.Uri
import org.json.JSONObject
import org.json.JSONArray
import androidx.room.withTransaction
import org.w3c.dom.Element
import org.w3c.dom.Node
import javax.xml.parsers.DocumentBuilderFactory
import java.io.ByteArrayInputStream
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Rect

enum class MainTab {
    BAN_HANG, HOA_DON, QUAN_LY, CAI_DAT
}

enum class SubScreen {
    CART,
    PRODUCTS_LIST,
    CATEGORIES_LIST,
    CREATE_PRODUCT,
    CREATE_CATEGORY,
    INVOICE_DETAIL,
    EDIT_PRODUCT,
    CUSTOMERS_LIST,
    INVENTORY_MANAGEMENT,
    DEBT_MANAGEMENT,
    IMPORT_MANAGEMENT,
    SIGN_IN,
    ACCOUNT_SETTINGS
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = AppRepository(
        productDao = db.productDao(),
        categoryDao = db.categoryDao(),
        invoiceDao = db.invoiceDao(),
        importOrderDao = db.importOrderDao()
    )
    val userRepository = UserRepository(application)
    val syncManager = FirestoreSyncManager(application, db)
    val syncState: StateFlow<SyncState> = syncManager.syncState

    private val _currentUserProfile = MutableStateFlow<UserProfile?>(null)
    val currentUserProfile: StateFlow<UserProfile?> = _currentUserProfile.asStateFlow()

    private val _currentAppAccount = MutableStateFlow<RegisteredAccount?>(null)
    val currentAppAccount: StateFlow<RegisteredAccount?> = _currentAppAccount.asStateFlow()

    private val _currentFirebaseUser = MutableStateFlow<com.google.firebase.auth.FirebaseUser?>(com.google.firebase.Firebase.auth.currentUser)
    val currentFirebaseUser: StateFlow<com.google.firebase.auth.FirebaseUser?> = _currentFirebaseUser.asStateFlow()

    fun setLoggedInAccount(account: RegisteredAccount?, shouldMigrate: Boolean = true) {
        _currentAppAccount.value = account
        AccountAuthManager.saveActiveAccount(getApplication(), account)
        if (account != null) {
            if (account.storeName.isNotBlank()) {
                setStoreName(account.storeName)
            }
            _currentUserProfile.value = UserProfile(
                id = account.userId,
                userId = account.userId,
                email = account.email,
                displayName = account.storeName.ifBlank { account.email.substringBefore("@") },
                photoURL = account.avatarUrl.takeIf { it.isNotBlank() }
            )
            val fbUser = com.google.firebase.Firebase.auth.currentUser
            if (fbUser != null && (fbUser.uid == account.userId || fbUser.email.equals(account.email, ignoreCase = true))) {
                syncUserProfile(fbUser, shouldMigrate)
            } else if (account.userId.isNotBlank()) {
                syncManager.startSync(account.userId, shouldMigrateLocalData = shouldMigrate)
            }
        } else {
            _currentUserProfile.value = null
            syncUserProfile(null, shouldMigrate)
        }
    }

    fun updateCurrentAccountProfile(
        context: Context,
        newStoreName: String? = null,
        newAvatarUrl: String? = null,
        newPassword: String? = null,
        onResult: (Boolean, String?) -> Unit
    ) {
        val account = _currentAppAccount.value
        if (account == null) {
            onResult(false, "Không tìm thấy thông tin tài khoản hiện tại")
            return
        }
        viewModelScope.launch {
            try {
                val updated = AccountAuthManager.updateAccountFull(
                    context = context,
                    syncManager = syncManager,
                    account = account,
                    newStoreName = newStoreName,
                    newAvatarUrl = newAvatarUrl,
                    newPassword = newPassword
                )
                _currentAppAccount.value = updated
                if (newStoreName != null && newStoreName.isNotBlank()) {
                    setStoreName(newStoreName)
                }
                _currentUserProfile.value = UserProfile(
                    id = updated.userId,
                    userId = updated.userId,
                    email = updated.email,
                    displayName = updated.storeName.ifBlank { updated.email.substringBefore("@") },
                    photoURL = updated.avatarUrl.takeIf { it.isNotBlank() }
                )
                onResult(true, null)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error updating account profile", e)
                onResult(false, e.localizedMessage ?: "Lỗi khi cập nhật tài khoản")
            }
        }
    }

    fun resetLocalFinancialPrefs(context: Context = getApplication()) {
        manualCashAdjustment.value = 0.0
        manualAccountAdjustment.value = 0.0
        try {
            val p = context.getSharedPreferences("proodos_prefs", Context.MODE_PRIVATE)
            val editor = p.edit()
            editor.putFloat("manual_cash_adjustment", 0f)
            editor.putFloat("manual_account_adjustment", 0f)
            p.all.keys.filter { it.startsWith("invoice_") || it.startsWith("import_order_") }.forEach { key ->
                editor.remove(key)
            }
            editor.commit()
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error resetting local financial prefs", e)
        }
    }

    fun deleteCurrentAccount(
        context: Context,
        onComplete: (Boolean) -> Unit
    ) {
        val account = _currentAppAccount.value
        if (account == null) {
            onComplete(false)
            return
        }
        viewModelScope.launch {
            try {
                syncManager.stopSync()
                resetLocalFinancialPrefs(context)
                val success = AccountAuthManager.deleteAccountAndAllData(context, syncManager, account)
                setLoggedInAccount(null)
                syncUserProfile(null)
                setStoreName("")

                // 1. Xóa sạch toàn bộ cơ sở dữ liệu Room cục bộ (sản phẩm, danh mục, hóa đơn, đơn nhập hàng...)
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        GuestBackupManager.clearDatabase(db)
                        db.clearAllTables()
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "Error clearing local database tables", e)
                    }
                }

                // 2. Xóa sạch mọi file sao lưu dự phòng (guest backup, file .json)
                GuestBackupManager.deleteGuestBackup(context)
                try {
                    context.filesDir.listFiles()?.forEach { file ->
                        if (file.isFile && file.name.endsWith(".json")) {
                            file.delete()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Error deleting local json backup files", e)
                }

                // 3. Xóa sạch ảnh đại diện, ảnh hóa đơn, ảnh sản phẩm đã lưu cục bộ
                try {
                    File(context.filesDir, "avatars").deleteRecursively()
                    File(context.filesDir, "invoice_images").deleteRecursively()
                    File(context.filesDir, "product_images").deleteRecursively()
                    File(context.filesDir, "synced_images").deleteRecursively()
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Error deleting media cache", e)
                }

                // 4. Xóa sạch toàn bộ cấu hình, cài đặt (settings), tiền điều chỉnh, thông tin cửa hàng...
                try {
                    val p = context.getSharedPreferences("proodos_prefs", Context.MODE_PRIVATE)
                    p.edit().clear().commit()
                    val accPrefs = context.getSharedPreferences("registered_user_accounts", Context.MODE_PRIVATE)
                    accPrefs.edit().clear().commit()
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Error clearing preferences", e)
                }

                // 5. Đặt lại toàn bộ biến trạng thái bộ nhớ và giỏ hàng
                manualCashAdjustment.value = 0.0
                manualAccountAdjustment.value = 0.0
                clearCart()

                // 6. Đăng xuất khỏi Firebase Auth
                try {
                    com.google.firebase.Firebase.auth.signOut()
                } catch (_: Exception) {}

                onComplete(success)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error deleting account", e)
                onComplete(false)
            }
        }
    }

    fun syncUserProfile(user: com.google.firebase.auth.FirebaseUser?, shouldMigrate: Boolean = false) {
        _currentFirebaseUser.value = user
        if (user == null) {
            val appAcc = _currentAppAccount.value
            if (appAcc != null && appAcc.userId.isNotBlank()) {
                // Người dùng đang đăng nhập bằng tài khoản app (ví dụ trên thiết bị mới)
                _currentUserProfile.value = UserProfile(
                    id = appAcc.userId,
                    userId = appAcc.userId,
                    email = appAcc.email,
                    displayName = appAcc.storeName.ifBlank { appAcc.email.substringBefore("@") }
                )
                syncManager.startSync(appAcc.userId, shouldMigrateLocalData = shouldMigrate)
                return
            }
            _currentUserProfile.value = null
            syncManager.stopSync()
            return
        }
        syncManager.startSync(user.uid, shouldMigrateLocalData = shouldMigrate)
        viewModelScope.launch {
            userRepository.createOrUpdateProfileOnSignIn(
                userId = user.uid,
                email = user.email ?: "",
                displayName = user.displayName ?: user.email?.substringBefore("@") ?: "Người dùng Google",
                photoURL = user.photoUrl?.toString()
            )
            userRepository.observeUserProfile(user.uid).collect { profile ->
                _currentUserProfile.value = profile
            }
        }
    }

    suspend fun prepareLoginForExistingAccount(context: Context): Boolean = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val p = context.getSharedPreferences("proodos_prefs", Context.MODE_PRIVATE)
        p.edit().putBoolean("has_merged_guest_backup_in_session", false).apply()
        val isCurrentlyLoggedIn = _currentAppAccount.value != null
        var hasBackedUp = false
        if (!isCurrentlyLoggedIn && GuestBackupManager.hasLocalData(db)) {
            val saved = GuestBackupManager.saveGuestBackup(context, db)
            if (saved) {
                GuestBackupManager.clearDatabase(db)
                hasBackedUp = true
            }
        }
        withContext(kotlinx.coroutines.Dispatchers.Main) {
            resetLocalFinancialPrefs(context)
        }
        return@withContext if (hasBackedUp) true else GuestBackupManager.hasGuestBackup(context)
    }

    suspend fun prepareForNewAccountRegistration(context: Context, userId: String) = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val p = context.getSharedPreferences("proodos_prefs", Context.MODE_PRIVATE)
        val hasLocal = GuestBackupManager.hasLocalData(db)
        if (!hasLocal) {
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                resetLocalFinancialPrefs(context)
            }
        } else {
            val localProds = db.productDao().getAllProductsList()
            val localInvs = db.invoiceDao().getAllInvoicesList()
            val localOrders = db.importOrderDao().getAllImportOrdersList()
            if (localProds.isEmpty() && localInvs.isEmpty() && localOrders.isEmpty()) {
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    resetLocalFinancialPrefs(context)
                }
            }
            if (!GuestBackupManager.hasGuestBackup(context)) {
                GuestBackupManager.saveGuestBackup(context, db)
            }
        }
        p.edit().putBoolean("has_merged_guest_backup_in_session", true).apply()
        // Xóa sạch mọi dữ liệu tồn đọng trên đám mây của userId này (trường hợp tài khoản từng bị xóa và tạo lại cùng Gmail)
        syncManager.wipeResidualCloudUserData(userId)
    }

    fun clearGuestBackupOnNewAccountRegistered(context: Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val p = context.getSharedPreferences("proodos_prefs", Context.MODE_PRIVATE)
            if (!GuestBackupManager.hasGuestBackup(context) && GuestBackupManager.hasLocalData(db)) {
                GuestBackupManager.saveGuestBackup(context, db)
            }
            p.edit().putBoolean("has_merged_guest_backup_in_session", true).apply()
        }
    }

    fun mergeDeviceDataIntoAccount(
        context: Context,
        onComplete: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val uid = syncManager.activeUserId
                ?: _currentAppAccount.value?.userId
                ?: com.google.firebase.Firebase.auth.currentUser?.uid
            if (uid.isNullOrBlank()) {
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onComplete(false, "Vui lòng đăng nhập tài khoản trước khi đồng bộ dữ liệu.")
                }
                return@launch
            }

            try {
                var mergedProductCount = 0
                var addedProductCount = 0
                var addedInvoiceCount = 0
                var addedImportOrderCount = 0

                val prefs = context.getSharedPreferences("proodos_prefs", Context.MODE_PRIVATE)
                val editor = prefs.edit()
                val alreadyMergedInSession = prefs.getBoolean("has_merged_guest_backup_in_session", false)

                // 1. Kiểm tra và gộp file sao lưu khách (Guest Backup) nếu có và chưa gộp trong phiên đăng nhập này
                val guestData = if (!alreadyMergedInSession) GuestBackupManager.readGuestBackup(context) else null
                if (guestData != null && (guestData.products.isNotEmpty() || guestData.categories.isNotEmpty() || guestData.invoices.isNotEmpty() || guestData.importOrders.isNotEmpty())) {
                    val currentCats = db.categoryDao().getAllCategoriesList().toMutableList()
                    val currentProds = db.productDao().getAllProductsList().toMutableList()
                    val currentInvs = db.invoiceDao().getAllInvoicesList().toMutableList()
                    val currentOrders = db.importOrderDao().getAllImportOrdersList().toMutableList()

                    // Danh mục
                    val catIdMap = mutableMapOf<Int, Int>()
                    for (gCat in guestData.categories) {
                        val existing = currentCats.find { it.name.trim().equals(gCat.name.trim(), ignoreCase = true) }
                        if (existing != null) {
                            catIdMap[gCat.id] = existing.id
                        } else {
                            val newId = db.categoryDao().insertCategory(Category(name = gCat.name)).toInt()
                            val newCat = Category(id = newId, name = gCat.name)
                            currentCats.add(newCat)
                            catIdMap[gCat.id] = newId
                        }
                    }

                    // Sản phẩm
                    val prodIdMap = mutableMapOf<Int, Int>()
                    for (gProd in guestData.products) {
                        val matched = currentProds.find { accProd ->
                            accProd.name.trim().equals(gProd.name.trim(), ignoreCase = true) &&
                            Math.abs(accProd.importPrice - gProd.importPrice) < 0.001 &&
                            Math.abs(accProd.sellPrice - gProd.sellPrice) < 0.001 &&
                            accProd.supplierName.trim().equals(gProd.supplierName.trim(), ignoreCase = true)
                        }

                        if (matched != null) {
                            val newStock = matched.stockQuantity + gProd.stockQuantity
                            val updatedAccProd = matched.copy(stockQuantity = newStock)
                            db.productDao().updateProduct(updatedAccProd)
                            val idx = currentProds.indexOfFirst { it.id == matched.id }
                            if (idx >= 0) currentProds[idx] = updatedAccProd
                            prodIdMap[gProd.id] = matched.id
                            mergedProductCount++
                        } else {
                            val mappedCatId = gProd.categoryId?.let { catIdMap[it] }
                            val newProd = Product(
                                name = gProd.name,
                                categoryId = mappedCatId,
                                importPrice = gProd.importPrice,
                                sellPrice = gProd.sellPrice,
                                trackInventory = gProd.trackInventory,
                                stockQuantity = gProd.stockQuantity,
                                imageUri = gProd.imageUri,
                                supplierName = gProd.supplierName
                            )
                            val newId = db.productDao().insertProduct(newProd).toInt()
                            val created = newProd.copy(id = newId)
                            currentProds.add(created)
                            prodIdMap[gProd.id] = newId
                            addedProductCount++
                        }
                    }

                    // Hóa đơn & Công nợ
                    for (gInv in guestData.invoices) {
                        val alreadyExists = currentInvs.any {
                            it.timestamp == gInv.timestamp &&
                            Math.abs(it.totalAmount - gInv.totalAmount) < 0.001 &&
                            it.storeName == gInv.storeName
                        }
                        if (alreadyExists) continue

                        val newInvoice = Invoice(
                            timestamp = gInv.timestamp,
                            storeName = gInv.storeName,
                            totalAmount = gInv.totalAmount,
                            profit = gInv.profit,
                            customerName = gInv.customerName,
                            customerPhone = gInv.customerPhone
                        )
                        val newInvId = db.invoiceDao().insertInvoice(newInvoice).toInt()
                        val savedInvoice = newInvoice.copy(id = newInvId)
                        currentInvs.add(savedInvoice)

                        val itemsForInv = guestData.invoiceItems.filter { it.invoiceId == gInv.id }
                        val newItems = itemsForInv.map { item ->
                            val mappedPId = prodIdMap[item.productId] ?: item.productId
                            InvoiceItem(
                                invoiceId = newInvId,
                                productId = mappedPId,
                                productName = item.productName,
                                quantity = item.quantity,
                                sellPrice = item.sellPrice,
                                importPrice = item.importPrice
                            )
                        }
                        db.invoiceDao().insertInvoiceItems(newItems)

                        val oldId = gInv.id
                        val pMethod = guestData.extraPrefs["invoice_payment_method_$oldId"]?.toString() ?: "TM"
                        val isDebt = guestData.extraPrefs["invoice_debt_$oldId"] as? Boolean ?: false
                        val debtTime = (guestData.extraPrefs["invoice_debt_time_$oldId"] as? Number)?.toLong() ?: 0L
                        val debtPaid = (guestData.extraPrefs["invoice_debt_paid_$oldId"] as? Number)?.toFloat() ?: 0f
                        val debtPaidTm = (guestData.extraPrefs["invoice_debt_paid_tm_$oldId"] as? Number)?.toFloat() ?: 0f
                        val debtPaidCk = (guestData.extraPrefs["invoice_debt_paid_ck_$oldId"] as? Number)?.toFloat() ?: 0f
                        val costActive = guestData.extraPrefs["invoice_cost_active_$oldId"] as? Boolean ?: false
                        val costAmount = guestData.extraPrefs["invoice_cost_amount_$oldId"]?.toString() ?: ""

                        editor.putString("invoice_payment_method_$newInvId", pMethod)
                        editor.putBoolean("invoice_debt_$newInvId", isDebt)
                        editor.putLong("invoice_debt_time_$newInvId", debtTime)
                        editor.putFloat("invoice_debt_paid_$newInvId", debtPaid)
                        editor.putFloat("invoice_debt_paid_tm_$newInvId", debtPaidTm)
                        editor.putFloat("invoice_debt_paid_ck_$newInvId", debtPaidCk)
                        editor.putBoolean("invoice_cost_active_$newInvId", costActive)
                        editor.putString("invoice_cost_amount_$newInvId", costAmount)
                        addedInvoiceCount++
                    }

                    // Đơn hàng nhập
                    for (gOrder in guestData.importOrders) {
                        val alreadyExists = currentOrders.any {
                            it.timestamp == gOrder.timestamp &&
                            it.supplierName.equals(gOrder.supplierName, ignoreCase = true) &&
                            Math.abs(it.totalAmount - gOrder.totalAmount) < 0.001
                        }
                        if (alreadyExists) continue

                        val newOrder = ImportOrder(
                            timestamp = gOrder.timestamp,
                            supplierName = gOrder.supplierName,
                            totalAmount = gOrder.totalAmount,
                            isDraft = gOrder.isDraft,
                            invoiceImageUri = gOrder.invoiceImageUri
                        )
                        val newOrderId = db.importOrderDao().insertImportOrder(newOrder).toInt()
                        val savedOrder = newOrder.copy(id = newOrderId)
                        currentOrders.add(savedOrder)

                        val itemsForOrder = guestData.importOrderItems.filter { it.importOrderId == gOrder.id }
                        val newItems = itemsForOrder.map { item ->
                            val mappedPId = prodIdMap[item.productId] ?: item.productId
                            ImportOrderItem(
                                importOrderId = newOrderId,
                                productId = mappedPId,
                                productName = item.productName,
                                quantity = item.quantity,
                                importPrice = item.importPrice
                            )
                        }
                        db.importOrderDao().insertImportOrderItems(newItems)

                        val oldId = gOrder.id
                        val cashPaid = (guestData.extraPrefs["import_order_cash_paid_$oldId"] as? Number)?.toFloat() ?: 0f
                        val transferPaid = (guestData.extraPrefs["import_order_transfer_paid_$oldId"] as? Number)?.toFloat() ?: 0f
                        if (cashPaid > 0f) editor.putFloat("import_order_cash_paid_$newOrderId", cashPaid)
                        if (transferPaid > 0f) editor.putFloat("import_order_transfer_paid_$newOrderId", transferPaid)
                        addedImportOrderCount++
                    }

                    // Đánh dấu đã gộp trong phiên này nhưng GIỮ NGUYÊN file GuestBackup trên máy
                    // để khi người dùng ấn Đăng xuất thì dữ liệu sẵn có trên máy trước khi đăng nhập vẫn được giữ nguyên!
                    editor.putBoolean("has_merged_guest_backup_in_session", true)
                    editor.apply()
                }

                // 2. Lấy toàn bộ dữ liệu hiện có trong máy (SQLite Room Database)
                val localCats = db.categoryDao().getAllCategoriesList()
                val localProds = db.productDao().getAllProductsList()
                val localInvs = db.invoiceDao().getAllInvoicesList()
                val localOrders = db.importOrderDao().getAllImportOrdersList()

                if (localCats.isEmpty() && localProds.isEmpty() && localInvs.isEmpty() && localOrders.isEmpty()) {
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onComplete(false, "Không tìm thấy dữ liệu nào trên máy để đồng bộ.")
                    }
                    return@launch
                }

                // Nếu trước đó chưa có bản sao lưu cục bộ trên máy thì tạo bản lưu để đảm bảo khi đăng xuất dữ liệu không bị mất
                if (!GuestBackupManager.hasGuestBackup(context)) {
                    GuestBackupManager.saveGuestBackup(context, db)
                    prefs.edit().putBoolean("has_merged_guest_backup_in_session", true).apply()
                }

                // 3. ĐỒNG BỘ TOÀN BỘ DỮ LIỆU ĐANG CÓ TRÊN MÁY LÊN CLOUD FIRESTORE
                // (Bao gồm cả sản phẩm, ảnh, tồn kho, hóa đơn, công nợ, đơn hàng nhập, cài đặt)
                val syncSuccess = syncManager.backupAllDataToCloud(uid)

                // Đảm bảo bộ lắng nghe realtime đang hoạt động
                syncManager.startSync(uid, shouldMigrateLocalData = false)

                // Tính tổng số đơn nợ (công nợ)
                val totalDebtsCount = localInvs.count {
                    prefs.getBoolean("invoice_debt_${it.id}", false) || prefs.getFloat("invoice_debt_paid_${it.id}", 0f) > 0f
                }

                val mergeNote = if (mergedProductCount > 0) " (Đã gộp số lượng $mergedProductCount mặt hàng trùng)" else ""
                val summary = "Đồng bộ thành công toàn bộ dữ liệu trên máy lên đám mây:\n• ${localProds.size} sản phẩm$mergeNote\n• ${localInvs.size} hóa đơn bán hàng\n• $totalDebtsCount đơn công nợ\n• ${localOrders.size} đơn nhập hàng!"

                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onComplete(true, summary)
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error in mergeDeviceDataIntoAccount", e)
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onComplete(false, "Lỗi khi đồng bộ dữ liệu: ${e.localizedMessage}")
                }
            }
        }
    }

    fun signOutAndRestoreGuestData(
        context: Context,
        credentialManager: androidx.credentials.CredentialManager,
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            try {
                // 1. Dừng đồng bộ và hủy phiên làm việc
                syncManager.stopSync()
                setLoggedInAccount(null)
                resetLocalFinancialPrefs(context)
                val prefs = context.getSharedPreferences("proodos_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("has_merged_guest_backup_in_session", false).apply()

                // 2. Xóa dữ liệu của tài khoản vừa đăng xuất khỏi máy và khôi phục lại dữ liệu có sẵn trên máy trước khi đăng nhập
                var restored = false
                if (GuestBackupManager.hasGuestBackup(context)) {
                    restored = withContext(kotlinx.coroutines.Dispatchers.IO) {
                        GuestBackupManager.clearDatabase(db)
                        GuestBackupManager.restoreGuestBackup(context, db)
                    }
                    val guestStore = prefs.getString("guest_backup_store_name", "") ?: ""
                    setStoreName(guestStore)
                    if (restored) {
                        GuestBackupManager.deleteGuestBackup(context)
                    }
                } else {
                    withContext(kotlinx.coroutines.Dispatchers.IO) {
                        GuestBackupManager.clearDatabase(db)
                    }
                    setStoreName("")
                }

                // 3. Đăng xuất khỏi Firebase Auth & Credential Manager
                AuthManager.signOut(
                    context = context,
                    credentialManager = credentialManager,
                    onSignOutComplete = {
                        syncUserProfile(null)
                        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                            onComplete(restored)
                        }
                    },
                    scope = viewModelScope
                )
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error in signOutAndRestoreGuestData", e)
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onComplete(false)
                }
            }
        }
    }

    fun backupAllDataBeforeSignOut(
        userId: String,
        onProgress: (Float, String) -> Unit,
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val success = syncManager.backupAllDataToCloud(userId) { progress, message ->
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    onProgress(progress, message)
                }
            }
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                onComplete(success)
            }
        }
    }

    fun syncAllDataNow(
        onProgress: (Float, String) -> Unit = { _, _ -> },
        onComplete: (Boolean) -> Unit = {}
    ) {
        val uid = _currentFirebaseUser.value?.uid ?: _currentAppAccount.value?.userId ?: return
        viewModelScope.launch {
            val success = syncManager.syncAllDataToCloud(uid) { progress, message ->
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    onProgress(progress, message)
                }
            }
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                onComplete(success)
            }
        }
    }

    private val listScrollPositions = mutableMapOf<String, Pair<Int, Int>>()

    fun saveScrollPosition(key: String, index: Int, offset: Int) {
        listScrollPositions[key] = Pair(index, offset)
    }

    fun getScrollPosition(key: String): Pair<Int, Int> {
        return listScrollPositions[key] ?: Pair(0, 0)
    }

    private val prefs = application.getSharedPreferences("proodos_prefs", Context.MODE_PRIVATE)

    // Database UI States
    val products = repository.allProducts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val categories = repository.allCategories.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val invoices = repository.allInvoicesWithItems.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val importOrders = repository.allImportOrdersWithItems.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Bottom Navigation & Hierarchy Settings
    var currentTab = MutableStateFlow(MainTab.BAN_HANG)
        private set

    var currentSubScreen = MutableStateFlow<SubScreen?>(null)
        private set

    // First-launch privacy policy popup (displayed first on initial install/launch)
    var showFirstLaunchPrivacyDialog = MutableStateFlow(!prefs.getBoolean("has_accepted_privacy_policy_v4", false))
        private set

    // First-launch onboarding tour (displayed after privacy policy acceptance and app permissions)
    var showOnboardingTour = MutableStateFlow(
        prefs.getBoolean("has_accepted_privacy_policy_v4", false) &&
        !prefs.getBoolean("has_seen_onboarding_tour", false)
    )
        private set

    var onboardingStepIndex = MutableStateFlow(0)
        private set

    fun acceptPrivacyPolicy() {
        prefs.edit()
            .putBoolean("has_accepted_privacy_policy_v4", true)
            .putBoolean("has_seen_onboarding_tour", false)
            .apply()
        showFirstLaunchPrivacyDialog.value = false
    }

    fun startOnboardingAfterPermissions() {
        prefs.edit().putBoolean("has_seen_onboarding_tour", false).apply()
        onboardingStepIndex.value = 0
        syncTabForOnboardingStep(0)
        showOnboardingTour.value = true
    }

    // Coordinates of targets on screen for pointing tooltip ("tin nổi lên chỉ vào phần cần giới thiệu")
    val onboardingTargetBounds = mutableStateMapOf<String, Rect>()

    fun registerOnboardingTarget(key: String, bounds: Rect) {
        onboardingTargetBounds[key] = bounds
    }

    fun syncTabForOnboardingStep(step: Int) {
        when (step) {
            0 -> {
                selectTab(MainTab.BAN_HANG)
                selectSubScreen(null)
            }
            1 -> {
                selectTab(MainTab.HOA_DON)
                selectSubScreen(null)
            }
            in 2..6 -> {
                selectTab(MainTab.QUAN_LY)
                selectSubScreen(null)
            }
            7 -> {
                selectTab(MainTab.CAI_DAT)
                selectSubScreen(null)
            }
        }
    }

    fun nextOnboardingStep() {
        val next = onboardingStepIndex.value + 1
        if (next < 8) {
            onboardingStepIndex.value = next
            syncTabForOnboardingStep(next)
        } else {
            completeOnboardingTour()
        }
    }

    fun previousOnboardingStep() {
        val prev = onboardingStepIndex.value - 1
        if (prev >= 0) {
            onboardingStepIndex.value = prev
            syncTabForOnboardingStep(prev)
        } else {
            completeOnboardingTour()
        }
    }

    fun completeOnboardingTour() {
        prefs.edit().putBoolean("has_seen_onboarding_tour", true).apply()
        showOnboardingTour.value = false
        onboardingStepIndex.value = 0
        selectTab(MainTab.BAN_HANG)
        selectSubScreen(null)
    }

    fun restartOnboardingTour() {
        onboardingStepIndex.value = 0
        syncTabForOnboardingStep(0)
        showOnboardingTour.value = true
    }

    // Preference States (Loaded from SharedPreferences)
    var isGridView = MutableStateFlow(prefs.getBoolean("is_grid_view", false))
        private set

    var isDarkMode = MutableStateFlow(prefs.getBoolean("is_dark_mode", false))
        private set

    var currentLanguage = MutableStateFlow(Translator.normalizeLanguage(prefs.getString("current_language", "Tiếng Việt") ?: "Tiếng Việt"))
        private set

    // Auto-saved POS store name (Starts empty for new installations)
    var storeName = MutableStateFlow(prefs.getString("saved_store_name", "") ?: "")
        private set

    var manualCashAdjustment = MutableStateFlow(try { prefs.getFloat("manual_cash_adjustment", 0f).toDouble() } catch (e: Exception) { 0.0 })
        private set

    var manualAccountAdjustment = MutableStateFlow(try { prefs.getFloat("manual_account_adjustment", 0f).toDouble() } catch (e: Exception) { 0.0 })
        private set

    // Active POS Cart
    private val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart: StateFlow<List<CartItem>> = _cart.asStateFlow()

    // Persisted customer name in Cart (survives screen changes and abrupt app exits)
    var cartCustomerName = MutableStateFlow(prefs.getString("saved_cart_customer_name", "") ?: "")
        private set

    fun setCartCustomerName(name: String) {
        cartCustomerName.value = name
        prefs.edit().putString("saved_cart_customer_name", name).apply()
        if (name.isNotBlank()) {
            applyCustomerPricingToCart(name)
        }
    }

    // Active detail view item
    var activeInvoice = MutableStateFlow<InvoiceWithItems?>(null)
        private set

    var editingProduct = MutableStateFlow<Product?>(null)
        private set

    val backupProgress = BackupManager.backupProgress
    val backupMessage = BackupManager.backupMessage
    
    private var activeBackupUri: android.net.Uri? = null

    fun cancelBackup(context: android.content.Context) {
        BackupManager.exportBackupJob?.let { job ->
            if (job.isActive) {
                job.cancel()
                val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                val notificationId = 1001
                notificationManager.cancel(notificationId)
                
                activeBackupUri?.let { uri ->
                    val resolver = context.contentResolver
                    try {
                        android.provider.DocumentsContract.deleteDocument(resolver, uri)
                    } catch (t: Throwable) {
                        try {
                            resolver.openOutputStream(uri, "rwt")?.use { it.write(ByteArray(0)) }
                        } catch (t2: Throwable) {
                            t2.printStackTrace()
                        }
                    }
                }
                
                backupProgress.value = null
                backupMessage.value = null
            }
        }
    }

    fun startEditingProduct(product: Product) {
        editingProduct.value = product
        selectSubScreen(SubScreen.EDIT_PRODUCT)
    }

    init {
        syncManager.onSettingsUpdated = { storeNameVal, isDark, isGrid, lang, manualCash, manualAccount ->
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                storeNameVal?.let { if (it.isNotBlank()) storeName.value = it }
                isDark?.let { isDarkMode.value = it }
                isGrid?.let { isGridView.value = it }
                lang?.let {
                    if (it.isNotBlank()) {
                        val norm = Translator.normalizeLanguage(it)
                        currentLanguage.value = norm
                        Translator.currentLanguageState.value = norm
                    }
                }
                manualCash?.let { manualCashAdjustment.value = it }
                manualAccount?.let { manualAccountAdjustment.value = it }
            }
        }
        val activeAccount = AccountAuthManager.getActiveAccount(getApplication())
        if (activeAccount != null) {
            _currentAppAccount.value = activeAccount
            if (activeAccount.storeName.isNotBlank() && storeName.value.isBlank()) {
                storeName.value = activeAccount.storeName
            }
            if (_currentUserProfile.value == null) {
                _currentUserProfile.value = UserProfile(
                    id = activeAccount.userId,
                    userId = activeAccount.userId,
                    email = activeAccount.email,
                    displayName = activeAccount.storeName.ifBlank { activeAccount.email.substringBefore("@") }
                )
            }
            if (activeAccount.userId.isNotBlank()) {
                syncManager.startSync(activeAccount.userId)
            }
        } else {
            // Không có tài khoản hoạt động: đăng xuất Firebase nếu có phiên xác thực dở dang từ việc thoát đăng ký
            if (com.google.firebase.Firebase.auth.currentUser != null) {
                try {
                    com.google.firebase.Firebase.auth.signOut()
                } catch (_: Exception) {}
            }
            _currentFirebaseUser.value = null
            _currentUserProfile.value = null
            _currentAppAccount.value = null
        }
        loadCartFromPrefs()
        viewModelScope.launch {
            repository.syncAllSuppliersToProducts()
        }

        // No pre-seeded categories on new installation - user starts with zero categories
        val hasInit = prefs.getBoolean("has_initialized_dummy_data", false)
        if (!hasInit) {
            prefs.edit().putBoolean("has_initialized_dummy_data", true).apply()
        }

        // Clean up legacy default store name if it matches the hardcoded preset
        if (prefs.getString("saved_store_name", "") == "Cửa hàng của Cường") {
            prefs.edit().putString("saved_store_name", "").apply()
            storeName.value = ""
        }
        val hasCleanedMockCategories = prefs.getBoolean("has_cleaned_initial_mock_categories", false)
        if (!hasCleanedMockCategories) {
            viewModelScope.launch {
                val mockCategoryNames = setOf("Điện tử", "Thực phẩm", "Gia dụng", "Khác")
                repository.allCategories.first().filter { it.name in mockCategoryNames }.forEach { cat ->
                    // Un-link any tied products first
                    repository.allProducts.first().filter { it.categoryId == cat.id }.forEach { prod ->
                        repository.updateProduct(prod.copy(categoryId = null))
                    }
                    repository.deleteCategory(cat)
                }
                prefs.edit().putBoolean("has_cleaned_initial_mock_categories", true).apply()
            }
        }

        // Clean up any default mock products that might have been seeded on earlier installs
        val hasCleanedMockProducts = prefs.getBoolean("has_cleaned_initial_mock_products", false)
        if (!hasCleanedMockProducts) {
            viewModelScope.launch {
                val mockNames = setOf(
                    "Điện thoại Samsung S24",
                    "Sữa đặc Ông Thọ 380g",
                    "Chảo chống dính Sunhouse",
                    "Bánh Karo sợi gà"
                )
                repository.allProducts.first().filter { it.name in mockNames }.forEach { prod ->
                    repository.deleteProduct(prod)
                }
                prefs.edit().putBoolean("has_cleaned_initial_mock_products", true).apply()
            }
        }

        // Auto-heal existing invoices where customerName was saved but storeName was blank or mismatched
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val allInvs = repository.allInvoicesWithItems.first()
                for (invWithItems in allInvs) {
                    val inv = invWithItems.invoice
                    val cust = inv.customerName?.trim()
                    if (!cust.isNullOrEmpty() && inv.storeName.trim() != cust) {
                        val healed = inv.copy(storeName = cust, customerName = cust)
                        db.invoiceDao().updateInvoice(healed)
                        syncManager.syncSaveInvoice(healed, invWithItems.items)
                    }
                }
            } catch (e: Exception) {
                Log.w("MainViewModel", "Error healing invoice customer names", e)
            }
        }
    }

    fun syncAllSuppliersToProducts(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.syncAllSuppliersToProducts()
            onComplete()
        }
    }

    fun clearAllData(onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.clearAllData()
            syncManager.clearCloudData()
            manualCashAdjustment.value = 0.0
            manualAccountAdjustment.value = 0.0
            // Ensure we don't automatically rebuild mock data on next start and clear all other transactions
            val editor = prefs.edit()
            val keysToKeep = setOf(
                "is_dark_mode",
                "is_grid_view",
                "current_language",
                "saved_store_name",
                "has_initialized_dummy_data",
                "has_cleaned_initial_mock_products",
                "has_cleaned_initial_mock_categories",
                "has_seen_onboarding_tour",
                "has_accepted_privacy_policy",
                "has_accepted_privacy_policy_v2",
                "has_accepted_privacy_policy_v3",
                "has_accepted_privacy_policy_v4"
            )
            prefs.all.keys.forEach { key ->
                if (key !in keysToKeep) {
                    editor.remove(key)
                }
            }
            editor.putFloat("manual_cash_adjustment", 0f)
            editor.putFloat("manual_account_adjustment", 0f)
            editor.remove("saved_cart_items")
            editor.putBoolean("has_initialized_dummy_data", true)
            editor.apply()
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                clearCart()
                onComplete()
            }
        }
    }

    // --- Tab / Navigation operations ---
    fun selectTab(tab: MainTab) {
        currentTab.value = tab
        currentSubScreen.value = null
    }

    fun selectSubScreen(sub: SubScreen?) {
        currentSubScreen.value = sub
    }

    // --- POS Cart Actions ---
    fun getLastSellPriceForCustomer(customerName: String, productId: Int): Double? {
        val trimmedCustomer = customerName.trim()
        if (trimmedCustomer.isEmpty()) return null
        
        val customerInvoices = invoices.value.filter {
            it.invoice.displayCustomerName.equals(trimmedCustomer, ignoreCase = true)
        }
        if (customerInvoices.isEmpty()) return null
        
        val sortedInvoices = customerInvoices.sortedByDescending { it.invoice.timestamp }
        
        for (invoiceWithItems in sortedInvoices) {
            val matchingItem = invoiceWithItems.items.find { it.productId == productId }
            if (matchingItem != null) {
                return matchingItem.sellPrice
            }
        }
        return null
    }

    fun addToCart(product: Product): Boolean {
        val currentList = _cart.value.toMutableList()
        val index = currentList.indexOfFirst { it.product.id == product.id }
        if (index != -1) {
            val item = currentList[index]
            if (product.trackInventory && item.quantity >= product.stockQuantity) {
                return false
            }
            currentList[index] = item.copy(quantity = item.quantity + 1)
        } else {
            if (product.trackInventory && product.stockQuantity <= 0) {
                return false
            }
            val activeCustName = cartCustomerName.value.ifBlank { storeName.value }
            val customPrice = getLastSellPriceForCustomer(activeCustName, product.id) ?: product.sellPrice
            currentList.add(CartItem(product, 1, customPrice, product.importPrice))
        }
        updateCart(currentList)
        return true
    }

    fun updateCartQuantity(productId: Int, newQuantity: Int): Boolean {
        if (newQuantity <= 0) return false
        val currentList = _cart.value.toMutableList()
        val index = currentList.indexOfFirst { it.product.id == productId }
        if (index != -1) {
            val item = currentList[index]
            if (item.product.trackInventory && newQuantity > item.product.stockQuantity) {
                currentList[index] = item.copy(quantity = item.product.stockQuantity)
                updateCart(currentList)
                return false
            }
            currentList[index] = item.copy(quantity = newQuantity)
            updateCart(currentList)
            return true
        }
        return false
    }

    fun updateCartPrice(productId: Int, newPrice: Double) {
        val currentList = _cart.value.toMutableList()
        val index = currentList.indexOfFirst { it.product.id == productId }
        if (index != -1) {
            currentList[index] = currentList[index].copy(sellPrice = newPrice)
            updateCart(currentList)
        }
    }

    fun removeFromCart(productId: Int) {
        val currentList = _cart.value.toMutableList()
        val index = currentList.indexOfFirst { it.product.id == productId }
        if (index != -1) {
            currentList.removeAt(index)
            updateCart(currentList)
        }
    }

    fun clearCart() {
        updateCart(emptyList())
        cartCustomerName.value = ""
        prefs.edit().remove("saved_cart_customer_name").apply()
    }

    fun setStoreName(name: String) {
        storeName.value = name
        prefs.edit().putString("saved_store_name", name).apply()
        syncManager.syncSaveSettings(storeName = name)
        
        // Update prices of items currently in the cart
        val currentList = _cart.value.toMutableList()
        var changed = false
        for (i in currentList.indices) {
            val item = currentList[i]
            val customPrice = getLastSellPriceForCustomer(name, item.product.id)
            if (customPrice != null) {
                if (item.sellPrice != customPrice) {
                    currentList[i] = item.copy(sellPrice = customPrice)
                    changed = true
                }
            }
        }
        if (changed) {
            updateCart(currentList)
        }
    }

    fun applyCustomerPricingToCart(customerName: String) {
        val trimmed = customerName.trim()
        if (trimmed.isEmpty()) return
        val currentList = _cart.value.toMutableList()
        var changed = false
        for (i in currentList.indices) {
            val item = currentList[i]
            val customPrice = getLastSellPriceForCustomer(trimmed, item.product.id)
            if (customPrice != null && item.sellPrice != customPrice) {
                currentList[i] = item.copy(sellPrice = customPrice)
                changed = true
            }
        }
        if (changed) {
            updateCart(currentList)
        }
    }

    fun updateInvoiceMetadata(
        invoiceId: Int,
        paymentMethod: String? = null,
        isDebt: Boolean? = null,
        debtTime: Long? = null,
        debtPaid: Double? = null,
        debtPaidTm: Double? = null,
        debtPaidCk: Double? = null,
        costActive: Boolean? = null,
        costAmount: String? = null,
        calendarEventId: Long? = null
    ) {
        val editor = prefs.edit()
        val updates = mutableMapOf<String, Any>()
        paymentMethod?.let {
            editor.putString("invoice_payment_method_$invoiceId", it)
            updates["paymentMethod"] = it
        }
        isDebt?.let {
            editor.putBoolean("invoice_debt_$invoiceId", it)
            updates["isDebt"] = it
        }
        debtTime?.let {
            editor.putLong("invoice_debt_time_$invoiceId", it)
            updates["debtTime"] = it
        }
        debtPaid?.let {
            editor.putFloat("invoice_debt_paid_$invoiceId", it.toFloat())
            updates["debtPaid"] = it
        }
        debtPaidTm?.let {
            editor.putFloat("invoice_debt_paid_tm_$invoiceId", it.toFloat())
            updates["debtPaidTm"] = it
        }
        debtPaidCk?.let {
            editor.putFloat("invoice_debt_paid_ck_$invoiceId", it.toFloat())
            updates["debtPaidCk"] = it
        }
        costActive?.let {
            editor.putBoolean("invoice_cost_active_$invoiceId", it)
            updates["costActive"] = it
        }
        costAmount?.let {
            editor.putString("invoice_cost_amount_$invoiceId", it)
            updates["costAmount"] = it
        }
        calendarEventId?.let {
            editor.putLong("invoice_calendar_event_$invoiceId", it)
            updates["calendarEventId"] = it
        }
        editor.apply()
        if (updates.isNotEmpty()) {
            syncManager.syncUpdateInvoiceMetadata(invoiceId, updates)
        }
    }

    fun syncCashBookAdjustments(manualCash: Double? = null, manualAccount: Double? = null) {
        val editor = prefs.edit()
        manualCash?.let { 
            editor.putFloat("manual_cash_adjustment", it.toFloat())
            manualCashAdjustment.value = it
        }
        manualAccount?.let { 
            editor.putFloat("manual_account_adjustment", it.toFloat())
            manualAccountAdjustment.value = it
        }
        editor.apply()
        syncManager.syncSaveSettings(manualCashAdjustment = manualCash, manualAccountAdjustment = manualAccount)
    }

    // --- Checkout POS ---
    fun checkout(customerName: String?, customerPhone: String?, paymentMethod: String = "TM", onComplete: () -> Unit) {
        val cartItems = _cart.value
        if (cartItems.isEmpty()) return
        viewModelScope.launch {
            val resolvedCustomerName = customerName?.trim() ?: ""
            val invoiceId = repository.createInvoice(
                storeName = resolvedCustomerName,
                customerName = resolvedCustomerName,
                customerPhone = customerPhone,
                cartItems = cartItems
            )
            val prefs = getApplication<android.app.Application>().getSharedPreferences("proodos_prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit().putString("invoice_payment_method_$invoiceId", paymentMethod).apply()

            repository.allInvoicesWithItems.first().find { it.invoice.id == invoiceId.toInt() }?.let { invWithItems ->
                syncManager.syncSaveInvoice(invWithItems.invoice, invWithItems.items)
                for (item in invWithItems.items) {
                    repository.getProductById(item.productId)?.let { prod ->
                        syncManager.syncSaveProduct(prod)
                    }
                }
            }

            clearCart()
            onComplete()
        }
    }

    private fun saveUriToInternalStorage(uriStr: String?, isInvoice: Boolean = false): String? {
        if (uriStr.isNullOrBlank()) return null
        val context = getApplication<Application>()
        val filesDirPath = context.filesDir.absolutePath

        // If it's already an existing file directly inside filesDir, and it's valid, keep it
        if (uriStr.contains(filesDirPath)) {
            val fileName = uriStr.substringAfterLast("/")
            val existing = File(context.filesDir, fileName)
            if (existing.exists() && existing.length() > 0) {
                return Uri.fromFile(existing).toString()
            }
        }

        return try {
            val uri = Uri.parse(uriStr)
            val inputStream = try {
                context.contentResolver.openInputStream(uri)
            } catch (e: Throwable) {
                if (uriStr.startsWith("file://") || uriStr.startsWith("/")) {
                    val rawPath = if (uriStr.startsWith("file://")) uri.path ?: uriStr.removePrefix("file://") else uriStr
                    try { java.io.FileInputStream(java.io.File(rawPath)) } catch (t: Throwable) { null }
                } else null
            } ?: return uriStr

            val mimeType = try { context.contentResolver.getType(uri) } catch (e: Throwable) { null }
            val extension = when (mimeType) {
                "image/png" -> "png"
                "image/gif" -> "gif"
                "image/webp" -> "webp"
                else -> {
                    val lower = uriStr.lowercase()
                    when {
                        lower.endsWith(".png") -> "png"
                        lower.endsWith(".webp") -> "webp"
                        lower.endsWith(".gif") -> "gif"
                        else -> "jpg"
                    }
                }
            }
            val prefix = if (isInvoice || uriStr.contains("invoice", ignoreCase = true)) "invoice_img_" else "prod_img_"
            val fileName = "${prefix}${System.currentTimeMillis()}.$extension"
            val file = File(context.filesDir, fileName)

            inputStream.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            Uri.fromFile(file).toString()
        } catch (e: Exception) {
            e.printStackTrace()
            uriStr
        }
    }

    // --- Product management actions ---
    fun saveProduct(
        name: String,
        categoryName: String,
        importPrice: Double,
        sellPrice: Double,
        trackInventory: Boolean,
        stockQuantity: Int,
        imageUri: String?,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            var categoryId: Int? = null
            if (categoryName.isNotBlank() && categoryName != "Không danh mục") {
                val existing = repository.getCategoryByName(categoryName)
                if (existing != null) {
                    categoryId = existing.id
                } else {
                    val newId = repository.insertCategory(Category(name = categoryName))
                    categoryId = newId.toInt()
                }
            }

            val savedImageUri = withContext(kotlinx.coroutines.Dispatchers.IO) {
                saveUriToInternalStorage(imageUri)
            }

            val product = Product(
                name = name,
                categoryId = categoryId,
                importPrice = importPrice,
                sellPrice = sellPrice,
                trackInventory = trackInventory,
                stockQuantity = stockQuantity,
                imageUri = savedImageUri
            )
            val newId = repository.insertProduct(product).toInt()
            val finalProduct = product.copy(id = newId)
            syncManager.syncSaveProduct(finalProduct)
            if (categoryId != null) {
                repository.allCategories.first().find { it.id == categoryId }?.let { syncManager.syncSaveCategory(it) }
            }
            onComplete()
        }
    }

    fun updateProductInDb(
        productId: Int,
        name: String,
        categoryName: String,
        importPrice: Double,
        sellPrice: Double,
        trackInventory: Boolean,
        stockQuantity: Int,
        imageUri: String?,
        supplierName: String,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            var categoryId: Int? = null
            if (categoryName.isNotBlank() && categoryName != "Không danh mục") {
                val existing = repository.getCategoryByName(categoryName)
                if (existing != null) {
                    categoryId = existing.id
                } else {
                    val newId = repository.insertCategory(Category(name = categoryName))
                    categoryId = newId.toInt()
                }
            }

            val savedImageUri = withContext(kotlinx.coroutines.Dispatchers.IO) {
                saveUriToInternalStorage(imageUri)
            }

            val product = Product(
                id = productId,
                name = name,
                categoryId = categoryId,
                importPrice = importPrice,
                sellPrice = sellPrice,
                trackInventory = trackInventory,
                stockQuantity = stockQuantity,
                imageUri = savedImageUri,
                supplierName = supplierName
            )
            repository.updateProduct(product)
            syncManager.syncSaveProduct(product)
            onComplete()
        }
    }

    // --- Category management actions ---
    fun saveCategory(
        name: String,
        assignedProductIds: List<Int>,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            val catId = repository.insertCategory(Category(name = name)).toInt()
            val cat = Category(id = catId, name = name)
            syncManager.syncSaveCategory(cat)

            // Update assigned products setting their categoryId to database
            assignedProductIds.forEach { pId ->
                repository.getProductById(pId)?.let { prod ->
                    val updated = prod.copy(categoryId = catId)
                    repository.updateProduct(updated)
                    syncManager.syncSaveProduct(updated)
                }
            }
            onComplete()
        }
    }

    fun deleteCategoryFromDb(category: Category) {
        viewModelScope.launch {
            // Un-link any tied products first
            products.value.filter { it.categoryId == category.id }.forEach { prod ->
                val updated = prod.copy(categoryId = null)
                repository.updateProduct(updated)
                syncManager.syncSaveProduct(updated)
            }
            repository.deleteCategory(category)
            syncManager.syncDeleteCategory(category.id)
        }
    }

    fun updateCategoryInDb(category: Category) {
        viewModelScope.launch {
            repository.updateCategory(category)
            syncManager.syncSaveCategory(category)
        }
    }

    fun deleteProductFromDb(product: Product) {
        viewModelScope.launch {
            repository.deleteProduct(product)
            syncManager.syncDeleteProduct(product.id)
        }
    }

    fun removeProductFromCategory(product: Product) {
        viewModelScope.launch {
            val updated = product.copy(categoryId = null)
            repository.updateProduct(updated)
            syncManager.syncSaveProduct(updated)
        }
    }

    fun createImportOrder(
        supplierName: String,
        items: List<ImportOrderItemTemp>,
        cashPaid: Double,
        transferPaid: Double,
        isDraft: Boolean = false,
        invoiceImageUri: String? = null,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            val savedImage = withContext(kotlinx.coroutines.Dispatchers.IO) {
                saveUriToInternalStorage(invoiceImageUri, isInvoice = true)
            }
            val orderId = repository.createImportOrder(supplierName, items, isDraft, savedImage)
            prefs.edit()
                .putFloat("import_order_cash_paid_$orderId", cashPaid.toFloat())
                .putFloat("import_order_transfer_paid_$orderId", transferPaid.toFloat())
                .apply()
            repository.allImportOrdersWithItems.first().find { it.importOrder.id == orderId.toInt() }?.let { created ->
                syncManager.syncSaveImportOrder(created.importOrder, created.items)
                for (item in created.items) {
                    repository.getProductById(item.productId)?.let { syncManager.syncSaveProduct(it) }
                }
            }
            onComplete()
        }
    }

    fun returnImportedProduct(
        orderId: Int,
        productId: Int,
        returnQuantity: Int,
        returnPrice: Double,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            val product = repository.getProductById(productId)
            if (product != null) {
                val updatedStock = (product.stockQuantity - returnQuantity).coerceAtLeast(0)
                val updatedProduct = product.copy(stockQuantity = updatedStock)
                repository.updateProduct(updatedProduct)
                syncManager.syncSaveProduct(updatedProduct)
            }

            val refundValue = returnQuantity * returnPrice
            val currentManualCash = prefs.getFloat("manual_cash_adjustment", 0f).toDouble()
            val newCash = currentManualCash + refundValue
            prefs.edit()
                .putFloat("manual_cash_adjustment", newCash.toFloat())
                .apply()
            manualCashAdjustment.value = newCash
            syncManager.syncSaveSettings(manualCashAdjustment = newCash)

            val oldItems = db.importOrderDao().getImportOrderItemsByOrderId(orderId)
            val matchedItem = oldItems.find { it.productId == productId }
            if (matchedItem != null) {
                val newQty = (matchedItem.quantity - returnQuantity).coerceAtLeast(0)
                val updatedItem = matchedItem.copy(quantity = newQty)
                db.importOrderDao().insertImportOrderItems(listOf(updatedItem))

                val order = db.importOrderDao().getAllImportOrdersList().find { it.id == orderId }
                if (order != null) {
                    val updatedTotal = (order.totalAmount - (returnQuantity * matchedItem.importPrice)).coerceAtLeast(0.0)
                    val updatedOrder = order.copy(totalAmount = updatedTotal)
                    db.importOrderDao().insertImportOrder(updatedOrder)

                    val allItemsForOrder = oldItems.map { if (it.id == matchedItem.id) updatedItem else it }
                    syncManager.syncSaveImportOrder(updatedOrder, allItemsForOrder)
                }
            }

            onComplete()
        }
    }

    fun completeImportOrder(orderWithItems: ImportOrderWithItems, onComplete: () -> Unit) {
        viewModelScope.launch {
            val orderId = orderWithItems.importOrder.id
            val cashPaidKey = "import_order_cash_paid_$orderId"
            val transferPaidKey = "import_order_transfer_paid_$orderId"
            
            val cashPaid = prefs.getFloat(cashPaidKey, 0f).toDouble()
            val transferPaid = prefs.getFloat(transferPaidKey, 0f).toDouble()

            // Deduct from manual adjustments upon completion
            val currentManualCash = prefs.getFloat("manual_cash_adjustment", 0f).toDouble()
            val currentManualAccount = prefs.getFloat("manual_account_adjustment", 0f).toDouble()

            val newCash = currentManualCash - cashPaid
            val newAcc = currentManualAccount - transferPaid
            prefs.edit()
                .putFloat("manual_cash_adjustment", newCash.toFloat())
                .putFloat("manual_account_adjustment", newAcc.toFloat())
                .apply()
            manualCashAdjustment.value = newCash
            manualAccountAdjustment.value = newAcc
            syncManager.syncSaveSettings(manualCashAdjustment = newCash, manualAccountAdjustment = newAcc)

            repository.completeImportOrder(orderWithItems)
            repository.allImportOrdersWithItems.first().find { it.importOrder.id == orderWithItems.importOrder.id }?.let { updated ->
                syncManager.syncSaveImportOrder(updated.importOrder, updated.items)
            }
            for (item in orderWithItems.items) {
                repository.getProductById(item.productId)?.let { syncManager.syncSaveProduct(it) }
            }
            onComplete()
        }
    }

    fun deleteImportOrder(orderWithItems: ImportOrderWithItems) {
        viewModelScope.launch {
            val orderId = orderWithItems.importOrder.id
            val cashPaidKey = "import_order_cash_paid_$orderId"
            val transferPaidKey = "import_order_transfer_paid_$orderId"
            
            val cashPaid = if (prefs.contains(cashPaidKey)) {
                prefs.getFloat(cashPaidKey, 0f).toDouble()
            } else {
                orderWithItems.importOrder.totalAmount
            }
            val transferPaid = prefs.getFloat(transferPaidKey, 0f).toDouble()

            if (!orderWithItems.importOrder.isDraft) {
                // Refund to manual adjustments only if it is NOT a draft
                val currentManualCash = prefs.getFloat("manual_cash_adjustment", 0f).toDouble()
                val currentManualAccount = prefs.getFloat("manual_account_adjustment", 0f).toDouble()

                val newCash = currentManualCash + cashPaid
                val newAcc = currentManualAccount + transferPaid
                prefs.edit()
                    .putFloat("manual_cash_adjustment", newCash.toFloat())
                    .putFloat("manual_account_adjustment", newAcc.toFloat())
                    .apply()
                manualCashAdjustment.value = newCash
                manualAccountAdjustment.value = newAcc
                syncManager.syncSaveSettings(manualCashAdjustment = newCash, manualAccountAdjustment = newAcc)
            }

            prefs.edit()
                .remove(cashPaidKey)
                .remove(transferPaidKey)
                .apply()

            repository.deleteImportOrder(orderWithItems)
            syncManager.syncDeleteImportOrder(orderWithItems.importOrder.id)
            for (item in orderWithItems.items) {
                repository.getProductById(item.productId)?.let { syncManager.syncSaveProduct(it) }
            }
        }
    }

    fun updateImportOrder(
        orderId: Int,
        timestamp: Long,
        supplierName: String,
        items: List<ImportOrderItemTemp>,
        isDraft: Boolean = false,
        invoiceImageUri: String? = null,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            val savedImage = withContext(kotlinx.coroutines.Dispatchers.IO) {
                saveUriToInternalStorage(invoiceImageUri, isInvoice = true)
            }
            repository.updateImportOrder(orderId, timestamp, supplierName, items, isDraft, savedImage)
            repository.allImportOrdersWithItems.first().find { it.importOrder.id == orderId }?.let { updated ->
                syncManager.syncSaveImportOrder(updated.importOrder, updated.items)
            }
            for (item in items) {
                repository.getProductById(item.productId)?.let { syncManager.syncSaveProduct(it) }
            }
            onComplete()
        }
    }

    fun saveProductForImport(
        name: String,
        categoryName: String,
        importPrice: Double,
        sellPrice: Double,
        trackInventory: Boolean,
        stockQuantity: Int,
        imageUri: String? = null,
        onComplete: (Product) -> Unit
    ) {
        viewModelScope.launch {
            var categoryId: Int? = null
            if (categoryName.isNotBlank() && categoryName != "Không danh mục") {
                val existing = repository.getCategoryByName(categoryName)
                if (existing != null) {
                    categoryId = existing.id
                } else {
                    val newId = repository.insertCategory(Category(name = categoryName))
                    categoryId = newId.toInt()
                }
            }

            val savedImageUri = withContext(kotlinx.coroutines.Dispatchers.IO) {
                saveUriToInternalStorage(imageUri)
            }

            val product = Product(
                name = name,
                categoryId = categoryId,
                importPrice = importPrice,
                sellPrice = sellPrice,
                trackInventory = trackInventory,
                stockQuantity = stockQuantity,
                imageUri = savedImageUri
            )
            val newId = repository.insertProduct(product)
            val createdProduct = product.copy(id = newId.toInt())
            syncManager.syncSaveProduct(createdProduct)
            if (categoryId != null) {
                repository.allCategories.first().find { it.id == categoryId }?.let { syncManager.syncSaveCategory(it) }
            }
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                onComplete(createdProduct)
            }
        }
    }

    // --- Preferences configuration changes ---
    fun setGridView(isGrid: Boolean) {
        isGridView.value = isGrid
        prefs.edit().putBoolean("is_grid_view", isGrid).apply()
        syncManager.syncSaveSettings(isGridView = isGrid)
    }

    fun setDarkMode(dark: Boolean) {
        isDarkMode.value = dark
        prefs.edit().putBoolean("is_dark_mode", dark).apply()
        syncManager.syncSaveSettings(isDarkMode = dark)
    }

    fun setLanguage(lang: String) {
        val canonical = Translator.normalizeLanguage(lang)
        currentLanguage.value = canonical
        Translator.currentLanguageState.value = canonical
        prefs.edit().putString("current_language", canonical).apply()
        syncManager.syncSaveSettings(currentLanguage = canonical)
    }

    fun viewInvoiceDetail(invoice: InvoiceWithItems) {
        activeInvoice.value = invoice
        selectSubScreen(SubScreen.INVOICE_DETAIL)
    }

    fun refundInvoice(invoice: InvoiceWithItems, onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.refundInvoice(invoice)
            syncManager.syncDeleteInvoice(invoice.invoice.id)
            for (item in invoice.items) {
                repository.getProductById(item.productId)?.let { syncManager.syncSaveProduct(it) }
            }
            activeInvoice.value = null
            selectSubScreen(null)
            onComplete()
        }
    }

    fun addCartItemsToActiveInvoice(invoice: InvoiceWithItems, onComplete: (Boolean) -> Unit) {
        val currentCart = _cart.value
        if (currentCart.isEmpty()) {
            onComplete(false)
            return
        }
        viewModelScope.launch {
            var addedTotal = 0.0
            var addedProfit = 0.0

            val updatedItems = invoice.items.toMutableList()

            for (cartItem in currentCart) {
                val price = cartItem.sellPrice
                val qty = cartItem.quantity
                addedTotal += price * qty
                addedProfit += (price - cartItem.importPrice) * qty

                val newItem = InvoiceItem(
                    invoiceId = invoice.invoice.id,
                    productId = cartItem.product.id,
                    productName = cartItem.product.name,
                    quantity = qty,
                    sellPrice = price,
                    importPrice = cartItem.importPrice
                )
                val newItemId = db.invoiceDao().insertInvoiceItem(newItem)
                val newItemWithId = newItem.copy(id = newItemId.toInt())
                updatedItems.add(newItemWithId)

                // Update stock if tracked
                val originalProd = db.productDao().getProductById(cartItem.product.id)
                if (originalProd != null && originalProd.trackInventory) {
                    val newStock = (originalProd.stockQuantity - qty).coerceAtLeast(0)
                    db.productDao().updateProduct(originalProd.copy(stockQuantity = newStock))
                }
            }

            val updatedInvoice = invoice.invoice.copy(
                totalAmount = invoice.invoice.totalAmount + addedTotal,
                profit = invoice.invoice.profit + addedProfit
            )

            db.invoiceDao().updateInvoice(updatedInvoice)
            syncManager.syncSaveInvoice(updatedInvoice, updatedItems)
            for (cartItem in currentCart) {
                repository.getProductById(cartItem.product.id)?.let { syncManager.syncSaveProduct(it) }
            }

            // Clear the cart
            clearCart()

            // Update local state so UI updates immediately
            activeInvoice.value = InvoiceWithItems(
                invoice = updatedInvoice,
                items = updatedItems
            )
            onComplete(true)
        }
    }

    fun updateInvoiceItemInActiveInvoice(invoice: InvoiceWithItems, item: InvoiceItem, newPrice: Double, newQty: Int) {
        viewModelScope.launch {
            val priceDiff = (newPrice * newQty) - (item.sellPrice * item.quantity)
            val profitDiff = ((newPrice - item.importPrice) * newQty) - ((item.sellPrice - item.importPrice) * item.quantity)
            val qtyDiff = newQty - item.quantity

            // 1. Update InvoiceItem in Database
            val updatedItem = item.copy(sellPrice = newPrice, quantity = newQty)
            db.invoiceDao().updateInvoiceItem(updatedItem)

            // 2. Update Product inventory if tracked
            val originalProd = db.productDao().getProductById(item.productId)
            if (originalProd != null && originalProd.trackInventory) {
                val newStock = (originalProd.stockQuantity - qtyDiff).coerceAtLeast(0)
                val updatedProd = originalProd.copy(stockQuantity = newStock)
                db.productDao().updateProduct(updatedProd)
                syncManager.syncSaveProduct(updatedProd)
            }

            // 3. Update Invoice total amount and profit in Database
            val updatedInvoice = invoice.invoice.copy(
                totalAmount = invoice.invoice.totalAmount + priceDiff,
                profit = invoice.invoice.profit + profitDiff
            )
            db.invoiceDao().updateInvoice(updatedInvoice)

            // 4. Update the activeInvoice state flow
            val updatedItems = invoice.items.map {
                if (it.id == item.id) updatedItem else it
            }
            syncManager.syncSaveInvoice(updatedInvoice, updatedItems)
            activeInvoice.value = InvoiceWithItems(
                invoice = updatedInvoice,
                items = updatedItems
            )
        }
    }

    fun deleteInvoiceItemFromActiveInvoice(invoice: InvoiceWithItems, item: InvoiceItem) {
        viewModelScope.launch {
            val priceDiff = item.sellPrice * item.quantity
            val profitDiff = (item.sellPrice - item.importPrice) * item.quantity

            // 1. Delete InvoiceItem from Database
            db.invoiceDao().deleteInvoiceItem(item)

            // 2. Revert inventory (increase stock level by the quantity sold in this item)
            val originalProd = db.productDao().getProductById(item.productId)
            if (originalProd != null && originalProd.trackInventory) {
                val newStock = originalProd.stockQuantity + item.quantity
                val updatedProd = originalProd.copy(stockQuantity = newStock)
                db.productDao().updateProduct(updatedProd)
                syncManager.syncSaveProduct(updatedProd)
            }

            // 3. Update Invoice total amount and profit in Database
            val updatedInvoice = invoice.invoice.copy(
                totalAmount = (invoice.invoice.totalAmount - priceDiff).coerceAtLeast(0.0),
                profit = invoice.invoice.profit - profitDiff
            )
            db.invoiceDao().updateInvoice(updatedInvoice)

            // 4. Update the activeInvoice state flow
            val updatedItems = invoice.items.filter { it.id != item.id }
            syncManager.syncSaveInvoice(updatedInvoice, updatedItems)
            activeInvoice.value = InvoiceWithItems(
                invoice = updatedInvoice,
                items = updatedItems
            )
        }
    }

    fun updateCustomerName(oldName: String, newName: String) {
        viewModelScope.launch {
            val trimmedOld = oldName.trim()
            val trimmedNew = newName.trim()
            repository.updateStoreName(oldName, trimmedNew)
            val allInvs = repository.allInvoicesWithItems.first()
            for (invWithItems in allInvs) {
                val inv = invWithItems.invoice
                if (inv.displayCustomerName.equals(trimmedOld, ignoreCase = true) ||
                    inv.storeName.trim().equals(trimmedOld, ignoreCase = true) ||
                    inv.customerName?.trim()?.equals(trimmedOld, ignoreCase = true) == true ||
                    inv.customerName == trimmedNew || inv.storeName == trimmedNew
                ) {
                    val updatedInv = inv.copy(storeName = trimmedNew, customerName = trimmedNew)
                    db.invoiceDao().updateInvoice(updatedInv)
                    syncManager.syncSaveInvoice(updatedInv, invWithItems.items)
                }
            }
        }
    }

    // --- Import / Export Backup ---
    fun exportBackup(context: Context, uri: Uri, onResult: (Boolean, String) -> Unit) {
        val appContext = context.applicationContext
        BackupManager.activeBackupUri = uri
        BackupManager.exportBackupJob = BackupManager.scope.launch {
            val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val channelId = "backup_channel_id"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(channelId, "Sao lưu", android.app.NotificationManager.IMPORTANCE_LOW)
                notificationManager.createNotificationChannel(channel)
            }
            val notificationId = 1001

            val intent = android.content.Intent(appContext, com.example.MainActivity::class.java).apply {
                action = "ACTION_SHOW_BACKUP"
            }
            val pendingIntent = android.app.PendingIntent.getActivity(
                appContext, 0, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val builder = androidx.core.app.NotificationCompat.Builder(appContext, channelId)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Đang sao lưu dữ liệu...")
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pendingIntent)

            try {
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    backupProgress.value = 0f
                    backupMessage.value = "Đang chuẩn bị dữ liệu..."
                }

                val categoriesList = db.categoryDao().getAllCategoriesList()
                val initialProductsList = db.productDao().getAllProductsList()
                val invoicesList = db.invoiceDao().getAllInvoicesList()
                val itemsList = db.invoiceDao().getAllInvoiceItemsList()
                val initialImportOrdersList = db.importOrderDao().getAllImportOrdersList()
                val importOrderItemsList = db.importOrderDao().getAllImportOrderItemsList()
                val prefsKeys = prefs.all.filterValues { it != null }.keys.toList()
                val filesDir = appContext.filesDir
                val existingFiles = (filesDir.listFiles { file -> file.isFile } ?: emptyArray()).toMutableList()

                // 1. Ensure all original invoice images from import orders are safely in filesDir and included (read-only copy, no DB mutation)
                for (order in initialImportOrdersList) {
                    val imgUri = order.invoiceImageUri
                    if (!imgUri.isNullOrBlank()) {
                        try {
                            val fileName = imgUri.substringAfterLast("/")
                            val localFile = File(filesDir, fileName)
                            if (!localFile.exists() || localFile.length() == 0L) {
                                val u = Uri.parse(imgUri)
                                val stream = try {
                                    appContext.contentResolver.openInputStream(u)
                                } catch (e: Exception) {
                                    if (imgUri.startsWith("file://") || imgUri.startsWith("/")) {
                                        val path = if (imgUri.startsWith("file://")) u.path ?: imgUri.removePrefix("file://") else imgUri
                                        try { java.io.FileInputStream(java.io.File(path)) } catch (t: Throwable) { null }
                                    } else null
                                }
                                if (stream != null) {
                                    val safeName = if (fileName.isNotBlank() && !fileName.contains(":") && !fileName.contains("/")) {
                                        fileName
                                    } else {
                                        "invoice_img_${order.id}.jpg"
                                    }
                                    val targetFile = File(filesDir, safeName)
                                    stream.use { input ->
                                        FileOutputStream(targetFile).use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                    if (targetFile.exists() && targetFile.length() > 0) {
                                        existingFiles.add(targetFile)
                                    }
                                }
                            }
                        } catch (e: Throwable) {
                            e.printStackTrace()
                        }
                    }
                }

                // 2. Ensure all product images are safely in filesDir and included (read-only copy, no DB mutation to stock or fields)
                for (prod in initialProductsList) {
                    val imgUri = prod.imageUri
                    if (!imgUri.isNullOrBlank()) {
                        try {
                            val fileName = imgUri.substringAfterLast("/")
                            val localFile = File(filesDir, fileName)
                            if (!localFile.exists() || localFile.length() == 0L) {
                                val u = Uri.parse(imgUri)
                                val stream = try {
                                    appContext.contentResolver.openInputStream(u)
                                } catch (e: Exception) {
                                    if (imgUri.startsWith("file://") || imgUri.startsWith("/")) {
                                        val path = if (imgUri.startsWith("file://")) u.path ?: imgUri.removePrefix("file://") else imgUri
                                        try { java.io.FileInputStream(java.io.File(path)) } catch (t: Throwable) { null }
                                    } else null
                                }
                                if (stream != null) {
                                    val safeName = if (fileName.isNotBlank() && !fileName.contains(":") && !fileName.contains("/")) {
                                        fileName
                                    } else {
                                        "prod_img_${prod.id}.jpg"
                                    }
                                    val targetFile = File(filesDir, safeName)
                                    stream.use { input ->
                                        FileOutputStream(targetFile).use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                    if (targetFile.exists() && targetFile.length() > 0) {
                                        existingFiles.add(targetFile)
                                    }
                                }
                            }
                        } catch (e: Throwable) {
                            e.printStackTrace()
                        }
                    }
                }

                val productsList = initialProductsList
                val importOrdersList = initialImportOrdersList
                val uniqueImageFiles = existingFiles.distinctBy { it.name }.filter { it.isFile && it.length() > 0 }

                val totalItems = 1 + categoriesList.size + productsList.size + invoicesList.size + itemsList.size + importOrdersList.size + importOrderItemsList.size + prefsKeys.size + uniqueImageFiles.size
                var writtenItems = 0

                suspend fun incrementProgress(msg: String) {
                    writtenItems++
                    val progress = if (totalItems > 0) writtenItems.toFloat() / totalItems.toFloat() else 1f
                    val percent = (progress * 100).toInt()
                    
                    builder.setContentTitle("Đang sao lưu dữ liệu... ($percent%)")
                           .setContentText("$percent% - $msg")
                           .setProgress(100, percent, false)
                    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU || androidx.core.content.ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        notificationManager.notify(notificationId, builder.build())
                    }

                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        backupProgress.value = progress.coerceIn(0f, 1f)
                        backupMessage.value = "$percent% - $msg"
                    }
                }

                appContext.contentResolver.openOutputStream(uri)?.use { os ->
                    val writer = android.util.JsonWriter(os.writer(Charsets.UTF_8))
                    writer.beginObject()

                    // 1. Settings
                    writer.name("app_settings")
                    writer.beginObject()
                    writer.name("storeName").value(storeName.value)
                    writer.name("is_auto_backup").value(prefs.getBoolean("is_auto_backup", false))
                    writer.name("is_grid_view").value(prefs.getBoolean("is_grid_view", false))
                    writer.name("is_dark_mode").value(prefs.getBoolean("is_dark_mode", false))
                    writer.name("current_language").value(prefs.getString("current_language", "Tiếng Việt") ?: "Tiếng Việt")
                    writer.name("has_initialized_dummy_data").value(prefs.getBoolean("has_initialized_dummy_data", false))
                    writer.endObject()
                    incrementProgress("Cài đặt hệ thống")
                    kotlinx.coroutines.delay(10)

                    // 2. Categories
                    writer.name("categories")
                    writer.beginArray()
                    for (cat in categoriesList) {
                        writer.beginObject()
                        writer.name("id").value(cat.id.toLong())
                        writer.name("name").value(cat.name)
                        writer.endObject()
                        incrementProgress("Danh mục: ${cat.name}")
                        kotlinx.coroutines.delay(5)
                    }
                    writer.endArray()

                    // 3. Products
                    writer.name("products")
                    writer.beginArray()
                    for (prod in productsList) {
                        writer.beginObject()
                        writer.name("id").value(prod.id.toLong())
                        writer.name("name").value(prod.name)
                        if (prod.categoryId != null) {
                            writer.name("categoryId").value(prod.categoryId.toLong())
                        } else {
                            writer.name("categoryId").value(-1L)
                        }
                        writer.name("importPrice").value(prod.importPrice)
                        writer.name("sellPrice").value(prod.sellPrice)
                        writer.name("trackInventory").value(prod.trackInventory)
                        writer.name("stockQuantity").value(prod.stockQuantity.toLong())
                        writer.name("imageUri").value(prod.imageUri ?: "")
                        writer.name("supplierName").value(prod.supplierName)
                        writer.endObject()
                        incrementProgress("Sản phẩm: ${prod.name}")
                        kotlinx.coroutines.delay(5)
                    }
                    writer.endArray()

                    // 4. Invoices
                    writer.name("invoices")
                    writer.beginArray()
                    for (inv in invoicesList) {
                        writer.beginObject()
                        writer.name("id").value(inv.id.toLong())
                        writer.name("timestamp").value(inv.timestamp)
                        writer.name("storeName").value(inv.storeName)
                        writer.name("totalAmount").value(inv.totalAmount)
                        writer.name("profit").value(inv.profit)
                        writer.name("customerName").value(inv.customerName ?: "")
                        writer.name("customerPhone").value(inv.customerPhone ?: "")
                        
                        val isDebt = prefs.getBoolean("invoice_debt_${inv.id}", false)
                        writer.name("is_debt").value(isDebt)
                        if (isDebt) {
                            writer.name("debt_paid").value(prefs.getFloat("invoice_debt_paid_${inv.id}", 0f).toDouble())
                            writer.name("debt_time").value(prefs.getLong("invoice_debt_time_${inv.id}", 0L))
                            writer.name("calendar_event_id").value(prefs.getLong("invoice_calendar_event_${inv.id}", -1L))
                        }
                        writer.endObject()
                        incrementProgress("Hóa đơn: ${inv.id}")
                        kotlinx.coroutines.delay(5)
                    }
                    writer.endArray()

                    // 5. Invoice Items
                    writer.name("invoice_items")
                    writer.beginArray()
                    for (item in itemsList) {
                        writer.beginObject()
                        writer.name("id").value(item.id.toLong())
                        writer.name("invoiceId").value(item.invoiceId.toLong())
                        writer.name("productId").value(item.productId.toLong())
                        writer.name("productName").value(item.productName)
                        writer.name("quantity").value(item.quantity.toLong())
                        writer.name("sellPrice").value(item.sellPrice)
                        writer.name("importPrice").value(item.importPrice)
                        writer.endObject()
                        incrementProgress("Mặt hàng bán: ${item.productName}")
                        kotlinx.coroutines.delay(5)
                    }
                    writer.endArray()

                    // 5b. Import Orders (Including Original Invoice Image & Draft State)
                    writer.name("import_orders")
                    writer.beginArray()
                    for (order in importOrdersList) {
                        writer.beginObject()
                        writer.name("id").value(order.id.toLong())
                        writer.name("timestamp").value(order.timestamp)
                        writer.name("supplierName").value(order.supplierName)
                        writer.name("totalAmount").value(order.totalAmount)
                        writer.name("isDraft").value(order.isDraft)
                        writer.name("invoiceImageUri").value(order.invoiceImageUri ?: "")
                        writer.endObject()
                        incrementProgress("Đơn nhập: ${order.id}")
                        kotlinx.coroutines.delay(5)
                    }
                    writer.endArray()

                    // 5c. Import Order Items
                    writer.name("import_order_items")
                    writer.beginArray()
                    for (item in importOrderItemsList) {
                        writer.beginObject()
                        writer.name("id").value(item.id.toLong())
                        writer.name("importOrderId").value(item.importOrderId.toLong())
                        writer.name("productId").value(item.productId.toLong())
                        writer.name("productName").value(item.productName)
                        writer.name("quantity").value(item.quantity.toLong())
                        writer.name("importPrice").value(item.importPrice)
                        writer.endObject()
                        incrementProgress("Mặt hàng nhập: ${item.productName}")
                        kotlinx.coroutines.delay(5)
                    }
                    writer.endArray()

                    // 6. Complete SharedPreferences
                    writer.name("shared_preferences")
                    writer.beginObject()
                    prefs.all.forEach { (key, value) ->
                        if (value != null) {
                            writer.name(key)
                            when (value) {
                                is Boolean -> writer.value(value)
                                is Int -> writer.value(value.toLong())
                                is Long -> writer.value(value)
                                is Float -> writer.value(value.toDouble())
                                is Double -> writer.value(value)
                                else -> writer.value(value.toString())
                            }
                        }
                    }
                    writer.endObject()
                    incrementProgress("Cấu hình cài đặt")
                    kotlinx.coroutines.delay(5)

                    // 7. Backup All Images (Product Images & Original Invoice Images)
                    writer.name("product_images")
                    writer.beginObject()
                    for (file in uniqueImageFiles) {
                        try {
                            val bytes = file.readBytes()
                            val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                            writer.name(file.name).value(base64)
                        } catch (e: Throwable) {
                            e.printStackTrace()
                        }
                        val label = if (file.name.contains("invoice", ignoreCase = true)) "Ảnh hóa đơn gốc: ${file.name}" else "Hình ảnh: ${file.name}"
                        incrementProgress(label)
                        kotlinx.coroutines.delay(10)
                    }
                    writer.endObject()

                    writer.endObject()
                    writer.flush()
                    writer.close()
                }

                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    backupProgress.value = 1f
                    backupMessage.value = "Hoàn thành!"
                }
                
                builder.setContentTitle("Sao lưu hoàn tất")
                       .setContentText("Dữ liệu đã được lưu thành công")
                       .setProgress(0, 0, false)
                       .setOngoing(false)
                       .setSmallIcon(android.R.drawable.stat_sys_download_done)
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU || androidx.core.content.ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    notificationManager.notify(notificationId, builder.build())
                }

                kotlinx.coroutines.delay(150)

                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(true, "Sao lưu dữ liệu thành công!")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Handle cancellation cleanly
                val notificationManagerCancel = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                notificationManagerCancel.cancel(notificationId)
                try {
                    android.provider.DocumentsContract.deleteDocument(appContext.contentResolver, uri)
                } catch (t: Throwable) {
                    try {
                        appContext.contentResolver.openOutputStream(uri, "rwt")?.use { it.write(ByteArray(0)) }
                    } catch (t2: Throwable) {
                        t2.printStackTrace()
                    }
                }
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(false, "Đã hủy sao lưu thành công!")
                }
                throw e
            } catch (e: Throwable) {
                e.printStackTrace()
                
                builder.setContentTitle("Lỗi sao lưu")
                       .setContentText("Đã xảy ra lỗi trong quá trình sao lưu")
                       .setProgress(0, 0, false)
                       .setOngoing(false)
                       .setSmallIcon(android.R.drawable.stat_notify_error)
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU || androidx.core.content.ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    notificationManager.notify(notificationId, builder.build())
                }

                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(false, "Lỗi sao lưu: ${e.localizedMessage ?: "Không đủ bộ nhớ hoặc lỗi không xác định"}")
                }
            } finally {
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    backupProgress.value = null
                    backupMessage.value = null
                }
            }
        }
    }

    fun importBackup(context: Context, uri: Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Check if file size exceeds 800MB limit
                val fileSize = try {
                    context.contentResolver.openAssetFileDescriptor(uri, "r")?.use {
                        it.length
                    } ?: -1L
                } catch (e: Throwable) {
                    -1L
                }
                if (fileSize > 800L * 1024L * 1024L) {
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onResult(false, "Lỗi: Bản sao lưu vượt quá giới hạn tối đa 800MB")
                    }
                    return@launch
                }

                // 1. Detect format by inspecting stream prefix
                val isXml = try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val reader = inputStream.bufferedReader(Charsets.UTF_8)
                        val buffer = CharArray(20)
                        val read = reader.read(buffer, 0, 20)
                        if (read > 0) {
                            val prefix = String(buffer, 0, read).trim()
                            prefix.startsWith("<?xml") || prefix.startsWith("<")
                        } else {
                            false
                        }
                    } ?: false
                } catch (e: Throwable) {
                    false
                }

                if (isXml) {
                    // Fallback XML Parsing
                    val fileContent = try {
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            inputStream.bufferedReader().use { it.readText() }
                        }
                    } catch (e: Throwable) {
                        if (uri.scheme == "file") {
                            File(uri.path ?: "").readText(Charsets.UTF_8)
                        } else {
                            throw e
                        }
                    } ?: throw Exception("Không thể đọc tệp")

                    val trimmed = fileContent.trim()
                    val factory = DocumentBuilderFactory.newInstance()
                    val builder = factory.newDocumentBuilder()
                    val doc = builder.parse(ByteArrayInputStream(trimmed.toByteArray(Charsets.UTF_8)))
                    doc.documentElement.normalize()

                    fun getTagValue(parent: Element, tag: String): String {
                        val nodeList = parent.getElementsByTagName(tag)
                        if (nodeList.length > 0) {
                            return nodeList.item(0).textContent ?: ""
                        }
                        return ""
                    }

                    // settings
                    val settingsList = doc.getElementsByTagName("app_settings")
                    var newStoreName: String? = null
                    var newIsAutoBackup: Boolean? = null
                    var newIsGridView: Boolean? = null
                    var newIsDarkMode: Boolean? = null
                    var newLanguage: String? = null
                    var hasInitDummy: Boolean? = null

                    if (settingsList.length > 0) {
                        val settingsEl = settingsList.item(0) as Element
                        newStoreName = getTagValue(settingsEl, "storeName").ifBlank { null }
                        newIsAutoBackup = getTagValue(settingsEl, "is_auto_backup").toBooleanStrictOrNull()
                        newIsGridView = getTagValue(settingsEl, "is_grid_view").toBooleanStrictOrNull()
                        newIsDarkMode = getTagValue(settingsEl, "is_dark_mode").toBooleanStrictOrNull()
                        newLanguage = getTagValue(settingsEl, "current_language").ifBlank { null }
                        hasInitDummy = getTagValue(settingsEl, "has_initialized_dummy_data").toBooleanStrictOrNull()
                    }

                    val importedCategories = mutableListOf<Category>()
                    val categoryNodes = doc.getElementsByTagName("category")
                    for (i in 0 until categoryNodes.length) {
                        val catEl = categoryNodes.item(i) as Element
                        val idStr = getTagValue(catEl, "id")
                        val nameStr = getTagValue(catEl, "name")
                        if (idStr.isNotEmpty() && nameStr.isNotEmpty()) {
                            importedCategories.add(
                                Category(
                                    id = idStr.toInt(),
                                    name = nameStr
                                )
                            )
                        }
                    }

                    val importedProducts = mutableListOf<Product>()
                    val productNodes = doc.getElementsByTagName("product")
                    for (i in 0 until productNodes.length) {
                        val prodEl = productNodes.item(i) as Element
                        val idStr = getTagValue(prodEl, "id")
                        val nameStr = getTagValue(prodEl, "name")
                        val categoryIdStr = getTagValue(prodEl, "categoryId")
                        val importPriceStr = getTagValue(prodEl, "importPrice")
                        val sellPriceStr = getTagValue(prodEl, "sellPrice")
                        val trackInventoryStr = getTagValue(prodEl, "trackInventory")
                        val stockQuantityStr = getTagValue(prodEl, "stockQuantity")
                        val imageUriStr = getTagValue(prodEl, "imageUri")
                        
                        if (idStr.isNotEmpty() && nameStr.isNotEmpty()) {
                            val catIdRaw = categoryIdStr.toIntOrNull() ?: -1
                            val catId = if (catIdRaw == -1) null else catIdRaw
                            importedProducts.add(
                                Product(
                                    id = idStr.toInt(),
                                    name = nameStr,
                                    categoryId = catId,
                                    importPrice = importPriceStr.toDoubleOrNull() ?: 0.0,
                                    sellPrice = sellPriceStr.toDoubleOrNull() ?: 0.0,
                                    trackInventory = trackInventoryStr.toBooleanStrictOrNull() ?: true,
                                    stockQuantity = stockQuantityStr.toIntOrNull() ?: 0,
                                    imageUri = imageUriStr.ifBlank { null }
                                )
                            )
                        }
                    }

                    db.withTransaction {
                        db.categoryDao().deleteAllCategories()
                        db.productDao().deleteAllProducts()

                        if (importedCategories.isNotEmpty()) {
                            db.categoryDao().insertCategories(importedCategories)
                        }
                        if (importedProducts.isNotEmpty()) {
                            db.productDao().insertProducts(importedProducts)
                        }
                    }

                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        if (newStoreName != null) {
                            setStoreName(newStoreName)
                        }
                        if (newIsAutoBackup != null) {
                            prefs.edit().putBoolean("is_auto_backup", false).apply()
                        }
                        if (newIsGridView != null) {
                            setGridView(newIsGridView)
                        }
                        if (newIsDarkMode != null) {
                            setDarkMode(newIsDarkMode)
                        }
                        if (newLanguage != null) {
                            setLanguage(newLanguage)
                        }
                        if (hasInitDummy != null) {
                            prefs.edit().putBoolean("has_initialized_dummy_data", hasInitDummy).apply()
                        }
                    }
                } else {
                    // Modern streaming JSON reader
                    val importedCategories = mutableListOf<Category>()
                    val importedProducts = mutableListOf<Product>()
                    val importedInvoices = mutableListOf<Invoice>()
                    val invoiceDebtsMap = mutableMapOf<Int, JSONObject>()
                    val importedInvoiceItems = mutableListOf<InvoiceItem>()
                    val importedImportOrders = mutableListOf<ImportOrder>()
                    val importedImportOrderItems = mutableListOf<ImportOrderItem>()
                    val importedPrefs = mutableMapOf<String, Any>()

                    var newStoreName: String? = null
                    var newIsAutoBackup: Boolean? = null
                    var newIsGridView: Boolean? = null
                    var newIsDarkMode: Boolean? = null
                    var newLanguage: String? = null
                    var hasInitDummy: Boolean? = null

                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val reader = android.util.JsonReader(inputStream.bufferedReader(Charsets.UTF_8))
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "app_settings" -> {
                                    reader.beginObject()
                                    while (reader.hasNext()) {
                                        when (reader.nextName()) {
                                            "storeName" -> newStoreName = reader.nextString().ifBlank { null }
                                            "is_auto_backup" -> newIsAutoBackup = reader.nextBoolean()
                                            "is_grid_view" -> newIsGridView = reader.nextBoolean()
                                            "is_dark_mode" -> newIsDarkMode = reader.nextBoolean()
                                            "current_language" -> newLanguage = reader.nextString().ifBlank { null }
                                            "has_initialized_dummy_data" -> hasInitDummy = reader.nextBoolean()
                                            else -> reader.skipValue()
                                        }
                                    }
                                    reader.endObject()
                                }
                                "categories" -> {
                                    reader.beginArray()
                                    while (reader.hasNext()) {
                                        reader.beginObject()
                                        var id = -1
                                        var catName = ""
                                        while (reader.hasNext()) {
                                            when (reader.nextName()) {
                                                "id" -> id = reader.nextInt()
                                                "name" -> catName = reader.nextString()
                                                else -> reader.skipValue()
                                            }
                                        }
                                        reader.endObject()
                                        if (id != -1 && catName.isNotEmpty()) {
                                            importedCategories.add(Category(id = id, name = catName))
                                        }
                                    }
                                    reader.endArray()
                                }
                                "products" -> {
                                    reader.beginArray()
                                    var idx = 0
                                    while (reader.hasNext()) {
                                        reader.beginObject()
                                        var id = -1
                                        var pName = ""
                                        var catId: Int? = null
                                        var importPrice = 0.0
                                        var sellPrice = 0.0
                                        var trackInventory = true
                                        var stockQuantity = 0
                                        var imageUri: String? = null
                                        var imageBase64: String? = null
                                        var supplierName = ""
                                        while (reader.hasNext()) {
                                            when (reader.nextName()) {
                                                "id" -> id = reader.nextInt()
                                                "name" -> pName = reader.nextString()
                                                "categoryId" -> {
                                                    val rawId = reader.nextInt()
                                                    catId = if (rawId == -1) null else rawId
                                                }
                                                "importPrice" -> importPrice = reader.nextDouble()
                                                "sellPrice" -> sellPrice = reader.nextDouble()
                                                "trackInventory" -> trackInventory = reader.nextBoolean()
                                                "stockQuantity" -> stockQuantity = reader.nextInt()
                                                "imageUri" -> imageUri = reader.nextString().ifBlank { null }
                                                "imageBase64" -> imageBase64 = reader.nextString().ifBlank { null }
                                                "supplierName" -> supplierName = reader.nextString()
                                                else -> reader.skipValue()
                                            }
                                        }
                                        reader.endObject()

                                        if (id != -1 && pName.isNotEmpty()) {
                                            var finalImageUri = imageUri
                                            if (finalImageUri != null && finalImageUri.contains("/files/")) {
                                                val fileName = finalImageUri.substringAfterLast("/")
                                                finalImageUri = Uri.fromFile(File(context.filesDir, fileName)).toString()
                                            }
                                            if (imageBase64 != null) {
                                                try {
                                                    val bytes = android.util.Base64.decode(imageBase64, android.util.Base64.NO_WRAP)
                                                    val extension = "jpg"
                                                    val fileName = "prod_img_imported_${System.currentTimeMillis()}_$idx.$extension"
                                                    val file = File(context.filesDir, fileName)
                                                    FileOutputStream(file).use { fos ->
                                                        fos.write(bytes)
                                                    }
                                                    finalImageUri = Uri.fromFile(file).toString()
                                                } catch (e: Throwable) {
                                                    e.printStackTrace()
                                                }
                                            }
                                            importedProducts.add(
                                                Product(
                                                    id = id,
                                                    name = pName,
                                                    categoryId = catId,
                                                    importPrice = importPrice,
                                                    sellPrice = sellPrice,
                                                    trackInventory = trackInventory,
                                                    stockQuantity = stockQuantity,
                                                    imageUri = finalImageUri,
                                                    supplierName = supplierName
                                                )
                                            )
                                        }
                                        idx++
                                    }
                                    reader.endArray()
                                }
                                "invoices" -> {
                                    reader.beginArray()
                                    while (reader.hasNext()) {
                                        reader.beginObject()
                                        var id = -1
                                        var timestamp = 0L
                                        var sName = ""
                                        var totalAmount = 0.0
                                        var profit = 0.0
                                        var customerName: String? = null
                                        var customerPhone: String? = null
                                        var isDebt = false
                                        var debtPaid = 0.0
                                        var debtTime = 0L
                                        var calendarEventId = -1L
                                        while (reader.hasNext()) {
                                            when (reader.nextName()) {
                                                "id" -> id = reader.nextInt()
                                                "timestamp" -> timestamp = reader.nextLong()
                                                "storeName" -> sName = reader.nextString()
                                                "totalAmount" -> totalAmount = reader.nextDouble()
                                                "profit" -> profit = reader.nextDouble()
                                                "customerName" -> customerName = reader.nextString().ifBlank { null }
                                                "customerPhone" -> customerPhone = reader.nextString().ifBlank { null }
                                                "is_debt" -> isDebt = reader.nextBoolean()
                                                "debt_paid" -> debtPaid = reader.nextDouble()
                                                "debt_time" -> debtTime = reader.nextLong()
                                                "calendar_event_id" -> calendarEventId = reader.nextLong()
                                                else -> reader.skipValue()
                                            }
                                        }
                                        reader.endObject()
                                        if (id != -1) {
                                            if (timestamp == 0L) {
                                                timestamp = id.toLong()
                                            }
                                            importedInvoices.add(
                                                Invoice(
                                                    id = id,
                                                    timestamp = timestamp,
                                                    storeName = sName,
                                                    totalAmount = totalAmount,
                                                    profit = profit,
                                                    customerName = customerName,
                                                    customerPhone = customerPhone
                                                )
                                            )
                                            if (isDebt) {
                                                val debtObj = JSONObject()
                                                debtObj.put("is_debt", true)
                                                debtObj.put("debt_paid", debtPaid)
                                                debtObj.put("debt_time", debtTime)
                                                debtObj.put("calendar_event_id", calendarEventId)
                                                invoiceDebtsMap[id] = debtObj
                                            }
                                        }
                                    }
                                    reader.endArray()
                                }
                                "invoice_items" -> {
                                    reader.beginArray()
                                    while (reader.hasNext()) {
                                        reader.beginObject()
                                        var id = -1
                                        var invoiceId = -1
                                        var productId = -1
                                        var productName = ""
                                        var quantity = 0
                                        var sellPrice = 0.0
                                        var importPrice = 0.0
                                        while (reader.hasNext()) {
                                            when (reader.nextName()) {
                                                "id" -> id = reader.nextInt()
                                                "invoiceId" -> invoiceId = reader.nextInt()
                                                "productId" -> productId = reader.nextInt()
                                                "productName" -> productName = reader.nextString()
                                                "quantity" -> quantity = reader.nextInt()
                                                "sellPrice" -> sellPrice = reader.nextDouble()
                                                "importPrice" -> importPrice = reader.nextDouble()
                                                else -> reader.skipValue()
                                            }
                                        }
                                        reader.endObject()
                                        if (id != -1) {
                                            importedInvoiceItems.add(
                                                InvoiceItem(
                                                    id = id,
                                                    invoiceId = invoiceId,
                                                    productId = productId,
                                                    productName = productName,
                                                    quantity = quantity,
                                                    sellPrice = sellPrice,
                                                    importPrice = importPrice
                                                )
                                            )
                                        }
                                    }
                                    reader.endArray()
                                }
                                "import_orders" -> {
                                    reader.beginArray()
                                    while (reader.hasNext()) {
                                        reader.beginObject()
                                        var id = -1
                                        var timestamp = 0L
                                        var supplierName = ""
                                        var totalAmount = 0.0
                                        var isDraft = false
                                        var invoiceImageUri: String? = null
                                        while (reader.hasNext()) {
                                            when (reader.nextName()) {
                                                "id" -> id = reader.nextInt()
                                                "timestamp" -> timestamp = reader.nextLong()
                                                "supplierName" -> supplierName = reader.nextString()
                                                "totalAmount" -> totalAmount = reader.nextDouble()
                                                "isDraft" -> isDraft = reader.nextBoolean()
                                                "invoiceImageUri" -> invoiceImageUri = reader.nextString().ifBlank { null }
                                                else -> reader.skipValue()
                                            }
                                        }
                                        reader.endObject()
                                        if (id != -1) {
                                            if (timestamp == 0L) timestamp = id.toLong()
                                            importedImportOrders.add(
                                                ImportOrder(
                                                    id = id,
                                                    timestamp = timestamp,
                                                    supplierName = supplierName,
                                                    totalAmount = totalAmount,
                                                    isDraft = isDraft,
                                                    invoiceImageUri = invoiceImageUri
                                                )
                                            )
                                        }
                                    }
                                    reader.endArray()
                                }
                                "import_order_items" -> {
                                    reader.beginArray()
                                    while (reader.hasNext()) {
                                        reader.beginObject()
                                        var id = -1
                                        var importOrderId = -1
                                        var productId = -1
                                        var productName = ""
                                        var quantity = 0
                                        var importPrice = 0.0
                                        while (reader.hasNext()) {
                                            when (reader.nextName()) {
                                                "id" -> id = reader.nextInt()
                                                "importOrderId" -> importOrderId = reader.nextInt()
                                                "productId" -> productId = reader.nextInt()
                                                "productName" -> productName = reader.nextString()
                                                "quantity" -> quantity = reader.nextInt()
                                                "importPrice" -> importPrice = reader.nextDouble()
                                                else -> reader.skipValue()
                                            }
                                        }
                                        reader.endObject()
                                        if (id != -1) {
                                            importedImportOrderItems.add(
                                                ImportOrderItem(
                                                    id = id,
                                                    importOrderId = importOrderId,
                                                    productId = productId,
                                                    productName = productName,
                                                    quantity = quantity,
                                                    importPrice = importPrice
                                                )
                                            )
                                        }
                                    }
                                    reader.endArray()
                                }
                                "shared_preferences" -> {
                                    reader.beginObject()
                                    while (reader.hasNext()) {
                                        val key = reader.nextName()
                                        if (reader.peek() == android.util.JsonToken.NULL) {
                                            reader.nextNull()
                                            continue
                                        }
                                        when (reader.peek()) {
                                            android.util.JsonToken.BOOLEAN -> {
                                                importedPrefs[key] = reader.nextBoolean()
                                            }
                                            android.util.JsonToken.NUMBER -> {
                                                val strVal = reader.nextString()
                                                if (strVal.contains(".")) {
                                                    val dVal = strVal.toDoubleOrNull()
                                                    if (dVal != null) importedPrefs[key] = dVal
                                                    else importedPrefs[key] = strVal
                                                } else {
                                                    val lVal = strVal.toLongOrNull()
                                                    if (lVal != null) {
                                                        if (lVal >= Int.MIN_VALUE && lVal <= Int.MAX_VALUE) {
                                                            importedPrefs[key] = lVal.toInt()
                                                        } else {
                                                            importedPrefs[key] = lVal
                                                        }
                                                    } else {
                                                        importedPrefs[key] = strVal
                                                    }
                                                }
                                            }
                                            android.util.JsonToken.STRING -> {
                                                importedPrefs[key] = reader.nextString()
                                            }
                                            else -> reader.skipValue()
                                        }
                                    }
                                    reader.endObject()
                                }
                                "product_images", "invoice_images", "images" -> {
                                    reader.beginObject()
                                    while (reader.hasNext()) {
                                        val fileName = reader.nextName()
                                        val base64Str = reader.nextString()
                                        if (base64Str.isNotEmpty()) {
                                            try {
                                                val bytes = android.util.Base64.decode(base64Str, android.util.Base64.DEFAULT)
                                                val file = File(context.filesDir, fileName)
                                                file.writeBytes(bytes)
                                            } catch (e: Throwable) {
                                                e.printStackTrace()
                                            }
                                        }
                                    }
                                    reader.endObject()
                                    System.gc()
                                }
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                    }

                    // Map restored files to local filesDir URIs
                    val resolvedProducts = importedProducts.map { prod ->
                        val img = prod.imageUri
                        if (!img.isNullOrBlank()) {
                            val fileName = img.substringAfterLast("/")
                            val localFile = File(context.filesDir, fileName)
                            if (localFile.exists()) {
                                prod.copy(imageUri = Uri.fromFile(localFile).toString())
                            } else prod
                        } else prod
                    }

                    val resolvedImportOrders = importedImportOrders.map { order ->
                        val img = order.invoiceImageUri
                        if (!img.isNullOrBlank()) {
                            val fileName = img.substringAfterLast("/")
                            val localFile = File(context.filesDir, fileName)
                            if (localFile.exists()) {
                                order.copy(invoiceImageUri = Uri.fromFile(localFile).toString())
                            } else order
                        } else order
                    }

                    // Process Room replacement
                    db.withTransaction {
                        db.categoryDao().deleteAllCategories()
                        db.productDao().deleteAllProducts()
                        db.invoiceDao().deleteAllInvoices()
                        db.invoiceDao().deleteAllInvoiceItems()
                        db.importOrderDao().deleteAllImportOrders()
                        db.importOrderDao().deleteAllImportOrderItems()

                        if (importedCategories.isNotEmpty()) {
                            db.categoryDao().insertCategories(importedCategories)
                        }
                        if (resolvedProducts.isNotEmpty()) {
                            db.productDao().insertProducts(resolvedProducts)
                        }
                        if (importedInvoices.isNotEmpty()) {
                            db.invoiceDao().insertInvoices(importedInvoices)
                        }
                        if (importedInvoiceItems.isNotEmpty()) {
                            db.invoiceDao().insertInvoiceItems(importedInvoiceItems)
                        }
                        if (resolvedImportOrders.isNotEmpty()) {
                            db.importOrderDao().insertImportOrders(resolvedImportOrders)
                        }
                        if (importedImportOrderItems.isNotEmpty()) {
                            db.importOrderDao().insertImportOrderItems(importedImportOrderItems)
                        }
                    }

                    // Apply Preferences with exact, type-safe casting
                    val editor = prefs.edit()
                    if (importedPrefs.isNotEmpty()) {
                        editor.clear().apply()
                        for ((key, value) in importedPrefs) {
                            when {
                                key.startsWith("invoice_debt_paid_") -> {
                                    val num = value.toString().toFloatOrNull() ?: 0f
                                    editor.putFloat(key, num)
                                }
                                key.startsWith("invoice_debt_time_") -> {
                                    val num = value.toString().toLongOrNull() ?: 0L
                                    editor.putLong(key, num)
                                }
                                key.startsWith("invoice_calendar_event_") -> {
                                    val num = value.toString().toLongOrNull() ?: -1L
                                    editor.putLong(key, num)
                                }
                                key.startsWith("import_order_") || key == "manual_cash_adjustment" || key == "manual_account_adjustment" -> {
                                    val num = value.toString().toFloatOrNull() ?: 0f
                                    editor.putFloat(key, num)
                                }
                                key.startsWith("invoice_debt_") || key.startsWith("invoice_cost_active_") -> {
                                    val bool = when (value) {
                                        is Boolean -> value
                                        is String -> value.toBoolean()
                                        is Number -> value.toInt() != 0
                                        else -> value.toString().lowercase().trim() == "true" || value.toString() == "1"
                                    }
                                    editor.putBoolean(key, bool)
                                }
                                key == "is_grid_view" || key == "is_dark_mode" || key == "is_auto_backup" || key == "has_initialized_dummy_data" -> {
                                    val bool = when (value) {
                                        is Boolean -> value
                                        is String -> value.toBoolean()
                                        is Number -> value.toInt() != 0
                                        else -> value.toString().lowercase().trim() == "true" || value.toString() == "1"
                                    }
                                    editor.putBoolean(key, bool)
                                }
                                key == "saved_store_name" || key == "current_language" || key.startsWith("invoice_cost_amount_") -> {
                                    editor.putString(key, value.toString())
                                }
                                else -> {
                                    // Default generic fallback with safe types
                                    when (value) {
                                        is Boolean -> editor.putBoolean(key, value)
                                        is Int -> editor.putInt(key, value)
                                        is Long -> editor.putLong(key, value)
                                        is Float -> editor.putFloat(key, value)
                                        is Double -> editor.putFloat(key, value.toFloat())
                                        is String -> editor.putString(key, value)
                                    }
                                }
                            }
                        }
                        editor.apply()
                    } else {
                        // Fallback: Parse Settings from app_settings
                        // Clear existing invoice-specific debt preferences to start fresh
                        prefs.all.keys.forEach { key ->
                            if (key.startsWith("invoice_debt_") || key.startsWith("invoice_calendar_event_") || key.startsWith("invoice_cost_")) {
                                editor.remove(key)
                            }
                        }
                        // Write imported debt preferences
                        for ((invId, debtObj) in invoiceDebtsMap) {
                            editor.putBoolean("invoice_debt_$invId", debtObj.optBoolean("is_debt", false))
                            editor.putFloat("invoice_debt_paid_$invId", debtObj.optDouble("debt_paid", 0.0).toFloat())
                            editor.putLong("invoice_debt_time_$invId", debtObj.optLong("debt_time", 0L))
                            editor.putLong("invoice_calendar_event_$invId", debtObj.optLong("calendar_event_id", -1L))
                        }
                        editor.putBoolean("has_cleaned_initial_mock_products", true)
                        editor.putBoolean("has_cleaned_initial_mock_categories", true)
                        editor.putBoolean("has_initialized_dummy_data", true)
                        editor.apply()

                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            if (newStoreName != null) setStoreName(newStoreName)
                            if (newIsAutoBackup != null) prefs.edit().putBoolean("is_auto_backup", false).apply()
                            if (newIsGridView != null) setGridView(newIsGridView)
                            if (newIsDarkMode != null) setDarkMode(newIsDarkMode)
                            if (newLanguage != null) setLanguage(newLanguage)
                            prefs.edit().putBoolean("has_initialized_dummy_data", true).apply()
                            clearCart()
                        }
                    }

                    // Update live MainViewModel flows to reflect imported preferences
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        setStoreName(prefs.getString("saved_store_name", "") ?: "")
                        setGridView(prefs.getBoolean("is_grid_view", false))
                        setDarkMode(prefs.getBoolean("is_dark_mode", false))
                        setLanguage(prefs.getString("current_language", "Tiếng Việt") ?: "Tiếng Việt")
                        clearCart()
                    }
                    if (syncManager.activeUserId != null) {
                        syncManager.syncAllDataNow()
                    }
                }

                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(true, "Khôi phục dữ liệu thành công!")
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(false, "Lỗi khôi phục: ${e.localizedMessage ?: "Không đủ bộ nhớ hoặc lỗi không xác định"}")
                }
            }
        }
    }

    private fun updateCart(newList: List<CartItem>) {
        _cart.value = newList
        saveCartToPrefs()
    }

    private fun saveCartToPrefs() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val cartList = _cart.value
                val jsonArray = org.json.JSONArray()
                for (item in cartList) {
                    val jsonItem = org.json.JSONObject()
                    jsonItem.put("quantity", item.quantity)
                    jsonItem.put("sellPrice", item.sellPrice)
                    jsonItem.put("importPrice", item.importPrice)
                    
                    val p = item.product
                    val jsonProduct = org.json.JSONObject()
                    jsonProduct.put("id", p.id)
                    jsonProduct.put("name", p.name)
                    jsonProduct.put("importPrice", p.importPrice)
                    jsonProduct.put("sellPrice", p.sellPrice)
                    jsonProduct.put("stockQuantity", p.stockQuantity)
                    jsonProduct.put("imageUri", p.imageUri)
                    jsonProduct.put("supplierName", p.supplierName)
                    jsonProduct.put("trackInventory", p.trackInventory)
                    p.categoryId?.let { jsonProduct.put("categoryId", it) }
                    
                    jsonItem.put("product", jsonProduct)
                    jsonArray.put(jsonItem)
                }
                prefs.edit().putString("saved_cart_items", jsonArray.toString()).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadCartFromPrefs() {
        val jsonString = prefs.getString("saved_cart_items", null) ?: return
        try {
            val list = mutableListOf<CartItem>()
            val jsonArray = org.json.JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val jsonItem = jsonArray.getJSONObject(i)
                val jsonProduct = jsonItem.getJSONObject("product")
                
                val p = Product(
                    id = jsonProduct.getInt("id"),
                    name = jsonProduct.getString("name"),
                    importPrice = jsonProduct.getDouble("importPrice"),
                    sellPrice = jsonProduct.getDouble("sellPrice"),
                    stockQuantity = jsonProduct.getInt("stockQuantity"),
                    imageUri = if (jsonProduct.has("imageUri") && !jsonProduct.isNull("imageUri")) jsonProduct.getString("imageUri") else null,
                    supplierName = jsonProduct.optString("supplierName", ""),
                    trackInventory = jsonProduct.getBoolean("trackInventory"),
                    categoryId = if (jsonProduct.has("categoryId")) jsonProduct.getInt("categoryId") else null,
                )
                val item = CartItem(
                    product = p,
                    quantity = jsonItem.getInt("quantity"),
                    sellPrice = jsonItem.getDouble("sellPrice"),
                    importPrice = jsonItem.getDouble("importPrice")
                )
                list.add(item)
            }
            _cart.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
