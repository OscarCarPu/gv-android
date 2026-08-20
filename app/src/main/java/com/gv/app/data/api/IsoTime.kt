package com.gv.app.data.api

import java.time.Instant
import java.time.OffsetDateTime

/**
 * Reads an instant the way gv-api writes them.
 *
 * RFC3339 always carries an offset but not always `Z`: a calendar's own zone comes through as
 * `+02:00`, and [Instant.parse] accepts only `Z`. Getting this wrong is invisible until an
 * appointment renders an hour or two out, or vanishes because the value failed to parse and was
 * quietly treated as absent — so every caller goes through here rather than picking a parser.
 */
fun parseInstantOrNull(iso: String?): Instant? {
    if (iso.isNullOrBlank()) return null
    return runCatching { OffsetDateTime.parse(iso).toInstant() }
        .recoverCatching { Instant.parse(iso) }
        .getOrNull()
}
