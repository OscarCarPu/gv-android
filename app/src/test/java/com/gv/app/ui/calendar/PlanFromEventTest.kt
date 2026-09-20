package com.gv.app.ui.calendar

import com.gv.app.domain.model.CalendarEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** Turning an event into a plan block: what is asked for, and when the event itself may be touched. */
class PlanFromEventTest {

    private val zone = ZoneId.of("Europe/Madrid")

    private fun event(
        summary: String = "Dentist",
        startsAt: String = "2026-09-21T08:00:00Z", // 10:00 in Madrid
        endsAt: String = "2026-09-21T09:00:00Z",
        allDay: Boolean = false,
        startDate: String? = null,
    ) = CalendarEvent(
        instance_id = "12@$startsAt", event_id = 12, calendar_id = 1, account_id = 1,
        account_email = "me@example.com", calendar_name = "Personal", color = "#3366cc",
        summary = summary, all_day = allDay, starts_at = startsAt, ends_at = endsAt,
        start_date = startDate, end_date = null, recurrence = null,
    )

    private fun at(h: Int, m: Int = 0) = LocalDateTime.of(2026, 9, 21, h, m)

    private fun check(
        e: CalendarEvent = event(),
        mode: PlanTaskMode = PlanTaskMode.NONE,
        taskId: Int? = null,
        name: String = "",
        start: LocalDateTime? = at(10),
        end: LocalDateTime? = at(11),
    ) = checkPlanFromEvent(e, mode, taskId, name, start, end, zone)

    // --- defaults -------------------------------------------------------------------------

    @Test
    fun `a timed event opens on its own times, in the phone's zone`() {
        val (s, e) = defaultPlanTimes(event(), zone)
        assertEquals(at(10), s)
        assertEquals(at(11), e)
    }

    @Test
    fun `an all-day event opens on working hours of its first day`() {
        val (s, e) = defaultPlanTimes(event(allDay = true, startDate = "2026-09-23", startsAt = "2026-09-23T00:00:00Z", endsAt = "2026-09-24T00:00:00Z"), zone)
        assertEquals(LocalDateTime.of(2026, 9, 23, 9, 0), s)
        assertEquals(LocalDateTime.of(2026, 9, 23, 10, 0), e)
    }

    // --- validation -----------------------------------------------------------------------

    @Test
    fun `the end must be after the start, and both are required`() {
        val bad = PlanFromEventCheck.Invalid(timeError = true, taskError = false)
        assertEquals(bad, check(start = at(11), end = at(10)))
        assertEquals(bad, check(start = at(10), end = at(10)))
        assertEquals(bad, check(start = null))
        assertEquals(bad, check(end = null))
    }

    @Test
    fun `a chosen task must actually be chosen`() {
        val bad = PlanFromEventCheck.Invalid(timeError = false, taskError = true)
        assertEquals(bad, check(mode = PlanTaskMode.EXISTING, taskId = null))
        assertEquals(bad, check(mode = PlanTaskMode.NEW, name = "   "))
        assertTrue(check(mode = PlanTaskMode.EXISTING, taskId = 4) is PlanFromEventCheck.Ok)
    }

    // --- what is sent ---------------------------------------------------------------------

    @Test
    fun `with no task the block is labelled with the event's title`() {
        assertEquals("Dentist", (check() as PlanFromEventCheck.Ok).label)
        assertEquals("Event", (check(e = event(summary = "")) as PlanFromEventCheck.Ok).label)
    }

    @Test
    fun `with a task the block takes the task's name, not a label`() {
        assertNull((check(mode = PlanTaskMode.EXISTING, taskId = 4) as PlanFromEventCheck.Ok).label)
        val new = check(mode = PlanTaskMode.NEW, name = "  Book it ") as PlanFromEventCheck.Ok
        assertNull(new.label)
        assertEquals("Book it", new.newTaskName)
        assertNull(new.taskId)
    }

    @Test
    fun `times go out as instants in the phone's zone`() {
        val ok = check(start = at(10), end = at(11, 30)) as PlanFromEventCheck.Ok
        assertEquals("2026-09-21T08:00:00Z", ok.startsAt)
        assertEquals("2026-09-21T09:30:00Z", ok.endsAt)
    }

    // --- when the event itself moves --------------------------------------------------------

    @Test
    fun `an unchanged event is not rewritten`() {
        assertFalse((check() as PlanFromEventCheck.Ok).moveEvent)
    }

    @Test
    fun `the same instant in another offset is still unchanged`() {
        // The API writes a calendar's own offset; the picker yields Z. Same moment, no write.
        val e = event(startsAt = "2026-09-21T10:00:00+02:00", endsAt = "2026-09-21T11:00:00+02:00")
        assertFalse((check(e = e) as PlanFromEventCheck.Ok).moveEvent)
        assertFalse(eventTimesChanged(e, "2026-09-21T08:00:00Z", "2026-09-21T09:00:00Z"))
    }

    @Test
    fun `different times reschedule the event`() {
        assertTrue((check(start = at(10), end = at(12)) as PlanFromEventCheck.Ok).moveEvent)
    }

    @Test
    fun `an all-day event is never rewritten into a timed one`() {
        val allDay = event(allDay = true, startDate = "2026-09-21", startsAt = "2026-09-21T00:00:00Z", endsAt = "2026-09-22T00:00:00Z")
        assertFalse((check(e = allDay, start = at(9), end = at(10)) as PlanFromEventCheck.Ok).moveEvent)
    }
}
