package com.gv.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.gv.app.data.local.ThemePreference

private fun materialFrom(c: GvColorScheme) = if (c.isDark) {
    darkColorScheme(
        primary = c.Primary, onPrimary = Color.White,
        secondary = c.Secondary, onSecondary = Color.White, tertiary = c.Secondary,
        background = c.Bg, onBackground = c.Text,
        surface = c.BgLight, onSurface = c.Text,
        surfaceVariant = c.Surface, onSurfaceVariant = c.TextMuted,
        error = c.Danger, onError = Color.White,
        outline = c.BorderLight, outlineVariant = c.Border,
    )
} else {
    lightColorScheme(
        primary = c.Primary, onPrimary = Color.White,
        secondary = c.Secondary, onSecondary = Color.White, tertiary = c.Secondary,
        background = c.Bg, onBackground = c.Text,
        surface = c.BgLight, onSurface = c.Text,
        surfaceVariant = c.Surface, onSurfaceVariant = c.TextMuted,
        error = c.Danger, onError = Color.White,
        outline = c.BorderLight, outlineVariant = c.Border,
    )
}

@Composable
fun GvTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { context.applicationContext.let { (it as com.gv.app.GvApp).container.themeStore } }
    val preference by store.theme.collectAsState(initial = ThemePreference.SYSTEM)
    val systemDark = isSystemInDarkTheme()

    val dark = when (preference) {
        ThemePreference.SYSTEM -> systemDark
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    val target = if (dark) DarkColors else LightColors
    // Bridge the runtime palette into the state-backed GvColors so direct `GvColors.X` reads flip.
    if (GvColors.scheme != target) GvColors.scheme = target

    CompositionLocalProvider(LocalSpacing provides Spacing()) {
        MaterialTheme(
            colorScheme = materialFrom(target),
            typography = GvTypography,
            shapes = GvShapes,
            content = content,
        )
    }
}
