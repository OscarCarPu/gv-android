package com.gv.app.ui.alarm

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.gv.app.spotify.Spotify
import com.gv.app.spotify.SpotifyAuthState
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistPickerDialog(
    vm: AlarmViewModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val authState by vm.authState.collectAsStateWithLifecycle()
    val picker by vm.picker.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current

    LaunchedEffect(authState) {
        if (authState == SpotifyAuthState.Connected) {
            vm.loadPlaylists()
        } else {
            vm.resetPicker()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = GvColors.Bg,
            topBar = {
                TopAppBar(
                    title = { Text("Choose playlist", color = GvColors.Text) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "Close",
                                tint = GvColors.Text,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = GvColors.BgLight,
                    ),
                )
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(spacing.xl),
            ) {
                when (val s = picker) {
                    is PlaylistPickerState.Disconnected -> DisconnectedView(
                        onConnect = { Spotify.startLogin(context as Activity) },
                    )
                    is PlaylistPickerState.Loading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = GvColors.Primary)
                    }
                    is PlaylistPickerState.Loaded -> LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        items(s.playlists, key = { it.id }) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        vm.onPlaylistSelected(playlist)
                                        onDismiss()
                                    }
                                    .padding(spacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(spacing.lg),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(GvColors.BgLight),
                                ) {
                                    if (playlist.imageUrl != null) {
                                        AsyncImage(
                                            model = playlist.imageUrl,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                }
                                Text(
                                    text = playlist.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = GvColors.Text,
                                )
                            }
                        }
                    }
                    is PlaylistPickerState.Error -> Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = s.message,
                            color = GvColors.Danger,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(
                            onClick = vm::loadPlaylists,
                            colors = ButtonDefaults.buttonColors(containerColor = GvColors.Primary),
                            modifier = Modifier.padding(top = spacing.lg),
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DisconnectedView(onConnect: () -> Unit) {
    val spacing = LocalSpacing.current
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Connect your Spotify account to browse playlists",
            style = MaterialTheme.typography.bodyMedium,
            color = GvColors.TextMuted,
        )
        Button(
            onClick = onConnect,
            modifier = Modifier.padding(top = spacing.lg),
            colors = ButtonDefaults.buttonColors(containerColor = GvColors.Primary),
        ) {
            Text("Connect Spotify")
        }
    }
}
