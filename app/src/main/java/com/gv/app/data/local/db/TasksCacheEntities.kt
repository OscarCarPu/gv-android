package com.gv.app.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached read model for the Tasks screen, stored as one row of JSON snapshots. The tasks views
 * are display + actions-that-reconcile (not offline tree editing), so a single refreshable
 * snapshot lets the screen load instantly and stay usable offline without per-row normalisation.
 */
@Entity(tableName = "tasks_snapshot")
data class TasksSnapshotEntity(
    @PrimaryKey val id: Int = 0,
    val byDueJson: String,
    val treeJson: String,
    val summaryJson: String?,
    val planJson: String?,
    val projectsJson: String,
    val updatedAt: Long,
)

/**
 * The single running timer (presence of the row = a timer is running). [outboxId] links to the
 * queued create (a `tmp_…` id until the server assigns one); [serverId] is filled in on sync so
 * later stop/assign mutations target the real entry. Elapsed time is derived from [startedAt].
 */
@Entity(tableName = "active_timer")
data class ActiveTimerEntity(
    @PrimaryKey val id: Int = 0,
    val outboxId: String,
    val serverId: Int?,
    val taskId: Int,
    val taskName: String,
    val projectName: String?,
    val taskType: String?,
    val recurrence: Int?,
    val priority: Int?,
    val startedAt: String,
    val comment: String?,
)
