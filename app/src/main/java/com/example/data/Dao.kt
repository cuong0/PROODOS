package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProducts(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductById(id: Int): Product?

    @Query("SELECT * FROM products")
    suspend fun getAllProductsList(): List<Product>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: Product): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<Product>)

    @Update
    suspend fun updateProduct(product: Product)

    @Delete
    suspend fun deleteProduct(product: Product)

    @Query("DELETE FROM products")
    suspend fun deleteAllProducts()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    suspend fun getCategoryByName(name: String): Category?

    @Query("SELECT * FROM categories")
    suspend fun getAllCategoriesList(): List<Category>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<Category>)

    @Update
    suspend fun updateCategory(category: Category)

    @Delete
    suspend fun deleteCategory(category: Category)

    @Query("DELETE FROM categories")
    suspend fun deleteAllCategories()
}

@Dao
interface InvoiceDao {
    @Query("SELECT * FROM invoices ORDER BY timestamp DESC")
    fun getAllInvoices(): Flow<List<Invoice>>

    @Transaction
    @Query("SELECT * FROM invoices ORDER BY timestamp DESC")
    fun getAllInvoicesWithItems(): Flow<List<InvoiceWithItems>>

    @Query("SELECT * FROM invoices")
    suspend fun getAllInvoicesList(): List<Invoice>

    @Query("SELECT * FROM invoice_items")
    suspend fun getAllInvoiceItemsList(): List<InvoiceItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoice(invoice: Invoice): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoices(invoices: List<Invoice>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoiceItem(item: InvoiceItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoiceItems(items: List<InvoiceItem>)

    @Query("SELECT * FROM invoice_items WHERE invoiceId = :invoiceId")
    fun getInvoiceItems(invoiceId: Int): Flow<List<InvoiceItem>>

    @Query("DELETE FROM invoices WHERE id = :invoiceId")
    suspend fun deleteInvoiceById(invoiceId: Int)

    @Update
    suspend fun updateInvoice(invoice: Invoice)

    @Update
    suspend fun updateInvoiceItem(item: InvoiceItem)

    @Delete
    suspend fun deleteInvoiceItem(item: InvoiceItem)

    @Query("UPDATE invoices SET storeName = :newName, customerName = :newName WHERE storeName = :oldName OR customerName = :oldName")
    suspend fun updateStoreName(oldName: String, newName: String)

    @Query("DELETE FROM invoice_items WHERE invoiceId = :invoiceId")
    suspend fun deleteInvoiceItemsByInvoiceId(invoiceId: Int)

    @Query("DELETE FROM invoices")
    suspend fun deleteAllInvoices()

    @Query("DELETE FROM invoice_items")
    suspend fun deleteAllInvoiceItems()
}

@Dao
interface ImportOrderDao {
    @Query("SELECT * FROM import_orders ORDER BY timestamp DESC")
    fun getAllImportOrders(): Flow<List<ImportOrder>>

    @Query("SELECT * FROM import_orders")
    suspend fun getAllImportOrdersList(): List<ImportOrder>

    @Query("SELECT * FROM import_order_items")
    suspend fun getAllImportOrderItemsList(): List<ImportOrderItem>

    @Transaction
    @Query("SELECT * FROM import_orders ORDER BY timestamp DESC")
    fun getAllImportOrdersWithItems(): Flow<List<ImportOrderWithItems>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImportOrder(order: ImportOrder): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImportOrders(orders: List<ImportOrder>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImportOrderItems(items: List<ImportOrderItem>)

    @Query("SELECT * FROM import_order_items WHERE importOrderId = :orderId")
    suspend fun getImportOrderItemsByOrderId(orderId: Int): List<ImportOrderItem>

    @Query("DELETE FROM import_orders WHERE id = :orderId")
    suspend fun deleteImportOrderById(orderId: Int)

    @Query("DELETE FROM import_order_items WHERE importOrderId = :orderId")
    suspend fun deleteImportOrderItemsByOrderId(orderId: Int)

    @Query("DELETE FROM import_orders")
    suspend fun deleteAllImportOrders()

    @Query("DELETE FROM import_order_items")
    suspend fun deleteAllImportOrderItems()
}
