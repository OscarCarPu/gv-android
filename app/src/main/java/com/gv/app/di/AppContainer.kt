package com.gv.app.di

import android.content.Context
import com.gv.app.data.api.ApiService
import com.gv.app.data.api.RetrofitClient
import com.gv.app.data.local.ThemeStore
import com.gv.app.data.local.TokenManager
import com.gv.app.data.local.db.GvDatabase
import com.gv.app.data.local.db.OutboxMutation
import com.gv.app.data.repository.HabitRepository
import com.gv.app.data.repository.RutasRepository
import com.gv.app.data.repository.TaskRepository
import com.gv.app.data.sync.CacheRefresher
import com.gv.app.data.sync.ConnectivityObserver
import com.gv.app.data.sync.Outbox
import com.gv.app.data.sync.OutboxHandler
import com.gv.app.data.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow

/**
 * Manual dependency-injection container, owned by [com.gv.app.GvApp] for the app's lifetime.
 *
 * The single seam through which screens obtain repositories, and through which the offline
 * stack (Room cache, outbox, connectivity, background sync) is assembled. Repositories are
 * added per feature phase; each registers itself as an [OutboxHandler] and/or [CacheRefresher]
 * here so the sync Workers stay domain-agnostic. Kept deliberately manual (no Hilt/Koin).
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    /** Long-lived scope for app-level collectors (connectivity → flush). */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val tokenManager: TokenManager = TokenManager(appContext)

    val themeStore: ThemeStore = ThemeStore(appContext)

    val apiService: ApiService
        get() = RetrofitClient.apiService

    val database: GvDatabase = GvDatabase.get(appContext)

    val outbox: Outbox = Outbox(database.outboxDao())

    val connectivityObserver: ConnectivityObserver = ConnectivityObserver(appContext)

    val syncScheduler: SyncScheduler = SyncScheduler(appContext)

    // --- Repositories register here as they are introduced (habits, tasks, rutas) ---

    val habitRepository: HabitRepository =
        HabitRepository(apiService, database, database.habitDao(), outbox, syncScheduler)

    val taskRepository: TaskRepository =
        TaskRepository(apiService, database, database.taskDao(), outbox, syncScheduler)

    val rutasRepository: RutasRepository =
        RutasRepository(apiService, database, database.rutasDao(), outbox, syncScheduler)

    private val repositories: List<Any> = listOf(
        habitRepository,
        taskRepository,
        rutasRepository,
    )

    /** entityType → handler that knows how to replay/reconcile that mutation. */
    val outboxHandlers: Map<String, OutboxHandler> =
        repositories.filterIsInstance<OutboxHandler>()
            .flatMap { handler -> handler.entityTypes.map { it to handler } }
            .toMap()

    val cacheRefreshers: List<CacheRefresher> = repositories.filterIsInstance<CacheRefresher>()

    // --- Sync status surfaced to the UI (offline / syncing / sync-error chip) ---

    val isOnline: Flow<Boolean> = connectivityObserver.online
    val pendingSyncCount: Flow<Int> = database.outboxDao().pendingCountFlow()
    val failedSync: Flow<List<OutboxMutation>> = database.outboxDao().failedFlow()

    init {
        // RetrofitClient builds its OkHttp/Retrofit stack lazily and reads this on first use,
        // so it must be assigned before any repository touches apiService.
        RetrofitClient.tokenManager = tokenManager
    }
}
