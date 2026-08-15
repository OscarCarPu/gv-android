package com.gv.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.gv.app.di.AppContainer
import com.gv.app.spotify.SpotifyAuth
import kotlinx.coroutines.launch

/**
 * Application entry point. Builds the [AppContainer] (manual DI) before any Activity starts,
 * signs in if credentials were baked in, and wires the cache warm-ups.
 *
 * There is no write queue to flush on reconnect: the app writes straight to the server and
 * refuses writes when offline, so only reads ever need catching up.
 */
class GvApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        SpotifyAuth.init(applicationContext)
        signInIfConfigured()
        initBackgroundRefresh()
    }

    /**
     * Fire-and-forget sign-in. Nothing waits on it: navigation is driven by
     * [com.gv.app.data.local.TokenManager.tokenFlow], so the login screen shows for the moment
     * this takes and then moves on by itself when the token lands. If no credentials were baked
     * in, or the attempt fails, the manual login screen is simply what remains.
     */
    private fun signInIfConfigured() {
        val autoLogin = container.autoLogin
        if (!autoLogin.isConfigured) return
        container.appScope.launch { autoLogin.attempt() }
    }

    private fun initBackgroundRefresh() {
        container.syncScheduler.schedulePeriodicRefresh()

        // App brought to foreground → warm the cache (non-blocking; the cache is shown first).
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
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
