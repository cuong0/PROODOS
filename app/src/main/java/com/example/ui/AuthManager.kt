package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest

object AuthManager {
    private const val TAG = "AuthManager"

    // SHA-1 hashes registered in google-services.json
    private val REGISTERED_CERT_HASHES = setOf(
        "a8f4b16868c4b1eb6ab20a0fff2a2c5cb20135ec",
        "cdebb6d8b2599a770ae8091efef6b9a0a988ddfb"
    )

    tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    /**
     * Checks whether the currently running APK is signed with a certificate whose SHA-1
     * is registered in google-services.json. On Google Play Console (Internal / Closed / Production),
     * Play App Signing re-signs the APK with a different certificate, causing CredentialManager
     * OAuth token requests to return RESULT_CANCELED unless the Play SHA-1 is manually added in Firebase.
     */
    fun isGoogleSignInCertRegistered(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            val pkgName = context.packageName
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(pkgName, PackageManager.GET_SIGNING_CERTIFICATES)
                info.signingInfo?.apkContentsSigners ?: info.signingInfo?.signingCertificateHistory
            } else {
                @Suppress("DEPRECATION")
                val info = pm.getPackageInfo(pkgName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                info.signatures
            }
            if (signatures.isNullOrEmpty()) return false
            val md = MessageDigest.getInstance("SHA-1")
            signatures.any { sig ->
                val sha1Hex = md.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
                REGISTERED_CERT_HASHES.contains(sha1Hex.lowercase())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not verify APK signing certificate SHA-1: ${e.message}")
            false
        }
    }

    fun attemptAutoSignIn(
        context: Context,
        credentialManager: CredentialManager,
        onAuthSuccess: () -> Unit,
        onUnauthenticated: () -> Unit,
        scope: CoroutineScope
    ) {
        if (Firebase.auth.currentUser != null) {
            onAuthSuccess()
            return
        }
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            onUnauthenticated()
            return
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        scope.launch {
            try {
                val result = credentialManager.getCredential(context, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    withTimeoutOrNull(8000L) {
                        Firebase.auth.signInWithCredential(authCredential).await()
                    }
                    onAuthSuccess()
                } else {
                    onUnauthenticated()
                }
            } catch (e: Exception) {
                onUnauthenticated()
            }
        }
    }

    fun onGoogleSignInClicked(
        context: Context,
        credentialManager: CredentialManager,
        onAuthSuccess: () -> Unit,
        onAuthError: (String) -> Unit,
        scope: CoroutineScope,
        onAuthCancelled: () -> Unit = {}
    ) {
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            onAuthError("Không tìm thấy cấu hình Google Client ID (default_web_client_id)")
            return
        }

        val activity = context.findActivity()
        if (activity == null) {
            onAuthError("Không tìm thấy Activity môi trường đăng nhập")
            return
        }

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        scope.launch {
            try {
                val result = credentialManager.getCredential(activity, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    withTimeoutOrNull(8000L) {
                        Firebase.auth.signInWithCredential(authCredential).await()
                    }
                    onAuthSuccess()
                } else {
                    onAuthError("Loại thông tin đăng nhập không hợp lệ")
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
                onAuthCancelled()
            } catch (e: Exception) {
                Log.e(TAG, "Google Sign-In failed", e)
                val msg = e.localizedMessage ?: "Đăng nhập Google thất bại"
                onAuthError(msg)
            }
        }
    }

    fun signOut(
        context: Context,
        credentialManager: CredentialManager,
        onSignOutComplete: () -> Unit,
        scope: CoroutineScope
    ) {
        Firebase.auth.signOut()
        scope.launch {
            try {
                withTimeoutOrNull(3000L) {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear credential state", e)
            } finally {
                onSignOutComplete()
            }
        }
    }
}
