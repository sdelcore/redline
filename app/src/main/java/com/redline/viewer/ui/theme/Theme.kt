package com.redline.viewer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkScheme = darkColorScheme(
    background = RedlineColors.Bg,
    surface = RedlineColors.Surface,
    surfaceVariant = RedlineColors.Surface2,
    onBackground = RedlineColors.Text,
    onSurface = RedlineColors.Text,
    onSurfaceVariant = RedlineColors.TextDim,
    primary = RedlineColors.Accent,
    onPrimary = RedlineColors.Bg,
    secondary = RedlineColors.Blue,
    outline = RedlineColors.Border,
    outlineVariant = RedlineColors.BorderSoft,
)

@Composable
fun RedlineTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        content = content,
    )
}
