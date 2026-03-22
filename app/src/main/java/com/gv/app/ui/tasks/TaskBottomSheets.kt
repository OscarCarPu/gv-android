package com.gv.app.ui.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gv.app.AppColors
import com.gv.app.domain.model.ProjectDetail
import com.gv.app.domain.model.ProjectFast
import com.gv.app.domain.model.TaskFull
import com.gv.app.domain.model.Todo
import com.gv.app.ui.components.StatusBadge
import com.gv.app.ui.components.formatHours

// ── Create Bottom Sheet ─────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateBottomSheet(
    mode: CreateMode,
    projectsFast: List<ProjectFast>,
    onCreateTask: (name: String, description: String?, dueAt: String?, projectId: Int?) -> Unit,
    onCreateProject: (name: String, description: String?, dueAt: String?, parentId: Int?) -> Unit,
    onModeChange: (CreateMode) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        var name by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var dueAt by remember { mutableStateOf("") }
        var selectedProjectId by remember { mutableStateOf<Int?>(null) }
        var projectDropdownExpanded by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Mode toggle
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mode == CreateMode.TASK,
                    onClick = { onModeChange(CreateMode.TASK) },
                    label = { Text("Tarea") }
                )
                FilterChip(
                    selected = mode == CreateMode.PROJECT,
                    onClick = { onModeChange(CreateMode.PROJECT) },
                    label = { Text("Proyecto") }
                )
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Descripción") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            OutlinedTextField(
                value = dueAt,
                onValueChange = { dueAt = it },
                label = { Text("Fecha límite (YYYY-MM-DD)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Project/Parent selector
            if (projectsFast.isNotEmpty()) {
                val label = if (mode == CreateMode.TASK) "Proyecto" else "Proyecto padre"
                ExposedDropdownMenuBox(
                    expanded = projectDropdownExpanded,
                    onExpandedChange = { projectDropdownExpanded = it }
                ) {
                    val selectedName = projectsFast.find { it.id == selectedProjectId }?.name ?: "Ninguno"
                    OutlinedTextField(
                        value = selectedName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(label) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = projectDropdownExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = projectDropdownExpanded,
                        onDismissRequest = { projectDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Ninguno") },
                            onClick = { selectedProjectId = null; projectDropdownExpanded = false }
                        )
                        projectsFast.forEach { project ->
                            DropdownMenuItem(
                                text = { Text(project.name) },
                                onClick = { selectedProjectId = project.id; projectDropdownExpanded = false }
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val desc = description.ifBlank { null }
                    val due = dueAt.ifBlank { null }
                    if (name.isNotBlank()) {
                        if (mode == CreateMode.TASK) {
                            onCreateTask(name, desc, due, selectedProjectId)
                        } else {
                            onCreateProject(name, desc, due, selectedProjectId)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank()
            ) {
                Text("Crear")
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Task Detail Sheet ───────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailSheet(
    task: TaskFull,
    onStart: () -> Unit,
    onFinish: () -> Unit,
    onDelete: () -> Unit,
    onStartTimer: () -> Unit,
    onToggleTodo: (Todo) -> Unit,
    onCreateTodo: (String) -> Unit,
    onDeleteTodo: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var newTodoName by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Text(task.name, style = MaterialTheme.typography.titleLarge)
            task.description?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = AppColors.muted)
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(startedAt = task.startedAt, finishedAt = task.finishedAt)
                if (task.timeSpent > 0) {
                    Text(
                        formatHours(task.timeSpent),
                        style = MaterialTheme.typography.labelMedium,
                        color = AppColors.muted
                    )
                }
                task.dueAt?.let {
                    Text("Vence: ${it.take(10)}", style = MaterialTheme.typography.labelSmall, color = AppColors.muted)
                }
            }

            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (task.startedAt == null) {
                    FilledTonalButton(onClick = onStart) { Text("Iniciar") }
                } else if (task.finishedAt == null) {
                    FilledTonalButton(onClick = onFinish) { Text("Terminar") }
                }
                if (task.finishedAt == null) {
                    OutlinedButton(onClick = onStartTimer) { Text("Timer") }
                }
            }

            HorizontalDivider()

            // Todos section
            Text("Pendientes", style = MaterialTheme.typography.titleMedium)

            task.todos.forEach { todo ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = todo.isDone,
                        onCheckedChange = { onToggleTodo(todo) }
                    )
                    Text(
                        todo.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    IconButton(onClick = { onDeleteTodo(todo.id) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "Eliminar", tint = AppColors.danger, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Add todo
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newTodoName,
                    onValueChange = { newTodoName = it },
                    label = { Text("Nuevo pendiente") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        if (newTodoName.isNotBlank()) {
                            onCreateTodo(newTodoName)
                            newTodoName = ""
                        }
                    }
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Agregar")
                }
            }

            HorizontalDivider()

            // Delete
            if (showDeleteConfirm) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("¿Eliminar esta tarea?", modifier = Modifier.weight(1f))
                    TextButton(onClick = { showDeleteConfirm = false }) { Text("No") }
                    Button(
                        onClick = onDelete,
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.danger)
                    ) { Text("Sí, eliminar") }
                }
            } else {
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = AppColors.danger)
                ) { Text("Eliminar tarea") }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Project Detail Sheet ────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailSheet(
    project: ProjectDetail,
    onStart: () -> Unit,
    onFinish: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDeleteConfirm by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(project.name, style = MaterialTheme.typography.titleLarge)
            project.description?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = AppColors.muted)
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(startedAt = project.startedAt, finishedAt = project.finishedAt)
                if (project.timeSpent > 0) {
                    Text(
                        formatHours(project.timeSpent),
                        style = MaterialTheme.typography.labelMedium,
                        color = AppColors.muted
                    )
                }
                project.dueAt?.let {
                    Text("Vence: ${it.take(10)}", style = MaterialTheme.typography.labelSmall, color = AppColors.muted)
                }
            }

            // Actions
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (project.startedAt == null) {
                    FilledTonalButton(onClick = onStart) { Text("Iniciar") }
                } else if (project.finishedAt == null) {
                    FilledTonalButton(onClick = onFinish) { Text("Terminar") }
                }
            }

            HorizontalDivider()

            // Delete
            if (showDeleteConfirm) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("¿Eliminar este proyecto?", modifier = Modifier.weight(1f))
                    TextButton(onClick = { showDeleteConfirm = false }) { Text("No") }
                    Button(
                        onClick = onDelete,
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.danger)
                    ) { Text("Sí, eliminar") }
                }
            } else {
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = AppColors.danger)
                ) { Text("Eliminar proyecto") }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
