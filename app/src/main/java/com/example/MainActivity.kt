package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.MainViewModel
import com.example.ui.PosApp
import com.example.ui.MainTab
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    private var pendingAction by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            val viewModel: MainViewModel = viewModel()
            var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

            androidx.compose.runtime.DisposableEffect(Unit) {
                val listener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { auth ->
                    val user = auth.currentUser
                    currentUser = user
                    if (user != null) {
                        val activeAcc = com.example.ui.AccountAuthManager.getActiveAccount(this@MainActivity)
                        if (activeAcc != null && (activeAcc.userId == user.uid || activeAcc.email.equals(user.email, ignoreCase = true))) {
                            viewModel.syncUserProfile(user)
                        }
                    }
                }
                com.google.firebase.Firebase.auth.addAuthStateListener(listener)
                onDispose {
                    com.google.firebase.Firebase.auth.removeAuthStateListener(listener)
                }
            }

            LaunchedEffect(pendingAction) {
                if (pendingAction == "ACTION_SHOW_BACKUP") {
                    viewModel.selectTab(MainTab.CAI_DAT)
                    pendingAction = null
                }
            }

            val currentLang by viewModel.currentLanguage.collectAsStateWithLifecycle()
            androidx.compose.runtime.key(currentLang) {
                PosApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == "ACTION_SHOW_BACKUP") {
            pendingAction = "ACTION_SHOW_BACKUP"
        }
    }
}
