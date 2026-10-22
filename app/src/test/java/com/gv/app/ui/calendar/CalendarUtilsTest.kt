package com.gv.app.ui.calendar

import com.gv.app.domain.model.CalendarEvent
import com.gv.app.domain.model.PlanBlockResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The calendar's silent-failure surface: things that render plausibly while being wrong — an
 * appointment on the wrong day, an event an hour out, an event drawn twice.
 */
class CalendarUtilsTest {

    private fun item(startsAt: String, endsAt: String, key: String = "k") = AgendaItem(
        key = key, kind = ItemKind.EVENT, title = "Thing", startsAt = startsAt, endsAt = endsAt,
        event = null, planned = false,
    )

    private fun event(id: Int, name: String = "Dentist") = CalendarEvent(
        id = id, name = name, start_time = "2026-08-20T07:00:00Z", end_time = "2026-08-20T08:00:00Z",
    )

    private fun block(id: Int, eventRef: String? = null, task: String? = null, label: String = "Focus") =
        PlanBlockResponse(
            id = id, plan_date = "2026-08-20", started_at = "2026-08-20T07:00:00Z",
            ended_at = "2026-08-20T08:00:00Z", task_id = null, task_name = task, label = label,
            note = null, event_ref = eventRef, commitment_id = null, task_type = null,
            task_recurrence = null, task_started_at = null, task_finished_at = null,
        )

    // --- sources --------------------------------------------------------------------------

    private val events = listOf(event(1), event(2, "Lunch"))
    private val blocks = listOf(block(10, eventRef = "1", task = "Dentist prep"), block(11))

    @Test
    fun `events only shows events, and marks the one with a plan`() {
        val items = buildItems(events, blocks, CalendarSource.EVENTS)
        assertEquals(listOf("event-1", "event-2"), items.map { it.key })
        assertTrue(items.first { it.key == "event-1" }.planned)
        assertFalse(items.first { it.key == "event-2" }.planned)
    }

    @Test
    fun `plans only shows every block, linked or not`() {
        val items = buildItems(events, blocks, CalendarSource.PLANS)
        assertEquals(listOf("plan-10", "plan-11"), items.map { it.key })
        assertEquals(listOf("Dentist prep", "Focus"), items.map { it.title })
    }

    @Test
    fun `both shows an event with a plan once, as its plan block`() {
        val items = buildItems(events, blocks, CalendarSource.BOTH)
        assertEquals(listOf("event-2", "plan-10", "plan-11"), items.map { it.key })
        // The plan block still opens the event it hangs off; an unlinked one opens nothing.
        assertEquals(1, items.first { it.key == "plan-10" }.event?.id)
        assertEquals(null, items.first { it.key == "plan-11" }.event)
    }

    // --- placement ------------------------------------------------------------------------

    @Test
    fun `a multi-day item shows on every day it covers, end exclusive`() {
        val utc = ZoneOffset.UTC
        val trip = item("2026-08-19T22:00:00Z", "2026-08-21T00:00:00Z")
        assertTrue(occupiesDay(trip, LocalDate.of(2026, 8, 19), utc))
        assertTrue(occupiesDay(trip, LocalDate.of(2026, 8, 20), utc))
        assertFalse(occupiesDay(trip, LocalDate.of(2026, 8, 21), utc))
    }

    @Test
    fun `a zero-length item is placed by where it starts`() {
        val point = item("2026-08-20T09:00:00Z", "2026-08-20T09:00:00Z")
        assertTrue(occupiesDay(point, LocalDate.of(2026, 8, 20), ZoneOffset.UTC))
        assertFalse(occupiesDay(point, LocalDate.of(2026, 8, 21), ZoneOffset.UTC))
    }

    @Test
    fun `an item is read at the offset the API sent, not only at Z`() {
        // 00:30 on the 21st in Madrid is still the 20th at 22:30Z.
        val late = item("2026-08-21T00:30:00+02:00", "2026-08-21T01:30:00+02:00")
        val madrid = ZoneId.of("Europe/Madrid")
        assertTrue(occupiesDay(late, LocalDate.of(2026, 8, 21), madrid))
        assertFalse(occupiesDay(late, LocalDate.of(2026, 8, 20), madrid))
    }

    @Test
    fun `a picked wall clock becomes the instant it names, not the same clock face in UTC`() {
        val madrid = ZoneId.of("Europe/Madrid")
        assertEquals("2026-08-20T15:00:00Z", localToApiInstant(LocalDateTime.of(2026, 8, 20, 17, 0), madrid))
        assertEquals("2026-01-20T16:00:00Z", localToApiInstant(LocalDateTime.of(2026, 1, 20, 17, 0), madrid))
    }

    // --- ranges ---------------------------------------------------------------------------

    @Test
    fun `a month range covers whole Monday-first weeks`() {
        // August 2026 starts on a Saturday, so the grid opens on Monday 27 July.
        val august = LocalDate.of(2026, 8, 20)
        assertEquals(LocalDate.of(2026, 7, 27), rangeStart(CalendarViewMode.MONTH, august))
        assertEquals(6, monthWeeks(august))
        assertEquals(42, rangeDays(CalendarViewMode.MONTH, august).size)

        val february = LocalDate.of(2026, 2, 10)
        assertEquals(LocalDate.of(2026, 1, 26), rangeStart(CalendarViewMode.MONTH, february))
        assertEquals(5, monthWeeks(february))

        assertEquals(LocalDate.of(2026, 8, 17), rangeStart(CalendarViewMode.WEEK, august))
        assertEquals(LocalDate.of(2026, 8, 24), rangeEnd(CalendarViewMode.WEEK, august))
        assertEquals(august, rangeStart(CalendarViewMode.DAY, august))
        assertEquals(august.plusDays(1), rangeEnd(CalendarViewMode.DAY, august))
    }

    @Test
    fun `stepping a month lands on the first, never on a day the month lacks`() {
        assertEquals(
            LocalDate.of(2026, 2, 1),
            shiftAnchor(CalendarViewMode.MONTH, LocalDate.of(2026, 1, 31), 1),
        )
    }

    // --- layout ---------------------------------------------------------------------------

    @Test
    fun `overlapping items take separate lanes and sequential ones share one`() {
        val day = LocalDate.of(2026, 8, 20)
        val layout = layoutDay(
            listOf(
                item("2026-08-20T09:00:00Z", "2026-08-20T09:30:00Z", "a"),
                item("2026-08-20T09:15:00Z", "2026-08-20T10:00:00Z", "b"),
                item("2026-08-20T13:00:00Z", "2026-08-20T14:00:00Z", "c"),
            ),
            day,
            ZoneOffset.UTC,
        )

        assertEquals(2, layout.lanes)
        assertEquals(listOf(0, 1, 0), layout.boxes.map { it.lane })
        assertEquals(9 * 60, layout.boxes[0].topMinutes)
        assertEquals(30, layout.boxes[0].heightMinutes)
    }

    @Test
    fun `a box is clamped to the day and never too small to tap`() {
        val day = LocalDate.of(2026, 8, 20)
        val spanning = item("2026-08-19T22:00:00Z", "2026-08-21T04:00:00Z")
        val instant = item("2026-08-20T09:00:00Z", "2026-08-20T09:00:00Z")

        val spanningBox = layoutDay(listOf(spanning), day, ZoneOffset.UTC).boxes.single()
        assertEquals(0, spanningBox.topMinutes)
        assertEquals(24 * 60, spanningBox.heightMinutes)

        assertEquals(MIN_BOX_MINUTES, layoutDay(listOf(instant), day, ZoneOffset.UTC).boxes.single().heightMinutes)
    }
}
