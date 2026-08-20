package com.gv.app.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The app's local read cache. Every row here is a copy of something the server owns, so a
 * destructive migration costs nothing but a re-fetch — there are no local-only writes to lose
 * (the app refuses writes when offline rather than queueing them). Schemas are exported (see
 * the `room.schemaLocation` ksp arg) so real migrations can be authored later.
 *
 * Entities are added per feature phase; the version is bumped alongside.
 */
@Database(
    entities = [
        HabitDayEntity::class,
        TasksSnapshotEntity::class,
        ActiveTimerEntity::class,
        ConcelloMarkEntity::class,
        CalendarEntity::class,
        CalendarEventEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class GvDatabase : RoomDatabase() {

    abstract fun habitDao(): HabitDao

    abstract fun taskDao(): TaskDao

    abstract fun rutasDao(): RutasDao

    abstract fun calendarDao(): CalendarDao

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
