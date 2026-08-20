package com.gv.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gv.app.domain.model.CalendarEvent
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

private val WeekdayLabel = DateTimeFormatter.ofPattern("EEE", Locale.UK)
private val DayNumberLabel = DateTimeFormatter.ofPattern("d", Locale.UK)
private val AgendaDayLabel = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK)

/** One hour of the time grid. Tall enough that a 30-minute meeting is still a readable box. */
private val HourHeight = 56.dp

/** How many colour dots a month cell shows before it stops adding them. */
private const val MaxDots = 4

/** The colour to paint an event with, falling back to the theme when the API's is unreadable. */
@Composable
private fun eventColor(event: CalendarEvent): Color =
    parseHexColor(event.color) ?: GvColors.Primary

// -------------------------------------------------------------------------------------------
// Month
// -------------------------------------------------------------------------------------------

/**
 * Month grid plus the selected day's agenda underneath.
 *
 * The grid carries colour dots rather than the text chips gv-web shows: a phone column is about
 * fifty points wide, where a title is clipped to two letters and reads as noise. The day list
 * below is where the detail goes, which also means a month is one tap from any day's plan instead
 * of a drill-in and a way back.
 */
@Composable
fun MonthView(
    state: CalendarUiState,
    onSelectDay: (LocalDate) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
) {
    val spacing = LocalSpacing.current
    val today = remember { LocalDate.now() }

    // Placing an event means parsing its instants, and a month grid asks 42 times. Bucketing
    // once per (events, days) keeps a tick of the clock or a scroll from re-parsing the month.
    val byDay = remember(state.events, state.days) {
        state.days.associateWith { day -> state.events.filter { occupiesDay(it, day) } }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.xs, vertical = spacing.xs),
        ) {
            state.days.take(7).forEach { day ->
                Text(
                    text = day.format(WeekdayLabel),
                    style = MaterialTheme.typography.labelSmall,
                    color = GvColors.TextMuted,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.xs),
        ) {
            state.days.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    week.forEach { day ->
                        MonthCell(
                            day = day,
                            events = byDay[day].orEmpty(),
                            inMonth = day.month == state.anchor.month,
                            isToday = day == today,
                            isSelected = day == state.anchor,
                            onClick = { onSelectDay(day) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(spacing.md))
        DayAgenda(
            day = state.anchor,
            events = state.events,
            onOpenDay = onOpenDay,
            onEventClick = onEventClick,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MonthCell(
    day: LocalDate,
    events: List<CalendarEvent>,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(1.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) GvColors.Primary.copy(alpha = 0.14f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (isSelected) GvColors.Primary else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (isToday) GvColors.Primary else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.format(DayNumberLabel),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isToday -> Color.White
                    inMonth -> GvColors.Text
                    // Leading and trailing days stay visible but recede: they belong to the
                    // weeks the grid has to draw, not to the month being read.
                    else -> GvColors.TextMuted.copy(alpha = 0.5f)
                },
            )
        }
        Spacer(Modifier.height(spacing.xxs))
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            events.take(MaxDots).forEach { event ->
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(parseHexColor(event.color) ?: GvColors.Primary),
                )
            }
            if (events.size > MaxDots) {
                Text(
                    text = "+",
                    style = MaterialTheme.typography.labelSmall,
                    color = GvColors.TextMuted,
                )
            }
        }
    }
}

/** The selected day's events as a list: the readable half of the month view. */
@Composable
private fun DayAgenda(
    day: LocalDate,
    events: List<CalendarEvent>,
    onOpenDay: (LocalDate) -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val ofDay = remember(events, day) { allDayOn(events, day) + timedOn(events, day) }

    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenDay(day) }
                .padding(horizontal = spacing.xl, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = day.format(AgendaDayLabel),
                style = MaterialTheme.typography.labelLarge,
                color = GvColors.Text,
            )
            Text(
                text = "Open day",
                style = MaterialTheme.typography.labelSmall,
                color = GvColors.Primary,
            )
        }
        if (ofDay.isEmpty()) {
            Text(
                text = "Nothing scheduled",
                style = MaterialTheme.typography.bodyMedium,
                color = GvColors.TextMuted,
                modifier = Modifier.padding(horizontal = spacing.xl, vertical = spacing.lg),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = spacing.xl,
                    end = spacing.xl,
                    bottom = spacing.huge,
                ),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                items(ofDay, key = { it.instance_id }) { event ->
                    AgendaRow(event = event, onClick = { onEventClick(event) })
                }
            }
        }
    }
}

