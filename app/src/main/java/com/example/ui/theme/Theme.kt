package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = NaturalPrimaryDark,
    secondary = NaturalSecondaryDark,
    tertiary = NaturalPrimaryContainerDark,
    background = NaturalBackgroundDark,
    surface = NaturalSurfaceDark,
    onPrimary = NaturalOnPrimaryDark,
    onSecondary = NaturalOnSecondaryDark,
    onBackground = NaturalOnBackgroundDark,
    onSurface = NaturalOnSurfaceDark,
    primaryContainer = NaturalPrimaryContainerDark,
    onPrimaryContainer = NaturalOnPrimaryContainerDark,
    secondaryContainer = NaturalPrimaryContainerDark,
    onSecondaryContainer = NaturalOnPrimaryContainerDark
)

private val LightColorScheme = lightColorScheme(
    primary = NaturalPrimary,
    secondary = NaturalSecondary,
    tertiary = NaturalTertiary,
    background = NaturalBackground,
    surface = NaturalSurface,
    onPrimary = NaturalOnPrimary,
    onSecondary = NaturalOnSecondary,
    onBackground = NaturalOnBackground,
    onSurface = NaturalOnSurface,
    primaryContainer = NaturalPrimaryContainer,
    onPrimaryContainer = NaturalOnPrimaryContainer,
    secondaryContainer = NaturalSecondaryContainer,
    onSecondaryContainer = NaturalOnSecondaryContainer
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
