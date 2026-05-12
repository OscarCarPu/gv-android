package com.gv.app.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.gv.app.domain.model.CreateTaskRequest
import com.gv.app.domain.model.ProjectListItem
import com.gv.app.domain.model.TaskFullResponse
import com.gv.app.domain.model.TodoResponse
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// ---------- Task detail ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailSheet(
    task: TaskFullResponse,
    onDismiss: () -> Unit,
    onSave: (Int, String, String?, String?, String, Int?, Int, (Boolean) -> Unit) -> Unit,
    onDelete: (Int) -> Unit,
    onStart: () -> Unit,
    onFinish: () -> Unit,
    onTimerStart: () -> Unit,
    onAddTodo: (Int, String) -> Unit,
    onToggleTodo: (Int, Int, Boolean) -> Unit,
    onDeleteTodo: (Int, Int) -> Unit,
    timerRunning: Boolean,
) {
    val spacing = LocalSpacing.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember(task.id) { mutableStateOf(task.name) }
    var description by remember(task.id) { mutableStateOf(task.description.orEmpty()) }
    var dueAt by remember(task.id) {
        mutableStateOf(parseIso(task.due_at)?.toLocalDate())
    }
    var taskType by remember(task.id) { mutableStateOf(task.task_type) }
    var recurrence by remember(task.id) { mutableStateOf(task.recurrence?.toString().orEmpty()) }
    var priority by remember(task.id) { mutableStateOf(task.priority) }
    var newTodoName by remember(task.id) { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }

    val started = !task.started_at.isNullOrBlank()
    val finished = !task.finished_at.isNullOrBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GvColors.BgLight,
        contentColor = GvColors.Text,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 740.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.xl, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Text(
                text = "Task detail",
                style = MaterialTheme.typography.titleLarge,
                color = GvColors.Text,
            )

            if (task.blocked) {
                BlockedHint()
            }

            GvField(
                label = "Name",
                value = name,
                onValueChange = { name = it; nameError = false },
                error = nameError,
            )

            GvField(
                label = "Description",
                value = description,
                onValueChange = { description = it },
                singleLine = false,
            )

            DateField(
                label = "Due date",
                value = dueAt,
                onChange = { dueAt = it },
                onClear = { dueAt = null },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                TypeChip("Standard", "standard", taskType, GvColors.Primary, { taskType = it }, Modifier.weight(1f))
                TypeChip("Continuous", "continuous", taskType, GvColors.Continuous, { taskType = it }, Modifier.weight(1f))
                TypeChip("Recurring", "recurring", taskType, GvColors.Recurring, { taskType = it }, Modifier.weight(1f))
            }

            if (taskType == "recurring") {
                GvField(
                    label = "Every (days)",
                    value = recurrence,
                    onValueChange = { recurrence = it.filter { ch -> ch.isDigit() } },
                    keyboardType = KeyboardType.Number,
                )
            }

            PrioritySelector(priority = priority, onChange = { priority = it })

            // Start/Finish row
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                if (!started) {
                    OutlinedButton(
                        onClick = onStart,
                        enabled = !task.blocked,
                        modifier = Modifier.weight(1f),
                    ) { Text("Start", color = GvColors.Primary) }
                } else if (!finished) {
                    OutlinedButton(
                        onClick = onFinish,
                        enabled = !task.blocked,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            if (task.task_type == "recurring") "Renew" else "Finish",
                            color = GvColors.Primary,
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(GvColors.Success.copy(alpha = 0.18f))
                            .border(1.dp, GvColors.Success.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Completed",
                            style = MaterialTheme.typography.labelLarge,
                            color = GvColors.Success,
                        )
                    }
                }
                Button(
                    onClick = onTimerStart,
                    enabled = !task.blocked,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GvColors.Primary,
                        contentColor = GvColors.Text,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (timerRunning) "Assign" else "Timer")
                }
            }

            // Time spent / status
            if (task.time_spent > 0 || started) {
                MetaRow(task = task)
            }

            // Deps badges (read-only)
            if (task.depends_on.isNotEmpty() || task.blocks.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    if (task.depends_on.isNotEmpty()) {
                        DepRow(label = "Depends on", deps = task.depends_on.map { it.name })
                    }
                    if (task.blocks.isNotEmpty()) {
                        DepRow(label = "Blocks", deps = task.blocks.map { it.name })
                    }
                }
            }

            // Todos
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(
                    text = "Todos",
                    style = MaterialTheme.typography.labelLarge,
                    color = GvColors.TextMuted,
                )
                task.todos.sortedBy { it.is_done }.forEach { todo ->
                    TodoRow(
                        todo = todo,
                        onToggle = { onToggleTodo(task.id, todo.id, !todo.is_done) },
                        onDelete = { onDeleteTodo(task.id, todo.id) },
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    OutlinedTextField(
                        value = newTodoName,
                        onValueChange = { newTodoName = it },
                        placeholder = { Text("New todo...", color = GvColors.TextMuted) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = GvColors.Text,
                            unfocusedTextColor = GvColors.Text,
                            focusedBorderColor = GvColors.Primary,
                            unfocusedBorderColor = GvColors.BorderLight,
                            cursorColor = GvColors.Primary,
                        ),
                    )
                    IconButton(
                        onClick = {
                            onAddTodo(task.id, newTodoName)
                            newTodoName = ""
                        },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add todo", tint = GvColors.Primary)
                    }
                }
            }

            // Save / Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                OutlinedButton(
                    onClick = { onDelete(task.id) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = GvColors.Danger, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Delete", color = GvColors.Danger)
                }
                Button(
                    onClick = {
                        if (name.isBlank()) {
                            nameError = true
                            return@Button
                        }
                        val dueIso = dueAt?.atTime(12, 0)?.let { localDateTimeToIsoUtc(it) }
                        val rec = recurrence.toIntOrNull()
                        onSave(
                            task.id,
                            name.trim(),
                            description.trim().ifEmpty { null },
                            dueIso,
                            taskType,
                            rec,
                            priority,
                        ) { /* close on success handled in VM trigger */ }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GvColors.Primary,
                        contentColor = GvColors.Text,
                    ),
                    modifier = Modifier.weight(1f),
                ) { Text("Save") }
            }
        }
    }
}

