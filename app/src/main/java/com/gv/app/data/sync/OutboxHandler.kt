package com.gv.app.data.sync

import com.gv.app.data.api.ApiService
import com.gv.app.data.local.db.OutboxMutation
import com.gv.app.data.repository.ApiResult
import com.gv.app.data.repository.isClientError

/** Result of attempting to flush one outbox row. */
sealed interface SyncOutcome {
    /** Reached the server (or a 404-on-delete that means "already gone"); drop the row. */
    data object Synced : SyncOutcome

    /** Transient failure (no network / 5xx); keep the row and let WorkManager back off. */
    data object Retry : SyncOutcome

    /** Permanent failure (4xx / exhausted); park the row as FAILED and surface it. */
    data class DeadLetter(val error: String) : SyncOutcome
}

/**
 * Per-domain replay logic, implemented by repositories and registered in the AppContainer.
 * A handler owns one or more [entityTypes]; it performs the network call for [mutation] and,
 * on success, reconciles the local cache (re-fetch, server-id remap, etc.). This keeps the
 * OutboxFlushWorker free of any domain knowledge.
 */
interface OutboxHandler {
    val entityTypes: Set<String>
    suspend fun sync(api: ApiService, mutation: OutboxMutation): SyncOutcome
}

/** A cache that can be refreshed in the background by the periodic CacheRefreshWorker. */
interface CacheRefresher {
    suspend fun refresh()
}

/** 4xx is permanent (dead-letter); anything else (no network / 5xx) is worth retrying. */
fun ApiResult.Failure.toSyncOutcome(): SyncOutcome =
    if (isClientError) SyncOutcome.DeadLetter(message) else SyncOutcome.Retry
