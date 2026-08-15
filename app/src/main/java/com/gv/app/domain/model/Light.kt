package com.gv.app.domain.model

/**
 * Domotics light DTOs. Field names are snake-free camelCase because gv-web serialises the
 * TypeScript shape verbatim — unlike gv-api's snake_case JSON — so Gson's defaults match as-is.
 * Mirrors `src/lib/server/domotics/lights/types.ts`.
 */

data class LightRgb(
    val r: Int = 255,
    val g: Int = 255,
    val b: Int = 255,
)

data class LightState(
    val id: String,
    val name: String,
    val model: String,
    /** False when the bridge could not reach the bulb; the rest is then last-known, not live. */
    val online: Boolean,
    val power: Boolean,
    /** 0–100, already normalised by the server from whatever scale the bulb uses. */
    val brightness: Double,
    /** "color" or "white". */
    val mode: String,
    val color: LightRgb = LightRgb(),
    val colorTemp: Double,
    val supportsColor: Boolean,
    val supportsColorTemp: Boolean,
    val minColorTemp: Double,
    val maxColorTemp: Double,
    /** Per-bulb error, shown on its card instead of failing the screen. */
    val error: String? = null,
    val updatedAt: Long = 0L,
)

data class LightStatesResponse(
    val states: List<LightState>,
)

/**
 * One command. The server discriminates on [type] and ignores the fields that do not apply,
 * so exactly one of [on] / [value] / [color] / [kelvin] is set per instance — see the factory
 * methods, which are the only intended way to build these.
 */
data class LightCommandRequest(
    val type: String,
    val on: Boolean? = null,
    val value: Int? = null,
    val color: LightRgb? = null,
    val kelvin: Int? = null,
) {
    companion object {
        fun power(on: Boolean) = LightCommandRequest(type = "power", on = on)

        fun brightness(value: Int) = LightCommandRequest(type = "brightness", value = value)

        fun color(color: LightRgb) = LightCommandRequest(type = "color", color = color)

        fun colorTemp(kelvin: Int) = LightCommandRequest(type = "colorTemp", kelvin = kelvin)
    }
}
