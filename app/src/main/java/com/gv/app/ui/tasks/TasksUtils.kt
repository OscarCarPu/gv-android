package com.gv.app.ui.tasks

import androidx.compose.ui.graphics.Color
import com.gv.app.ui.theme.GvColors
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val IsoUtcFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT)

val DayLabelFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, d MMM", Locale.UK)

val FullDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, d MMM yyyy · HH:mm", Locale.UK)

val DateShortFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM", Locale.UK)

fun parseIso(iso: String?): LocalDateTime? {
    if (iso.isNullOrBlank()) return null
    return try {
        Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDateTime()
    } catch (_: Exception) {
        try { LocalDateTime.parse(iso.substringBefore('Z')) } catch (_: Exception) { null }
    }
}

fun nowIsoUtc(): String =
    LocalDateTime.now().atZone(ZoneId.systemDefault())
        .withZoneSameInstant(ZoneId.of("UTC"))
        .toLocalDateTime()
        .format(IsoUtcFormatter)

fun localDateTimeToIsoUtc(local: LocalDateTime): String =
    local.atZone(ZoneId.systemDefault())
        .withZoneSameInstant(ZoneId.of("UTC"))
        .toLocalDateTime()
        .format(IsoUtcFormatter)

fun formatRelativeDay(iso: String?): String {
    if (iso.isNullOrBlank()) return "—"
    val date = parseIso(iso)?.toLocalDate() ?: return iso
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        today.plusDays(1) -> "Tomorrow"
        else -> date.format(DayLabelFormatter)
    }
}

fun formatShortDate(iso: String?): String {
    if (iso.isNullOrBlank()) return "—"
    val date = parseIso(iso)?.toLocalDate() ?: return iso
    return date.format(DateShortFormatter)
}

fun formatHhMmSs(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return "%02d:%02d:%02d".format(Locale.UK, h, m, s)
}

fun formatDurationShort(totalSeconds: Long): String {
    val totalMinutes = totalSeconds / 60
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

fun isoDateKey(iso: String?): String =
    iso?.substring(0, 10.coerceAtMost(iso.length)) ?: "no-date"

fun isOverdue(iso: String?): Boolean {
    if (iso.isNullOrBlank()) return false
    val date = parseIso(iso)?.toLocalDate() ?: return false
    return date.isBefore(LocalDate.now())
}

fun isToday(iso: String?): Boolean {
    if (iso.isNullOrBlank()) return false
    val date = parseIso(iso)?.toLocalDate() ?: return false
    return date == LocalDate.now()
}

fun statusLabel(startedAt: String?, taskType: String?, recurrence: Int?): String =
    if (startedAt.isNullOrBlank()) {
        "Pending"
    } else when (taskType) {
        "continuous" -> "Continuous"
        "recurring" -> "Recurring · every ${recurrence ?: "?"} days"
        else -> "In progress"
    }

fun priorityColor(priority: Int?): Color = when (priority ?: 3) {
    1 -> GvColors.Danger
    2 -> GvColors.Warning
    3 -> GvColors.Primary
    4 -> GvColors.Secondary
    else -> GvColors.TextMuted
}

fun taskTypeColor(taskType: String?, started: Boolean): Color = when {
    !started -> GvColors.TextMuted
    taskType == "continuous" -> GvColors.Continuous
    taskType == "recurring" -> GvColors.Recurring
    else -> GvColors.Primary
}

fun buildRecurringDueAt(recurrence: Int): String {
    val tomorrow = LocalDate.now().plusDays(recurrence.toLong())
    val noon = tomorrow.atTime(12, 0)
    return localDateTimeToIsoUtc(noon)
}
