package com.gv.app.domain.model

/**
 * Calendar DTOs. Mirrors `gv-api/internal/calendar/dto.go`: an event is a name and a time range,
 * with instants as RFC 3339 strings. snake_case so Gson's defaults map the JSON as-is.
 */
data class CalendarEvent(
    val id: Int,
    val name: String = "",
    val start_time: String,
    val end_time: String,
)

data class CreateEventRequest(
    val name: String,
    val start_time: String,
    val end_time: String,
)

/** A patch: null fields are left alone. */
data class UpdateEventRequest(
    val name: String? = null,
    val start_time: String? = null,
    val end_time: String? = null,
)
