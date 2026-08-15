package com.gv.app.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules cache warm-ups.
 *
 * Only refreshes live here. There is no write queue to flush: the app writes straight to the
 * server when online and refuses the write when not (see
 * [com.gv.app.data.repository.OnlineGate]), so nothing is ever pending locally.
 */
class SyncScheduler(context: Context) {

    private val workManager = WorkManager.getInstance(context.applicationContext)

    private val connected = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

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
}
