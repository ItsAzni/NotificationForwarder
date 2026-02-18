package com.itsazni.notificationforwarder.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9EC8FF),
    onPrimary = Color(0xFF0A2A4A),
    primaryContainer = Color(0xFF1F3F62),
    onPrimaryContainer = Color(0xFFD7E8FF),
    secondary = Color(0xFFB6C8DE),
    secondaryContainer = Color(0xFF324A63),
    tertiary = Color(0xFFA9D0F5),
    tertiaryContainer = Color(0xFF2A4A65),
    background = Color(0xFF0F1722),
    surface = Color(0xFF111B27),
    surfaceVariant = Color(0xFF223041),
    error = Color(0xFFF2B8B5),
    errorContainer = Color(0xFF8C1D18)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}
