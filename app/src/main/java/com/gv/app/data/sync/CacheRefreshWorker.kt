package com.gv.app.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gv.app.GvApp

/**
 * Keeps the local cache warm in the background. Flushes any pending writes first (so the cache
 * isn't refreshed over un-synced local edits), then refreshes every registered [CacheRefresher].
 * Scheduled periodically (15 min, the OS floor) and one-shot on app foreground / reconnect.
 */
class CacheRefreshWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as GvApp).container
        // Each refresher isolates its own failures so one offline domain can't sink the rest.
        container.cacheRefreshers.forEach { refresher ->
            runCatching { refresher.refresh() }
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE_PERIODIC = "cache-refresh-periodic"
        const val UNIQUE_NOW = "cache-refresh-now"
    }
}
