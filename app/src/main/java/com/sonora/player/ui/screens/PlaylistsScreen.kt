package com.sonora.player.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonora.player.data.model.Playlist
import com.sonora.player.data.model.Track
import com.sonora.player.ui.components.CreatePlaylistDialog
import com.sonora.player.ui.components.TrackListItem
import java.io.File
import java.io.FileOutputStream

@Composable
fun PlaylistsScreen(
    playlists: List<Playlist>,
    tracks: List<Track>,
    onPlayTrackList: (List<Track>, startIndex: Int) -> Unit,
    onImportM3uFile: (File) -> Unit,
    onCreatePlaylist: (String, List<String>) -> Unit,
    onDeletePlaylist: (Playlist) -> Unit,
    onRemoveTrackFromPlaylist: (Playlist, Track) -> Unit,
    onChangeCoverClick: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var isFabMenuExpanded by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val tempFile = copyUriToTempFile(context, uri)
            if (tempFile != null) {
                onImportM3uFile(tempFile)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        val activePlaylist = selectedPlaylist?.let { cur ->
            playlists.firstOrNull { it.id == cur.id }
        }

        if (activePlaylist != null) {
            PlaylistDetailView(
                playlist = activePlaylist,
                allTracks = tracks,
                onBack = { selectedPlaylist = null },
                onPlayTrack = { track, list ->
                    val idx = list.indexOf(track).coerceAtLeast(0)
                    onPlayTrackList(list, idx)
                },
                onPlayAll = { list -> onPlayTrackList(list, 0) },
                onShuffle = { list -> onPlayTrackList(list.shuffled(), 0) },
                onRemoveTrack = { track -> onRemoveTrackFromPlaylist(activePlaylist, track) },
                onChangeCoverClick = onChangeCoverClick
            )
        } else {
            // Main Playlists List
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "Плейлисты",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (playlists.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Плейлисты не добавлены",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(playlists, key = { it.id }) { playlist ->
                            var itemMenuExpanded by remember { mutableStateOf(false) }

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { selectedPlaylist = playlist },
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = playlist.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${playlist.trackPaths.size} треков",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Box {
                                        IconButton(onClick = { itemMenuExpanded = true }) {
                                            Icon(
                                                imageVector = Icons.Default.MoreVert,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        DropdownMenu(
                                            expanded = itemMenuExpanded,
                                            onDismissRequest = { itemMenuExpanded = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Удалить плейлист") },
                                                leadingIcon = {
                                                    Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                                                },
                                                onClick = {
                                                    itemMenuExpanded = false
                                                    onDeletePlaylist(playlist)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Circular FAB with popup menu in bottom right
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp)
            ) {
                FloatingActionButton(
                    onClick = { isFabMenuExpanded = true },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Создать"
                    )
                }

                DropdownMenu(
                    expanded = isFabMenuExpanded,
                    onDismissRequest = { isFabMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Создать плейлист") },
                        leadingIcon = {
                            Icon(Icons.Default.PlaylistAdd, contentDescription = null)
                        },
                        onClick = {
                            isFabMenuExpanded = false
                            showCreateDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Импорт M3U") },
                        leadingIcon = {
                            Icon(Icons.Default.FileOpen, contentDescription = null)
                        },
                        onClick = {
                            isFabMenuExpanded = false
                            filePickerLauncher.launch(
                                arrayOf(
                                    "audio/x-mpegurl",
                                    "application/vnd.apple.mpegurl",
                                    "audio/mpegurl",
                                    "text/plain",
                                    "*/*"
                                )
                            )
                        }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            allTracks = tracks,
            onCreate = { name, paths ->
                onCreatePlaylist(name, paths)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }
}

@Composable
private fun PlaylistDetailView(
    playlist: Playlist,
    allTracks: List<Track>,
    onBack: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayAll: (List<Track>) -> Unit,
    onShuffle: (List<Track>) -> Unit,
    onRemoveTrack: (Track) -> Unit,
    onChangeCoverClick: (Track) -> Unit
) {
    val playlistTracks = remember(playlist, allTracks) {
        allTracks.filter { it.path in playlist.trackPaths }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${playlistTracks.size} треков",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = { onPlayAll(playlistTracks) },
                enabled = playlistTracks.isNotEmpty(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Слушать")
            }

            FilledTonalButton(
                onClick = { onShuffle(playlistTracks) },
                enabled = playlistTracks.isNotEmpty(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Shuffle, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Перемешать")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (playlistTracks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Плейлист пуст",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(playlistTracks, key = { it.path }) { track ->
                    TrackListItem(
                        track = track,
                        isPlaying = false,
                        onClick = { onPlayTrack(track, playlistTracks) },
                        onCoverChangeClick = { onChangeCoverClick(track) }
                    )
                }
            }
        }
    }
}

private fun copyUriToTempFile(context: Context, uri: Uri): File? {
    return try {
        val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "playlist.m3u"
        val tempFile = File(context.cacheDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        tempFile
    } catch (_: Exception) {
        null
    }
}
