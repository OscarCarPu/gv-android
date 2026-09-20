package com.gv.app.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gv.app.domain.model.TaskFastResponse
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing

/** Tasks matching [query] (case-insensitive substring of the name), grouped by project. */
internal fun groupPickerTasks(tasks: List<TaskFastResponse>, query: String): List<Pair<String, List<TaskFastResponse>>> {
    val q = query.trim().lowercase()
    val matching = if (q.isEmpty()) tasks else tasks.filter { it.name.lowercase().contains(q) }
    return matching
        .groupBy { it.project_id }
        .values
        .map { group -> (group.first().project_name ?: "No project") to group }
}

/**
 * The searchable task list behind the timer's pen button, the plan editor and the commitments
 * form — one picker for all three, as on the web. The list is read live each time it opens
 * (`list-fast` already scopes to unfinished tasks in active projects), and a failed load says
 * so instead of passing for an empty account.
 *
 * [onOpenDetail], when given, adds an "Open details" row for the current task.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskPickerSheet(
    load: suspend () -> PickerTasks,
    currentTaskId: Int?,
    onPick: (TaskFastResponse) -> Unit,
    onDismiss: () -> Unit,
    onOpenDetail: (() -> Unit)? = null,
) {
    val spacing = LocalSpacing.current
    var attempt by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    val loaded by produceState<PickerTasks>(PickerTasks.Loading, attempt) {
        value = PickerTasks.Loading
        value = load()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = GvColors.BgLight,
        contentColor = GvColors.Text,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            if (currentTaskId != null && onOpenDetail != null) {
                TextButton(onClick = onOpenDetail) {
                    Icon(Icons.Filled.Edit, contentDescription = null, tint = GvColors.Primary, modifier = Modifier.size(16.dp))
                    Text("  Open details", color = GvColors.Primary)
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search tasks…", color = GvColors.TextMuted) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = GvColors.TextMuted) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = GvColors.Text,
                    unfocusedTextColor = GvColors.Text,
                    focusedBorderColor = GvColors.Primary,
                    unfocusedBorderColor = GvColors.BorderLight,
                    cursorColor = GvColors.Primary,
                ),
            )
            Box(Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 460.dp)) {
                when (val state = loaded) {
                    PickerTasks.Loading -> CircularProgressIndicator(color = GvColors.Primary, modifier = Modifier.align(Alignment.Center))
                    PickerTasks.Failed -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Could not load tasks", color = GvColors.TextMuted)
                        TextButton(onClick = { attempt++ }) { Text("Retry", color = GvColors.Primary) }
                    }
                    is PickerTasks.Loaded -> {
                        val groups = groupPickerTasks(state.tasks, query)
                        if (groups.isEmpty()) {
                            Text("No tasks", color = GvColors.TextMuted, modifier = Modifier.align(Alignment.Center))
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                groups.forEach { (project, tasks) ->
                                    item(key = "g-$project") {
                                        Text(
                                            project.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = GvColors.TextMuted,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(top = spacing.md, bottom = spacing.xs),
                                        )
                                    }
                                    items(tasks, key = { it.id }) { task ->
                                        Text(
                                            text = task.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (task.id == currentTaskId) GvColors.Primary else GvColors.Text,
                                            fontWeight = if (task.id == currentTaskId) FontWeight.SemiBold else FontWeight.Normal,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(if (task.id == currentTaskId) GvColors.Primary.copy(alpha = 0.10f) else androidx.compose.ui.graphics.Color.Transparent)
                                                .clickable { onPick(task) }
                                                .padding(horizontal = spacing.md, vertical = spacing.md),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Box(Modifier.size(spacing.lg))
        }
    }
}
