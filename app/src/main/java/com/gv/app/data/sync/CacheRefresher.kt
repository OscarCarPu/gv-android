package com.gv.app.data.sync

/**
 * A cache that can be refreshed in the background by [CacheRefreshWorker].
 *
 * This file used to also declare `OutboxHandler` and `SyncOutcome`, the replay contract for a
 * write-behind queue. That queue is gone: the app is online-first and refuses writes when
 * offline rather than replaying them later, so there is nothing to reconcile after the fact.
 */
interface CacheRefresher {
    suspend fun refresh()
}
