package com.gv.app.ui.common

/**
 * A day's free time as `5h 30m` (`5h` on the hour), the way gv-web shows it. The API sends hours
 * as a decimal string; rounding that to whole hours turned a 5h 30m day into "6h" and a 20-minute
 * one into "0h" — the very difference that decides whether a task still fits. Unreadable or
 * negative input reads as no free time. Rounded to the nearest minute, so 5.999 is `6h`, not `5h 60m`.
 */
fun formatFreeHours(hours: String): String {
    val h = hours.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 } ?: 0.0
    val minutes = Math.round(h * 60)
    val whole = minutes / 60
    val rest = minutes % 60
    return if (rest > 0) "${whole}h ${rest}m" else "${whole}h"
}
