package com.gv.app.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Enqueues the sync Workers with the right constraints, backoff, and de-duplication. */
class SyncScheduler(context: Context) {

    private val workManager = WorkManager.getInstance(context.applicationContext)

    private val connected = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Flush pending writes as soon as there's a connection. Coalesces with any in-flight flush. */
    fun requestFlush() {
        val request = OneTimeWorkRequestBuilder<OutboxFlushWorker>()
            .setConstraints(connected)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, INITIAL_BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(
            OutboxFlushWorker.UNIQUE_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }

    /** One-shot cache refresh (app foreground / reconnect). Drops if one is already queued. */
    fun requestRefreshNow() {
        val request = OneTimeWorkRequestBuilder<CacheRefreshWorker>()
            .setConstraints(connected)
            .build()
        workManager.enqueueUniqueWork(
            CacheRefreshWorker.UNIQUE_NOW,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun schedulePeriodicRefresh() {
        val request = PeriodicWorkRequestBuilder<CacheRefreshWorker>(15, TimeUnit.MINUTES)
            .setConstraints(connected)
            .build()
        workManager.enqueueUniquePeriodicWork(
            CacheRefreshWorker.UNIQUE_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private companion object {
        const val INITIAL_BACKOFF_SECONDS = 5L
    }
}