// ---------- Task create ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskCreateSheet(
    projects: List<ProjectListItem>,
    onDismiss: () -> Unit,
    onCreate: (CreateTaskRequest, startNow: Boolean) -> Unit,
) {
    val spacing = LocalSpacing.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var dueAt by remember { mutableStateOf<LocalDate?>(null) }
    var taskType by remember { mutableStateOf("standard") }
    var recurrence by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(3) }
    var projectId by remember { mutableStateOf<Int?>(null) }
    var startNow by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GvColors.BgLight,
        contentColor = GvColors.Text,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 660.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.xl, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Text(
                text = "New task",
                style = MaterialTheme.typography.titleLarge,
                color = GvColors.Text,
            )

            GvField(
                label = "Name",
                value = name,
                onValueChange = { name = it; nameError = false },
                error = nameError,
            )

            GvField(
                label = "Description",
                value = description,
                onValueChange = { description = it },
                singleLine = false,
            )

            DateField(
                label = "Due date",
                value = dueAt,
                onChange = { dueAt = it },
                onClear = { dueAt = null },
            )

            ProjectDropdown(
                projects = projects,
                selected = projectId,
                onSelect = { projectId = it },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                TypeChip("Standard", "standard", taskType, GvColors.Primary, { taskType = it }, Modifier.weight(1f))
                TypeChip("Continuous", "continuous", taskType, GvColors.Continuous, { taskType = it }, Modifier.weight(1f))
                TypeChip("Recurring", "recurring", taskType, GvColors.Recurring, { taskType = it }, Modifier.weight(1f))
            }

            if (taskType == "recurring") {
                GvField(
                    label = "Every (days)",
                    value = recurrence,
                    onValueChange = { recurrence = it.filter { ch -> ch.isDigit() } },
                    keyboardType = KeyboardType.Number,
                )
            }

            PrioritySelector(priority = priority, onChange = { priority = it })

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = startNow,
                    onCheckedChange = { startNow = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = GvColors.Primary,
                        uncheckedColor = GvColors.TextMuted,
                        checkmarkColor = GvColors.Text,
                    ),
                )
                Text("Start now", color = GvColors.Text)
            }

            Button(
                onClick = {
                    if (name.isBlank()) { nameError = true; return@Button }
                    val dueIso = dueAt?.atTime(12, 0)?.let { localDateTimeToIsoUtc(it) }
                    val rec = recurrence.toIntOrNull()
                    onCreate(
                        CreateTaskRequest(
                            project_id = projectId,
                            name = name.trim(),
                            description = description.trim().ifEmpty { null },
                            due_at = dueIso,
                            task_type = if (taskType != "standard") taskType else null,
                            recurrence = if (taskType == "recurring") rec else null,
                            priority = if (priority != 3) priority else null,
                        ),
                        startNow,
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = GvColors.Primary,
                    contentColor = GvColors.Text,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Create") }
        }
    }
}

