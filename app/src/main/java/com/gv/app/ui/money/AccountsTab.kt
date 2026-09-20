package com.gv.app.ui.money

import com.gv.app.ui.common.EmptyHint
import com.gv.app.ui.common.SmallButton
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.gv.app.domain.model.Account
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing

/** Accounts as on the web: a header with **New**, one row each with edit and delete. Delete is immediate. */
@Composable
internal fun AccountsTab(
    accounts: List<Account>,
    onNew: () -> Unit,
    onEdit: (Account) -> Unit,
    onDelete: (Account) -> Unit,
) {
    val spacing = LocalSpacing.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        item(key = "accounts-header") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Accounts", style = MaterialTheme.typography.titleMedium, color = GvColors.Text, modifier = Modifier.weight(1f))
                SmallButton("New", onNew, icon = Icons.Filled.Add)
            }
        }
        if (accounts.isEmpty()) item(key = "accounts-empty") { EmptyHint("No accounts") }
        items(items = accounts, key = { it.id }) { account ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(GvColors.BgLight)
                    .border(1.dp, GvColors.BorderLight, RoundedCornerShape(10.dp))
                    .clickable { onEdit(account) }
                    .padding(horizontal = spacing.lg, vertical = spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(account.name, style = MaterialTheme.typography.bodyLarge, color = GvColors.Text)
                    Text(
                        formatMoney(account.total),
                        style = MaterialTheme.typography.labelMedium,
                        color = if ((account.total.toDoubleOrNull() ?: 0.0) < 0) GvColors.Danger else GvColors.TextMuted,
                    )
                }
                IconButton(onClick = { onEdit(account) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = GvColors.TextMuted)
                }
                IconButton(onClick = { onDelete(account) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = GvColors.TextMuted)
                }
            }
        }
    }
}
