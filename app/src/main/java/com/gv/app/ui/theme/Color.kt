package com.gv.app.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * A full palette of the app's semantic tokens. Two instances ([DarkColors], [LightColors]) let
 * the app switch theme at runtime. Property names match the original [GvColors] tokens so call
 * sites are unchanged.
 */
data class GvColorScheme(
    val Bg: Color,
    val BgLight: Color,
    val Surface: Color,
    val Text: Color,
    val TextMuted: Color,
    val Primary: Color,
    val Secondary: Color,
    val Success: Color,
    val Danger: Color,
    val Warning: Color,
    val Continuous: Color,
    val Recurring: Color,
    val Border: Color,
    val BorderLight: Color,
    val isDark: Boolean,
)

val DarkColors = GvColorScheme(
    Bg = Color(0xFF0B0F1A),
    BgLight = Color(0xFF141926),
    Surface = Color(0xFF1A2033),
    Text = Color(0xFFE8ECF4),
    TextMuted = Color(0xFF7B8BA5),
    Primary = Color(0xFF3B82F6),
    Secondary = Color(0xFFA78BFA),
    Success = Color(0xFF34D399),
    Danger = Color(0xFFF87171),
    Warning = Color(0xFFFBBF24),
    Continuous = Color(0xFF2DD4BF),
    Recurring = Color(0xFFF59E0B),
    Border = Color(0x0FFFFFFF),
    BorderLight = Color(0x14FFFFFF),
    isDark = true,
)

val LightColors = GvColorScheme(
    Bg = Color(0xFFF6F7FB),
    BgLight = Color(0xFFFFFFFF),
    Surface = Color(0xFFEDF0F6),
    Text = Color(0xFF161B2B),
    TextMuted = Color(0xFF5A6B85),
    Primary = Color(0xFF2563EB),
    Secondary = Color(0xFF7C3AED),
    Success = Color(0xFF059669),
    Danger = Color(0xFFDC2626),
    Warning = Color(0xFFD97706),
    Continuous = Color(0xFF0D9488),
    Recurring = Color(0xFFD97706),
    Border = Color(0x14000000),
    BorderLight = Color(0x1F000000),
    isDark = false,
)

/**
 * The active palette. Backed by a Compose [mutableStateOf] so reads of `GvColors.Bg` inside a
 * composable re-run when [GvTheme] flips [scheme] — without rewriting every call site to a
 * CompositionLocal. Reads from non-@Composable helpers return the current value.
 */
object GvColors {
    var scheme: GvColorScheme by mutableStateOf(DarkColors)

    val Bg get() = scheme.Bg
    val BgLight get() = scheme.BgLight
    val Surface get() = scheme.Surface
    val Text get() = scheme.Text
    val TextMuted get() = scheme.TextMuted
    val Primary get() = scheme.Primary
    val Secondary get() = scheme.Secondary
    val Success get() = scheme.Success
    val Danger get() = scheme.Danger
    val Warning get() = scheme.Warning
    val Continuous get() = scheme.Continuous
    val Recurring get() = scheme.Recurring
    val Border get() = scheme.Border
    val BorderLight get() = scheme.BorderLight
}