// ---------- Pieces ----------

@Composable
private fun BlockedHint() {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(GvColors.Danger.copy(alpha = 0.12f))
            .border(1.dp, GvColors.Danger.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Icon(Icons.Filled.Block, contentDescription = null, tint = GvColors.Danger, modifier = Modifier.size(16.dp))
        Text(
            text = "Blocked by a dependency",
            style = MaterialTheme.typography.labelMedium,
            color = GvColors.Danger,
        )
    }
}

@Composable
private fun MetaRow(task: TaskFullResponse) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.xl),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Status", style = MaterialTheme.typography.labelSmall, color = GvColors.TextMuted)
            Text(
                statusLabel(task.started_at, task.task_type, task.recurrence),
                style = MaterialTheme.typography.labelMedium,
                color = GvColors.Text,
            )
        }
        if (task.time_spent > 0) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Time", style = MaterialTheme.typography.labelSmall, color = GvColors.TextMuted)
                Text(
                    formatDurationShort(task.time_spent),
                    style = MaterialTheme.typography.labelMedium,
                    color = GvColors.Text,
                )
            }
        }
    }
}

@Composable
private fun DepRow(label: String, deps: List<String>) {
    val spacing = LocalSpacing.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelMedium,
            color = GvColors.TextMuted,
        )
        deps.forEach { name ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(GvColors.Bg)
                    .border(1.dp, GvColors.BorderLight, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(name, style = MaterialTheme.typography.labelSmall, color = GvColors.Text)
            }
        }
    }
}

