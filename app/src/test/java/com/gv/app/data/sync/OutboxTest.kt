package com.gv.app.data.sync

import com.gv.app.data.local.db.OutboxDao
import com.gv.app.data.local.db.OutboxMutation
import com.gv.app.data.local.db.OutboxMutation.Companion.OP_CREATE
import com.gv.app.data.local.db.OutboxMutation.Companion.OP_DELETE
import com.gv.app.data.local.db.OutboxMutation.Companion.OP_UPDATE
import com.gv.app.data.local.db.OutboxMutation.Companion.OP_UPSERT
import com.gv.app.data.local.db.OutboxMutation.Companion.TMP_PREFIX
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory fake so the collapse/merge rules can be tested without Android/Room. */
private class FakeOutboxDao : OutboxDao {
    val rows = mutableListOf<OutboxMutation>()
    private var nextId = 1L

    override suspend fun pending() = rows.filter { it.status == OutboxMutation.STATUS_PENDING }.sortedBy { it.createdAt }
    override suspend fun byId(id: Long): OutboxMutation? = rows.firstOrNull { it.id == id }
    override suspend fun countPendingForEntity(type: String, entityId: String): Int =
        rows.count { it.entityType == type && it.entityId == entityId && it.status == OutboxMutation.STATUS_PENDING }
    override suspend fun findPending(type: String, entityId: String, op: String) =
        rows.lastOrNull { it.status == OutboxMutation.STATUS_PENDING && it.entityType == type && it.entityId == entityId && it.operation == op }
    override suspend fun pendingForEntity(type: String, entityId: String) =
        rows.filter { it.status == OutboxMutation.STATUS_PENDING && it.entityType == type && it.entityId == entityId }
    override suspend fun insert(mutation: OutboxMutation): Long {
        val id = nextId++
        rows.add(mutation.copy(id = id))
        return id
    }
    override suspend fun update(mutation: OutboxMutation) {
        val i = rows.indexOfFirst { it.id == mutation.id }
        if (i >= 0) rows[i] = mutation
    }
    override suspend fun deleteById(id: Long) { rows.removeAll { it.id == id } }
    override suspend fun deletePendingForEntity(type: String, entityId: String) {
        rows.removeAll { it.entityType == type && it.entityId == entityId && it.status == OutboxMutation.STATUS_PENDING }
    }
    override suspend fun remapEntityId(type: String, tmpId: String, realId: String) {
        rows.replaceAll { if (it.entityType == type && it.entityId == tmpId) it.copy(entityId = realId) else it }
    }
    override fun pendingCountFlow(): Flow<Int> = flowOf(0)
    override fun failedFlow(): Flow<List<OutboxMutation>> = flowOf(emptyList())
    override suspend fun pendingCount(): Int = pending().size
    override suspend fun clearFailed() { rows.removeAll { it.status == OutboxMutation.STATUS_FAILED } }
}

class OutboxTest {

    private var clock = 1000L
    private fun newOutbox(dao: FakeOutboxDao) = Outbox(dao) { clock++ }

    @Test
    fun `repeated upserts collapse into one merged row`() = runTest {
        val dao = FakeOutboxDao()
        val outbox = newOutbox(dao)
        outbox.enqueueUpsert("habit_log", "5:2026-06-22", """{"value":1.0}""")
        outbox.enqueueUpsert("habit_log", "5:2026-06-22", """{"value":3.0}""")

        val pending = dao.pending()
        assertEquals(1, pending.size)
        assertTrue(pending.first().payloadJson.contains("\"value\":3.0"))
    }

    @Test
    fun `update merge keeps untouched fields and applies newer ones`() = runTest {
        val dao = FakeOutboxDao()
        val outbox = newOutbox(dao)
        outbox.enqueueUpdate("task", "7", """{"name":"A"}""")
        outbox.enqueueUpdate("task", "7", """{"priority":1}""")

        val row = dao.pending().single()
        assertTrue(row.payloadJson.contains("\"name\":\"A\""))
        assertTrue(row.payloadJson.contains("\"priority\":1"))
    }

    @Test
    fun `delete of a local-only create collapses to a no-op`() = runTest {
        val dao = FakeOutboxDao()
        val outbox = newOutbox(dao)
        val tmp = TMP_PREFIX + "abc"
        outbox.enqueueCreate("time_entry", tmp, """{"task_id":1}""")
        outbox.enqueueDelete("time_entry", tmp)

        // Nothing should remain to send — the server never saw the entity.
        assertTrue(dao.pending().isEmpty())
    }

    @Test
    fun `delete of a synced entity queues a delete`() = runTest {
        val dao = FakeOutboxDao()
        val outbox = newOutbox(dao)
        outbox.enqueueDelete("task", "42")

        val row = dao.pending().single()
        assertEquals(OP_DELETE, row.operation)
        assertEquals("42", row.entityId)
    }

    @Test
    fun `delete drops a prior pending update for a synced entity`() = runTest {
        val dao = FakeOutboxDao()
        val outbox = newOutbox(dao)
        outbox.enqueueUpdate("task", "42", """{"name":"x"}""")
        outbox.enqueueDelete("task", "42")

        val rows = dao.pending()
        assertEquals(1, rows.size)
        assertEquals(OP_DELETE, rows.single().operation)
    }

    @Test
    fun `remap re-points queued follow-ups from tmp to server id`() = runTest {
        val dao = FakeOutboxDao()
        val outbox = newOutbox(dao)
        val tmp = TMP_PREFIX + "xyz"
        outbox.enqueueCreate("time_entry", tmp, """{"task_id":1}""")
        outbox.enqueueUpdate("time_entry", tmp, """{"finished_at":"2026-06-22T10:00:00Z"}""")
        outbox.remapCreatedId("time_entry", tmp, "99")

        assertTrue(dao.rows.none { it.entityId == tmp })
        assertTrue(dao.rows.all { it.entityId == "99" })
        // The create still precedes the update (FIFO by createdAt).
        val ordered = dao.pending()
        assertEquals(OP_CREATE, ordered.first().operation)
        assertEquals(OP_UPDATE, ordered.last().operation)
    }

    @Test
    fun `merge preserves an explicit null over an earlier value`() {
        val merged = Outbox.mergeJson("""{"finished_at":"x"}""", """{"finished_at":null}""")
        assertTrue(merged.contains("\"finished_at\":null"))
    }

    @Test
    fun `findPending returns null when nothing queued`() = runTest {
        val dao = FakeOutboxDao()
        assertNull(dao.findPending("task", "1", OP_UPSERT))
    }
}
