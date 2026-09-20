package com.gv.app.domain.model

/**
 * The plan and capacity domain, mirroring gv-api's `plan` and `capacity` packages. Times are
 * instants (RFC 3339, UTC); `plan_date` is the local calendar day the block belongs to.
 */
data class PlanBlockResponse(
    val id: Int,
    val plan_date: String?,
    val started_at: String,
    val ended_at: String,
    val task_id: Int?,
    val task_name: String?,
    val label: String,
    val note: String?,
    val event_ref: String? = null,
    val commitment_id: Int? = null,
    val task_type: String?,
    val task_recurrence: Int?,
    val task_started_at: String?,
    val task_finished_at: String?,
)

data class PlanTotals(
    val task_seconds: Long,
    val free_seconds: Long,
)

data class PlanTodayResponse(
    val date: String,
    val blocks: List<PlanBlockResponse>,
    val totals: PlanTotals,
    val budget: TimeEntrySummaryResponse,
)

data class PlanRangeResponse(
    val from: String,
    val to: String,
    val blocks: List<PlanBlockResponse>,
)

data class CreatePlanBlockRequest(
    val started_at: String,
    val ended_at: String,
    val task_id: Int? = null,
    val label: String? = null,
    val note: String? = null,
    val event_ref: String? = null,
)

/**
 * A PUT with patch semantics: omitted (null) fields are left alone. Clearing a task or a note is
 * spelled by the two flags, never by sending null, so Gson's null-omission is harmless here.
 */
data class UpdatePlanBlockRequest(
    val started_at: String? = null,
    val ended_at: String? = null,
    val task_id: Int? = null,
    val clear_task: Boolean = false,
    val label: String? = null,
    val note: String? = null,
    val clear_note: Boolean = false,
)

/** A weekly-recurring block. `days_of_week` is 0 = Sunday … 6 = Saturday; times are `HH:MM`. */
data class RecurringCommitmentResponse(
    val id: Int,
    val task_id: Int,
    val task_name: String,
    val label: String,
    val days_of_week: List<Int>,
    val start_time: String,
    val end_time: String,
    val active: Boolean,
)

data class CreateCommitmentRequest(
    val task_id: Int,
    val label: String,
    val days_of_week: List<Int>,
    val start_time: String,
    val end_time: String,
)

data class UpdateCommitmentRequest(
    val label: String? = null,
    val days_of_week: List<Int>? = null,
    val start_time: String? = null,
    val end_time: String? = null,
    val active: Boolean? = null,
)

/** One day of `GET /capacity/free-busy`. The hour fields are decimal strings. */
data class DayFreeBusy(
    val date: String,
    val capacity_hours: String,
    val busy_hours: String,
    val free_hours: String,
)

data class FreeBusyRangeResponse(
    val from: String,
    val to: String,
    val days: List<DayFreeBusy>,
)
