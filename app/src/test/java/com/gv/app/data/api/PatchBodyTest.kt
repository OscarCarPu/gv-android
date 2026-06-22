package com.gv.app.data.api

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the explicit-null contract the offline outbox + PATCH endpoints rely on.
 */
class PatchBodyTest {

    private val defaultGson = Gson()

    @Test
    fun `default gson omits even an explicit JsonObject null -- the gotcha we work around`() {
        // Documents why the converter's default Gson cannot be used to clear NullableTime fields.
        assertEquals("{}", defaultGson.toJson(PatchBody.create().putNull("finished_at").build()))
    }

    @Test
    fun `patch body serialises an explicit null for a cleared field`() {
        assertEquals("""{"finished_at":null}""", PatchBody.create().putNull("finished_at").toJsonString())
    }

    @Test
    fun `set value is serialised and untouched keys are omitted`() {
        val json = PatchBody.create().put("started_at", "2026-06-22T10:00:00Z").toJsonString()
        assertTrue(json.contains(""""started_at":"2026-06-22T10:00:00Z""""))
        assertFalse(json.contains("finished_at"))
    }

    @Test
    fun `putOrNull distinguishes clear from set`() {
        assertEquals("""{"due_at":null}""", PatchBody.create().putOrNull("due_at", null).toJsonString())
        assertEquals(
            """{"due_at":"2026-06-22"}""",
            PatchBody.create().putOrNull("due_at", "2026-06-22").toJsonString(),
        )
    }

    @Test
    fun `default gson omits a null field on a typed dto -- the bug this guards against`() {
        data class Dto(val finished_at: String?)
        assertEquals("{}", defaultGson.toJson(Dto(finished_at = null)))
    }
}
