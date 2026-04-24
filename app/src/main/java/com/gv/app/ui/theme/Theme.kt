package com.gv.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val GvColorScheme = darkColorScheme(
    primary         = GvColors.Primary,
    onPrimary       = androidx.compose.ui.graphics.Color.White,
    secondary       = GvColors.Secondary,
    onSecondary     = androidx.compose.ui.graphics.Color.White,
    tertiary        = GvColors.Secondary,
    background      = GvColors.Bg,
    onBackground    = GvColors.Text,
    surface         = GvColors.BgLight,
    onSurface       = GvColors.Text,
    surfaceVariant  = GvColors.Surface,
    onSurfaceVariant = GvColors.TextMuted,
    error           = GvColors.Danger,
    onError         = androidx.compose.ui.graphics.Color.White,
    outline         = GvColors.BorderLight,
    outlineVariant  = GvColors.Border,
)

@Composable
fun GvTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSpacing provides Spacing()) {
        MaterialTheme(
            colorScheme = GvColorScheme,
            typography = GvTypography,
            shapes = GvShapes,
            content = content,
        )
    }
}
