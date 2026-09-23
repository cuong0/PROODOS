package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    IMPORT_MANAGEMENT
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = AppRepository(
        productDao = db.productDao(),
        categoryDao = db.categoryDao(),
        invoiceDao = db.invoiceDao(),
        importOrderDao = db.importOrderDao()
    )

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

    // First-launch onboarding tour (only displayed on the very first install/launch)
    var showOnboardingTour = MutableStateFlow(!prefs.getBoolean("has_seen_onboarding_tour", false))
        private set

    var onboardingStepIndex = MutableStateFlow(0)
        private set

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
            in 7..8 -> {
                selectTab(MainTab.CAI_DAT)
                selectSubScreen(null)
            }
        }
    }

    fun nextOnboardingStep() {
        val next = onboardingStepIndex.value + 1
        if (next < 9) {
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

    var currentLanguage = MutableStateFlow(prefs.getString("current_language", "Tiếng Việt") ?: "Tiếng Việt")
        private set

    // Auto-saved POS store name (Starts empty for new installations)
    var storeName = MutableStateFlow(prefs.getString("saved_store_name", "") ?: "")
        private set

    // Active POS Cart
    private val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart: StateFlow<List<CartItem>> = _cart.asStateFlow()

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
                "has_seen_onboarding_tour"
            )
            prefs.all.keys.forEach { key ->
                if (key !in keysToKeep) {
                    editor.remove(key)
                }
            }
            editor.putBoolean("has_initialized_dummy_data", true)
            editor.apply()
            onComplete()
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
            it.invoice.storeName.trim().equals(trimmedCustomer, ignoreCase = true)
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
            val customPrice = getLastSellPriceForCustomer(storeName.value, product.id) ?: product.sellPrice
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
    }

    fun setStoreName(name: String) {
        storeName.value = name
        prefs.edit().putString("saved_store_name", name).apply()
        
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

    // --- Checkout POS ---
    fun checkout(customerName: String?, customerPhone: String?, paymentMethod: String = "TM", onComplete: () -> Unit) {
        val cartItems = _cart.value
        if (cartItems.isEmpty()) return
        viewModelScope.launch {
            val invoiceId = repository.createInvoice(
                storeName = storeName.value,
                customerName = customerName,
                customerPhone = customerPhone,
                cartItems = cartItems
            )
            val prefs = getApplication<android.app.Application>().getSharedPreferences("proodos_prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit().putString("invoice_payment_method_$invoiceId", paymentMethod).apply()
            clearCart()
            onComplete()
        }
    }

    private fun saveUriToInternalStorage(uriStr: String?): String? {
        if (uriStr == null) return null
        if (uriStr.startsWith("file:") || !uriStr.startsWith("content:")) {
            return uriStr
        }
        return try {
            val context = getApplication<Application>()
            val uri = Uri.parse(uriStr)
            val inputStream = context.contentResolver.openInputStream(uri) ?: return uriStr
            val extension = when (context.contentResolver.getType(uri)) {
                "image/png" -> "png"
                "image/gif" -> "gif"
                "image/webp" -> "webp"
                else -> "jpg"
            }
            val fileName = "prod_img_${System.currentTimeMillis()}.$extension"
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
            repository.insertProduct(product)
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

            // Update assigned products setting their categoryId to database
            assignedProductIds.forEach { pId ->
                repository.getProductById(pId)?.let { prod ->
                    repository.updateProduct(prod.copy(categoryId = catId))
                }
            }
            onComplete()
        }
    }

    fun deleteCategoryFromDb(category: Category) {
        viewModelScope.launch {
            // Un-link any tied products first
            products.value.filter { it.categoryId == category.id }.forEach { prod ->
                repository.updateProduct(prod.copy(categoryId = null))
            }
            repository.deleteCategory(category)
        }
    }

    fun updateCategoryInDb(category: Category) {
        viewModelScope.launch {
            repository.updateCategory(category)
        }
    }

    fun deleteProductFromDb(product: Product) {
        viewModelScope.launch {
            repository.deleteProduct(product)
        }
    }

    fun removeProductFromCategory(product: Product) {
        viewModelScope.launch {
            repository.updateProduct(product.copy(categoryId = null))
        }
    }

    fun createImportOrder(
        supplierName: String,
        items: List<ImportOrderItemTemp>,
        cashPaid: Double,
        transferPaid: Double,
        isDraft: Boolean = false,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            val orderId = repository.createImportOrder(supplierName, items, isDraft)
            prefs.edit()
                .putFloat("import_order_cash_paid_$orderId", cashPaid.toFloat())
                .putFloat("import_order_transfer_paid_$orderId", transferPaid.toFloat())
                .apply()
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
                repository.updateProduct(product.copy(stockQuantity = updatedStock))
            }

            val refundValue = returnQuantity * returnPrice
            val currentManualCash = prefs.getFloat("manual_cash_adjustment", 0f).toDouble()
            prefs.edit()
                .putFloat("manual_cash_adjustment", (currentManualCash + refundValue).toFloat())
                .apply()

            val oldItems = db.importOrderDao().getImportOrderItemsByOrderId(orderId)
            val matchedItem = oldItems.find { it.productId == productId }
            if (matchedItem != null) {
                val newQty = (matchedItem.quantity - returnQuantity).coerceAtLeast(0)
                db.importOrderDao().insertImportOrderItems(listOf(matchedItem.copy(quantity = newQty)))

                val order = db.importOrderDao().getAllImportOrdersList().find { it.id == orderId }
                if (order != null) {
                    val updatedTotal = (order.totalAmount - (returnQuantity * matchedItem.importPrice)).coerceAtLeast(0.0)
                    db.importOrderDao().insertImportOrder(order.copy(totalAmount = updatedTotal))
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

            prefs.edit()
                .putFloat("manual_cash_adjustment", (currentManualCash - cashPaid).toFloat())
                .putFloat("manual_account_adjustment", (currentManualAccount - transferPaid).toFloat())
                .apply()

            repository.completeImportOrder(orderWithItems)
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

                prefs.edit()
                    .putFloat("manual_cash_adjustment", (currentManualCash + cashPaid).toFloat())
                    .putFloat("manual_account_adjustment", (currentManualAccount + transferPaid).toFloat())
                    .apply()
            }

            prefs.edit()
                .remove(cashPaidKey)
                .remove(transferPaidKey)
                .apply()

            repository.deleteImportOrder(orderWithItems)
        }
    }

    fun updateImportOrder(
        orderId: Int,
        timestamp: Long,
        supplierName: String,
        items: List<ImportOrderItemTemp>,
        isDraft: Boolean = false,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            repository.updateImportOrder(orderId, timestamp, supplierName, items, isDraft)
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

            val product = Product(
                name = name,
                categoryId = categoryId,
                importPrice = importPrice,
                sellPrice = sellPrice,
                trackInventory = trackInventory,
                stockQuantity = stockQuantity,
                imageUri = imageUri
            )
            val newId = repository.insertProduct(product)
            val createdProduct = product.copy(id = newId.toInt())
            onComplete(createdProduct)
        }
    }

    // --- Preferences configuration changes ---
    fun setGridView(isGrid: Boolean) {
        isGridView.value = isGrid
        prefs.edit().putBoolean("is_grid_view", isGrid).apply()
    }

    fun setDarkMode(dark: Boolean) {
        isDarkMode.value = dark
        prefs.edit().putBoolean("is_dark_mode", dark).apply()
    }

    fun setLanguage(lang: String) {
        currentLanguage.value = lang
        prefs.edit().putString("current_language", lang).apply()
    }

    fun viewInvoiceDetail(invoice: InvoiceWithItems) {
        activeInvoice.value = invoice
        selectSubScreen(SubScreen.INVOICE_DETAIL)
    }

    fun refundInvoice(invoice: InvoiceWithItems, onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.refundInvoice(invoice)
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
                db.productDao().updateProduct(originalProd.copy(stockQuantity = newStock))
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
                db.productDao().updateProduct(originalProd.copy(stockQuantity = newStock))
            }

            // 3. Update Invoice total amount and profit in Database
            val updatedInvoice = invoice.invoice.copy(
                totalAmount = (invoice.invoice.totalAmount - priceDiff).coerceAtLeast(0.0),
                profit = invoice.invoice.profit - profitDiff
            )
            db.invoiceDao().updateInvoice(updatedInvoice)

            // 4. Update the activeInvoice state flow
            val updatedItems = invoice.items.filter { it.id != item.id }
            activeInvoice.value = InvoiceWithItems(
                invoice = updatedInvoice,
                items = updatedItems
            )
        }
    }

    fun updateCustomerName(oldName: String, newName: String) {
        viewModelScope.launch {
            repository.updateStoreName(oldName, newName)
        }
    }

    // --- Import / Export Backup ---
    fun exportBackup(context: Context, uri: Uri, onResult: (Boolean, String) -> Unit) {
        val appContext = context.applicationContext
        val serviceIntent = Intent(appContext, BackupService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            appContext.startForegroundService(serviceIntent)
        } else {
            appContext.startService(serviceIntent)
        }
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
                val productsList = db.productDao().getAllProductsList()
                val invoicesList = db.invoiceDao().getAllInvoicesList()
                val itemsList = db.invoiceDao().getAllInvoiceItemsList()
                val importOrdersList = db.importOrderDao().getAllImportOrdersList()
                val importOrderItemsList = db.importOrderDao().getAllImportOrderItemsList()
                val prefsKeys = prefs.all.filterValues { it != null }.keys.toList()
                val filesDir = appContext.filesDir
                val imageFiles = filesDir.listFiles { file -> file.isFile } ?: emptyArray()

                val totalItems = 1 + categoriesList.size + productsList.size + invoicesList.size + itemsList.size + importOrdersList.size + importOrderItemsList.size + prefsKeys.size + imageFiles.size
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

                    // 5b. Import Orders
                    writer.name("import_orders")
                    writer.beginArray()
                    for (order in importOrdersList) {
                        writer.beginObject()
                        writer.name("id").value(order.id.toLong())
                        writer.name("timestamp").value(order.timestamp)
                        writer.name("supplierName").value(order.supplierName)
                        writer.name("totalAmount").value(order.totalAmount)
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

                    // 7. Backup Product Images
                    writer.name("product_images")
                    writer.beginObject()
                    if (imageFiles != null) {
                        for (file in imageFiles) {
                            try {
                                val bytes = file.readBytes()
                                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                                writer.name(file.name).value(base64)
                            } catch (e: Throwable) {
                                e.printStackTrace()
                            }
                            incrementProgress("Hình ảnh: ${file.name}")
                            kotlinx.coroutines.delay(10)
                        }
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
                appContext.stopService(Intent(appContext, BackupService::class.java))
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
                                                    imageUri = finalImageUri
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
                                        while (reader.hasNext()) {
                                            when (reader.nextName()) {
                                                "id" -> id = reader.nextInt()
                                                "timestamp" -> timestamp = reader.nextLong()
                                                "supplierName" -> supplierName = reader.nextString()
                                                "totalAmount" -> totalAmount = reader.nextDouble()
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
                                                    totalAmount = totalAmount
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
                                "product_images" -> {
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
                        if (importedProducts.isNotEmpty()) {
                            db.productDao().insertProducts(importedProducts)
                        }
                        if (importedInvoices.isNotEmpty()) {
                            db.invoiceDao().insertInvoices(importedInvoices)
                        }
                        if (importedInvoiceItems.isNotEmpty()) {
                            db.invoiceDao().insertInvoiceItems(importedInvoiceItems)
                        }
                        if (importedImportOrders.isNotEmpty()) {
                            db.importOrderDao().insertImportOrders(importedImportOrders)
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
                        editor.apply()

                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            if (newStoreName != null) setStoreName(newStoreName)
                            if (newIsAutoBackup != null) prefs.edit().putBoolean("is_auto_backup", false).apply()
                            if (newIsGridView != null) setGridView(newIsGridView)
                            if (newIsDarkMode != null) setDarkMode(newIsDarkMode)
                            if (newLanguage != null) setLanguage(newLanguage)
                            if (hasInitDummy != null) prefs.edit().putBoolean("has_initialized_dummy_data", hasInitDummy).apply()
                        }
                    }

                    // Update live MainViewModel flows to reflect imported preferences
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        setStoreName(prefs.getString("saved_store_name", "Cửa hàng của Cường") ?: "Cửa hàng của Cường")
                        setGridView(prefs.getBoolean("is_grid_view", false))
                        setDarkMode(prefs.getBoolean("is_dark_mode", false))
                        setLanguage(prefs.getString("current_language", "Tiếng Việt") ?: "Tiếng Việt")
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
