sed -i '1d' app/src/main/java/com/example/ui/PosApp.kt
sed -i '/package com.example.ui/a \import androidx.compose.animation.AnimatedVisibility' app/src/main/java/com/example/ui/PosApp.kt
