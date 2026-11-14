package com.example.ui

import android.content.Context
import android.util.Log
import com.example.data.FirestoreSyncManager
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.security.MessageDigest
import java.text.Normalizer
import java.util.regex.Pattern

data class RegisteredAccount(
    val email: String,
    val storeName: String,
    val passwordHash: String,
    val salt: String,
    val userId: String,
    val registeredAt: Long = System.currentTimeMillis(),
    val avatarUrl: String = ""
)

data class PasswordValidationResult(
    val hasMinLength: Boolean,
    val hasUppercase: Boolean,
    val hasDigit: Boolean
) {
    val isValid: Boolean get() = hasMinLength && hasUppercase && hasDigit
}

sealed class AccountVerifyResult {
    data class Success(val account: RegisteredAccount) : AccountVerifyResult()
    object EmailNotFound : AccountVerifyResult()
    object WrongPassword : AccountVerifyResult()
}

object AccountAuthManager {
    private const val TAG = "AccountAuthManager"
    private const val PREFS_NAME = "registered_user_accounts"

    fun removeVietnameseDiacritics(str: String): String {
        if (str.isBlank()) return ""
        val nfd = Normalizer.normalize(str, Normalizer.Form.NFD)
        val pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+")
        return pattern.matcher(nfd).replaceAll("")
            .replace("đ", "d")
            .replace("Đ", "D")
            .lowercase()
            .trim()
    }

    fun validatePassword(password: String): PasswordValidationResult {
        return PasswordValidationResult(
            hasMinLength = password.length >= 8,
            hasUppercase = password.any { it.isUpperCase() },
            hasDigit = password.any { it.isDigit() }
        )
    }

    fun hashPassword(password: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest((password + salt).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun deterministicUserIdForEmail(email: String): String {
        val clean = email.lowercase().trim()
        return "uid_" + hashPassword(clean, "proodos_cloud_uid_salt").take(24)
    }

    fun saveAccountLocally(context: Context, account: RegisteredAccount) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = JSONObject().apply {
            put("email", account.email)
            put("storeName", account.storeName)
            put("passwordHash", account.passwordHash)
            put("salt", account.salt)
            put("userId", account.userId)
            put("registeredAt", account.registeredAt)
            put("avatarUrl", account.avatarUrl)
        }

        val jsonString = json.toString()
        val cleanEmail = account.email.lowercase().trim()
        val emailPrefix = cleanEmail.substringBefore("@")
        val cleanStore = account.storeName.trim()
        val cleanStoreLower = cleanStore.lowercase()
        val unaccentedStore = removeVietnameseDiacritics(cleanStore)

        val editor = prefs.edit()
        // Lưu theo các khóa email và prefix
        editor.putString("acc_$cleanEmail", jsonString)
        editor.putString("acc_$emailPrefix", jsonString)
        editor.putString("uid_${account.userId}", jsonString)
        editor.putString("last_registered_email", cleanEmail)
        editor.putString("last_registered_prefix", emailPrefix)
        editor.putString("last_registered_account_json", jsonString)

        // Lưu theo tên cửa hàng / tài khoản
        if (cleanStore.isNotBlank()) {
            editor.putString("store_$cleanStoreLower", jsonString)
            editor.putString("store_${cleanStoreLower.replace(" ", "_")}", jsonString)
            editor.putString("store_$unaccentedStore", jsonString)
            editor.putString("store_${unaccentedStore.replace(" ", "_")}", jsonString)
            editor.putString("last_registered_store_name", cleanStore)
        }
        editor.commit()
        Log.i(TAG, "Account locally saved for $cleanEmail ($cleanStore)")
    }

    fun getLastRegisteredIdentifier(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val email = prefs.getString("last_registered_email", null)
        if (!email.isNullOrBlank()) return email
        val store = prefs.getString("last_registered_store_name", null)
        if (!store.isNullOrBlank()) return store
        val prefix = prefs.getString("last_registered_prefix", null)
        if (!prefix.isNullOrBlank()) return prefix
        return ""
    }

    fun getAllLocalAccounts(context: Context): List<RegisteredAccount> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val list = mutableListOf<RegisteredAccount>()
        val seenEmails = mutableSetOf<String>()

        for ((k, v) in prefs.all) {
            if ((k.startsWith("acc_") || k == "last_registered_account_json") && v is String) {
                try {
                    val json = JSONObject(v)
                    val email = json.optString("email", "").lowercase().trim()
                    if (email.isNotBlank() && seenEmails.add(email)) {
                        list.add(
                            RegisteredAccount(
                                email = email,
                                storeName = json.optString("storeName", ""),
                                passwordHash = json.optString("passwordHash", ""),
                                salt = json.optString("salt", ""),
                                userId = json.optString("userId", ""),
                                registeredAt = json.optLong("registeredAt", System.currentTimeMillis()),
                                avatarUrl = json.optString("avatarUrl", "")
                            )
                        )
                    }
                } catch (e: Exception) {
                    // Ignore malformed entry
                }
            }
        }
        return list
    }

