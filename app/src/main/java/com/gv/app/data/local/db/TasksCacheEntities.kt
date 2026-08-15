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
 * The single running timer (presence of the row = a timer is running). [serverId] is the real
 * time-entry id: a timer only ever exists here after the server has created it, so stop/assign
 * can target it directly. Elapsed time is derived from [startedAt].
 */
@Entity(tableName = "active_timer")
data class ActiveTimerEntity(
    @PrimaryKey val id: Int = 0,
    val serverId: Int,
    val taskId: Int,
    val taskName: String,
    val projectName: String?,
    val taskType: String?,
    val recurrence: Int?,
    val priority: Int?,
    val startedAt: String,
    val comment: String?,
)
