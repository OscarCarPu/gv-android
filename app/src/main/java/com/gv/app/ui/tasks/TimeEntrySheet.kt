package com.gv.app.ui.tasks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.gv.app.domain.model.TaskOption
import com.gv.app.domain.model.TimeEntryWithTaskResponse
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing
import java.time.LocalDate

/**
 * Corrects one finished time entry — the same editor the agenda uses, opened straight from a
 * row of what-I-did in the plan. A running entry is never opened here: it has a timer panel,
 * and closing it by giving it an end time is not something a tap on a row should do.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeEntrySheet(entry: TimeEntryWithTaskResponse, vm: TasksViewModel, onDismiss: () -> Unit) {
    val spacing = LocalSpacing.current
    var options by remember { mutableStateOf<List<TaskOption>>(emptyList()) }
    LaunchedEffect(Unit) { vm.loadTaskOptions { options = it } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = GvColors.BgLight,
        contentColor = GvColors.Text,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = spacing.xl, vertical = spacing.md)) {
            EntryEditor(
                target = EditorTarget.Existing(entry),
                day = parseIso(entry.started_at)?.toLocalDate() ?: LocalDate.now(),
                taskOptions = options,
                onBack = onDismiss,
                onSave = { taskId, startIso, endIso, comment ->
                    vm.editEntry(entry.id, taskId, startIso, endIso, comment)
                    onDismiss()
                },
                onDelete = {
                    vm.deleteEntry(entry.id)
                    onDismiss()
                },
            )
        }
    }
}
