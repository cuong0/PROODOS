package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.MainViewModel
import com.example.ui.PosApp
import com.example.ui.MainTab

class MainActivity : ComponentActivity() {
    private var pendingAction by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            val viewModel: MainViewModel = viewModel()
            
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
