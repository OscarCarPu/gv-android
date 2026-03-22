package com.gv.app.ui.habits

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.gv.app.AppColors
import com.gv.app.domain.model.CreateHabitRequest
import com.gv.app.domain.model.Habit
import com.gv.app.ui.components.FrequencyBadge
import com.gv.app.ui.components.TargetProgressBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(
    vm: HabitsViewModel = viewModel(),
    openWizard: Boolean = false
) {
    val uiState     by vm.uiState.collectAsStateWithLifecycle()
    val displayDate by vm.displayDate.collectAsStateWithLifecycle()
    val isToday     by vm.isToday.collectAsStateWithLifecycle()
    val showCreate  by vm.showCreateSheet.collectAsStateWithLifecycle()

    var wizardActive by remember { mutableStateOf(false) }

    LaunchedEffect(openWizard) {
        if (openWizard) wizardActive = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hábitos") },
                actions = {
                    val habits = (uiState as? HabitsUiState.Success)?.habits
                    if (!habits.isNullOrEmpty()) {
                        IconButton(onClick = { wizardActive = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Registrar todos")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { vm.showCreateSheet() }) {
                Icon(Icons.Filled.Add, contentDescription = "Crear hábito")
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

        if (showCreate) {
            CreateHabitSheet(
                onCreate = { vm.createHabit(it) },
                onDismiss = { vm.dismissCreateSheet() }
            )
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
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Día anterior")
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = spacedBy(4.dp)
        ) {
            Text(displayDate, style = MaterialTheme.typography.titleMedium)
            if (!isToday) {
                IconButton(onClick = onToday) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Volver a hoy")
                }
            }
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Día siguiente")
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
        Button(onClick = onRetry) { Text("Reintentar") }
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
                onSet = { value -> vm.setHabitValue(habit, value) },
                onDelete = { vm.deleteHabit(habit.id) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HabitRow(
    habit: Habit,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    onSet: (Double) -> Unit,
    onDelete: () -> Unit
) {
    var editing by remember(habit.id) { mutableStateOf(false) }
    var input   by remember(habit.id, habit.logValue) {
        mutableStateOf(
            habit.logValue?.toBigDecimal()?.stripTrailingZeros()?.toPlainString() ?: ""
        )
    }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = { showDeleteConfirm = true }
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = spacedBy(8.dp)
                    ) {
                        Text(habit.name, style = MaterialTheme.typography.titleMedium)
                        FrequencyBadge(habit.frequency)
                    }
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
                            modifier = Modifier
                                .focusRequester(focusRequester)
                                .padding(end = 8.dp)
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
                            Icon(Icons.Filled.Check, contentDescription = "Confirmar")
                        }
                        IconButton(onClick = { editing = false }) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancelar")
                        }
                    } else {
                        OutlinedButton(
                            onClick = { input = ""; editing = true },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text("Set", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            // Progress bar
            if (habit.targetMin != null || habit.targetMax != null) {
                Spacer(Modifier.height(6.dp))
                TargetProgressBar(
                    current = habit.periodValue ?: habit.logValue ?: 0.0,
                    targetMin = habit.targetMin,
                    targetMax = habit.targetMax
                )
            }

            // Streak
            if ((habit.currentStreak ?: 0) > 0 || (habit.longestStreak ?: 0) > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Racha: ${habit.currentStreak ?: 0} | Mejor: ${habit.longestStreak ?: 0}",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.muted
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Eliminar hábito") },
            text = { Text("¿Eliminar \"${habit.name}\"?") },
            confirmButton = {
                Button(
                    onClick = { onDelete(); showDeleteConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.danger)
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancelar") }
            }
        )
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
            Text("Registrar (${step + 1} / ${habits.size}): ${habit.name}")
        },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Valor") },
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
                Text(if (isLast) "Listo" else "Siguiente")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = spacedBy(4.dp)) {
                if (!isLast) {
                    TextButton(onClick = { step++ }) { Text("Saltar") }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateHabitSheet(
    onCreate: (CreateHabitRequest) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        var name by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var frequency by remember { mutableStateOf("daily") }
        var targetMin by remember { mutableStateOf("") }
        var targetMax by remember { mutableStateOf("") }
        var recordingRequired by remember { mutableStateOf(true) }

        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = spacedBy(12.dp)
        ) {
            Text("Nuevo hábito", style = MaterialTheme.typography.titleLarge)

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
                modifier = Modifier.fillMaxWidth()
            )

            // Frequency
            Row(horizontalArrangement = spacedBy(8.dp)) {
                listOf("daily" to "Diario", "weekly" to "Semanal", "monthly" to "Mensual").forEach { (value, label) ->
                    FilterChip(
                        selected = frequency == value,
                        onClick = { frequency = value },
                        label = { Text(label) }
                    )
                }
            }

            Row(horizontalArrangement = spacedBy(8.dp)) {
                OutlinedTextField(
                    value = targetMin,
                    onValueChange = { targetMin = it },
                    label = { Text("Mínimo") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = targetMax,
                    onValueChange = { targetMax = it },
                    label = { Text("Máximo") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = spacedBy(8.dp)
            ) {
                Text("Registro requerido", modifier = Modifier.weight(1f))
                Switch(checked = recordingRequired, onCheckedChange = { recordingRequired = it })
            }

            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onCreate(
                            CreateHabitRequest(
                                name = name,
                                description = description.ifBlank { null },
                                frequency = frequency,
                                targetMin = targetMin.toDoubleOrNull(),
                                targetMax = targetMax.toDoubleOrNull(),
                                recordingRequired = recordingRequired
                            )
                        )
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
