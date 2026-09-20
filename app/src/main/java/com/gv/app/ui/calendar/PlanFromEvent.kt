package com.gv.app.ui.calendar

import com.gv.app.data.api.parseInstantOrNull
import com.gv.app.domain.model.CalendarEvent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * "Create plan" on an event, ported from gv-web's `CreatePlanFromEventWizard`: turn an event into
 * a plan block linked to it (`event_ref`), optionally on a task — an existing one, or one made on
 * the spot — and optionally with different times than the event has.
 */
enum class PlanTaskMode { NONE, EXISTING, NEW }

sealed interface PlanFromEventCheck {
    data class Ok(
        val startsAt: String,
        val endsAt: String,
        /** The task to attach, when an existing one was picked. */
        val taskId: Int?,
        /** A task to create first, when "new" was chosen. */
        val newTaskName: String?,
        /** Only for a block with no task: the plan row shows the event's own title. */
        val label: String?,
        /** Whether the *event* must be rescheduled to match — see [checkPlanFromEvent]. */
        val moveEvent: Boolean,
    ) : PlanFromEventCheck

    data class Invalid(val timeError: Boolean, val taskError: Boolean) : PlanFromEventCheck
}

/**
 * The times the sheet opens on: a timed event's own, and for an all-day event — which has none —
 * a working-hours default on its first day, where the web leaves the fields empty and makes you
 * type them.
 */
fun defaultPlanTimes(event: CalendarEvent, zone: ZoneId = ZoneId.systemDefault()): Pair<LocalDateTime, LocalDateTime> {
    if (!event.all_day) {
        val start = apiInstantToLocal(event.starts_at, zone)
        val end = apiInstantToLocal(event.ends_at, zone)
        if (start != null && end != null) return start to end
    }
    val day = event.start_date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now(zone)
    return day.atTime(LocalTime.of(9, 0)) to day.atTime(LocalTime.of(10, 0))
}

/**
 * Whether the plan's times differ from the event's, compared as **instants**: the API writes a
 * calendar's own offset (`+02:00`) where the picker yields `Z`, and comparing the strings would
 * call an unchanged event changed and send Google a needless write.
 */
fun eventTimesChanged(event: CalendarEvent, startsAt: String, endsAt: String): Boolean =
    parseInstantOrNull(event.starts_at) != parseInstantOrNull(startsAt) ||
        parseInstantOrNull(event.ends_at) != parseInstantOrNull(endsAt)

fun checkPlanFromEvent(
    event: CalendarEvent,
    mode: PlanTaskMode,
    selectedTaskId: Int?,
    newTaskName: String,
    start: LocalDateTime?,
    end: LocalDateTime?,
    zone: ZoneId = ZoneId.systemDefault(),
): PlanFromEventCheck {
    val timeError = start == null || end == null || !end.isAfter(start)
    val taskError = when (mode) {
        PlanTaskMode.NONE -> false
        PlanTaskMode.EXISTING -> selectedTaskId == null
        PlanTaskMode.NEW -> newTaskName.isBlank()
    }
    if (timeError || taskError) return PlanFromEventCheck.Invalid(timeError, taskError)

    val startsAt = localToApiInstant(start!!, zone)
    val endsAt = localToApiInstant(end!!, zone)
    val hasTask = mode != PlanTaskMode.NONE
    return PlanFromEventCheck.Ok(
        startsAt = startsAt,
        endsAt = endsAt,
        taskId = if (mode == PlanTaskMode.EXISTING) selectedTaskId else null,
        newTaskName = if (mode == PlanTaskMode.NEW) newTaskName.trim() else null,
        label = if (hasTask) null else event.summary.ifBlank { "Event" },
        // An all-day event is a date, not an instant: giving its plan block a time must not
        // rewrite the event into a timed one.
        moveEvent = !event.all_day && eventTimesChanged(event, startsAt, endsAt),
    )
}
