package com.gv.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gv.app.domain.model.CalendarAccount
import com.gv.app.domain.model.GoogleCalendar
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing

/**
 * Which calendars are shown and synced, and which Google accounts they come from.
 *
 * The visibility control is the coloured dot, not a checkbox: a platform checkbox renders in the
 * platform's own colour and says nothing about which calendar it belongs to, while the dot is the
 * same colour the event chips are painted with. Filled means shown, hollow means hidden.
 *
 * Visibility and sync are both **server-side preferences**, so every device agrees on them —
 * neither is a local filter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarsSheet(
    calendars: List<GoogleCalendar>,
    vm: CalendarViewModel,
    onDismiss: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    var pendingDisconnect by remember { mutableStateOf<CalendarAccount?>(null) }

    // Reloaded on every resume, not just once: connecting an account happens in the browser,
    // so the only sign it worked is the list being re-read when this screen comes back.
    LifecycleResumeEffect(vm) {
        vm.loadAccounts()
        onPauseOrDispose { }
    }

    val grouped = remember(calendars) { calendars.groupBy { it.account_email } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = GvColors.BgLight,
        contentColor = GvColors.Text,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.xl)
                .padding(bottom = spacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Calendars", style = MaterialTheme.typography.titleMedium, color = GvColors.Text)
                TextButton(onClick = vm::syncNow) {
                    Text("Sync now", color = GvColors.Primary)
                }
            }

            if (calendars.isEmpty()) {
                Text(
                    text = "No calendars yet. Connect a Google account to mirror its calendars here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GvColors.TextMuted,
                )
            }

            grouped.forEach { (email, group) ->
                val account = accounts.firstOrNull { it.email == email }
                AccountBlock(
                    email = email,
                    status = group.firstOrNull()?.account_status ?: account?.status ?: "",
                    account = account,
                    calendars = group,
                    vm = vm,
                    onDisconnect = { pendingDisconnect = it },
                )
            }

            // Accounts with nothing mirrored yet (just connected, or every calendar switched
            // off) still need somewhere to be acted on.
            accounts.filter { it.email !in grouped.keys }.forEach { account ->
                AccountBlock(
                    email = account.email,
                    status = account.status,
                    account = account,
                    calendars = emptyList(),
                    vm = vm,
                    onDisconnect = { pendingDisconnect = it },
                )
            }

            TextButton(onClick = vm::connectAccount) {
                Text("Add a Google account", color = GvColors.Primary)
            }
            Text(
                text = "Consent happens in the browser and finishes on the gv web app. Come back " +
                    "here afterwards and the new account appears — there is no callback into the " +
                    "app to wait for.",
                style = MaterialTheme.typography.labelSmall,
                color = GvColors.TextMuted,
            )
        }
    }

    pendingDisconnect?.let { account ->
        AlertDialog(
            onDismissRequest = { pendingDisconnect = null },
            containerColor = GvColors.BgLight,
            title = { Text("Disconnect ${account.email}?", color = GvColors.Text) },
            text = {
                Text(
                    text = "The grant is revoked at Google and this account's calendars and " +
                        "events are removed from gv. Nothing is deleted in Google itself.",
                    color = GvColors.TextMuted,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteAccount(account)
                    pendingDisconnect = null
                }) { Text("Disconnect", color = GvColors.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDisconnect = null }) {
                    Text("Cancel", color = GvColors.TextMuted)
                }
            },
        )
    }
}

@Composable
private fun AccountBlock(
    email: String,
    status: String,
    account: CalendarAccount?,
    calendars: List<GoogleCalendar>,
    vm: CalendarViewModel,
    onDisconnect: (CalendarAccount) -> Unit,
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(GvColors.Bg)
            .border(1.dp, GvColors.BorderLight, RoundedCornerShape(10.dp))
            .padding(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Text(
                text = email,
                style = MaterialTheme.typography.labelLarge,
                color = GvColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            StatusPill(status)
        }

        if (status == "needs_reauth" || status == "revoked") {
            Text(
                // Only a person can fix this: Google rejected the stored grant, and nothing on
                // this side can renew it.
                text = "Google rejected the stored access. Nothing from this account is syncing " +
                    "until it is connected again.",
                style = MaterialTheme.typography.labelSmall,
                color = GvColors.Warning,
            )
            TextButton(onClick = vm::connectAccount, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text("Reconnect", color = GvColors.Primary)
            }
        }

        calendars.forEach { calendar ->
            CalendarRow(calendar = calendar, vm = vm)
        }

        if (account != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                TextButton(onClick = { vm.resyncAccount(account) }) {
                    Text("Rebuild", color = GvColors.TextMuted)
                }
                TextButton(onClick = { onDisconnect(account) }) {
                    Text("Disconnect", color = GvColors.Danger)
                }
            }
        }
    }
}

@Composable
private fun CalendarRow(calendar: GoogleCalendar, vm: CalendarViewModel) {
    val spacing = LocalSpacing.current
    val color = parseHexColor(calendar.color) ?: GvColors.Primary
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        // Filled = shown, hollow = hidden. See the sheet's doc for why this is not a checkbox.
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(if (calendar.visible) color else Color.Transparent)
                .border(2.dp, color, CircleShape)
                .clickable(enabled = calendar.sync_enabled) { vm.toggleCalendarVisible(calendar) },
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(
                    text = calendar.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (calendar.sync_enabled) GvColors.Text else GvColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (calendar.is_primary) MiniPill("main", GvColors.Primary)
                if (!calendar.writable) MiniPill("read-only", GvColors.TextMuted)
                if (calendar.deleted) MiniPill("gone", GvColors.Danger)
            }
            val detail = when {
                // Turning sync on imports the calendar whole: gv-api cannot bound the first
                // import by date, which is why holiday and birthday calendars arrive off.
                !calendar.sync_enabled -> "Not syncing — turning it on imports the whole calendar"
                calendar.lastSyncErrorOrNull() != null -> calendar.lastSyncErrorOrNull()
                !calendar.sync.watch_active -> "Changes arrive on the next poll, not instantly"
                else -> null
            }
            detail?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (calendar.lastSyncErrorOrNull() != null) GvColors.Danger else GvColors.TextMuted,
                    maxLines = 2,
                )
            }
        }
        Switch(
            checked = calendar.sync_enabled,
            onCheckedChange = { vm.toggleCalendarSync(calendar) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = GvColors.Primary,
                checkedTrackColor = GvColors.Primary.copy(alpha = 0.4f),
                uncheckedThumbColor = GvColors.TextMuted,
                uncheckedTrackColor = GvColors.Surface,
            ),
        )
    }
}

private fun GoogleCalendar.lastSyncErrorOrNull(): String? =
    sync.last_sync_error?.takeIf { it.isNotBlank() }

@Composable
private fun StatusPill(status: String) {
    val (label, color) = when (status) {
        "connected" -> "connected" to GvColors.Success
        "needs_reauth" -> "reconnect" to GvColors.Warning
        "revoked" -> "revoked" to GvColors.Danger
        else -> status to GvColors.TextMuted
    }
    if (label.isBlank()) return
    MiniPill(label, color)
}

@Composable
private fun MiniPill(label: String, color: Color) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = color,
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}
