sed -i 's/val textColor = MaterialTheme.colorScheme.primary/val textColor = if (isDark) Color(0xFF4CAF50) else Color(0xFF2E7D32)/g' app/src/main/java/com/example/ui/PosApp.kt
