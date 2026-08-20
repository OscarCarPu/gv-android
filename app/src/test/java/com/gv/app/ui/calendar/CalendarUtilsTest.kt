package com.gv.app.ui.calendar

import com.gv.app.domain.model.CalendarEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The calendar's silent-failure surface.
 *
 * Every case here is something that renders plausibly while being wrong: an appointment on the
 * wrong day, an event an hour out, a recurrence rule quietly rewritten. None of it is visible
 * from a screenshot, which is why it is tested rather than eyeballed.
 */
class CalendarUtilsTest {

    private fun event(
        summary: String = "Thing",
        startsAt: String,
        endsAt: String,
        allDay: Boolean = false,
        startDate: String? = null,
        endDate: String? = null,
        recurrence: List<String>? = null,
    ) = CalendarEvent(
        instance_id = "1",
        event_id = 1,
        calendar_id = 1,
        account_id = 1,
        account_email = "me@example.com",
        calendar_name = "Personal",
        color = "#3366cc",
        summary = summary,
        all_day = allDay,
        starts_at = startsAt,
        ends_at = endsAt,
        start_date = startDate,
        end_date = endDate,
        recurrence = recurrence,
    )

    // --- placement ------------------------------------------------------------------------

    @Test
    fun `an all-day event is placed by its dates, not by its instants`() {
        // A calendar that reports its zone as UTC, read on a phone twelve hours ahead. The
        // instants say the event covers two local days; the dates say it covers one, and the
        // dates are what Google holds and what a person sees there.
        val ahead = ZoneOffset.ofHours(12)
        val withDates = event(
            startsAt = "2026-08-20T00:00:00Z",
            endsAt = "2026-08-21T00:00:00Z",
            allDay = true,
            startDate = "2026-08-20",
            endDate = "2026-08-21",
        )

        assertTrue(occupiesDay(withDates, LocalDate.of(2026, 8, 20), ahead))
        assertFalse(occupiesDay(withDates, LocalDate.of(2026, 8, 21), ahead))

        // Without the dates there is nothing to place it by but the instants, and that is
        // exactly where the duplicate comes from — the reason the dates are preferred.
        val withoutDates = withDates.copy(start_date = null, end_date = null)
        assertTrue(occupiesDay(withoutDates, LocalDate.of(2026, 8, 21), ahead))
    }

    @Test
    fun `a multi-day timed event shows on every day it covers, end exclusive`() {
        val utc = ZoneOffset.UTC
        val trip = event(startsAt = "2026-03-02T08:00:00Z", endsAt = "2026-03-04T00:00:00Z")

        assertTrue(occupiesDay(trip, LocalDate.of(2026, 3, 2), utc))
        assertTrue(occupiesDay(trip, LocalDate.of(2026, 3, 3), utc))
        // The end is exclusive: an event finishing at midnight does not reach into that day.
        assertFalse(occupiesDay(trip, LocalDate.of(2026, 3, 4), utc))
        assertFalse(occupiesDay(trip, LocalDate.of(2026, 3, 1), utc))
    }

    @Test
    fun `a zero-length event is placed by where it starts`() {
        val utc = ZoneOffset.UTC
        val point = event(startsAt = "2026-03-02T08:00:00Z", endsAt = "2026-03-02T08:00:00Z")

        assertTrue(occupiesDay(point, LocalDate.of(2026, 3, 2), utc))
        assertFalse(occupiesDay(point, LocalDate.of(2026, 3, 3), utc))
    }

    @Test
    fun `an event is read at the offset the API sent, not only at Z`() {
        // gv-api formats instants in the calendar's own zone, so an offset is normal. Failing to
        // parse it would drop the event from the day entirely.
        val madrid = ZoneId.of("Europe/Madrid")
        val local = event(startsAt = "2026-08-20T09:00:00+02:00", endsAt = "2026-08-20T10:00:00+02:00")

        assertTrue(occupiesDay(local, LocalDate.of(2026, 8, 20), madrid))
        assertEquals("09:00", eventTimeLabel(local.starts_at, madrid))
    }

    // --- time conversion ------------------------------------------------------------------

    @Test
    fun `a picked wall clock becomes the instant it names, not the same clock face in UTC`() {
        // 17:00 in Madrid is 15:00Z. Stamping the wall clock as UTC — which is right for a task's
        // due date — would move every appointment by the zone offset.
        val madrid = ZoneId.of("Europe/Madrid")
        val picked = LocalDateTime.of(2026, 8, 20, 17, 0)

        assertEquals("2026-08-20T15:00:00Z", localToApiInstant(picked, madrid))
        assertEquals(picked, apiInstantToLocal("2026-08-20T15:00:00Z", madrid))
    }

    @Test
    fun `an all-day end is exclusive, so the last day covered is the day before it`() {
        // A one-day event is starts_at = D, ends_at = D+1.
        assertEquals("2026-08-21", addDaysToDateKey("2026-08-20", 1))
        assertEquals("2026-08-20", addDaysToDateKey("2026-08-21", -1))

        val oneDay = event(
            startsAt = "2026-08-20T00:00:00Z",
            endsAt = "2026-08-21T00:00:00Z",
            allDay = true,
            startDate = "2026-08-20",
            endDate = "2026-08-21",
        )
        assertEquals("All day", eventWhenLabel(oneDay, ZoneOffset.UTC))

        val threeDays = oneDay.copy(end_date = "2026-08-23")
        assertEquals("All day, 20 Aug – 22 Aug", eventWhenLabel(threeDays, ZoneOffset.UTC))
    }

    // --- recurrence -----------------------------------------------------------------------

