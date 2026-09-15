package com.example.data

import kotlinx.coroutines.flow.Flow

class AppRepository(
    private val productDao: ProductDao,
    private val categoryDao: CategoryDao,
    private val invoiceDao: InvoiceDao,
    private val importOrderDao: ImportOrderDao
) {
    val allProducts: Flow<List<Product>> = productDao.getAllProducts()
    val allCategories: Flow<List<Category>> = categoryDao.getAllCategories()
    val allInvoicesWithItems: Flow<List<InvoiceWithItems>> = invoiceDao.getAllInvoicesWithItems()
    val allImportOrdersWithItems: Flow<List<ImportOrderWithItems>> = importOrderDao.getAllImportOrdersWithItems()

    suspend fun getProductById(id: Int): Product? = productDao.getProductById(id)
    suspend fun insertProduct(product: Product): Long = productDao.insertProduct(product)
    suspend fun updateProduct(product: Product) = productDao.updateProduct(product)
    suspend fun deleteProduct(product: Product) = productDao.deleteProduct(product)

    suspend fun getCategoryByName(name: String): Category? = categoryDao.getCategoryByName(name)
    suspend fun insertCategory(category: Category): Long = categoryDao.insertCategory(category)
    suspend fun updateCategory(category: Category) = categoryDao.updateCategory(category)
    suspend fun deleteCategory(category: Category) = categoryDao.deleteCategory(category)

    suspend fun clearAllProducts() = productDao.deleteAllProducts()
    suspend fun clearAllCategories() = categoryDao.deleteAllCategories()

    suspend fun clearAllData() {
        productDao.deleteAllProducts()
        categoryDao.deleteAllCategories()
        invoiceDao.deleteAllInvoices()
        invoiceDao.deleteAllInvoiceItems()
        importOrderDao.deleteAllImportOrders()
        importOrderDao.deleteAllImportOrderItems()
    }

    suspend fun deleteImportOrder(orderWithItems: ImportOrderWithItems) {
        if (!orderWithItems.importOrder.isDraft) {
            for (item in orderWithItems.items) {
                val product = productDao.getProductById(item.productId)
                if (product != null) {
                    val revertedStock = (product.stockQuantity - item.quantity).coerceAtLeast(0)
                    productDao.updateProduct(product.copy(stockQuantity = revertedStock))
                }
            }
        }
        importOrderDao.deleteImportOrderItemsByOrderId(orderWithItems.importOrder.id)
        importOrderDao.deleteImportOrderById(orderWithItems.importOrder.id)
    }

    suspend fun updateImportOrder(
        orderId: Int,
        timestamp: Long,
        supplierName: String,
        items: List<ImportOrderItemTemp>,
        isDraft: Boolean = false
    ) {
        val prevOrder = importOrderDao.getAllImportOrdersList().find { it.id == orderId }
        val wasDraft = prevOrder?.isDraft ?: false

        if (!wasDraft) {
            val oldItems = importOrderDao.getImportOrderItemsByOrderId(orderId)
            for (oldItem in oldItems) {
                val product = productDao.getProductById(oldItem.productId)
                if (product != null) {
                    val revertedStock = (product.stockQuantity - oldItem.quantity).coerceAtLeast(0)
                    productDao.updateProduct(product.copy(stockQuantity = revertedStock))
                }
            }
        }
        importOrderDao.deleteImportOrderItemsByOrderId(orderId)

        val total = items.sumOf { it.quantity * it.importPrice }
        val order = ImportOrder(
            id = orderId,
            timestamp = timestamp,
            supplierName = supplierName,
            totalAmount = total,
            isDraft = isDraft
        )
        importOrderDao.insertImportOrder(order)
        
        val dbItems = items.map {
            ImportOrderItem(
                importOrderId = orderId,
                productId = it.productId,
                productName = it.productName,
                quantity = it.quantity,
                importPrice = it.importPrice
            )
        }
        importOrderDao.insertImportOrderItems(dbItems)
        
        if (!isDraft) {
            for (item in items) {
                val originalProduct = productDao.getProductById(item.productId)
                if (originalProduct != null) {
                    val newPrice = item.importPrice
                    val newStock = originalProduct.stockQuantity + item.quantity
                    productDao.updateProduct(originalProduct.copy(
                        importPrice = newPrice,
                        stockQuantity = newStock,
                        supplierName = supplierName
                    ))
                }
            }
        } else {
            for (item in items) {
                val originalProduct = productDao.getProductById(item.productId)
                if (originalProduct != null) {
                    productDao.updateProduct(originalProduct.copy(
                        supplierName = supplierName
                    ))
                }
            }
        }
    }

    suspend fun createImportOrder(
        supplierName: String,
        items: List<ImportOrderItemTemp>,
        isDraft: Boolean = false
    ): Long {
        val total = items.sumOf { it.quantity * it.importPrice }
        val order = ImportOrder(
            supplierName = supplierName,
            totalAmount = total,
            isDraft = isDraft
        )
        val orderId = importOrderDao.insertImportOrder(order)
        
        val dbItems = items.map {
            ImportOrderItem(
                importOrderId = orderId.toInt(),
                productId = it.productId,
                productName = it.productName,
                quantity = it.quantity,
                importPrice = it.importPrice
            )
        }
        importOrderDao.insertImportOrderItems(dbItems)
        
        if (!isDraft) {
            for (item in items) {
                val originalProduct = productDao.getProductById(item.productId)
                if (originalProduct != null) {
                    val newPrice = item.importPrice
                    val newStock = originalProduct.stockQuantity + item.quantity
                    productDao.updateProduct(originalProduct.copy(
                        importPrice = newPrice,
                        stockQuantity = newStock,
                        supplierName = supplierName
                    ))
                }
            }
        } else {
            for (item in items) {
                val originalProduct = productDao.getProductById(item.productId)
                if (originalProduct != null) {
                    productDao.updateProduct(originalProduct.copy(
                        supplierName = supplierName
                    ))
                }
            }
        }
        
        return orderId
    }

    suspend fun completeImportOrder(orderWithItems: ImportOrderWithItems) {
        // Mark order as completed
        importOrderDao.insertImportOrder(orderWithItems.importOrder.copy(isDraft = false))
        
        // Add items to inventory stock
        for (item in orderWithItems.items) {
            val originalProduct = productDao.getProductById(item.productId)
            if (originalProduct != null) {
                val newPrice = item.importPrice
                val newStock = originalProduct.stockQuantity + item.quantity
                productDao.updateProduct(originalProduct.copy(
                    importPrice = newPrice,
                    stockQuantity = newStock,
                    supplierName = orderWithItems.importOrder.supplierName
                ))
            }
        }
    }

    suspend fun refundInvoice(invoiceWithItems: InvoiceWithItems) {
        for (item in invoiceWithItems.items) {
            val product = productDao.getProductById(item.productId)
            if (product != null && product.trackInventory) {
                val newStock = product.stockQuantity + item.quantity
                productDao.updateProduct(product.copy(stockQuantity = newStock))
            }
        }
        invoiceDao.deleteInvoiceItemsByInvoiceId(invoiceWithItems.invoice.id)
        invoiceDao.deleteInvoiceById(invoiceWithItems.invoice.id)
    }

    suspend fun createInvoice(
        storeName: String,
        customerName: String? = null,
        customerPhone: String? = null,
        cartItems: List<CartItem>
    ): Long {
        var total = 0.0
        var profit = 0.0

        for (item in cartItems) {
            total += item.sellPrice * item.quantity
            profit += (item.sellPrice - item.importPrice) * item.quantity
        }

        val invoice = Invoice(
            storeName = storeName,
            totalAmount = total,
            profit = profit,
            customerName = customerName,
            customerPhone = customerPhone
        )
        val invoiceId = invoiceDao.insertInvoice(invoice)

        for (item in cartItems) {
            val invoiceItem = InvoiceItem(
                invoiceId = invoiceId.toInt(),
                productId = item.product.id,
                productName = item.product.name,
                quantity = item.quantity,
                sellPrice = item.sellPrice,
                importPrice = item.importPrice
            )
            invoiceDao.insertInvoiceItem(invoiceItem)

            val originalProduct = productDao.getProductById(item.product.id)
            if (originalProduct != null && originalProduct.trackInventory) {
                val newStock = (originalProduct.stockQuantity - item.quantity).coerceAtLeast(0)
                productDao.updateProduct(originalProduct.copy(stockQuantity = newStock))
            }
        }

        return invoiceId
    }

    suspend fun updateStoreName(oldName: String, newName: String) {
        invoiceDao.updateStoreName(oldName, newName)
    }

    suspend fun syncAllSuppliersToProducts() {
        val importOrders = importOrderDao.getAllImportOrdersList()
        val sortedOrders = importOrders.sortedByDescending { it.timestamp }
        val products = productDao.getAllProductsList()
        for (prod in products) {
            var foundSupplier: String? = null
            for (order in sortedOrders) {
                if (order.supplierName.isNotBlank()) {
                    val items = importOrderDao.getImportOrderItemsByOrderId(order.id)
                    if (items.any { it.productId == prod.id }) {
                        foundSupplier = order.supplierName
                        break
                    }
                }
            }
            if (foundSupplier != null && prod.supplierName != foundSupplier) {
                productDao.updateProduct(prod.copy(supplierName = foundSupplier))
            }
        }
    }
}

data class CartItem(
    val product: Product,
    val quantity: Int,
    val sellPrice: Double,
    val importPrice: Double
)

data class ImportOrderItemTemp(
    val productId: Int,
    val productName: String,
    val quantity: Int,
    val importPrice: Double
)
