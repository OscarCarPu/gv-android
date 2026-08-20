package com.gv.app.ui.calendar

import androidx.compose.ui.graphics.Color
import com.gv.app.data.api.parseInstantOrNull
import com.gv.app.domain.model.CalendarEvent
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.pow

/**
 * Pure calendar logic: which range a view covers, which day an event belongs on, how overlapping
 * appointments are laid out, and how a recurrence preset maps onto a rule.
 *
 * Kept out of the composables because this is where a calendar actually goes wrong — an all-day
 * event drawn on the wrong day, or a rule silently rewritten — and because it is the part worth
 * testing without an emulator.
 */

enum class CalendarViewMode(val label: String) {
    MONTH("Month"),
    WEEK("Week"),
    DAY("Day"),
}

/** Monday first: the week as it reads on a Spanish wall calendar, matching gv-web. */
val CalendarWeekStart: DayOfWeek = DayOfWeek.MONDAY

// --- range ---------------------------------------------------------------------------------

fun startOfWeek(date: LocalDate): LocalDate {
    val shift = (date.dayOfWeek.value - CalendarWeekStart.value + 7) % 7
    return date.minusDays(shift.toLong())
}

fun rangeStart(mode: CalendarViewMode, anchor: LocalDate): LocalDate = when (mode) {
    CalendarViewMode.DAY -> anchor
    CalendarViewMode.WEEK -> startOfWeek(anchor)
    // A month view shows whole weeks, so it starts on the Monday on or before the 1st.
    CalendarViewMode.MONTH -> startOfWeek(anchor.withDayOfMonth(1))
}

/**
 * Six weeks unless five cover the month, so the grid does not change height from month to month
 * for no reason the reader can see.
 */
fun monthWeeks(anchor: LocalDate): Int {
    val start = rangeStart(CalendarViewMode.MONTH, anchor)
    val firstOfNext = anchor.withDayOfMonth(1).plusMonths(1)
    val days = ChronoUnit.DAYS.between(start, firstOfNext)
    return ((days + 6) / 7).toInt()
}

/** Exclusive, like the API's `to`. */
fun rangeEnd(mode: CalendarViewMode, anchor: LocalDate): LocalDate = when (mode) {
    CalendarViewMode.DAY -> anchor.plusDays(1)
    CalendarViewMode.WEEK -> rangeStart(mode, anchor).plusDays(7)
    CalendarViewMode.MONTH -> rangeStart(mode, anchor).plusDays(monthWeeks(anchor) * 7L)
}

fun rangeDays(mode: CalendarViewMode, anchor: LocalDate): List<LocalDate> {
    val from = rangeStart(mode, anchor)
    val total = ChronoUnit.DAYS.between(from, rangeEnd(mode, anchor)).toInt()
    return (0 until total).map { from.plusDays(it.toLong()) }
}

/** Step one period in [direction] (+1 / −1). A month step lands on the 1st, not on the 32nd. */
fun shiftAnchor(mode: CalendarViewMode, anchor: LocalDate, direction: Int): LocalDate =
    when (mode) {
        CalendarViewMode.MONTH -> anchor.withDayOfMonth(1).plusMonths(direction.toLong())
        CalendarViewMode.WEEK -> anchor.plusWeeks(direction.toLong())
        CalendarViewMode.DAY -> anchor.plusDays(direction.toLong())
    }

private val MonthTitle = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.UK)
private val DayTitle = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.UK)
private val ShortDate = DateTimeFormatter.ofPattern("d MMM", Locale.UK)
private val HourMinute = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)

fun rangeTitle(mode: CalendarViewMode, anchor: LocalDate): String = when (mode) {
    CalendarViewMode.DAY -> anchor.format(DayTitle)
    CalendarViewMode.MONTH -> anchor.format(MonthTitle)
    CalendarViewMode.WEEK -> {
        val from = rangeStart(mode, anchor)
        val to = from.plusDays(6)
        "${from.format(ShortDate)} – ${to.format(ShortDate)} ${to.year}"
    }
}

// --- placement -----------------------------------------------------------------------------

/**
 * Whether an event shows on a given day. It does when it overlaps that day at all, so a
 * multi-day trip appears on every day it covers; the end is exclusive, matching the API.
 *
 * **An all-day event is compared as dates, never as instants.** Its instants are midnight in the
 * *calendar's* zone, and calendars disagree about which that is — Google reports some as UTC and
 * some as Europe/Madrid — so converting them into the phone's zone spreads a one-day event over
 * two local days, which reads as a duplicate. The dates the API sends are the ones Google holds
 * and the ones a person sees there.
 */
