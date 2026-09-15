#!/bin/bash
sed -i 's/Color.Red/MaterialTheme.colorScheme.error/g' app/src/main/java/com/example/ui/PosApp.kt
sed -i 's/Color.Gray/MaterialTheme.colorScheme.onSurfaceVariant/g' app/src/main/java/com/example/ui/PosApp.kt
sed -i 's/Color.LightGray/MaterialTheme.colorScheme.outline/g' app/src/main/java/com/example/ui/PosApp.kt
