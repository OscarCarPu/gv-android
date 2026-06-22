package com.gv.app.data.api

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Builder for PATCH/PUT request bodies that must distinguish three states per field:
 *  - **absent**  → key omitted entirely (server treats it as "no change")
 *  - **cleared** → key present with JSON `null` (server `NullableTime` clears the value)
 *  - **set**     → key present with a value
 *
 * Retrofit's default Gson converter *omits* `null` fields — and, surprisingly, also omits
 * explicit [JsonNull] members of a [JsonObject] — unless `serializeNulls()` is enabled. So a
 * "clear this date" or "stop this timer" intent would silently become a no-op against the
 * server's `NullableTime` fields. We therefore serialise PATCH bodies here with a dedicated
 * `serializeNulls` Gson into a ready [RequestBody] ([toRequestBody]/[toJsonString]), kept
 * separate from RetrofitClient's default converter Gson so existing typed PATCH DTOs (where
 * `null` means "no change") are never affected. The corresponding `ApiService` methods take a
 * `RequestBody` `@Body`.
 *
 * Callers add only the keys they intend to change; "clear" is expressed with [putNull].
 */
class PatchBody private constructor(private val obj: JsonObject) {

    fun put(key: String, value: String): PatchBody = apply { obj.add(key, JsonPrimitive(value)) }

    fun put(key: String, value: Int): PatchBody = apply { obj.add(key, JsonPrimitive(value)) }

    fun put(key: String, value: Boolean): PatchBody = apply { obj.add(key, JsonPrimitive(value)) }

    /** Adds an explicit JSON null — use to clear a `NullableTime` field server-side. */
    fun putNull(key: String): PatchBody = apply { obj.add(key, JsonNull.INSTANCE) }

    /** Adds a string field, or an explicit null when [value] is null (clear vs set). */
    fun putOrNull(key: String, value: String?): PatchBody =
        if (value == null) putNull(key) else put(key, value)

    /** Replaces a key with a full list of ints (e.g. `depends_on` / `blocks` replace-semantics). */
    fun putInts(key: String, values: List<Int>): PatchBody = apply {
        val arr = JsonArray()
        values.forEach { arr.add(it) }
        obj.add(key, arr)
    }

    fun isEmpty(): Boolean = obj.size() == 0

    fun build(): JsonObject = obj

    /** Serialises with explicit nulls preserved. Also used to persist outbox payloads. */
    fun toJsonString(): String = PATCH_GSON.toJson(obj)

    /** Ready-to-send body with explicit nulls preserved. */
    fun toRequestBody(): RequestBody = toJsonString().toRequestBody(JSON_MEDIA_TYPE)

    companion object {
        private val PATCH_GSON = GsonBuilder().serializeNulls().create()
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun create(): PatchBody = PatchBody(JsonObject())

        /** Rebuilds a request body from a persisted outbox payload string. */
        fun bodyFromJson(json: String): RequestBody = json.toRequestBody(JSON_MEDIA_TYPE)
    }
}
