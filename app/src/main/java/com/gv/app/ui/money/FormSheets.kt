package com.gv.app.ui.money

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gv.app.domain.model.Account
import com.gv.app.domain.model.Category
import com.gv.app.domain.model.CreateTransactionRequest
import com.gv.app.domain.model.Transaction
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---------- Transaction ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionFormSheet(
    transaction: Transaction?,
    accounts: List<Account>,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (Int?, CreateTransactionRequest) -> Unit,
) {
    val spacing = LocalSpacing.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var type by remember { mutableStateOf(transaction?.type ?: "expense") }
    var amount by remember { mutableStateOf(transaction?.amount ?: "") }
    var accountId by remember { mutableStateOf(transaction?.account_id ?: accounts.firstOrNull()?.id) }
    var toAccountId by remember { mutableStateOf(transaction?.to_account_id) }
    var categoryId by remember { mutableStateOf(transaction?.category_id) }
    var description by remember { mutableStateOf(transaction?.description ?: "") }
    var occurredAt by remember {
        mutableStateOf(
            transaction?.occurred_at?.let { parseIsoToLocal(it) } ?: LocalDateTime.now(),
        )
    }
    var amountError by remember { mutableStateOf(false) }
    var accountError by remember { mutableStateOf(false) }
    var categoryError by remember { mutableStateOf(false) }
    var toAccountError by remember { mutableStateOf(false) }

    LaunchedEffect(type) {
        if (type != "transfer") toAccountId = null
    }

    val categoryOptions = remember(categories, type) {
        buildCategoryOptions(categories.filter { it.type == type })
    }
    LaunchedEffect(categoryOptions) {
        if (categoryId != null && categoryOptions.none { it.id == categoryId }) {
            categoryId = null
        }
    }

    val toAccountOptions = remember(accounts, accountId) {
        accounts.filter { it.id != accountId }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GvColors.BgLight,
        contentColor = GvColors.Text,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 700.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.xl, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Text(
                text = if (transaction != null) "Edit transaction" else "New transaction",
                style = MaterialTheme.typography.titleLarge,
                color = GvColors.Text,
            )

            TypeSelector(type = type, onChange = { type = it })

            GvTextField(
                label = "Amount",
                value = amount,
                onValueChange = { amount = it; amountError = false },
                error = amountError,
                keyboardType = KeyboardType.Decimal,
            )

            DateField(
                label = "Date",
                value = occurredAt,
                onChange = { occurredAt = it },
            )

            DropdownField(
                label = if (type == "transfer") "From account" else "Account",
                selectedLabel = accounts.firstOrNull { it.id == accountId }?.name ?: "Select account",
                error = accountError,
                items = accounts.map { it.id to it.name },
                onSelect = { id -> accountId = id; accountError = false },
            )

            if (type == "transfer") {
                DropdownField(
                    label = "To account",
                    selectedLabel = toAccountOptions.firstOrNull { it.id == toAccountId }?.name
                        ?: "Select destination",
                    error = toAccountError,
                    items = toAccountOptions.map { it.id to it.name },
                    onSelect = { id -> toAccountId = id; toAccountError = false },
                )
            }

            DropdownField(
                label = "Category",
                selectedLabel = categoryOptions.firstOrNull { it.id == categoryId }?.label
                    ?: "Select category",
                error = categoryError,
                items = categoryOptions.map { it.id to it.label },
                onSelect = { id -> categoryId = id; categoryError = false },
            )

            GvTextField(
                label = "Description",
                value = description,
                onValueChange = { description = it },
                singleLine = false,
            )

            Button(
                onClick = {
                    val amt = amount.toDoubleOrNull() ?: 0.0
                    amountError = amt <= 0.0
                    accountError = accountId == null
                    categoryError = categoryId == null
                    toAccountError = type == "transfer" &&
                        (toAccountId == null || toAccountId == accountId)
                    if (amountError || accountError || categoryError || toAccountError) {
                        return@Button
                    }
                    val req = CreateTransactionRequest(
                        type = type,
                        amount = String.format(Locale.ROOT, "%.2f", amt),
                        account_id = accountId!!,
                        to_account_id = if (type == "transfer") toAccountId else null,
                        category_id = categoryId,
                        description = description.trim().ifEmpty { null },
                        occurred_at = formatLocalToIso(occurredAt),
                    )
                    onSave(transaction?.id, req)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = GvColors.Primary,
                    contentColor = GvColors.Text,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (transaction != null) "Save" else "Create")
            }
        }
    }
}

// ---------- Account ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountFormSheet(
    initialName: String?,
    isEdit: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val spacing = LocalSpacing.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(initialName ?: "") }
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
                .padding(horizontal = spacing.xl, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Text(
                text = if (isEdit) "Edit account" else "New account",
                style = MaterialTheme.typography.titleLarge,
                color = GvColors.Text,
            )
            GvTextField(
                label = "Name",
                value = name,
                onValueChange = { name = it; nameError = false },
                error = nameError,
            )
            Button(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isEmpty()) {
                        nameError = true
                        return@Button
                    }
                    onSave(trimmed)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = GvColors.Primary,
                    contentColor = GvColors.Text,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isEdit) "Save" else "Create")
            }
        }
    }
}

