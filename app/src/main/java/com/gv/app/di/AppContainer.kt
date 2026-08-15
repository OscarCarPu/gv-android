package com.gv.app.di

import android.content.Context
import com.gv.app.data.api.ApiService
import com.gv.app.data.api.RetrofitClient
import com.gv.app.data.auth.AutoLogin
import com.gv.app.data.local.ThemeStore
import com.gv.app.data.local.TokenManager
import com.gv.app.data.local.db.GvDatabase
import com.gv.app.data.repository.HabitRepository
import com.gv.app.data.repository.LightsRepository
import com.gv.app.data.repository.OnlineGate
import com.gv.app.data.repository.RutasRepository
import com.gv.app.data.repository.TaskRepository
import com.gv.app.data.sync.CacheRefresher
import com.gv.app.data.sync.ConnectivityObserver
import com.gv.app.data.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow

/**
 * Manual dependency-injection container, owned by [com.gv.app.GvApp] for the app's lifetime.
 *
 * The single seam through which screens obtain repositories. The app is **online-first,
 * offline read-only**: repositories call the server directly and Room is only a read cache,
 * so what lives here is a connectivity observer, an [OnlineGate] that every write consults,
 * and a periodic cache warm-up. Kept deliberately manual (no Hilt/Koin).
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    /** Long-lived scope for app-level collectors. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val tokenManager: TokenManager = TokenManager(appContext)

    val themeStore: ThemeStore = ThemeStore(appContext)

    val apiService: ApiService
        get() = RetrofitClient.apiService

    val database: GvDatabase = GvDatabase.get(appContext)

    val connectivityObserver: ConnectivityObserver = ConnectivityObserver(appContext)

    val onlineGate: OnlineGate = OnlineGate(connectivityObserver)

    val syncScheduler: SyncScheduler = SyncScheduler(appContext)

    /** Signs in from build-time credentials; inert when they are absent. */
    val autoLogin: AutoLogin = AutoLogin(apiService, tokenManager)

    // --- Repositories ---

    val habitRepository: HabitRepository =
        HabitRepository(apiService, database, database.habitDao(), onlineGate)

    val taskRepository: TaskRepository =
        TaskRepository(apiService, database, database.taskDao(), onlineGate)

    val rutasRepository: RutasRepository =
        RutasRepository(apiService, database, database.rutasDao(), onlineGate)

    /** Lights hold no cache: a stale bulb state is worse than none, so reads are always live. */
    val lightsRepository: LightsRepository =
        LightsRepository(apiService, onlineGate)

    // Lights are absent on purpose: they keep no cache, so there is nothing to warm up.
    private val repositories: List<Any> = listOf(
        habitRepository,
        taskRepository,
        rutasRepository,
    )

    val cacheRefreshers: List<CacheRefresher> = repositories.filterIsInstance<CacheRefresher>()

    // --- Connectivity surfaced to the UI (offline / read-only banner) ---

    val isOnline: Flow<Boolean> = connectivityObserver.online

    init {
        // RetrofitClient builds its OkHttp/Retrofit stack lazily and reads this on first use,
        // so it must be assigned before any repository touches apiService.
        RetrofitClient.tokenManager = tokenManager
    }
}
