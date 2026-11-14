package com.example.data

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object GuestBackupManager {
    private const val TAG = "GuestBackupManager"
    private const val BACKUP_FILE_NAME = "guest_offline_data_backup.json"
    private const val PREFS_NAME = "proodos_prefs"
    private const val BACKUP_FLAG_KEY = "has_active_guest_backup"
    private const val GUEST_SAVED_STORE_NAME_KEY = "guest_backup_store_name"

    suspend fun hasLocalData(db: AppDatabase): Boolean {
        return try {
            val prods = db.productDao().getAllProductsList()
            val cats = db.categoryDao().getAllCategoriesList()
            val invoices = db.invoiceDao().getAllInvoicesList()
            val imports = db.importOrderDao().getAllImportOrdersList()
            prods.isNotEmpty() || cats.isNotEmpty() || invoices.isNotEmpty() || imports.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking local data", e)
            false
        }
    }

    fun hasGuestBackup(context: Context): Boolean {
        val file = File(context.filesDir, BACKUP_FILE_NAME)
        return file.exists() && file.length() > 0
    }

    suspend fun saveGuestBackup(context: Context, db: AppDatabase): Boolean {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentStoreName = prefs.getString("saved_store_name", "") ?: ""

            val cats = db.categoryDao().getAllCategoriesList()
            val prods = db.productDao().getAllProductsList()
            val invoices = db.invoiceDao().getAllInvoicesList()
            val invoiceItems = db.invoiceDao().getAllInvoiceItemsList()
            val importOrders = db.importOrderDao().getAllImportOrdersList()
            val importOrderItems = db.importOrderDao().getAllImportOrderItemsList()

            val rootJson = JSONObject()
            rootJson.put("version", 1)
            rootJson.put("timestamp", System.currentTimeMillis())
            rootJson.put("storeName", currentStoreName)

            // Categories
            val catsArray = JSONArray()
            for (c in cats) {
                val obj = JSONObject()
                obj.put("id", c.id)
                obj.put("name", c.name)
                catsArray.put(obj)
            }
            rootJson.put("categories", catsArray)

            // Products
            val prodsArray = JSONArray()
            for (p in prods) {
                val obj = JSONObject()
                obj.put("id", p.id)
                obj.put("name", p.name)
                obj.put("categoryId", p.categoryId ?: JSONObject.NULL)
                obj.put("importPrice", p.importPrice)
                obj.put("sellPrice", p.sellPrice)
                obj.put("trackInventory", p.trackInventory)
                obj.put("stockQuantity", p.stockQuantity)
                obj.put("imageUri", p.imageUri ?: JSONObject.NULL)
                obj.put("supplierName", p.supplierName)
                prodsArray.put(obj)
            }
            rootJson.put("products", prodsArray)

            // Invoices & Invoice Items
            val invArray = JSONArray()
            for (inv in invoices) {
                val obj = JSONObject()
                obj.put("id", inv.id)
                obj.put("timestamp", inv.timestamp)
                obj.put("storeName", inv.storeName)
                obj.put("totalAmount", inv.totalAmount)
                obj.put("profit", inv.profit)
                obj.put("customerName", inv.customerName ?: JSONObject.NULL)
                obj.put("customerPhone", inv.customerPhone ?: JSONObject.NULL)

                // Extra prefs
                val paymentMethod = prefs.getString("invoice_payment_method_${inv.id}", "TM")
                val isDebt = prefs.getBoolean("invoice_debt_${inv.id}", false)
                val debtTime = prefs.getLong("invoice_debt_time_${inv.id}", 0L)
                val debtPaid = prefs.getFloat("invoice_debt_paid_${inv.id}", 0f)
                val debtPaidTm = prefs.getFloat("invoice_debt_paid_tm_${inv.id}", 0f)
                val debtPaidCk = prefs.getFloat("invoice_debt_paid_ck_${inv.id}", 0f)
                val costActive = prefs.getBoolean("invoice_cost_active_${inv.id}", false)
                val costAmount = prefs.getString("invoice_cost_amount_${inv.id}", "")

                obj.put("paymentMethod", paymentMethod)
                obj.put("isDebt", isDebt)
                obj.put("debtTime", debtTime)
                obj.put("debtPaid", debtPaid)
                obj.put("debtPaidTm", debtPaidTm)
                obj.put("debtPaidCk", debtPaidCk)
                obj.put("costActive", costActive)
                obj.put("costAmount", costAmount)

                invArray.put(obj)
            }
            rootJson.put("invoices", invArray)

            val invItemsArray = JSONArray()
            for (item in invoiceItems) {
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("invoiceId", item.invoiceId)
                obj.put("productId", item.productId)
                obj.put("productName", item.productName)
                obj.put("quantity", item.quantity)
                obj.put("sellPrice", item.sellPrice)
                obj.put("importPrice", item.importPrice)
                invItemsArray.put(obj)
            }
            rootJson.put("invoiceItems", invItemsArray)

            // Import orders
            val importArray = JSONArray()
            for (order in importOrders) {
                val obj = JSONObject()
                obj.put("id", order.id)
                obj.put("timestamp", order.timestamp)
                obj.put("supplierName", order.supplierName)
                obj.put("totalAmount", order.totalAmount)
                obj.put("isDraft", order.isDraft)
                obj.put("invoiceImageUri", order.invoiceImageUri ?: JSONObject.NULL)
                val cashPaid = prefs.getFloat("import_order_cash_paid_${order.id}", 0f)
                val transferPaid = prefs.getFloat("import_order_transfer_paid_${order.id}", 0f)
                obj.put("cashPaid", cashPaid)
                obj.put("transferPaid", transferPaid)
                importArray.put(obj)
            }
            rootJson.put("importOrders", importArray)

            val importItemsArray = JSONArray()
            for (item in importOrderItems) {
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("importOrderId", item.importOrderId)
                obj.put("productId", item.productId)
                obj.put("productName", item.productName)
                obj.put("quantity", item.quantity)
                obj.put("importPrice", item.importPrice)
                importItemsArray.put(obj)
            }
            rootJson.put("importOrderItems", importItemsArray)

            // Write to file
            val file = File(context.filesDir, BACKUP_FILE_NAME)
            file.writeText(rootJson.toString())

            prefs.edit()
                .putBoolean(BACKUP_FLAG_KEY, true)
                .putString(GUEST_SAVED_STORE_NAME_KEY, currentStoreName)
                .apply()

            Log.i(TAG, "Successfully saved guest backup to ${file.absolutePath} with ${prods.size} products")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving guest backup", e)
            false
        }
    }

    suspend fun clearDatabase(db: AppDatabase) {
        try {
            db.withTransaction {
                db.productDao().deleteAllProducts()
                db.categoryDao().deleteAllCategories()
                db.invoiceDao().deleteAllInvoiceItems()
                db.invoiceDao().deleteAllInvoices()
                db.importOrderDao().deleteAllImportOrderItems()
                db.importOrderDao().deleteAllImportOrders()
            }
            Log.i(TAG, "Successfully cleared local database")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing local database", e)
        }
    }

    suspend fun restoreGuestBackup(context: Context, db: AppDatabase): Boolean {
        val file = File(context.filesDir, BACKUP_FILE_NAME)
        if (!file.exists() || file.length() == 0L) {
            Log.w(TAG, "No guest backup file found to restore")
            return false
        }

        return try {
            val content = file.readText()
            val rootJson = JSONObject(content)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val editor = prefs.edit()

            // Restore Store Name
            val savedStore = rootJson.optString("storeName", "")
            if (savedStore.isNotBlank()) {
                editor.putString("saved_store_name", savedStore)
            }

            // Categories
            val catsArray = rootJson.optJSONArray("categories") ?: JSONArray()
            val cats = mutableListOf<Category>()
            for (i in 0 until catsArray.length()) {
                val obj = catsArray.getJSONObject(i)
                cats.add(Category(id = obj.getInt("id"), name = obj.getString("name")))
            }

            // Products
            val prodsArray = rootJson.optJSONArray("products") ?: JSONArray()
            val prods = mutableListOf<Product>()
            for (i in 0 until prodsArray.length()) {
                val obj = prodsArray.getJSONObject(i)
                prods.add(
                    Product(
                        id = obj.getInt("id"),
                        name = obj.getString("name"),
                        categoryId = if (obj.isNull("categoryId")) null else obj.getInt("categoryId"),
                        importPrice = obj.getDouble("importPrice"),
                        sellPrice = obj.getDouble("sellPrice"),
                        trackInventory = obj.optBoolean("trackInventory", true),
                        stockQuantity = obj.optInt("stockQuantity", 0),
                        imageUri = if (obj.isNull("imageUri")) null else obj.getString("imageUri"),
                        supplierName = obj.optString("supplierName", "")
                    )
                )
            }

            // Invoices
            val invArray = rootJson.optJSONArray("invoices") ?: JSONArray()
            val invoices = mutableListOf<Invoice>()
            for (i in 0 until invArray.length()) {
                val obj = invArray.getJSONObject(i)
                val id = obj.getInt("id")
                invoices.add(
                    Invoice(
                        id = id,
                        timestamp = obj.getLong("timestamp"),
                        storeName = obj.getString("storeName"),
                        totalAmount = obj.getDouble("totalAmount"),
                        profit = obj.getDouble("profit"),
                        customerName = if (obj.isNull("customerName")) null else obj.getString("customerName"),
                        customerPhone = if (obj.isNull("customerPhone")) null else obj.getString("customerPhone")
                    )
                )
                // Restore extra prefs
                if (obj.has("paymentMethod")) editor.putString("invoice_payment_method_$id", obj.getString("paymentMethod"))
                if (obj.has("isDebt")) editor.putBoolean("invoice_debt_$id", obj.getBoolean("isDebt"))
                if (obj.has("debtTime")) editor.putLong("invoice_debt_time_$id", obj.getLong("debtTime"))
                if (obj.has("debtPaid")) editor.putFloat("invoice_debt_paid_$id", obj.getDouble("debtPaid").toFloat())
                if (obj.has("debtPaidTm")) editor.putFloat("invoice_debt_paid_tm_$id", obj.getDouble("debtPaidTm").toFloat())
                if (obj.has("debtPaidCk")) editor.putFloat("invoice_debt_paid_ck_$id", obj.getDouble("debtPaidCk").toFloat())
                if (obj.has("costActive")) editor.putBoolean("invoice_cost_active_$id", obj.getBoolean("costActive"))
                if (obj.has("costAmount")) editor.putString("invoice_cost_amount_$id", obj.getString("costAmount"))
            }

            // Invoice items
            val invItemsArray = rootJson.optJSONArray("invoiceItems") ?: JSONArray()
            val invoiceItems = mutableListOf<InvoiceItem>()
            for (i in 0 until invItemsArray.length()) {
                val obj = invItemsArray.getJSONObject(i)
                invoiceItems.add(
                    InvoiceItem(
                        id = obj.getInt("id"),
                        invoiceId = obj.getInt("invoiceId"),
                        productId = obj.getInt("productId"),
                        productName = obj.getString("productName"),
                        quantity = obj.getInt("quantity"),
                        sellPrice = obj.getDouble("sellPrice"),
                        importPrice = obj.getDouble("importPrice")
                    )
                )
            }

            // Import orders
            val importArray = rootJson.optJSONArray("importOrders") ?: JSONArray()
            val importOrders = mutableListOf<ImportOrder>()
            for (i in 0 until importArray.length()) {
                val obj = importArray.getJSONObject(i)
                val id = obj.getInt("id")
                importOrders.add(
                    ImportOrder(
                        id = id,
                        timestamp = obj.getLong("timestamp"),
                        supplierName = obj.getString("supplierName"),
                        totalAmount = obj.getDouble("totalAmount"),
                        isDraft = obj.optBoolean("isDraft", false),
                        invoiceImageUri = if (obj.isNull("invoiceImageUri")) null else obj.getString("invoiceImageUri")
                    )
                )
                if (obj.has("cashPaid")) editor.putFloat("import_order_cash_paid_$id", obj.getDouble("cashPaid").toFloat())
                if (obj.has("transferPaid")) editor.putFloat("import_order_transfer_paid_$id", obj.getDouble("transferPaid").toFloat())
            }

            val importItemsArray = rootJson.optJSONArray("importOrderItems") ?: JSONArray()
            val importOrderItems = mutableListOf<ImportOrderItem>()
            for (i in 0 until importItemsArray.length()) {
                val obj = importItemsArray.getJSONObject(i)
                importOrderItems.add(
                    ImportOrderItem(
                        id = obj.getInt("id"),
                        importOrderId = obj.getInt("importOrderId"),
                        productId = obj.getInt("productId"),
                        productName = obj.getString("productName"),
                        quantity = obj.getInt("quantity"),
                        importPrice = obj.getDouble("importPrice")
                    )
                )
            }

            editor.apply()

            // Insert into Room
            db.withTransaction {
                db.categoryDao().deleteAllCategories()
                db.categoryDao().insertCategories(cats)

                db.productDao().deleteAllProducts()
                db.productDao().insertProducts(prods)

                db.invoiceDao().deleteAllInvoiceItems()
                db.invoiceDao().deleteAllInvoices()
                db.invoiceDao().insertInvoices(invoices)
                db.invoiceDao().insertInvoiceItems(invoiceItems)

                db.importOrderDao().deleteAllImportOrderItems()
                db.importOrderDao().deleteAllImportOrders()
                db.importOrderDao().insertImportOrders(importOrders)
                db.importOrderDao().insertImportOrderItems(importOrderItems)
            }

            Log.i(TAG, "Successfully restored guest backup: ${prods.size} products, ${cats.size} categories")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring guest backup", e)
            false
        }
    }

    fun deleteGuestBackup(context: Context) {
        try {
            val file = File(context.filesDir, BACKUP_FILE_NAME)
            if (file.exists()) {
                file.delete()
            }
            context.filesDir.listFiles()?.forEach { f ->
                if (f.isFile && f.name.endsWith(".json")) {
                    f.delete()
                }
            }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .remove(BACKUP_FLAG_KEY)
                .remove(GUEST_SAVED_STORE_NAME_KEY)
                .apply()
            Log.i(TAG, "Guest backup cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting guest backup", e)
        }
    }

    fun readGuestBackup(context: Context): GuestBackupData? {
        val backupFile = File(context.filesDir, BACKUP_FILE_NAME)
        val targetFile = if (backupFile.exists() && backupFile.length() > 0) {
            backupFile
        } else {
            context.filesDir.listFiles()?.firstOrNull { it.name.endsWith(".json") && it.length() > 0 }
        } ?: return null

        return try {
            val content = targetFile.readText()
            val rootJson = JSONObject(content)

            val savedStore = rootJson.optString("storeName", "")

            // Categories
            val catsArray = rootJson.optJSONArray("categories") ?: JSONArray()
            val cats = mutableListOf<Category>()
            for (i in 0 until catsArray.length()) {
                val obj = catsArray.getJSONObject(i)
                cats.add(Category(id = obj.getInt("id"), name = obj.getString("name")))
            }

            // Products
            val prodsArray = rootJson.optJSONArray("products") ?: JSONArray()
            val prods = mutableListOf<Product>()
            for (i in 0 until prodsArray.length()) {
                val obj = prodsArray.getJSONObject(i)
                prods.add(
                    Product(
                        id = obj.getInt("id"),
                        name = obj.getString("name"),
                        categoryId = if (obj.isNull("categoryId")) null else obj.getInt("categoryId"),
                        importPrice = obj.getDouble("importPrice"),
                        sellPrice = obj.getDouble("sellPrice"),
                        trackInventory = obj.optBoolean("trackInventory", true),
                        stockQuantity = obj.optInt("stockQuantity", 0),
                        imageUri = if (obj.isNull("imageUri")) null else obj.getString("imageUri"),
                        supplierName = obj.optString("supplierName", "")
                    )
                )
            }

            // Invoices
            val invArray = rootJson.optJSONArray("invoices") ?: JSONArray()
            val invoices = mutableListOf<Invoice>()
            val extraPrefs = mutableMapOf<String, Any>()
            for (i in 0 until invArray.length()) {
                val obj = invArray.getJSONObject(i)
                val id = obj.getInt("id")
                invoices.add(
                    Invoice(
                        id = id,
                        timestamp = obj.getLong("timestamp"),
                        storeName = obj.getString("storeName"),
                        totalAmount = obj.getDouble("totalAmount"),
                        profit = obj.getDouble("profit"),
                        customerName = if (obj.isNull("customerName")) null else obj.getString("customerName"),
                        customerPhone = if (obj.isNull("customerPhone")) null else obj.getString("customerPhone")
                    )
                )
                if (obj.has("paymentMethod")) extraPrefs["invoice_payment_method_$id"] = obj.getString("paymentMethod")
                if (obj.has("isDebt")) extraPrefs["invoice_debt_$id"] = obj.getBoolean("isDebt")
                if (obj.has("debtTime")) extraPrefs["invoice_debt_time_$id"] = obj.getLong("debtTime")
                if (obj.has("debtPaid")) extraPrefs["invoice_debt_paid_$id"] = obj.getDouble("debtPaid")
                if (obj.has("debtPaidTm")) extraPrefs["invoice_debt_paid_tm_$id"] = obj.getDouble("debtPaidTm")
                if (obj.has("debtPaidCk")) extraPrefs["invoice_debt_paid_ck_$id"] = obj.getDouble("debtPaidCk")
                if (obj.has("costActive")) extraPrefs["invoice_cost_active_$id"] = obj.getBoolean("costActive")
                if (obj.has("costAmount")) extraPrefs["invoice_cost_amount_$id"] = obj.getString("costAmount")
            }

            // Invoice Items
            val invItemsArray = rootJson.optJSONArray("invoiceItems") ?: JSONArray()
            val invoiceItems = mutableListOf<InvoiceItem>()
            for (i in 0 until invItemsArray.length()) {
                val obj = invItemsArray.getJSONObject(i)
                invoiceItems.add(
                    InvoiceItem(
                        id = obj.getInt("id"),
                        invoiceId = obj.getInt("invoiceId"),
                        productId = obj.getInt("productId"),
                        productName = obj.getString("productName"),
                        quantity = obj.getInt("quantity"),
                        sellPrice = obj.getDouble("sellPrice"),
                        importPrice = obj.getDouble("importPrice")
                    )
                )
            }

            // Import Orders
            val importArray = rootJson.optJSONArray("importOrders") ?: JSONArray()
            val importOrders = mutableListOf<ImportOrder>()
            for (i in 0 until importArray.length()) {
                val obj = importArray.getJSONObject(i)
                val id = obj.getInt("id")
                importOrders.add(
                    ImportOrder(
                        id = id,
                        timestamp = obj.getLong("timestamp"),
                        supplierName = obj.getString("supplierName"),
                        totalAmount = obj.getDouble("totalAmount"),
                        isDraft = obj.optBoolean("isDraft", false),
                        invoiceImageUri = if (obj.isNull("invoiceImageUri")) null else obj.getString("invoiceImageUri")
                    )
                )
                if (obj.has("cashPaid")) extraPrefs["import_order_cash_paid_$id"] = obj.getDouble("cashPaid")
                if (obj.has("transferPaid")) extraPrefs["import_order_transfer_paid_$id"] = obj.getDouble("transferPaid")
            }

            // Import Order Items
            val importItemsArray = rootJson.optJSONArray("importOrderItems") ?: JSONArray()
            val importOrderItems = mutableListOf<ImportOrderItem>()
            for (i in 0 until importItemsArray.length()) {
                val obj = importItemsArray.getJSONObject(i)
                importOrderItems.add(
                    ImportOrderItem(
                        id = obj.getInt("id"),
                        importOrderId = obj.getInt("importOrderId"),
                        productId = obj.getInt("productId"),
                        productName = obj.getString("productName"),
                        quantity = obj.getInt("quantity"),
                        importPrice = obj.getDouble("importPrice")
                    )
                )
            }

            GuestBackupData(
                storeName = savedStore,
                categories = cats,
                products = prods,
                invoices = invoices,
                invoiceItems = invoiceItems,
                importOrders = importOrders,
                importOrderItems = importOrderItems,
                extraPrefs = extraPrefs
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error reading guest backup data", e)
            null
        }
    }
}

data class GuestBackupData(
    val storeName: String,
    val categories: List<Category>,
    val products: List<Product>,
    val invoices: List<Invoice>,
    val invoiceItems: List<InvoiceItem>,
    val importOrders: List<ImportOrder>,
    val importOrderItems: List<ImportOrderItem>,
    val extraPrefs: Map<String, Any>
)

