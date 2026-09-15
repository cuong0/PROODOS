import re

file_path = "app/src/main/java/com/example/ui/MainViewModel.kt"
with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

content = content.replace("var backupProgress = MutableStateFlow<Float?>(null)", "val backupProgress = BackupManager.backupProgress")
content = content.replace("var backupMessage = MutableStateFlow<String?>(null)", "val backupMessage = BackupManager.backupMessage")
content = content.replace("private var exportBackupJob: kotlinx.coroutines.Job? = null", "")
content = content.replace("private var activeBackupUri: Uri? = null", "private val activeBackupUri: Uri?\n        get() = BackupManager.activeBackupUri")

content = content.replace("exportBackupJob?.cancel()", "BackupManager.exportBackupJob?.cancel()")
content = content.replace("exportBackupJob = null", "BackupManager.exportBackupJob = null")
content = content.replace("exportBackupJob?.let { job ->", "BackupManager.exportBackupJob?.let { job ->")

# For exportBackup function, we need to change how it is launched and add startService
def export_repl(m):
    return """fun exportBackup(context: Context, uri: Uri, onResult: (Boolean, String) -> Unit) {
        val appContext = context.applicationContext
        val serviceIntent = Intent(appContext, BackupService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            appContext.startForegroundService(serviceIntent)
        } else {
            appContext.startService(serviceIntent)
        }
        BackupManager.activeBackupUri = uri
        BackupManager.exportBackupJob = BackupManager.scope.launch {
            val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager"""

content = re.sub(r'fun exportBackup\(context: Context, uri: Uri, onResult: \(Boolean, String\) -> Unit\) \{\s+activeBackupUri = uri\s+exportBackupJob = viewModelScope\.launch\(kotlinx\.coroutines\.Dispatchers\.IO\) \{\s+val notificationManager = context\.getSystemService\(Context\.NOTIFICATION_SERVICE\) as android\.app\.NotificationManager', export_repl, content)

content = content.replace("val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)", "val builder = androidx.core.app.NotificationCompat.Builder(appContext, channelId)")

content = re.sub(r'if \(android\.os\.Build\.VERSION\.SDK_INT < android\.os\.Build\.VERSION_CODES\.TIRAMISU \|\| androidx\.core\.content\.ContextCompat\.checkSelfPermission\(context, android\.Manifest\.permission\.POST_NOTIFICATIONS\) == android\.content\.pm\.PackageManager\.PERMISSION_GRANTED\) \{', 'if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU || androidx.core.content.ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {', content)

content = content.replace("val notificationManagerCancel = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager", "val notificationManagerCancel = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager")
content = content.replace("android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri)", "android.provider.DocumentsContract.deleteDocument(appContext.contentResolver, uri)")
content = content.replace("context.contentResolver.openOutputStream(uri, \"rwt\")?.use", "appContext.contentResolver.openOutputStream(uri, \"rwt\")?.use")

# At the end of finally block in exportBackup:
# stop service
finally_pattern = r'\} finally \{\s+withContext\(kotlinx\.coroutines\.Dispatchers\.Main\) \{\s+backupProgress\.value = null\s+backupMessage\.value = null\s+\}\s+\}'
finally_replacement = """} finally {
                appContext.stopService(Intent(appContext, BackupService::class.java))
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    backupProgress.value = null
                    backupMessage.value = null
                }
            }"""
content = re.sub(finally_pattern, finally_replacement, content)

# Update cancelBackup
cancel_repl = """fun cancelBackup(context: Context) {
        val appContext = context.applicationContext
        appContext.stopService(Intent(appContext, BackupService::class.java))"""
content = content.replace("fun cancelBackup(context: Context) {", cancel_repl)

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

print("MainViewModel patched")