fun occupiesDay(event: CalendarEvent, day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Boolean {
    val startDate = event.start_date
    val endDate = event.end_date
    if (event.all_day && startDate != null && endDate != null) {
        val key = day.toString()
        return key >= startDate && key < endDate
    }
    val dayStart = day.atStartOfDay(zone).toInstant()
    val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant()
    val start = parseInstantOrNull(event.starts_at) ?: return false
    val end = parseInstantOrNull(event.ends_at) ?: start
    // A zero-length event covers no interval, so it is placed by where it starts.
    if (end == start) return !start.isBefore(dayStart) && start.isBefore(dayEnd)
    return start.isBefore(dayEnd) && end.isAfter(dayStart)
}

fun allDayOn(events: List<CalendarEvent>, day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<CalendarEvent> =
    events.filter { it.all_day && occupiesDay(it, day, zone) }

fun timedOn(events: List<CalendarEvent>, day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<CalendarEvent> =
    events.filter { !it.all_day && occupiesDay(it, day, zone) }
        .sortedBy { parseInstantOrNull(it.starts_at) ?: java.time.Instant.EPOCH }

/** One event's box in a day column: minutes from midnight, and how many minutes tall. */
data class EventBox(
    val event: CalendarEvent,
    val topMinutes: Int,
    val heightMinutes: Int,
    val lane: Int,
)

data class DayLayout(val boxes: List<EventBox>, val lanes: Int)

/**
 * Lays a day's timed events out in lanes.
 *
 * Events are walked in start order and each takes the first lane whose previous event has already
 * finished, which is enough to stop two appointments at the same hour hiding one another without
 * the full interval-graph colouring a desktop calendar does.
 *
 * A box is clamped to the day, so a multi-day event is drawn to the column's edges rather than
 * off them, and is never shorter than [MIN_BOX_MINUTES] — a zero-length event still has to be
 * tappable.
 */
fun layoutDay(
    events: List<CalendarEvent>,
    day: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
): DayLayout {
    val dayStart = day.atStartOfDay(zone).toInstant()
    val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant()
    val laneEnds = mutableListOf<Long>()
    val boxes = timedOn(events, day, zone).map { event ->
        val rawStart = parseInstantOrNull(event.starts_at) ?: dayStart
        val rawEnd = parseInstantOrNull(event.ends_at) ?: rawStart
        val start = maxOf(rawStart, dayStart)
        val end = minOf(maxOf(rawEnd, start), dayEnd)
        val top = ChronoUnit.MINUTES.between(dayStart, start).toInt()
        val height = ChronoUnit.MINUTES.between(start, end).toInt().coerceAtLeast(MIN_BOX_MINUTES)
        val startMs = start.toEpochMilli()
        val endMs = end.toEpochMilli()
        var lane = laneEnds.indexOfFirst { it <= startMs }
        if (lane == -1) {
            lane = laneEnds.size
            laneEnds.add(endMs)
        } else {
            laneEnds[lane] = endMs
        }
        EventBox(event = event, topMinutes = top, heightMinutes = height, lane = lane)
    }
    return DayLayout(boxes = boxes, lanes = laneEnds.size.coerceAtLeast(1))
}

/** Enough to hold a tap target at the grid's hour height. */
const val MIN_BOX_MINUTES = 24

// --- labels --------------------------------------------------------------------------------

/** `HH:mm` in the phone's zone. */
fun eventTimeLabel(iso: String?, zone: ZoneId = ZoneId.systemDefault()): String {
    val instant = parseInstantOrNull(iso) ?: return ""
    return instant.atZone(zone).format(HourMinute)
}

/**
 * What an event's "when" reads as: a time range, or the day span for an all-day one.
 *
 * The all-day case reads the dates rather than the instants, for the same reason placement does.
 * The API's end is exclusive, so the last day named is the one before it — the convention
 * Google's own UI shows.
 */
fun eventWhenLabel(event: CalendarEvent, zone: ZoneId = ZoneId.systemDefault()): String {
    if (!event.all_day) {
        return "${eventTimeLabel(event.starts_at, zone)} – ${eventTimeLabel(event.ends_at, zone)}"
    }
    val first = event.start_date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: parseInstantOrNull(event.starts_at)?.atZone(zone)?.toLocalDate()
        ?: return "All day"
    val lastExclusive = event.end_date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: parseInstantOrNull(event.ends_at)?.atZone(zone)?.toLocalDate()
    val last = (lastExclusive ?: first.plusDays(1)).minusDays(1)
    if (!last.isAfter(first)) return "All day"
    return "All day, ${first.format(ShortDate)} – ${last.format(ShortDate)}"
}

// --- all-day date arithmetic ---------------------------------------------------------------

/**
 * Shifts a `YYYY-MM-DD` key. Used for the one conversion an all-day form has to get right in
 * both directions: the API's end is exclusive and the form shows the last day covered, so a
 * one-day event is `starts_at = D`, `ends_at = D+1`.
 */
fun addDaysToDateKey(key: String, days: Long): String =
    runCatching { LocalDate.parse(key).plusDays(days).toString() }.getOrDefault(key)

private val IsoUtcSeconds: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT)

/**
 * A wall-clock time the user picked, as the instant it actually names.
 *
 * A calendar time is a moment, not a conceptual date: 17:00 in Madrid is 15:00Z. Sending the
 * wall clock as if it were UTC — which is right for a task's `due_at`, and is what
 * `TasksUtils.localDateTimeToIsoUtc` is for — would move every appointment by the zone offset.
 */
fun localToApiInstant(local: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): String =
    local.atZone(zone).toInstant().atOffset(ZoneOffset.UTC).format(IsoUtcSeconds)

/** The inverse: the API's instant as the wall clock it shows on this phone. */
fun apiInstantToLocal(iso: String?, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime? =
    parseInstantOrNull(iso)?.atZone(zone)?.toLocalDateTime()

// --- recurrence ----------------------------------------------------------------------------

enum class RecurrencePreset(val label: String) {
    NONE("Does not repeat"),
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    YEARLY("Yearly"),

    /**
     * A rule that is more than a plain frequency. Offering it as one of the presets above would
     * silently drop the part that makes it specific, so it is shown and sent back untouched.
     */
    CUSTOM("Custom rule (kept as it is)"),
}

private val RRuleWeekdays =
    mapOf(
        DayOfWeek.MONDAY to "MO",
        DayOfWeek.TUESDAY to "TU",
        DayOfWeek.WEDNESDAY to "WE",
        DayOfWeek.THURSDAY to "TH",
        DayOfWeek.FRIDAY to "FR",
        DayOfWeek.SATURDAY to "SA",
        DayOfWeek.SUNDAY to "SU",
    )

/** The rule a preset means for an event starting on [start]. Empty for "does not repeat". */
fun presetToRule(preset: RecurrencePreset, start: LocalDate): List<String> = when (preset) {
    RecurrencePreset.DAILY -> listOf("RRULE:FREQ=DAILY")
    RecurrencePreset.WEEKLY -> listOf("RRULE:FREQ=WEEKLY;BYDAY=${RRuleWeekdays[start.dayOfWeek]}")
    RecurrencePreset.MONTHLY -> listOf("RRULE:FREQ=MONTHLY;BYMONTHDAY=${start.dayOfMonth}")
    RecurrencePreset.YEARLY -> listOf("RRULE:FREQ=YEARLY")
    RecurrencePreset.NONE, RecurrencePreset.CUSTOM -> emptyList()
}

/**
 * Which preset an existing rule corresponds to.
 *
 * A rule that also bounds itself (`UNTIL`, `COUNT`) or repeats every N (`INTERVAL`) is not one of
 * the presets, and mapping it onto one would drop that clause the next time the event is saved.
 */
fun ruleToPreset(recurrence: List<String>?): RecurrencePreset {
    if (recurrence.isNullOrEmpty()) return RecurrencePreset.NONE
    val rule = recurrence.firstOrNull { it.uppercase(Locale.ROOT).startsWith("RRULE") }
        ?: return RecurrencePreset.CUSTOM
    val upper = rule.uppercase(Locale.ROOT)
    if (upper.contains("UNTIL=") || upper.contains("COUNT=") || upper.contains("INTERVAL=")) {
        return RecurrencePreset.CUSTOM
    }
    return when {
        upper.contains("FREQ=DAILY") -> RecurrencePreset.DAILY
        upper.contains("FREQ=WEEKLY") -> RecurrencePreset.WEEKLY
        upper.contains("FREQ=MONTHLY") -> RecurrencePreset.MONTHLY
        upper.contains("FREQ=YEARLY") -> RecurrencePreset.YEARLY
        else -> RecurrencePreset.CUSTOM
    }
}

// --- colour --------------------------------------------------------------------------------

/**
 * A calendar's colour, as the API resolved it. Null when it is not a hex triplet, so the caller
 * falls back to a theme colour rather than painting something transparent.
 */
fun parseHexColor(hex: String?): Color? {
    val cleaned = hex?.trim()?.removePrefix("#") ?: return null
    if (cleaned.length != 6) return null
    val value = cleaned.toLongOrNull(16) ?: return null
    return Color(0xFF000000 or value)
}

/**
 * Whether dark ink is the readable choice on a chip of this colour, by WCAG relative luminance.
 *
 * Calendar colours arrive from the API and can be anything — gv's palette, or one the user pinned
 * by hand. Hardcoding white text works until someone picks a pale colour and the title vanishes.
 */
fun prefersDarkInk(hex: String?): Boolean {
    val cleaned = hex?.trim()?.removePrefix("#") ?: return false
    if (cleaned.length != 6) return false
    fun channel(offset: Int): Double {
        val raw = cleaned.substring(offset, offset + 2).toIntOrNull(16) ?: return 0.0
        val v = raw / 255.0
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }
    val luminance = 0.2126 * channel(0) + 0.7152 * channel(2) + 0.0722 * channel(4)
    // The threshold is where white and near-black read about equally well; above it, dark wins.
    return luminance > 0.45
}

private val ChipInkDark = Color(0xFF111827)

/** The ink to write on a chip of [hex]. */
fun chipInk(hex: String?): Color = if (prefersDarkInk(hex)) ChipInkDark else Color.White
