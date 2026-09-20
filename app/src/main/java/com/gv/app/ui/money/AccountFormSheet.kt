package com.gv.app.ui.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.gv.app.domain.model.Account
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing

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
