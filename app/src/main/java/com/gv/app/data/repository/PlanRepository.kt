package com.gv.app.data.repository

import com.gv.app.data.api.ApiService
import com.gv.app.domain.model.CalendarEvent
import com.gv.app.domain.model.CreateTaskRequest
import com.gv.app.domain.model.DayFreeBusy
import com.gv.app.domain.model.UpdateEventRequest
import com.gv.app.domain.model.CreateCommitmentRequest
import com.gv.app.domain.model.CreatePlanBlockRequest
import com.gv.app.domain.model.PlanBlockResponse
import com.gv.app.domain.model.RecurringCommitmentResponse
import com.gv.app.domain.model.UpdateCommitmentRequest
import com.gv.app.domain.model.UpdatePlanBlockRequest
import java.time.LocalDate

/**
 * Plan blocks and recurring commitments — the writes behind the Today tab's plan.
 *
 * Today's plan itself is part of [TaskRepository]'s cached snapshot, so a block write ends by
 * asking it to re-read: what the screen shows after an edit is what the server holds, not a
 * patched guess. Other days and the commitment list are read live (nothing is cached for them),
 * so a stale row can never be edited.
 */
class PlanRepository(
    private val api: ApiService,
    private val gate: OnlineGate,
    private val tasks: TaskRepository,
) {

    // ----- Blocks -----

    /** Blocks on one local day, earliest first. */
    suspend fun blocksOn(date: LocalDate): ApiResult<List<PlanBlockResponse>> =
        safeApiCall { api.getPlanRange(date.toString(), date.plusDays(1).toString()) }
            .map { range -> range.blocks.sortedBy { it.started_at } }

    /** Blocks in `[from, to)` — the calendar marks the events that already have one. */
    suspend fun blocksBetween(from: LocalDate, to: LocalDate): ApiResult<List<PlanBlockResponse>> =
        safeApiCall { api.getPlanRange(from.toString(), to.toString()) }.map { it.blocks }

    /** Free / busy hours per day, for the free-time panel. */
    suspend fun freeBusy(from: LocalDate, to: LocalDate): ApiResult<List<DayFreeBusy>> =
        safeApiCall { api.getFreeBusy(from.toString(), to.toString()) }.map { it.days }

    /**
     * "Create plan" on a calendar event: a task first if one was asked for, then the event's
     * times if they changed, then the block linked to the event. In that order, like the web, so
     * a failure part-way leaves the earlier steps done rather than a block pointing at nothing.
     */
    suspend fun createFromEvent(event: CalendarEvent, plan: PlanFromEventRequest): ApiResult<Unit> {
        gate.requireOnline()?.let { return it }

        var taskId = plan.taskId
        if (plan.newTaskName != null) {
            val created = safeApiCall {
                api.createTask(CreateTaskRequest(null, plan.newTaskName, null, null, null, null, null))
            }
            if (created is ApiResult.Failure) return created
            taskId = (created as ApiResult.Success).data.id
        }

        if (plan.moveEvent) {
            val moved = safeApiCall {
                api.updateCalendarEvent(event.instance_id, UpdateEventRequest(starts_at = plan.startsAt, ends_at = plan.endsAt))
            }
            if (moved is ApiResult.Failure) return moved
        }

        val block = safeApiCall {
            api.createPlanBlock(
                CreatePlanBlockRequest(
                    started_at = plan.startsAt,
                    ended_at = plan.endsAt,
                    task_id = taskId,
                    label = plan.label,
                    event_ref = event.instance_id,
                ),
            )
        }
        if (block is ApiResult.Failure) {
            // A task made for this plan is useless without it, and a retry would make another.
            // Best effort: if even this fails, the task is at least visible to delete by hand.
            if (plan.newTaskName != null && taskId != null) runCatching { api.deleteTask(taskId) }
            return block
        }
        return block.map { }.thenReconcile()
    }

    suspend fun createBlock(request: CreatePlanBlockRequest): ApiResult<Unit> {
        gate.requireOnline()?.let { return it }
        return safeApiCall { api.createPlanBlock(request) }.map { }.thenReconcile()
    }

    suspend fun updateBlock(id: Int, request: UpdatePlanBlockRequest): ApiResult<Unit> {
        gate.requireOnline()?.let { return it }
        return safeApiCall { api.updatePlanBlock(id, request) }.map { }.thenReconcile()
    }

    suspend fun deleteBlock(id: Int): ApiResult<Unit> {
        gate.requireOnline()?.let { return it }
        return safeApiCallNoBody { api.deletePlanBlock(id) }.thenReconcile()
    }

    /** Wipes every block that has not started yet — the "start the plan over" button. */
    suspend fun clearFutureBlocks(): ApiResult<Unit> {
        gate.requireOnline()?.let { return it }
        return safeApiCallNoBody { api.deleteFuturePlanBlocks() }.thenReconcile()
    }

    // ----- Commitments -----

    suspend fun commitments(): ApiResult<List<RecurringCommitmentResponse>> =
        safeApiCall { api.listCommitments() }

    suspend fun createCommitment(request: CreateCommitmentRequest): ApiResult<Unit> {
        gate.requireOnline()?.let { return it }
        return safeApiCall { api.createCommitment(request) }.map { }.thenReconcile()
    }

    suspend fun updateCommitment(id: Int, request: UpdateCommitmentRequest): ApiResult<Unit> {
        gate.requireOnline()?.let { return it }
        return safeApiCall { api.updateCommitment(id, request) }.map { }.thenReconcile()
    }

    suspend fun deleteCommitment(id: Int): ApiResult<Unit> {
        gate.requireOnline()?.let { return it }
        return safeApiCallNoBody { api.deleteCommitment(id) }.thenReconcile()
    }

    // ----- helpers -----

    /** A commitment generates blocks, so any commitment write can change today's plan too. */
    private suspend fun ApiResult<Unit>.thenReconcile(): ApiResult<Unit> {
        if (this is ApiResult.Success) tasks.reconcile()
        return this
    }
}

/** What [PlanRepository.createFromEvent] needs; the UI's checked form, without its UI types. */
data class PlanFromEventRequest(
    val startsAt: String,
    val endsAt: String,
    val taskId: Int?,
    val newTaskName: String?,
    val label: String?,
    val moveEvent: Boolean,
)
