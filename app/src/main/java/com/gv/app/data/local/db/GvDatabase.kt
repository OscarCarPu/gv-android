package com.gv.app.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The app's local cache + write-behind outbox. The DB is a *cache* of server state plus the
 * durable outbox queue, so a destructive migration only costs a re-sync (and, in the rare
 * case, unsynced offline writes) — acceptable during active development. Schemas are exported
 * (see the `room.schemaLocation` ksp arg) so real migrations can be authored later.
 *
 * Entities are added per feature phase; the version is bumped alongside.
 */
@Database(
    entities = [
        OutboxMutation::class,
        HabitDayEntity::class,
        TasksSnapshotEntity::class,
        ActiveTimerEntity::class,
        ConcelloMarkEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class GvDatabase : RoomDatabase() {

    abstract fun outboxDao(): OutboxDao

    abstract fun habitDao(): HabitDao

    abstract fun taskDao(): TaskDao

    abstract fun rutasDao(): RutasDao

    companion object {
        @Volatile
        private var instance: GvDatabase? = null

        fun get(context: Context): GvDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        private fun build(context: Context): GvDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                GvDatabase::class.java,
                "gv-cache.db",
            )
                .fallbackToDestructiveMigration()
                .build()
    }
}
