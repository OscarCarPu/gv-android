package com.gv.app.ui.habits

import com.gv.app.domain.model.HabitWithLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The habit card's numbers and the form's checks — the same ones the web's controllers pin. */
class HabitLogicTest {

    private fun habit(log: Double? = null, period: Double = 0.0, min: Double? = null, max: Double? = null) = HabitWithLog(
        id = 1, name = "Read", description = null, frequency = "daily", target_min = min, target_max = max,
        recording_required = true, log_value = log, period_value = period, current_streak = 0, longest_streak = 0,
    )

    // --- optimistic values ----------------------------------------------------------------

    @Test
    fun `an optimistic value wins, then the logged one, then zero`() {
        assertEquals(7.0, displayValue(habit(log = 3.0), 7.0), 0.0)
        assertEquals(3.0, displayValue(habit(log = 3.0), null), 0.0)
        assertEquals(0.0, displayValue(habit(log = null), null), 0.0)
    }

    @Test
    fun `the period moves by the difference, not to the new value`() {
        // 10 this week, 3 of it logged today. Today becomes 5: the week becomes 12, not 5.
        assertEquals(12.0, optimisticPeriodValue(habit(log = 3.0, period = 10.0), 5.0), 0.0)
        assertEquals(10.0, optimisticPeriodValue(habit(log = 3.0, period = 10.0), null), 0.0)
        assertEquals(6.0, optimisticPeriodValue(habit(log = null, period = 5.0), 1.0), 0.0)
    }

    // --- progress -------------------------------------------------------------------------

    @Test
    fun `with a range, progress is the position within it`() {
        assertEquals(0f, progressFraction(4.0, 8.0, 4.0), 0f)
        assertEquals(0.5f, progressFraction(4.0, 8.0, 6.0), 0.001f)
        assertEquals(1f, progressFraction(4.0, 8.0, 8.0), 0f)
        assertEquals(1f, progressFraction(4.0, 8.0, 20.0), 0f)
        assertEquals(0f, progressFraction(4.0, 8.0, 1.0), 0f)
    }

    @Test
    fun `a range that is one point is all or nothing`() {
        assertEquals(1f, progressFraction(5.0, 5.0, 5.0), 0f)
        assertEquals(0f, progressFraction(5.0, 5.0, 4.0), 0f)
    }

    @Test
    fun `a single target is measured against itself`() {
        assertEquals(0.5f, progressFraction(10.0, null, 5.0), 0.001f)
        assertEquals(0.25f, progressFraction(null, 8.0, 2.0), 0.001f)
        assertEquals(1f, progressFraction(10.0, null, 30.0), 0f)
    }

    @Test
    fun `a zero target does not divide by zero`() {
        assertEquals(1f, progressFraction(0.0, null, 0.0), 0f)
        assertEquals(1f, progressFraction(null, 0.0, 0.0), 0f)
        assertEquals(0f, progressFraction(null, 0.0, -1.0), 0f)
    }

    @Test
    fun `no targets means an empty bar`() {
        assertEquals(0f, progressFraction(null, null, 5.0), 0f)
    }

    // --- target state ---------------------------------------------------------------------

    @Test
    fun `target met and exceeded follow whichever bounds are set`() {
        assertTrue(targetMet(3.0, 8.0, 5.0))
        assertFalse(targetMet(3.0, 8.0, 9.0))
        assertFalse(targetMet(3.0, 8.0, 2.0))
        assertTrue(targetMet(3.0, null, 100.0))
        assertTrue(targetMet(null, 8.0, 8.0))
        assertFalse(targetMet(null, 8.0, 9.0))
        assertFalse(targetMet(null, null, 5.0))

        assertTrue(exceeded(8.0, 9.0))
        assertFalse(exceeded(8.0, 8.0))
        assertFalse(exceeded(null, 100.0))
    }

    @Test
    fun `progress text uses the web's wording`() {
        assertEquals("5 (3-8)", progressText(3.0, 8.0, 5.0))
        assertEquals("5/3", progressText(3.0, null, 5.0))
        assertEquals("2.5/8", progressText(null, 8.0, 2.5))
        assertEquals("", progressText(null, null, 5.0))
    }

    // --- form -----------------------------------------------------------------------------

    private fun check(
        name: String = "Read",
        description: String = "",
        frequency: String = "daily",
        min: String = "",
        max: String = "",
        required: Boolean = true,
    ) = checkHabitForm(name, description, frequency, min, max, required)

    @Test
    fun `a valid form is trimmed and blank fields become null`() {
        val ok = check(name = "  Read  ", description = "  ", min = "", max = "") as HabitFormCheck.Ok
        assertEquals("Read", ok.name)
        assertEquals(null, ok.description)
        assertEquals(null, ok.targetMin)
        assertEquals(null, ok.targetMax)
    }

    @Test
    fun `a name is required and capped`() {
        assertEquals(HabitFormCheck.Invalid(nameError = true, targetError = false), check(name = "   "))
        assertEquals(HabitFormCheck.Invalid(nameError = true, targetError = false), check(name = "x".repeat(HABIT_NAME_MAX + 1)))
        assertTrue(check(name = "x".repeat(HABIT_NAME_MAX)) is HabitFormCheck.Ok)
    }

    @Test
    fun `a decimal comma is a decimal point`() {
        // The decimal keypad on a Spanish-locale phone types a comma.
        val ok = check(min = "1,5", max = "2,5") as HabitFormCheck.Ok
        assertEquals(1.5, ok.targetMin!!, 0.0)
        assertEquals(2.5, ok.targetMax!!, 0.0)
    }

    @Test
    fun `targets may not be negative, unreadable, or inverted`() {
        val bad = HabitFormCheck.Invalid(nameError = false, targetError = true)
        assertEquals(bad, check(min = "-1"))
        assertEquals(bad, check(max = "-0.5"))
        assertEquals(bad, check(min = "abc"))
        assertEquals(bad, check(min = "9", max = "3"))
        assertTrue(check(min = "3", max = "3") is HabitFormCheck.Ok)
        assertTrue(check(min = "0") is HabitFormCheck.Ok)
    }

    @Test
    fun `both problems are reported together`() {
        assertEquals(HabitFormCheck.Invalid(nameError = true, targetError = true), check(name = "", min = "x"))
    }

    @Test
    fun `an unknown frequency falls back to daily`() {
        assertEquals("weekly", (check(frequency = "weekly") as HabitFormCheck.Ok).frequency)
        assertEquals("daily", (check(frequency = "hourly") as HabitFormCheck.Ok).frequency)
    }
}
