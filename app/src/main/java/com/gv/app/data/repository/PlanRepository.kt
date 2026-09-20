package com.gv.app.data.repository

import com.gv.app.data.api.ApiService
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
