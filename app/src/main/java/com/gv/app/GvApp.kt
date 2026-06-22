package com.gv.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.gv.app.di.AppContainer
import com.gv.app.spotify.SpotifyAuth
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Application entry point. Builds the [AppContainer] (manual DI) before any Activity starts,
 * and wires the background-sync triggers: periodic refresh, reconnect-driven flush, and a
 * foreground flush+refresh so reopening the app reconciles without blocking the UI.
 */
class GvApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        SpotifyAuth.init(applicationContext)
        initBackgroundSync()
    }

    private fun initBackgroundSync() {
        container.syncScheduler.schedulePeriodicRefresh()

        // Connectivity restored → flush queued offline writes. drop(1) skips the initial value.
        container.connectivityObserver.online
            .drop(1)
            .filter { online -> online }
            .onEach { container.syncScheduler.requestFlush() }
            .launchIn(container.appScope)

        // App brought to foreground → flush and warm the cache (non-blocking; cache is shown first).
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                container.syncScheduler.requestFlush()
                container.syncScheduler.requestRefreshNow()
            }
        })
    }
}

/**
 * Manual-DI accessor for AndroidViewModels: `(getApplication() as GvApp).container` without
 * the cast noise, and the single place that knows the Application is a [GvApp].
 */
val Application.container: AppContainer
    get() = (this as GvApp).container
