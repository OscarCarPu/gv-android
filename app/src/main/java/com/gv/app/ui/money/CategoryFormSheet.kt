package com.gv.app.ui.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.gv.app.domain.model.Category
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing

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
            val banned = collectDescendantIds(sameType, category.id)
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
                selectedLabel = parentOptions.firstOrNull { it.id == parentId }?.name
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
