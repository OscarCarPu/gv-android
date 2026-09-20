package com.gv.app.ui.habits

import com.gv.app.domain.model.HabitWithLog

/**
 * Habit card maths, ported from gv-web's `habitCard.svelte.ts` so a card reads the same on both.
 *
 * `optimistic` is a value the user has just entered and the server has not confirmed yet. The
 * card shows it straight away and recomputes progress from it, so a tap is felt at once; the
 * server-computed `period_value` is adjusted by the *difference* rather than replaced, because
 * only the server knows what the rest of the period holds.
 */
fun displayValue(habit: HabitWithLog, optimistic: Double?): Double = optimistic ?: habit.log_value ?: 0.0

fun optimisticPeriodValue(habit: HabitWithLog, optimistic: Double?): Double =
    if (optimistic != null) habit.period_value + (optimistic - (habit.log_value ?: 0.0)) else habit.period_value

fun hasTarget(habit: HabitWithLog): Boolean = habit.target_min != null || habit.target_max != null

/**
 * How full the bar is, 0..1. With both targets it is the position *within* [min, max]; with one
 * it is the value against that one. A zero target cannot be divided by, so it reads as full once
 * reached and empty before.
 */
fun progressFraction(min: Double?, max: Double?, period: Double): Float {
    val fraction = when {
        min != null && max != null ->
            if (max == min) (if (period >= min) 1.0 else 0.0) else (period - min) / (max - min)
        min != null -> if (min <= 0.0) (if (period >= min) 1.0 else 0.0) else period / min
        max != null -> if (max <= 0.0) (if (period >= max) 1.0 else 0.0) else period / max
        else -> 0.0
    }
    return fraction.coerceIn(0.0, 1.0).toFloat()
}

/** Inside the target: at least the minimum and at most the maximum, whichever are set. */
fun targetMet(min: Double?, max: Double?, period: Double): Boolean = when {
    min != null && max != null -> period >= min && period <= max
    min != null -> period >= min
    max != null -> period <= max
    else -> false
}

fun exceeded(max: Double?, period: Double): Boolean = max != null && period > max

/** `5 (3-8)` for a range, `5/3` for a single target — the web's wording. */
fun progressText(min: Double?, max: Double?, period: Double): String = when {
    min != null && max != null -> "${formatHabitValue(period)} (${formatHabitValue(min)}-${formatHabitValue(max)})"
    min != null -> "${formatHabitValue(period)}/${formatHabitValue(min)}"
    max != null -> "${formatHabitValue(period)}/${formatHabitValue(max)}"
    else -> ""
}

fun formatHabitValue(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

// ---------- The create / edit form ----------

const val HABIT_NAME_MAX = 40
val HabitFrequencies = listOf("daily", "weekly", "monthly")

/** The outcome of checking the form: a request-shaped value, or which fields to flag. */
sealed interface HabitFormCheck {
    data class Ok(
        val name: String,
        val description: String?,
        val frequency: String,
        val targetMin: Double?,
        val targetMax: Double?,
        val recordingRequired: Boolean,
    ) : HabitFormCheck

    data class Invalid(val nameError: Boolean, val targetError: Boolean) : HabitFormCheck
}

/** A target as typed: blank is "no target", a comma is a decimal point, anything else is invalid. */
private sealed interface Target {
    data object None : Target
    data object Bad : Target
    data class Value(val v: Double) : Target
}

private fun parseTarget(text: String): Target {
    val t = text.trim().replace(',', '.')
    if (t.isEmpty()) return Target.None
    val v = t.toDoubleOrNull() ?: return Target.Bad
    return if (v.isNaN() || v.isInfinite()) Target.Bad else Target.Value(v)
}

/**
 * The web's `HabitForm.validate`, plus the parsing the web gets for free from
 * `<input type=number>`: a name is required, a target may not be negative, and the minimum may
 * not exceed the maximum.
 */
fun checkHabitForm(
    name: String,
    description: String,
    frequency: String,
    minText: String,
    maxText: String,
    recordingRequired: Boolean,
): HabitFormCheck {
    val nameError = name.isBlank() || name.trim().length > HABIT_NAME_MAX
    val min = parseTarget(minText)
    val max = parseTarget(maxText)
    val minV = (min as? Target.Value)?.v
    val maxV = (max as? Target.Value)?.v
    val targetError = min is Target.Bad || max is Target.Bad ||
        (minV != null && minV < 0) || (maxV != null && maxV < 0) ||
        (minV != null && maxV != null && minV > maxV)
    if (nameError || targetError) return HabitFormCheck.Invalid(nameError, targetError)
    return HabitFormCheck.Ok(
        name = name.trim(),
        description = description.trim().ifEmpty { null },
        frequency = if (frequency in HabitFrequencies) frequency else "daily",
        targetMin = minV,
        targetMax = maxV,
        recordingRequired = recordingRequired,
    )
}
