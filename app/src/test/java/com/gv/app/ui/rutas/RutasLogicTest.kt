package com.gv.app.ui.rutas

import com.gv.app.data.local.db.ConcelloMarkEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class RutasLogicTest {

    private fun concello(name: String, province: String) = Concello(name, province, emptyList())

    private val concellos = listOf(
        concello("Vigo", "Pontevedra"),
        concello("Lugo", "Lugo"),
        concello("Ourense", "Ourense"),
        concello("Pontevedra", "Pontevedra"),
    )

    private fun mark(name: String, on: String, note: String = "") = ConcelloMarkEntity(name, serverId = 1, visitedOn = on, description = note)

    private fun marks(vararg m: ConcelloMarkEntity) = m.associateBy { it.name }

    // --- progress -------------------------------------------------------------------------

    @Test
    fun `with no filter the counter covers all of Galicia`() {
        assertEquals(Progress(2, 4), progressIn(concellos, marks(mark("Vigo", "2026-01-01"), mark("Lugo", "2026-02-01")), null))
    }

    @Test
    fun `a province filter counts only that province`() {
        val m = marks(mark("Vigo", "2026-01-01"), mark("Lugo", "2026-02-01"))
        assertEquals(Progress(1, 2), progressIn(concellos, m, "Pontevedra"))
        assertEquals(Progress(0, 1), progressIn(concellos, m, "Ourense"))
    }

    @Test
    fun `a mark that matches no concello is not counted`() {
        assertEquals(Progress(0, 4), progressIn(concellos, marks(mark("Atlantis", "2026-01-01")), null))
    }

    @Test
    fun `an unknown province is empty, not an error`() {
        assertEquals(Progress(0, 0), progressIn(concellos, marks(mark("Vigo", "2026-01-01")), "Madrid"))
    }

    // --- visited list ---------------------------------------------------------------------

    @Test
    fun `the list is oldest visit first, ties by name`() {
        val list = visitedItems(
            concellos,
            marks(mark("Vigo", "2026-03-01"), mark("Lugo", "2026-01-05"), mark("Ourense", "2026-03-01")),
        )
        assertEquals(listOf("Lugo", "Ourense", "Vigo"), list.map { it.name })
    }

    @Test
    fun `the list carries the province and note, and trims a timestamp to its date`() {
        val item = visitedItems(concellos, marks(mark("Vigo", "2026-03-01T10:00:00Z", "beach"))).single()
        assertEquals("Pontevedra", item.province)
        assertEquals("2026-03-01", item.date)
        assertEquals("beach", item.description)
    }

    @Test
    fun `a mark with no concello in the geometry is left out of the list`() {
        assertEquals(emptyList<VisitedItem>(), visitedItems(concellos, marks(mark("Atlantis", "2026-01-01"))))
    }

    @Test
    fun `nothing visited is an empty list`() {
        assertEquals(emptyList<VisitedItem>(), visitedItems(concellos, emptyMap()))
    }
}
