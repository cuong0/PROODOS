#!/bin/bash
sed -i 's/focusedContainerColor = Color.White/focusedContainerColor = MaterialTheme.colorScheme.surface/g' app/src/main/java/com/example/ui/PosApp.kt
sed -i 's/unfocusedContainerColor = Color.White/unfocusedContainerColor = MaterialTheme.colorScheme.surface/g' app/src/main/java/com/example/ui/PosApp.kt
sed -i 's/color = if (isSelected) Color.White else MaterialTheme.colorScheme.onBackground/color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground/g' app/src/main/java/com/example/ui/PosApp.kt
sed -i 's/color = Color.White/color = MaterialTheme.colorScheme.onPrimary/g' app/src/main/java/com/example/ui/PosApp.kt
sed -i 's/containerColor = Color.White/containerColor = MaterialTheme.colorScheme.surface/g' app/src/main/java/com/example/ui/PosApp.kt
sed -i 's/background(Color.White)/background(MaterialTheme.colorScheme.surface)/g' app/src/main/java/com/example/ui/PosApp.kt
sed -i 's/contentColor = Color.White/contentColor = MaterialTheme.colorScheme.onPrimary/g' app/src/main/java/com/example/ui/PosApp.kt
sed -i 's/checkedThumbColor = Color.White/checkedThumbColor = MaterialTheme.colorScheme.surface/g' app/src/main/java/com/example/ui/PosApp.kt
sed -i 's/tint = Color.White/tint = MaterialTheme.colorScheme.onPrimary/g' app/src/main/java/com/example/ui/PosApp.kt
