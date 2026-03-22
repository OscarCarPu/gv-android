package com.gv.app.ui.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gv.app.AppColors
import com.gv.app.domain.model.TaskByDueDate
import com.gv.app.ui.components.SectionHeader
import com.gv.app.ui.components.StatusBadge
import com.gv.app.ui.components.TreeNodeRow
import com.gv.app.ui.components.formatHours

@Composable
fun TasksScreen(
    timerVm: TimerViewModel,
    tasksVm: TasksViewModel = viewModel()
) {
    val tasksState by tasksVm.state.collectAsStateWithLifecycle()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { tasksVm.showCreate(CreateMode.TASK) }) {
                Icon(Icons.Filled.Add, contentDescription = "Crear")
            }
        }
    ) { _ ->
        if (tasksState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (tasksState.error != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(tasksState.error ?: "Error", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { tasksVm.loadAll() }) { Text("Reintentar") }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Timer widget
                item { TimerWidget(vm = timerVm) }

                // Due-date tasks
                item { SectionHeader("Próximas a vencer") }
                if (tasksState.tasksByDueDate.isEmpty()) {
                    item {
                        Text(
                            "No hay tareas pendientes",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.muted,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
                items(tasksState.tasksByDueDate, key = { it.id }) { task ->
                    TaskDueDateRow(
                        task = task,
                        onTap = { tasksVm.loadTaskDetail(task.id) },
                        onPlay = {
                            timerVm.selectTask(task.id, task.name)
                            timerVm.startTimer()
                        }
                    )
                }

                // Active projects tree
                item { SectionHeader("Proyectos activos") }
                if (tasksState.activeTree.isEmpty()) {
                    item {
                        Text(
                            "No hay proyectos activos",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.muted,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
                items(tasksState.activeTree, key = { it.id }) { node ->
                    TreeNodeRow(
                        node = node,
                        depth = 0,
                        expandedIds = tasksState.expandedNodeIds,
                        onToggleExpand = { tasksVm.toggleNodeExpanded(it) },
                        onNodeClick = { clicked ->
                            if (clicked.type == "project") {
                                tasksVm.loadProjectDetail(clicked.id)
                            } else {
                                tasksVm.loadTaskDetail(clicked.id)
                            }
                        },
                        onStartTimer = { clicked ->
                            timerVm.selectTask(clicked.id, clicked.name)
                            timerVm.startTimer()
                        }
                    )
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }

        // Bottom sheets
        if (tasksState.showCreateSheet) {
            CreateBottomSheet(
                mode = tasksState.createMode,
                projectsFast = tasksState.projectsFast,
                onCreateTask = { name, desc, due, projId -> tasksVm.createTask(name, desc, due, projId) },
                onCreateProject = { name, desc, due, parentId -> tasksVm.createProject(name, desc, due, parentId) },
                onModeChange = { tasksVm.showCreate(it) },
                onDismiss = { tasksVm.dismissCreate() }
            )
        }

        tasksState.selectedTask?.let { task ->
            TaskDetailSheet(
                task = task,
                onStart = { tasksVm.startTask(task.id) },
                onFinish = { tasksVm.finishTask(task.id) },
                onDelete = { tasksVm.deleteTask(task.id) },
                onStartTimer = {
                    timerVm.selectTask(task.id, task.name)
                    timerVm.startTimer()
                },
                onToggleTodo = { tasksVm.toggleTodo(it) },
                onCreateTodo = { tasksVm.createTodo(task.id, it) },
                onDeleteTodo = { tasksVm.deleteTodo(it) },
                onDismiss = { tasksVm.dismissTaskDetail() }
            )
        }

        tasksState.selectedProject?.let { project ->
            ProjectDetailSheet(
                project = project,
                onStart = { tasksVm.startProject(project.id) },
                onFinish = { tasksVm.finishProject(project.id) },
                onDelete = { tasksVm.deleteProject(project.id) },
                onDismiss = { tasksVm.dismissProjectDetail() }
            )
        }
    }
}

@Composable
private fun TaskDueDateRow(
    task: TaskByDueDate,
    onTap: () -> Unit,
    onPlay: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clickable { onTap() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(task.name, style = MaterialTheme.typography.bodyMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    task.projectName?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = AppColors.secondary)
                    }
                    task.dueAt?.let {
                        Text(it.take(10), style = MaterialTheme.typography.labelSmall, color = AppColors.muted)
                    }
                    StatusBadge(startedAt = task.startedAt, finishedAt = null)
                }
                if (task.timeSpent > 0) {
                    Text(
                        formatHours(task.timeSpent),
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.muted
                    )
                }
            }

            IconButton(onClick = onPlay, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Iniciar timer",
                    tint = AppColors.success
                )
            }
        }
    }
}