@Composable
private fun TodoRow(todo: TodoResponse, onToggle: () -> Unit, onDelete: () -> Unit) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(GvColors.Bg)
            .border(1.dp, GvColors.BorderLight, RoundedCornerShape(8.dp))
            .padding(horizontal = spacing.sm, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = todo.is_done,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = GvColors.Primary,
                uncheckedColor = GvColors.TextMuted,
                checkmarkColor = GvColors.Text,
            ),
        )
        Text(
            text = todo.name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (todo.is_done) GvColors.TextMuted else GvColors.Text,
            textDecoration = if (todo.is_done) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Delete todo", tint = GvColors.TextMuted, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun TypeChip(
    label: String,
    value: String,
    selected: String,
    accent: Color,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = selected == value
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) accent.copy(alpha = 0.18f) else GvColors.Bg)
            .border(
                1.dp,
                if (active) accent else GvColors.BorderLight,
                RoundedCornerShape(8.dp),
            )
            .clickable { onChange(value) }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) accent else GvColors.TextMuted,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun PrioritySelector(priority: Int, onChange: (Int) -> Unit) {
    val spacing = LocalSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(
            text = "Priority",
            style = MaterialTheme.typography.labelMedium,
            color = GvColors.TextMuted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            (1..5).forEach { p ->
                val active = priority == p
                val color = priorityColor(p)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) color.copy(alpha = 0.2f) else GvColors.Bg)
                        .border(
                            1.dp,
                            if (active) color else GvColors.BorderLight,
                            RoundedCornerShape(8.dp),
                        )
                        .clickable { onChange(p) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "P$p",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) color else GvColors.TextMuted,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun GvField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: Boolean = false,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error,
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = GvColors.Text,
            unfocusedTextColor = GvColors.Text,
            focusedBorderColor = GvColors.Primary,
            unfocusedBorderColor = GvColors.BorderLight,
            errorBorderColor = GvColors.Danger,
            focusedLabelColor = GvColors.Primary,
            unfocusedLabelColor = GvColors.TextMuted,
            errorLabelColor = GvColors.Danger,
            cursorColor = GvColors.Primary,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(
    label: String,
    value: LocalDate?,
    onChange: (LocalDate) -> Unit,
    onClear: () -> Unit,
) {
    val spacing = LocalSpacing.current
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = GvColors.TextMuted,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(GvColors.Bg)
                .border(1.dp, GvColors.BorderLight, RoundedCornerShape(8.dp))
                .clickable { open = true }
                .padding(horizontal = spacing.lg, vertical = spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = value?.toString() ?: "Not set",
                style = MaterialTheme.typography.bodyMedium,
                color = if (value != null) GvColors.Text else GvColors.TextMuted,
                modifier = Modifier.weight(1f),
            )
            if (value != null) {
                IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear", tint = GvColors.TextMuted, modifier = Modifier.size(16.dp))
                }
            }
        }
    }

    if (open) {
        val initMillis = (value ?: LocalDate.now()).atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initMillis)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val d = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                        onChange(d)
                    }
                    open = false
                }) {
                    Text("OK", color = GvColors.Primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) {
                    Text("Cancel", color = GvColors.TextMuted)
                }
            },
            colors = DatePickerDefaults.colors(containerColor = GvColors.BgLight),
        ) {
            DatePicker(state = state)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectDropdown(
    projects: List<ProjectListItem>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
) {
    val spacing = LocalSpacing.current
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(
            text = "Project",
            style = MaterialTheme.typography.labelMedium,
            color = GvColors.TextMuted,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(GvColors.Bg)
                .border(1.dp, GvColors.BorderLight, RoundedCornerShape(8.dp))
                .clickable { open = true }
                .padding(horizontal = spacing.lg, vertical = spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = projects.firstOrNull { it.id == selected }?.name ?: "Standalone",
                style = MaterialTheme.typography.bodyMedium,
                color = GvColors.Text,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = GvColors.TextMuted)
        }
    }

    if (open) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { open = false },
            sheetState = sheetState,
            containerColor = GvColors.BgLight,
            contentColor = GvColors.Text,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .padding(horizontal = spacing.xl, vertical = spacing.md),
            ) {
                Text(
                    "Project",
                    style = MaterialTheme.typography.titleMedium,
                    color = GvColors.Text,
                    modifier = Modifier.padding(bottom = spacing.md),
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = spacing.xs),
                ) {
                    item(key = "none") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(null); open = false }
                                .padding(vertical = spacing.lg, horizontal = spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Standalone (no project)", color = GvColors.Text)
                            if (selected == null) {
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.Filled.Check, contentDescription = null, tint = GvColors.Primary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    items(projects, key = { it.id }) { p ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(p.id); open = false }
                                .padding(vertical = spacing.lg, horizontal = spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(p.name, color = GvColors.Text, modifier = Modifier.weight(1f))
                            if (selected == p.id) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = GvColors.Primary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
