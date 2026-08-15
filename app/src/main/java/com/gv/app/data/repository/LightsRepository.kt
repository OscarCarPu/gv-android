package com.gv.app.data.repository

import com.gv.app.data.api.ApiService
import com.gv.app.domain.model.LightCommandRequest
import com.gv.app.domain.model.LightRgb
import com.gv.app.domain.model.LightState

/**
 * Domotics lights, served by gv-api like every other domain.
 *
 * Deliberately **not cached**. Every other repository keeps a Room copy so its screen still
 * reads offline, but a bulb's state is only meaningful live: showing "on, 40%" from an hour
 * ago invites tapping a control that cannot run, and the answer to "is the light on?" is
 * visible from the sofa anyway. With no connection the screen says so and shows nothing.
 *
 * The BLE bridge behind the API is slow on a cold connect (it may have to rediscover an
 * unbonded bulb), so calls here can take several seconds. gv-api answers `200` with
 * `online: false` for an unreachable bulb rather than failing, so a dead bulb never fails the
 * whole request.
 */
class LightsRepository(
    private val api: ApiService,
    private val gate: OnlineGate,
) {

    /** All bulbs. [force] bypasses gv-api's short read cache for a genuinely live read. */
    suspend fun loadStates(force: Boolean = false): ApiResult<List<LightState>> {
        gate.requireOnline()?.let { return it }
        return when (val r = safeApiCall { api.getLightStates(if (force) 1 else null) }) {
            is ApiResult.Success -> ApiResult.Success(r.data.states)
            is ApiResult.Failure -> r
        }
    }

    suspend fun setPower(id: String, on: Boolean): ApiResult<LightState> =
        send(id, LightCommandRequest.power(on))

    suspend fun setBrightness(id: String, value: Int): ApiResult<LightState> =
        send(id, LightCommandRequest.brightness(value.coerceIn(0, 100)))

    suspend fun setColorTemp(id: String, kelvin: Int): ApiResult<LightState> =
        send(id, LightCommandRequest.colorTemp(kelvin))

    suspend fun setColor(id: String, color: LightRgb): ApiResult<LightState> =
        send(id, LightCommandRequest.color(color))

    private suspend fun send(id: String, command: LightCommandRequest): ApiResult<LightState> {
        gate.requireOnline()?.let { return it }
        return safeApiCall { api.sendLightCommand(id, command) }
    }
}