    fun getLocalAccountByUserId(context: Context, userId: String): RegisteredAccount? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("uid_$userId", null) ?: return null
        return try {
            val json = JSONObject(jsonStr)
            RegisteredAccount(
                email = json.getString("email"),
                storeName = json.optString("storeName", ""),
                passwordHash = json.getString("passwordHash"),
                salt = json.getString("salt"),
                userId = json.optString("userId", userId),
                registeredAt = json.optLong("registeredAt", System.currentTimeMillis()),
                avatarUrl = json.optString("avatarUrl", "")
            )
        } catch (e: Exception) {
            null
        }
    }

    fun getLocalAccount(context: Context, email: String): RegisteredAccount? {
        return getLocalAccountByUsernameOrEmail(context, email)
    }

    fun getLocalAccountByUsernameOrEmail(context: Context, input: String): RegisteredAccount? {
        val clean = input.lowercase().trim()
        if (clean.isBlank()) return null
        val cleanPrefix = clean.substringBefore("@")
        val cleanUnaccented = removeVietnameseDiacritics(clean)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 1. Thử lấy trực tiếp theo các khóa đã lập chỉ mục
        val directKeys = listOf(
            "acc_$clean",
            "acc_$cleanPrefix",
            "acc_${clean}@gmail.com",
            "store_$clean",
            "store_${clean.replace(" ", "_")}",
            "store_$cleanUnaccented",
            "store_${cleanUnaccented.replace(" ", "_")}",
            "uid_$clean"
        )
        for (k in directKeys) {
            val jsonStr = prefs.getString(k, null)
            if (jsonStr != null) {
                try {
                    val json = JSONObject(jsonStr)
                    return RegisteredAccount(
                        email = json.getString("email"),
                        storeName = json.optString("storeName", ""),
                        passwordHash = json.getString("passwordHash"),
                        salt = json.getString("salt"),
                        userId = json.optString("userId", ""),
                        registeredAt = json.optLong("registeredAt", System.currentTimeMillis()),
                        avatarUrl = json.optString("avatarUrl", "")
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing local account JSON for key $k", e)
                }
            }
        }

        // 2. Tìm kiếm trong danh sách tất cả tài khoản đã lưu
        for ((k, v) in prefs.all) {
            if ((k.startsWith("acc_") || k.startsWith("store_") || k == "last_registered_account_json") && v is String) {
                try {
                    val json = JSONObject(v)
                    val email = json.optString("email", "").lowercase().trim()
                    val emailPrefix = email.substringBefore("@")
                    val store = json.optString("storeName", "").trim()
                    val storeLower = store.lowercase()
                    val storeUnaccented = removeVietnameseDiacritics(store)

                    val isMatch = email.equals(clean, ignoreCase = true) ||
                            emailPrefix.equals(clean, ignoreCase = true) ||
                            emailPrefix.equals(cleanPrefix, ignoreCase = true) ||
                            email.equals("$clean@gmail.com", ignoreCase = true) ||
                            store.equals(input.trim(), ignoreCase = true) ||
                            storeLower.equals(clean, ignoreCase = true) ||
                            storeUnaccented.equals(cleanUnaccented, ignoreCase = true) ||
                            (clean.length >= 3 && (storeLower.contains(clean) || clean.contains(storeLower))) ||
                            (cleanUnaccented.length >= 3 && (storeUnaccented.contains(cleanUnaccented) || cleanUnaccented.contains(storeUnaccented)))

                    if (isMatch) {
                        return RegisteredAccount(
                            email = email,
                            storeName = store,
                            passwordHash = json.getString("passwordHash"),
                            salt = json.getString("salt"),
                            userId = json.optString("userId", ""),
                            registeredAt = json.optLong("registeredAt", System.currentTimeMillis()),
                            avatarUrl = json.optString("avatarUrl", "")
                        )
                    }
                } catch (e: Exception) {
                    // Ignore parsing error
                }
            }
        }

        return null
    }

    fun saveActiveAccount(context: Context, account: RegisteredAccount?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (account == null) {
            prefs.edit().remove("current_active_account_email").commit()
        } else {
            prefs.edit()
                .putString("current_active_account_email", account.email.lowercase().trim())
                .putString("last_registered_email", account.email.lowercase().trim())
                .commit()
        }
    }

    fun getActiveAccount(context: Context): RegisteredAccount? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val email = prefs.getString("current_active_account_email", null)
            ?: prefs.getString("last_registered_email", null)
            ?: return null
        return getLocalAccount(context, email)
    }

    suspend fun saveAccountToFirestore(
        syncManager: FirestoreSyncManager,
        userId: String,
        account: RegisteredAccount
    ): Boolean {
        val cleanEmail = account.email.lowercase().trim()
        val emailPrefix = cleanEmail.substringBefore("@")
        val cleanStore = account.storeName.trim()
        val cleanStoreLower = cleanStore.lowercase()
        val unaccentedStore = removeVietnameseDiacritics(cleanStore)

        val authMap = mutableMapOf<String, Any>(
            "email" to cleanEmail,
            "emailPrefix" to emailPrefix,
            "storeName" to cleanStore,
            "storeNameLower" to cleanStoreLower,
            "unaccentedStoreName" to unaccentedStore,
            "passwordHash" to account.passwordHash,
            "salt" to account.salt,
            "userId" to userId,
            "avatarUrl" to account.avatarUrl,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val docKeys = mutableListOf<String>()
        docKeys.add("acc_" + cleanEmail.replace("/", "_").replace("@", "_at_"))
        docKeys.add("acc_" + emailPrefix.replace("/", "_"))
        docKeys.add(cleanEmail.replace("/", "_"))
        docKeys.add(emailPrefix.replace("/", "_"))
        docKeys.add("uid_$userId")

        if (cleanStore.isNotBlank()) {
            docKeys.add("store_" + cleanStoreLower.replace("/", "_").replace(" ", "_"))
            docKeys.add(cleanStoreLower.replace("/", "_").replace(" ", "_"))
            if (unaccentedStore != cleanStoreLower) {
                docKeys.add("store_" + unaccentedStore.replace("/", "_").replace(" ", "_"))
                docKeys.add(unaccentedStore.replace("/", "_").replace(" ", "_"))
            }
        }

        // Khởi chạy tất cả các lệnh set() song song với giới hạn thời gian 3.5 giây
        // (Firestore PersistentCache vẫn ghi vào bộ nhớ đệm và tự đồng bộ lên cloud ngay cả khi mạng chậm)
        val anySuccess = withTimeoutOrNull(3500L) {
            coroutineScope {
                val jobs = docKeys.distinct().map { key ->
                    async {
                        try {
                            val task = syncManager.firestore.collection("app_accounts").document(key).set(
                                authMap,
                                SetOptions.merge()
                            )
                            withTimeoutOrNull(2500L) { task.await() }
                            true
                        } catch (e: Exception) {
                            Log.w(TAG, "Notice: write to app_accounts key $key: ${e.message}")
                            false
                        }
                    }
                } + async {
                    try {
                        val task = syncManager.firestore.collection("users").document(userId)
                            .collection("settings").document("account_auth").set(
                                authMap,
                                SetOptions.merge()
                            )
                        withTimeoutOrNull(2500L) { task.await() }
                        true
                    } catch (e: Exception) {
                        Log.w(TAG, "Notice: write to users/$userId/settings/account_auth: ${e.message}")
                        false
                    }
                }
                jobs.awaitAll().any { it }
            }
        } ?: true

        Log.i(TAG, "saveAccountToFirestore completed for $cleanEmail with success=$anySuccess")
        return anySuccess
    }

    suspend fun fetchAccountFromFirestore(
        syncManager: FirestoreSyncManager,
        userId: String
    ): RegisteredAccount? = withTimeoutOrNull(3000L) {
        try {
            val docRef = syncManager.firestore.collection("users").document(userId)
                .collection("settings").document("account_auth")
            val doc = try {
                withTimeoutOrNull(2000L) { docRef.get().await() }
            } catch (e: Exception) {
                null
            }
            if (doc != null && doc.exists()) {
                val email = doc.getString("email") ?: return@withTimeoutOrNull null
                val storeName = doc.getString("storeName") ?: ""
                val passwordHash = doc.getString("passwordHash") ?: return@withTimeoutOrNull null
                val salt = doc.getString("salt") ?: ""
                val avatarUrl = doc.getString("avatarUrl") ?: ""
                RegisteredAccount(
                    email = email,
                    storeName = storeName,
                    passwordHash = passwordHash,
                    salt = salt,
                    userId = userId,
                    avatarUrl = avatarUrl
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching account auth from Firestore", e)
            null
        }
    }

    suspend fun fetchAccountByInputFromFirestore(
        syncManager: FirestoreSyncManager,
        input: String
    ): RegisteredAccount? = withTimeoutOrNull(4500L) {
        val clean = input.lowercase().trim()
        if (clean.isBlank()) return@withTimeoutOrNull null
        val cleanPrefix = clean.substringBefore("@")
        val cleanUnaccented = removeVietnameseDiacritics(input.trim())

        fun parseAccountFromDoc(doc: com.google.firebase.firestore.DocumentSnapshot?): RegisteredAccount? {
            if (doc != null && doc.exists()) {
                val email = doc.getString("email") ?: clean
                val storeName = doc.getString("storeName") ?: ""
                val passwordHash = doc.getString("passwordHash") ?: return null
                val salt = doc.getString("salt") ?: ""
                val userId = doc.getString("userId")?.takeIf { it.isNotBlank() }
                    ?: deterministicUserIdForEmail(email)
                val avatarUrl = doc.getString("avatarUrl") ?: ""
                return RegisteredAccount(
                    email = email,
                    storeName = storeName,
                    passwordHash = passwordHash,
                    salt = salt,
                    userId = userId,
                    avatarUrl = avatarUrl
                )
            }
            return null
        }

        try {
            // Đợt 1: Truy vấn song song các khóa trực tiếp phổ biến nhất trong app_accounts (tối đa 2 giây)
            val candidateKeys = buildList {
                add("acc_" + clean.replace("/", "_").replace("@", "_at_"))
                add("acc_" + cleanPrefix.replace("/", "_"))
                add(clean.replace("/", "_"))
                add(cleanPrefix.replace("/", "_"))
                if (!clean.contains("@")) {
                    add("acc_" + clean + "_at_gmail.com")
                    add(clean + "@gmail.com")
                }
                add("store_" + clean.replace("/", "_").replace(" ", "_"))
                add("store_" + cleanUnaccented.replace("/", "_").replace(" ", "_"))
                add("uid_$clean")
                if (clean.contains("@")) {
                    add("uid_" + deterministicUserIdForEmail(clean))
                } else {
                    add("uid_" + deterministicUserIdForEmail("$clean@gmail.com"))
                }
            }.distinct()

            val directHit = withTimeoutOrNull(2200L) {
                coroutineScope {
                    candidateKeys.map { key ->
                        async {
                            try {
                                val doc = syncManager.firestore.collection("app_accounts").document(key).get().await()
                                parseAccountFromDoc(doc)
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }.awaitAll().firstOrNull { it != null }
                }
            }
            if (directHit != null) {
                return@withTimeoutOrNull directHit
            }

            // Đợt 2: Truy vấn song song theo các trường email, emailPrefix, storeName (tối đa 2 giây)
            val queryHit = withTimeoutOrNull(2000L) {
                coroutineScope {
                    val col = syncManager.firestore.collection("app_accounts")
                    val queries = buildList {
                        add(async {
                            try {
                                val snap = col.whereEqualTo("email", clean).limit(1).get().await()
                                snap.documents.firstOrNull()?.let { parseAccountFromDoc(it) }
                            } catch (_: Exception) { null }
                        })
                        if (!clean.contains("@")) {
                            add(async {
                                try {
                                    val snap = col.whereEqualTo("email", "$clean@gmail.com").limit(1).get().await()
                                    snap.documents.firstOrNull()?.let { parseAccountFromDoc(it) }
                                } catch (_: Exception) { null }
                            })
                        }
                        add(async {
                            try {
                                val snap = col.whereEqualTo("emailPrefix", cleanPrefix).limit(1).get().await()
                                snap.documents.firstOrNull()?.let { parseAccountFromDoc(it) }
                            } catch (_: Exception) { null }
                        })
                        add(async {
                            try {
                                val snap = col.whereEqualTo("storeNameLower", clean).limit(1).get().await()
                                snap.documents.firstOrNull()?.let { parseAccountFromDoc(it) }
                            } catch (_: Exception) { null }
                        })
                        if (cleanUnaccented.isNotBlank()) {
                            add(async {
                                try {
                                    val snap = col.whereEqualTo("unaccentedStoreName", cleanUnaccented).limit(1).get().await()
                                    snap.documents.firstOrNull()?.let { parseAccountFromDoc(it) }
                                } catch (_: Exception) { null }
                            })
                        }
                    }
                    queries.awaitAll().firstOrNull { it != null }
                }
            }
            if (queryHit != null) {
                return@withTimeoutOrNull queryHit
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up account by input: $input", e)
        }

        null
    }

    fun verifyCredentials(context: Context, email: String, password: String): AccountVerifyResult {
        val account = getLocalAccount(context, email) ?: return AccountVerifyResult.EmailNotFound
        val computedHash = hashPassword(password, account.salt)
        return if (computedHash == account.passwordHash) {
            AccountVerifyResult.Success(account)
        } else {
            AccountVerifyResult.WrongPassword
        }
    }

    suspend fun updateAccountPassword(
        context: Context,
        syncManager: FirestoreSyncManager?,
        account: RegisteredAccount,
        newPassword: String
    ): RegisteredAccount {
        val newSalt = java.util.UUID.randomUUID().toString().take(16)
        val newHash = hashPassword(newPassword, newSalt)
        val updated = account.copy(
            passwordHash = newHash,
            salt = newSalt,
            registeredAt = System.currentTimeMillis()
        )
        saveAccountLocally(context, updated)
        saveActiveAccount(context, updated)
        if (syncManager != null) {
            try {
                saveAccountToFirestore(syncManager, updated.userId, updated)
            } catch (e: Exception) {
                Log.w(TAG, "Notice: could not sync updated password to firestore: ${e.message}")
            }
        }
        return updated
    }

    suspend fun updateAccountFull(
        context: Context,
        syncManager: FirestoreSyncManager?,
        account: RegisteredAccount,
        newStoreName: String? = null,
        newAvatarUrl: String? = null,
        newPassword: String? = null
    ): RegisteredAccount {
        var updated = account
        if (newStoreName != null) {
            updated = updated.copy(storeName = newStoreName.trim())
        }
        if (newAvatarUrl != null) {
            updated = updated.copy(avatarUrl = newAvatarUrl.trim())
        }
        if (!newPassword.isNullOrBlank()) {
            val newSalt = java.util.UUID.randomUUID().toString().take(16)
            val newHash = hashPassword(newPassword, newSalt)
            updated = updated.copy(
                passwordHash = newHash,
                salt = newSalt,
                registeredAt = System.currentTimeMillis()
            )
        }
        saveAccountLocally(context, updated)
        saveActiveAccount(context, updated)

        if (syncManager != null) {
            try {
                saveAccountToFirestore(syncManager, updated.userId, updated)
                val userUpdateMap = mutableMapOf<String, Any>(
                    "displayName" to updated.storeName.ifBlank { updated.email.substringBefore("@") },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (updated.avatarUrl.isNotBlank()) {
                    userUpdateMap["photoURL"] = updated.avatarUrl
                }
                withTimeoutOrNull(2500L) {
                    syncManager.firestore.collection("users").document(updated.userId)
                        .set(userUpdateMap, SetOptions.merge()).await()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Notice: could not sync updated account to firestore: ${e.message}")
            }
        }
        return updated
    }

    suspend fun deleteAccountAndAllData(
        context: Context,
        syncManager: FirestoreSyncManager?,
        account: RegisteredAccount
    ): Boolean = coroutineScope {
        var success = true
        val userId = account.userId
        val cleanEmail = account.email.lowercase().trim()
        val emailPrefix = cleanEmail.substringBefore("@")
        val cleanStore = account.storeName.trim()
        val cleanStoreLower = cleanStore.lowercase()
        val unaccentedStore = removeVietnameseDiacritics(cleanStore)

        // 1. Xóa sạch dữ liệu nguồn tiền và cài đặt cục bộ ngay lập tức để tránh rò rỉ sang phiên sau
        try {
            val proodosPrefs = context.getSharedPreferences("proodos_prefs", Context.MODE_PRIVATE)
            proodosPrefs.edit()
                .putFloat("manual_cash_adjustment", 0f)
                .putFloat("manual_account_adjustment", 0f)
                .commit()
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting financial adjustments before delete", e)
        }

        // 2. Delete all Firestore data for this user in parallel (bounded by timeout so UI never hangs)
        if (syncManager != null) {
            withTimeoutOrNull(10000L) {
                try {
                    val uidsToDelete = mutableSetOf<String>()
                    if (userId.isNotBlank()) uidsToDelete.add(userId)
                    if (cleanEmail.isNotBlank()) {
                        uidsToDelete.add(deterministicUserIdForEmail(cleanEmail))
                    }
                    try {
                        Firebase.auth.currentUser?.uid?.let {
                            if (it.isNotBlank()) uidsToDelete.add(it)
                        }
                    } catch (_: Exception) {}

                    val allSubcollections = listOf(
                        "settings",
                        "importOrders",
                        "import_orders",
                        "products",
                        "categories",
                        "invoices",
                        "debts",
                        "backups",
                        "customers",
                        "suppliers",
                        "transactions",
                        "reports"
                    )

                    val deleteJobs = mutableListOf<Deferred<Any?>>()

                    for (uid in uidsToDelete) {
                        val userDocRef = syncManager.firestore.collection("users").document(uid)
                        // Xóa trực tiếp các document cố định quan trọng (đặc biệt là settings/general chứa manualCashAdjustment & manualAccountAdjustment)
                        deleteJobs.add(async(Dispatchers.IO) {
                            try { userDocRef.collection("settings").document("general").delete().await() } catch (_: Exception) {}
                        })
                        deleteJobs.add(async(Dispatchers.IO) {
                            try { userDocRef.collection("settings").document("account_auth").delete().await() } catch (_: Exception) {}
                        })
                        deleteJobs.add(async(Dispatchers.IO) {
                            try { userDocRef.collection("backups").document("latest").delete().await() } catch (_: Exception) {}
                        })

                        for (colName in allSubcollections) {
                            deleteJobs.add(async(Dispatchers.IO) {
                                try {
                                    val colSnap = withTimeoutOrNull(3500L) { userDocRef.collection(colName).get().await() }
                                    if (colSnap != null && !colSnap.isEmpty) {
                                        colSnap.documents.map { doc ->
                                            async(Dispatchers.IO) {
                                                try { doc.reference.delete().await() } catch (_: Exception) {}
                                            }
                                        }.awaitAll()
                                    }
                                } catch (_: Exception) {}
                            })
                        }
                    }

                    val docKeys = mutableListOf<String>()
                    if (cleanEmail.isNotBlank()) {
                        docKeys.add("acc_" + cleanEmail.replace("/", "_").replace("@", "_at_"))
                        docKeys.add("acc_" + emailPrefix.replace("/", "_"))
                        docKeys.add(cleanEmail.replace("/", "_"))
                        docKeys.add(emailPrefix.replace("/", "_"))
                    }
                    for (uid in uidsToDelete) {
                        docKeys.add("uid_$uid")
                    }
                    if (cleanStore.isNotBlank()) {
                        docKeys.add("store_" + cleanStoreLower.replace("/", "_").replace(" ", "_"))
                        docKeys.add(cleanStoreLower.replace("/", "_").replace(" ", "_"))
                        if (unaccentedStore != cleanStoreLower) {
                            docKeys.add("store_" + unaccentedStore.replace("/", "_").replace(" ", "_"))
                            docKeys.add(unaccentedStore.replace("/", "_").replace(" ", "_"))
                        }
                    }
                    for (key in docKeys.distinct()) {
                        deleteJobs.add(async(Dispatchers.IO) {
                            try {
                                syncManager.firestore.collection("app_accounts").document(key).delete().await()
                            } catch (_: Exception) {}
                        })
                    }

                    deleteJobs.awaitAll()

                    // Xóa document gốc của user sau khi đã xóa sạch subcollections
                    uidsToDelete.map { uid ->
                        async(Dispatchers.IO) {
                            try { syncManager.firestore.collection("users").document(uid).delete().await() } catch (_: Exception) {}
                        }
                    }.awaitAll()

                    try {
                        val fbUser = Firebase.auth.currentUser
                        if (fbUser != null) {
                            try {
                                withTimeoutOrNull(2000L) { fbUser.delete().await() }
                            } catch (e: Exception) {
                                Firebase.auth.signOut()
                            }
                        }
                    } catch (_: Exception) {}
                } catch (e: Exception) {
                    Log.e(TAG, "Error in deleteAccountAndAllData from firestore", e)
                    success = false
                }
            }
        }

        // 3. Xóa sạch preferences tài khoản
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().clear().commit()
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing local account prefs", e)
        }

        success
    }
}
