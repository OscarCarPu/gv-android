package com.gv.app.ui.habits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gv.app.domain.model.Habit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(vm: HabitsViewModel = viewModel()) {
    val uiState     by vm.uiState.collectAsStateWithLifecycle()
    val displayDate by vm.displayDate.collectAsStateWithLifecycle()
    val isToday     by vm.isToday.collectAsStateWithLifecycle()

    var wizardActive by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Habits") }) },
        floatingActionButton = {
            val habits = (uiState as? HabitsUiState.Success)?.habits
            if (!habits.isNullOrEmpty()) {
                FloatingActionButton(onClick = { wizardActive = true }) {
                    Icon(Icons.Filled.Edit, contentDescription = "Set All")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            DateNavBar(
                displayDate = displayDate,
                isToday = isToday,
                onPrevious = vm::previousDay,
                onNext = vm::nextDay,
                onToday = vm::returnToday
            )

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                when (val state = uiState) {
                    is HabitsUiState.Loading -> CircularProgressIndicator()
                    is HabitsUiState.Error   -> ErrorState(state.message, vm::loadHabits)
                    is HabitsUiState.Success -> HabitList(state.habits, vm)
                }
            }
        }

        if (wizardActive) {
            val habits = (uiState as? HabitsUiState.Success)?.habits
            if (!habits.isNullOrEmpty()) {
                SetWizardDialog(
                    habits = habits,
                    onSet = { habit, value -> vm.setHabitValue(habit, value) },
                    onDismiss = { wizardActive = false }
                )
            }
        }
    }
}

@Composable
private fun DateNavBar(
    displayDate: String,
    isToday: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous day")
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = spacedBy(4.dp)
        ) {
            Text(displayDate, style = MaterialTheme.typography.titleMedium)
            if (!isToday) {
                IconButton(onClick = onToday) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Return to today")
                }
            }
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next day")
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = spacedBy(12.dp)
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun HabitList(habits: List<Habit>, vm: HabitsViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = spacedBy(8.dp),
        contentPadding = PaddingValues(12.dp)
    ) {
        items(habits, key = { it.id }) { habit ->
            HabitRow(
                habit = habit,
                onDecrement = { vm.decrementHabit(habit) },
                onIncrement = { vm.incrementHabit(habit) },
                onSet = { value -> vm.setHabitValue(habit, value) }
            )
        }
    }
}

@Composable
private fun HabitRow(
    habit: Habit,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    onSet: (Double) -> Unit
) {
    var editing by remember(habit.id) { mutableStateOf(false) }
    var input   by remember(habit.id, habit.logValue) {
        mutableStateOf(
            habit.logValue?.toBigDecimal()?.stripTrailingZeros()?.toPlainString() ?: ""
        )
    }
    val focusRequester = remember { FocusRequester() }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(habit.name, style = MaterialTheme.typography.titleMedium)
                if (editing) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            input.toDoubleOrNull()?.let { onSet(it) }
                            editing = false
                        }),
                        modifier = Modifier.focusRequester(focusRequester)
                    )
                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
                } else {
                    val valueText = habit.logValue
                        ?.toBigDecimal()?.stripTrailingZeros()?.toPlainString()
                        ?: "—"
                    Text(valueText, style = MaterialTheme.typography.bodySmall)
                }
            }

            Row(horizontalArrangement = spacedBy(6.dp)) {
                FilledTonalButton(onClick = onDecrement) { Text("-1") }
                FilledTonalButton(onClick = onIncrement) { Text("+1") }
                if (editing) {
                    IconButton(onClick = {
                        input.toDoubleOrNull()?.let { onSet(it) }
                        editing = false
                    }) {
                        Icon(Icons.Filled.Check, contentDescription = "Confirm")
                    }
                    IconButton(onClick = { editing = false }) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel")
                    }
                } else {
                    OutlinedButton(
                        onClick = { editing = true },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text("Set", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun SetWizardDialog(
    habits: List<Habit>,
    onSet: (Habit, Double) -> Unit,
    onDismiss: () -> Unit
) {
    var step  by remember { mutableStateOf(0) }
    val habit  = habits[step]
    val isLast = step == habits.lastIndex

    var input by remember(step) {
        mutableStateOf(
            habit.logValue?.toBigDecimal()?.stripTrailingZeros()?.toPlainString() ?: ""
        )
    }
    val focusRequester = remember(step) { FocusRequester() }

    fun commitAndAdvance() {
        input.toDoubleOrNull()?.let { onSet(habit, it) }
        if (isLast) onDismiss() else step++
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Set All (${step + 1} / ${habits.size}): ${habit.name}")
        },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Value") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = if (isLast) ImeAction.Done else ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { commitAndAdvance() },
                    onDone = { commitAndAdvance() }
                ),
                modifier = Modifier.focusRequester(focusRequester)
            )
            LaunchedEffect(step) { focusRequester.requestFocus() }
        },
        confirmButton = {
            TextButton(onClick = { commitAndAdvance() }) {
                Text(if (isLast) "Done" else "Next")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = spacedBy(4.dp)) {
                if (!isLast) {
                    TextButton(onClick = { step++ }) { Text("Skip") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
