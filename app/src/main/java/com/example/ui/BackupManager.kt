package com.example.ui

import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow

object BackupManager {
    val backupProgress = MutableStateFlow<Float?>(null)
    val backupMessage = MutableStateFlow<String?>(null)
    var exportBackupJob: Job? = null
    var activeBackupUri: Uri? = null
    val scope = CoroutineScope(Dispatchers.IO)
}