@Composable
private fun AgendaRow(event: CalendarEvent, onClick: () -> Unit) {
    val spacing = LocalSpacing.current
    val color = eventColor(event)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(GvColors.BgLight)
            .border(1.dp, GvColors.BorderLight, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = event.summary.ifBlank { "(no title)" },
                style = MaterialTheme.typography.bodyLarge,
                color = GvColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    eventWhenLabel(event),
                    event.location.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = GvColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (event.recurring) {
            Text("↻", style = MaterialTheme.typography.labelMedium, color = GvColors.Recurring)
        }
    }
}

// -------------------------------------------------------------------------------------------
// Week / Day
// -------------------------------------------------------------------------------------------

/**
 * The time grid, serving both week and day: a day is the same grid with one column.
 *
 * Opens on the working part of the day — an hour before the first event, or 08:00 when the day is
 * empty — because midnight is almost never what anyone came to look at.
 */
@Composable
fun TimeGridView(
    state: CalendarUiState,
    onEventClick: (CalendarEvent) -> Unit,
    onCreateAt: (LocalDate, Int) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
) {
    val spacing = LocalSpacing.current
    val today = remember { LocalDate.now() }
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val compact = state.days.size > 1

    // Re-ticks so the "now" line moves; a line frozen where the screen opened is worse than none.
    val now by produceState(initialValue = LocalTime.now()) {
        while (true) {
            value = LocalTime.now()
            delay(30_000)
        }
    }

    // Same reason as the month grid: laying out a day parses every event in it, and this
    // composable re-runs whenever the now-line ticks.
    val perDay = remember(state.events, state.days) {
        state.days.associateWith { day ->
            DayContents(allDay = allDayOn(state.events, day), layout = layoutDay(state.events, day))
        }
    }

    val firstHour = remember(state.events, state.days) {
        state.days
            .mapNotNull { day -> timedOn(state.events, day).firstOrNull() }
            .mapNotNull { apiInstantToLocal(it.starts_at)?.hour }
            .minOrNull()
            ?.let { (it - 1).coerceAtLeast(0) }
            ?: 8
    }
    // Keyed on the range, not on firstHour: re-running when the events change would drag the
    // grid back to the top under the user's finger every time a refresh landed.
    LaunchedEffect(state.mode, state.days.firstOrNull()) {
        scroll.scrollTo(with(density) { (HourHeight * firstHour).roundToPx() })
    }

    Column(Modifier.fillMaxSize()) {
        // Sticky header: weekday labels and the all-day band, which must not scroll away with
        // the hours — an all-day event belongs to the whole column, not to a position in it.
        Row(
            Modifier
                .fillMaxWidth()
                .background(GvColors.BgLight)
                .padding(bottom = spacing.xs),
        ) {
            Spacer(Modifier.width(GutterWidth))
            state.days.forEach { day ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpenDay(day) }
                        .padding(horizontal = 1.dp, vertical = spacing.xs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = if (compact) day.format(WeekdayLabel) else day.format(AgendaDayLabel),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (day == today) GvColors.Primary else GvColors.TextMuted,
                        maxLines = 1,
                    )
                    if (compact) {
                        Text(
                            text = day.format(DayNumberLabel),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (day == today) FontWeight.Bold else FontWeight.Normal,
                            color = if (day == today) GvColors.Primary else GvColors.Text,
                        )
                    }
                    AllDayBand(
                        events = perDay[day]?.allDay.orEmpty(),
                        compact = compact,
                        onEventClick = onEventClick,
                    )
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scroll),
        ) {
            HourGutter()
            state.days.forEach { day ->
                DayColumn(
                    day = day,
                    layout = perDay[day]?.layout ?: DayLayout(emptyList(), 1),
                    now = now.takeIf { day == today },
                    onEventClick = onEventClick,
                    onCreateAt = onCreateAt,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** One day column's contents, worked out once rather than on every recomposition. */
private data class DayContents(val allDay: List<CalendarEvent>, val layout: DayLayout)

private val GutterWidth = 44.dp

@Composable
private fun HourGutter() {
    Column(Modifier.width(GutterWidth)) {
        (0 until 24).forEach { hour ->
            Box(Modifier.height(HourHeight).fillMaxWidth()) {
                Text(
                    text = "%02d:00".format(hour),
                    style = MaterialTheme.typography.labelSmall,
                    color = GvColors.TextMuted,
                    modifier = Modifier.align(Alignment.TopEnd).padding(end = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun AllDayBand(
    events: List<CalendarEvent>,
    compact: Boolean,
    onEventClick: (CalendarEvent) -> Unit,
) {
    if (events.isEmpty()) return
    val spacing = LocalSpacing.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        events.take(if (compact) 2 else 4).forEach { event ->
            val color = eventColor(event)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
                    .clickable { onEventClick(event) }
                    .padding(horizontal = if (compact) 1.dp else spacing.sm, vertical = 2.dp),
            ) {
                // In a seven-column week there is no room for a title, so the colour is the
                // whole message: something covers this day, tap to find out what.
                if (!compact) {
                    Text(
                        text = event.summary.ifBlank { "(no title)" },
                        style = MaterialTheme.typography.labelSmall,
                        color = chipInk(event.color),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun DayColumn(
    day: LocalDate,
    layout: DayLayout,
    now: LocalTime?,
    onEventClick: (CalendarEvent) -> Unit,
    onCreateAt: (LocalDate, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .height(HourHeight * 24)
            .border(width = 0.5.dp, color = GvColors.Border),
    ) {
        val columnWidth = maxWidth

        // Hour slots first, so they sit under the events and a tap on empty space creates one
        // at the hour it landed on.
        Column {
            (0 until 24).forEach { hour ->
                Box(
                    Modifier
                        .height(HourHeight)
                        .fillMaxWidth()
                        .clickable { onCreateAt(day, hour) },
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(GvColors.Border),
                    )
                }
            }
        }

        if (now != null) {
            val top = HourHeight * ((now.hour * 60 + now.minute) / 60f)
            Box(
                Modifier
                    .offset(y = top)
                    .fillMaxWidth()
                    .height(1.5.dp)
                    .background(GvColors.Danger),
            )
        }

        layout.boxes.forEach { box ->
            val laneWidth = columnWidth / layout.lanes
            EventBoxCard(
                box = box,
                width = laneWidth,
                offsetX = laneWidth * box.lane,
                onClick = { onEventClick(box.event) },
            )
        }
    }
}

@Composable
private fun EventBoxCard(
    box: EventBox,
    width: Dp,
    offsetX: Dp,
    onClick: () -> Unit,
) {
    val color = eventColor(box.event)
    val ink = chipInk(box.event.color)
    val top = HourHeight * (box.topMinutes / 60f)
    val height = HourHeight * (box.heightMinutes / 60f)
    Column(
        modifier = Modifier
            .offset(x = offsetX, y = top)
            .width(width)
            .height(height)
            .padding(horizontal = 1.dp, vertical = 0.5.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
                // Tentative events read as provisional rather than booked.
                if (box.event.status == "tentative") color.copy(alpha = 0.45f) else color,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 3.dp, vertical = 2.dp),
    ) {
        Text(
            text = box.event.summary.ifBlank { "(no title)" },
            style = MaterialTheme.typography.labelSmall,
            color = ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium,
        )
        // Only worth the line when the box is tall enough to show it without clipping.
        if (box.heightMinutes >= 45) {
            Text(
                text = eventTimeLabel(box.event.starts_at),
                style = MaterialTheme.typography.labelSmall,
                color = ink.copy(alpha = 0.85f),
                maxLines = 1,
            )
        }
    }
}
