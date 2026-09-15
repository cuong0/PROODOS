package com.example.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String
)

@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val categoryId: Int? = null, // Can refer to category.id
    val importPrice: Double = 0.0,
    val sellPrice: Double = 0.0,
    val trackInventory: Boolean = true,
    val stockQuantity: Int = 0,
    val imageUri: String? = null,
    val supplierName: String = ""
)

@Entity(tableName = "invoices")
data class Invoice(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val storeName: String,
    val totalAmount: Double,
    val profit: Double,
    val customerName: String? = null,
    val customerPhone: String? = null
)

@Entity(tableName = "invoice_items")
data class InvoiceItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val invoiceId: Int,
    val productId: Int,
    val productName: String,
    val quantity: Int,
    val sellPrice: Double,
    val importPrice: Double
)

data class InvoiceWithItems(
    @Embedded val invoice: Invoice,
    @Relation(
        parentColumn = "id",
        entityColumn = "invoiceId"
    )
    val items: List<InvoiceItem>
)

@Entity(tableName = "import_orders")
data class ImportOrder(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val supplierName: String,
    val totalAmount: Double,
    val isDraft: Boolean = false
)

@Entity(tableName = "import_order_items")
data class ImportOrderItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val importOrderId: Int,
    val productId: Int,
    val productName: String,
    val quantity: Int,
    val importPrice: Double
)

data class ImportOrderWithItems(
    @Embedded val importOrder: ImportOrder,
    @Relation(
        parentColumn = "id",
        entityColumn = "importOrderId"
    )
    val items: List<ImportOrderItem>
)