    @Test
    fun `a rule that bounds itself is never mapped onto a preset`() {
        // Showing any of these as "Weekly" would drop the clause that makes it specific the next
        // time the event is saved.
        assertEquals(RecurrencePreset.CUSTOM, ruleToPreset(listOf("RRULE:FREQ=DAILY;COUNT=5")))
        assertEquals(RecurrencePreset.CUSTOM, ruleToPreset(listOf("RRULE:FREQ=WEEKLY;UNTIL=20261231T000000Z")))
        assertEquals(RecurrencePreset.CUSTOM, ruleToPreset(listOf("RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=TH")))
        // No RRULE line at all (EXDATE only, say) is also not a preset.
        assertEquals(RecurrencePreset.CUSTOM, ruleToPreset(listOf("EXDATE;TZID=Europe/Madrid:20260820T090000")))
    }

    @Test
    fun `a plain frequency maps onto its preset both ways`() {
        assertEquals(RecurrencePreset.NONE, ruleToPreset(null))
        assertEquals(RecurrencePreset.NONE, ruleToPreset(emptyList()))
        assertEquals(RecurrencePreset.DAILY, ruleToPreset(listOf("RRULE:FREQ=DAILY")))
        assertEquals(RecurrencePreset.WEEKLY, ruleToPreset(listOf("RRULE:FREQ=WEEKLY;BYDAY=TH")))
        assertEquals(RecurrencePreset.MONTHLY, ruleToPreset(listOf("RRULE:FREQ=MONTHLY;BYMONTHDAY=20")))
        assertEquals(RecurrencePreset.YEARLY, ruleToPreset(listOf("RRULE:FREQ=YEARLY")))

        // A weekly rule repeats on the start's weekday, a monthly one on its day of the month.
        val thursday = LocalDate.of(2026, 8, 20)
        assertEquals(listOf("RRULE:FREQ=WEEKLY;BYDAY=TH"), presetToRule(RecurrencePreset.WEEKLY, thursday))
        assertEquals(listOf("RRULE:FREQ=MONTHLY;BYMONTHDAY=20"), presetToRule(RecurrencePreset.MONTHLY, thursday))
        assertEquals(emptyList<String>(), presetToRule(RecurrencePreset.NONE, thursday))
        // "Custom" describes a rule; it never generates one, so the original survives untouched.
        assertEquals(emptyList<String>(), presetToRule(RecurrencePreset.CUSTOM, thursday))
    }

    // --- ranges ---------------------------------------------------------------------------

    @Test
    fun `a month range covers whole Monday-first weeks`() {
        // August 2026 starts on a Saturday, so the grid opens on Monday 27 July.
        val august = LocalDate.of(2026, 8, 20)
        assertEquals(LocalDate.of(2026, 7, 27), rangeStart(CalendarViewMode.MONTH, august))
        assertEquals(6, monthWeeks(august))
        assertEquals(42, rangeDays(CalendarViewMode.MONTH, august).size)

        // February 2026 is exactly four weeks from a Monday, so five rows cover it and the grid
        // does not pad to six for no reason.
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
        val endOfJanuary = LocalDate.of(2026, 1, 31)
        assertEquals(
            LocalDate.of(2026, 2, 1),
            shiftAnchor(CalendarViewMode.MONTH, endOfJanuary, 1),
        )
    }

    // --- layout ---------------------------------------------------------------------------

    @Test
    fun `overlapping events take separate lanes and sequential ones share one`() {
        val utc = ZoneOffset.UTC
        val day = LocalDate.of(2026, 8, 20)
        val overlapping = listOf(
            event(summary = "standup", startsAt = "2026-08-20T09:00:00Z", endsAt = "2026-08-20T09:30:00Z").copy(instance_id = "a"),
            event(summary = "review", startsAt = "2026-08-20T09:15:00Z", endsAt = "2026-08-20T10:00:00Z").copy(instance_id = "b"),
            event(summary = "lunch", startsAt = "2026-08-20T13:00:00Z", endsAt = "2026-08-20T14:00:00Z").copy(instance_id = "c"),
        )

        val layout = layoutDay(overlapping, day, utc)

        assertEquals(2, layout.lanes)
        assertEquals(listOf(0, 1, 0), layout.boxes.map { it.lane })
        assertEquals(9 * 60, layout.boxes[0].topMinutes)
        assertEquals(30, layout.boxes[0].heightMinutes)
    }

    @Test
    fun `a box is clamped to the day and never too small to tap`() {
        val utc = ZoneOffset.UTC
        val day = LocalDate.of(2026, 8, 20)
        val spanning = event(startsAt = "2026-08-19T22:00:00Z", endsAt = "2026-08-21T04:00:00Z")
        val instant = event(startsAt = "2026-08-20T09:00:00Z", endsAt = "2026-08-20T09:00:00Z")

        val spanningBox = layoutDay(listOf(spanning), day, utc).boxes.single()
        assertEquals(0, spanningBox.topMinutes)
        assertEquals(24 * 60, spanningBox.heightMinutes)

        val instantBox = layoutDay(listOf(instant), day, utc).boxes.single()
        assertEquals(MIN_BOX_MINUTES, instantBox.heightMinutes)
    }

    // --- colour ---------------------------------------------------------------------------

    @Test
    fun `ink is chosen per colour, so a pale calendar keeps a readable title`() {
        // Google hands out pale colours (every primary calendar is the same #9fe1e7), and
        // hardcoding white text makes those titles disappear.
        assertTrue(prefersDarkInk("#9fe1e7"))
        assertTrue(prefersDarkInk("#ffffff"))
        assertFalse(prefersDarkInk("#3366cc"))
        assertFalse(prefersDarkInk("#000000"))
        // Anything that is not a hex triplet falls back to white rather than throwing.
        assertFalse(prefersDarkInk(null))
        assertFalse(prefersDarkInk("transparent"))
    }
}