// ---------- Category ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryFormSheet(
    category: Category?,
    allCategories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (name: String, type: String, parentId: Int?) -> Unit,
) {
    val spacing = LocalSpacing.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(category?.name ?: "") }
    var type by remember { mutableStateOf(category?.type ?: "expense") }
    var parentId by remember { mutableStateOf(category?.parent_id) }
    var nameError by remember { mutableStateOf(false) }

    val parentOptions = remember(allCategories, type, category) {
        val sameType = allCategories.filter { it.type == type }
        if (category == null) {
            buildCategoryOptions(sameType)
        } else {
            val banned = mutableSetOf(category.id)
            var added = true
            while (added) {
                added = false
                for (c in sameType) {
                    val p = c.parent_id
                    if (p != null && p in banned && c.id !in banned) {
                        banned.add(c.id)
                        added = true
                    }
                }
            }
            buildCategoryOptions(sameType.filter { it.id !in banned })
        }
    }

    LaunchedEffect(parentOptions) {
        if (parentId != null && parentOptions.none { it.id == parentId }) {
            parentId = null
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GvColors.BgLight,
        contentColor = GvColors.Text,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.xl, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Text(
                text = if (category != null) "Edit category" else "New category",
                style = MaterialTheme.typography.titleLarge,
                color = GvColors.Text,
            )

            TypeSelector(type = type, onChange = { type = it })

            GvTextField(
                label = "Name",
                value = name,
                onValueChange = { name = it; nameError = false },
                error = nameError,
            )

            DropdownField(
                label = "Parent category",
                selectedLabel = parentOptions.firstOrNull { it.id == parentId }?.label
                    ?: "No parent",
                error = false,
                items = listOf<Pair<Int?, String>>(null to "No parent") +
                    parentOptions.map { it.id as Int? to it.label },
                onSelect = { id -> parentId = id },
            )

            Button(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isEmpty()) {
                        nameError = true
                        return@Button
                    }
                    onSave(trimmed, type, parentId)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = GvColors.Primary,
                    contentColor = GvColors.Text,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (category != null) "Save" else "Create")
            }
        }
    }
}

// ---------- Pieces ----------

@Composable
private fun TypeSelector(type: String, onChange: (String) -> Unit) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        TypeChip("Income", "income", type, GvColors.Success, onChange, Modifier.weight(1f))
        TypeChip("Expense", "expense", type, GvColors.Danger, onChange, Modifier.weight(1f))
        TypeChip("Transfer", "transfer", type, GvColors.Secondary, onChange, Modifier.weight(1f))
    }
}

@Composable
private fun TypeChip(
    label: String,
    value: String,
    selected: String,
    accent: androidx.compose.ui.graphics.Color,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isActive = selected == value
    val bg = if (isActive) accent.copy(alpha = 0.18f) else GvColors.Bg
    val border = if (isActive) accent else GvColors.BorderLight
    val textColor = if (isActive) accent else GvColors.TextMuted
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .clickable { onChange(value) }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
fun GvTextField(
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
        singleLine = singleLine,
        isError = error,
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

@Composable
fun <T> DropdownField(
    label: String,
    selectedLabel: String,
    error: Boolean,
    items: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
) {
    val spacing = LocalSpacing.current
    var open by remember { mutableStateOf(false) }
    val borderColor = if (error) GvColors.Danger else GvColors.BorderLight

    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (error) GvColors.Danger else GvColors.TextMuted,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(GvColors.Bg)
                .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                .clickable { open = true }
                .padding(horizontal = spacing.lg, vertical = spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selectedLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = GvColors.Text,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = GvColors.TextMuted,
            )
        }
    }

    if (open) {
        DropdownPicker(
            title = label,
            items = items,
            onSelect = { onSelect(it); open = false },
            onDismiss = { open = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> DropdownPicker(
    title: String,
    items: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = GvColors.Text,
                modifier = Modifier.padding(bottom = spacing.md),
            )
            if (items.isEmpty()) {
                Text(
                    text = "No options",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GvColors.TextMuted,
                    modifier = Modifier.padding(vertical = spacing.lg),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = spacing.xs),
                ) {
                    items(items) { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(value) }
                                .padding(vertical = spacing.lg, horizontal = spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = GvColors.Text,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(
    label: String,
    value: LocalDateTime,
    onChange: (LocalDateTime) -> Unit,
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
                text = value.format(DateDisplayFormatter),
                style = MaterialTheme.typography.bodyMedium,
                color = GvColors.Text,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (open) {
        val initialMillis = value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val newDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        onChange(LocalDateTime.of(newDate, value.toLocalTime()))
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
            colors = androidx.compose.material3.DatePickerDefaults.colors(
                containerColor = GvColors.BgLight,
            ),
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private val DateDisplayFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.UK)
private val IsoFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT)

private fun parseIsoToLocal(iso: String): LocalDateTime {
    return try {
        Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDateTime()
    } catch (_: Exception) {
        try {
            LocalDateTime.parse(iso.substringBefore('Z'))
        } catch (_: Exception) {
            LocalDateTime.now()
        }
    }
}

private fun formatLocalToIso(local: LocalDateTime): String {
    return local.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneId.of("UTC"))
        .toLocalDateTime().format(IsoFormatter)
}
