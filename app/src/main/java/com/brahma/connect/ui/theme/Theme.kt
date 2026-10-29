package com.brahma.connect.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BrahmaDarkColors = darkColorScheme(
    primary = Color(0xFF00E5FF),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF003846),
    onPrimaryContainer = Color(0xFF80F0FF),
    secondary = Color(0xFF70E0FF),
    onSecondary = Color(0xFF000000),
    background = Color(0xFF050505),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF0F141C),
    onSurface = Color(0xFFF0F0F0),
    surfaceVariant = Color(0xFF1B2230),
    onSurfaceVariant = Color(0xFFB0BEC5),
    error = Color(0xFFFF5252),
    onError = Color(0xFF000000)
)

@Composable
fun BrahmaConnectTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BrahmaDarkColors,
        content = content
    )
}
