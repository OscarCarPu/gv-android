package com.gv.app.ui.calendar

import com.gv.app.data.api.parseInstantOrNull
import com.gv.app.domain.model.CalendarEvent
import com.gv.app.domain.model.PlanBlockResponse
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Pure calendar logic: which range a view covers, which day an item belongs on, how overlapping
 * items are laid out, and which of events and plan blocks are drawn.
 *
 * Kept out of the composables because this is where a calendar actually goes wrong, and because
 * it is the part worth testing without an emulator.
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

// --- what is drawn -------------------------------------------------------------------------

/** Which sources the views draw. */
enum class CalendarSource(val label: String) {
    EVENTS("Events"),
    PLANS("Plans"),
    BOTH("Both"),
}

enum class ItemKind { EVENT, PLAN }

/** One thing on the grid: a calendar event or a plan block, with the times it occupies. */
data class AgendaItem(
    val key: String,
    val kind: ItemKind,
    val title: String,
    val startsAt: String,
    val endsAt: String,
    /** The event it opens: the item itself, or the event a plan block is linked to. */
    val event: CalendarEvent?,
    /** An event that has a plan block linked to it. */
    val planned: Boolean,
)

/**
 * What the views draw for [source]. An event with a plan block linked to it is one thing, not
 * two: with both sources on it appears once, as the plan block.
 */
fun buildItems(events: List<CalendarEvent>, blocks: List<PlanBlockResponse>, source: CalendarSource): List<AgendaItem> {
    val plannedRefs = blocks.mapNotNull { it.event_ref }.toSet()
    val showEvents = source != CalendarSource.PLANS
    val showPlans = source != CalendarSource.EVENTS
    val items = mutableListOf<AgendaItem>()

    for (event in events) {
        val planned = event.id.toString() in plannedRefs
        if (!showEvents || (showPlans && planned)) continue
        items += AgendaItem(
            key = "event-${event.id}",
            kind = ItemKind.EVENT,
            title = event.name,
            startsAt = event.start_time,
            endsAt = event.end_time,
            event = event,
            planned = planned,
        )
    }
    if (showPlans) {
        val byId = events.associateBy { it.id.toString() }
        for (block in blocks) {
            items += AgendaItem(
                key = "plan-${block.id}",
                kind = ItemKind.PLAN,
                title = block.task_name?.takeIf { it.isNotBlank() } ?: block.label,
                startsAt = block.started_at,
                endsAt = block.ended_at,
                event = block.event_ref?.let { byId[it] },
                planned = false,
            )
        }
    }
    return items
}

// --- placement -----------------------------------------------------------------------------

/**
 * Whether an item shows on a given day. It does when it overlaps that day at all, so a multi-day
 * item appears on every day it covers; the end is exclusive, matching the API.
 */
fun occupiesDay(item: AgendaItem, day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Boolean {
    val dayStart = day.atStartOfDay(zone).toInstant()
    val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant()
    val start = parseInstantOrNull(item.startsAt) ?: return false
    val end = parseInstantOrNull(item.endsAt) ?: start
    // A zero-length item covers no interval, so it is placed by where it starts.
    if (end == start) return !start.isBefore(dayStart) && start.isBefore(dayEnd)
    return start.isBefore(dayEnd) && end.isAfter(dayStart)
}

/** The day's items, earliest first. */
fun itemsOn(items: List<AgendaItem>, day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<AgendaItem> =
    items.filter { occupiesDay(it, day, zone) }
        .sortedBy { parseInstantOrNull(it.startsAt) ?: Instant.EPOCH }

/** One item's box in a day column: minutes from midnight, and how many minutes tall. */
data class ItemBox(
    val item: AgendaItem,
    val topMinutes: Int,
    val heightMinutes: Int,
    val lane: Int,
)

data class DayLayout(val boxes: List<ItemBox>, val lanes: Int)

/**
 * Lays a day's items out in lanes.
 *
 * Items are walked in start order and each takes the first lane whose previous item has already
 * finished, which is enough to stop two appointments at the same hour hiding one another.
 *
 * A box is clamped to the day, so a multi-day item is drawn to the column's edges rather than
 * off them, and is never shorter than [MIN_BOX_MINUTES] — a zero-length item still has to be
 * tappable.
 */
fun layoutDay(
    items: List<AgendaItem>,
    day: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
): DayLayout {
    val dayStart = day.atStartOfDay(zone).toInstant()
    val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant()
    val laneEnds = mutableListOf<Long>()
    val boxes = itemsOn(items, day, zone).map { item ->
        val rawStart = parseInstantOrNull(item.startsAt) ?: dayStart
        val rawEnd = parseInstantOrNull(item.endsAt) ?: rawStart
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
        ItemBox(item = item, topMinutes = top, heightMinutes = height, lane = lane)
    }
    return DayLayout(boxes = boxes, lanes = laneEnds.size.coerceAtLeast(1))
}

/** Enough to hold a tap target at the grid's hour height. */
const val MIN_BOX_MINUTES = 24

// --- labels and instants -------------------------------------------------------------------

/** `HH:mm` in the phone's zone. */
fun eventTimeLabel(iso: String?, zone: ZoneId = ZoneId.systemDefault()): String {
    val instant = parseInstantOrNull(iso) ?: return ""
    return instant.atZone(zone).format(HourMinute)
}

/** An item's "when": a time range. */
fun whenLabel(item: AgendaItem, zone: ZoneId = ZoneId.systemDefault()): String =
    "${eventTimeLabel(item.startsAt, zone)} – ${eventTimeLabel(item.endsAt, zone)}"

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
