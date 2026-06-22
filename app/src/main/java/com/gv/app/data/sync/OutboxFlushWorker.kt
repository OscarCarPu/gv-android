package com.gv.app.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gv.app.GvApp
import com.gv.app.data.local.db.OutboxMutation.Companion.STATUS_FAILED

/**
 * Flushes the outbox FIFO. Per row it asks the registered [OutboxHandler] to replay the
 * mutation and reconcile the cache, then:
 *  - **Synced**     → delete the row.
 *  - **DeadLetter** → park the row as FAILED (4xx / unrecoverable) and move on.
 *  - **Retry**      → bump the row's attempt count; once exhausted, dead-letter; otherwise
 *                     stop the run and return [Result.retry] so WorkManager backs off and the
 *                     FIFO order (this row first) is preserved on the next attempt.
 */
class OutboxFlushWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as GvApp).container
        val dao = container.database.outboxDao()
        val api = container.apiService
        val handlers = container.outboxHandlers

        val pending = dao.pending()
        if (pending.isEmpty()) return Result.success()

        for (snapshot in pending) {
            // Re-read fresh: an earlier CREATE in this same flush may have remapped this row's
            // entityId (tmp_ → server id) or another pass may have changed its status. Using the
            // stale in-memory snapshot would dead-letter follow-up UPDATE/DELETE rows.
            val mutation = dao.byId(snapshot.id) ?: continue
            if (mutation.status != com.gv.app.data.local.db.OutboxMutation.STATUS_PENDING) continue

            val handler = handlers[mutation.entityType]
            if (handler == null) {
                dao.update(mutation.copy(status = STATUS_FAILED, errorBody = "No handler for ${mutation.entityType}"))
                continue
            }
            val outcome = runCatching { handler.sync(api, mutation) }.getOrElse { SyncOutcome.Retry }
            when (outcome) {
                is SyncOutcome.Synced -> dao.deleteById(mutation.id)
                is SyncOutcome.DeadLetter ->
                    dao.update(mutation.copy(status = STATUS_FAILED, errorBody = outcome.error))
                is SyncOutcome.Retry -> {
                    val nextAttempt = mutation.retryCount + 1
                    if (nextAttempt >= MAX_RETRIES) {
                        dao.update(
                            mutation.copy(
                                status = STATUS_FAILED,
                                errorBody = "Gave up after $MAX_RETRIES attempts",
                            ),
                        )
                    } else {
                        dao.update(mutation.copy(retryCount = nextAttempt))
                        return Result.retry()
                    }
                }
            }
        }
        return Result.success()
    }

    companion object {
        const val MAX_RETRIES = 10
        const val UNIQUE_NAME = "outbox-flush"
    }
}
