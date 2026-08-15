package com.gv.app.ui.domotics

import androidx.compose.ui.graphics.Color
import kotlin.math.ln
import kotlin.math.pow

/**
 * Colour helpers for the Lights cards, mirroring gv-web's `lights/color.ts` so both clients
 * render the same bulb the same way.
 */

/**
 * Approximate sRGB for a colour temperature, using Tanner Helland's piecewise fit.
 *
 * Accurate enough to preview what a warm-vs-cool setting will look like, and it avoids asking
 * the bulb what its own whites look like — which it cannot answer.
 */
fun kelvinToColor(kelvin: Double): Color {
    val t = kelvin.coerceIn(1000.0, 40000.0) / 100.0

    val r: Double
    val g: Double
    if (t <= 66) {
        r = 255.0
        g = 99.4708025861 * ln(t) - 161.1195681661
    } else {
        r = 329.698727446 * (t - 60).pow(-0.1332047592)
        g = 288.1221695283 * (t - 60).pow(-0.0755148492)
    }

    val b: Double = when {
        t >= 66 -> 255.0
        t <= 19 -> 0.0
        else -> 138.5177312231 * ln(t - 10) - 305.0447927307
    }

    return Color(
        red = (r / 255.0).toFloat().coerceIn(0f, 1f),
        green = (g / 255.0).toFloat().coerceIn(0f, 1f),
        blue = (b / 255.0).toFloat().coerceIn(0f, 1f),
    )
}

/**
 * Stops for the colour-temperature track: warm on the left, cool on the right, matching the
 * slider's own min..max (these bulbs run 2700K warm to 6500K cool).
 *
 * Fixed stops rather than a sampled [kelvinToColor] sweep, and the same five the web uses, so
 * the two look identical side by side.
 */
val TemperatureTrackColors: List<Color> = listOf(
    Color(0xFFFF9329),
    Color(0xFFFFD7A8),
    Color(0xFFFFFFFF),
    Color(0xFFCFE3FF),
    Color(0xFFA8C8FF),
)

/** Dim-to-full in the bulb's own colour, so the track previews what it will actually look like. */
fun brightnessTrackColors(dark: Color, glow: Color): List<Color> = listOf(dark, glow)
