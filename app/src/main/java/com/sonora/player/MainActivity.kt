package com.sonora.player

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.sonora.player.data.model.Track
import com.sonora.player.ui.components.AddToPlaylistDialog
import com.sonora.player.ui.components.CoverPickerSheet
import com.sonora.player.ui.components.MiniPlayer
import com.sonora.player.ui.components.SpeedPitchDialog
import com.sonora.player.ui.screens.LibraryScreen
import com.sonora.player.ui.screens.PlayerBottomSheet
import com.sonora.player.ui.screens.PlaylistsScreen
import com.sonora.player.ui.screens.SearchScreen
import com.sonora.player.ui.screens.SettingsScreen
import com.sonora.player.ui.theme.SonoraTheme
import com.sonora.player.ui.viewmodel.PlayerViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.scanLibrary()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkAndRequestPermissions()
        viewModel.initController(this)

        setContent {
            SonoraTheme {
                MainAppScreen(viewModel = viewModel)
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissions.toTypedArray())
        }

        // Check MANAGE_EXTERNAL_STORAGE on Android 11+ for editing ID3 tags in Download/
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (_: Exception) {}
        }
    }
}

@Composable
fun MainAppScreen(viewModel: PlayerViewModel) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var trackForPlaylist by remember { mutableStateOf<Track?>(null) }
    var showSpeedPitchDialog by remember { mutableStateOf(false) }

    val tracks by viewModel.tracks.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val positionMs by viewModel.playbackPositionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()

    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val playbackPitch by viewModel.playbackPitch.collectAsState()
    val isApplySpeedToAll by viewModel.isApplySpeedToAll.collectAsState()

    val topHits by viewModel.topHits.collectAsState()
    val searchHits by viewModel.searchHits.collectAsState()
    val isLoadingOnline by viewModel.isLoadingOnline.collectAsState()
    val downloadStates by viewModel.downloadStates.collectAsState()

    val playlists by viewModel.playlists.collectAsState()

    val coverTargetTrack by viewModel.coverTargetTrack.collectAsState()
    val coverArtworks by viewModel.coverSearchResults.collectAsState()
    val isSearchingCovers by viewModel.isSearchingCovers.collectAsState()

    val progress = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f

    Scaffold(
        bottomBar = {
            Column(modifier = Modifier.navigationBarsPadding()) {
                // Persistent MiniPlayer if a track is active and not on Settings
                currentTrack?.let { track ->
                    MiniPlayer(
                        track = track,
                        isPlaying = isPlaying,
                        progress = progress,
                        onPlayPauseClick = { viewModel.togglePlayPause() },
                        onNextClick = { viewModel.next() },
                        onExpand = { isPlayerExpanded = true }
                    )
                }

                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.MusicNote, contentDescription = null) },
                        label = { Text("Треки") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Search, contentDescription = null) },
                        label = { Text("Поиск") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null) },
                        label = { Text("Плейлисты") }
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = {
                            if (currentTrack != null) {
                                isPlayerExpanded = true
                            } else if (tracks.isNotEmpty()) {
                                viewModel.playTrack(tracks.first(), tracks)
                                isPlayerExpanded = true
                            } else {
                                Toast.makeText(context, "Ничего не воспроизводится", Toast.LENGTH_SHORT).show()
                            }
                        },
                        icon = { Icon(Icons.Default.PlayCircle, contentDescription = null) },
                        label = { Text("Играет") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 4,
                        onClick = { selectedTab = 4 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("Настройки") }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> LibraryScreen(
                    tracks = tracks,
                    isScanning = isScanning,
                    currentPlayingId = currentTrack?.id,
                    onTrackClick = { track, list -> viewModel.playTrack(track, list) },
                    onChangeCoverClick = { viewModel.openCoverPickerForTrack(it) },
                    onRefreshScan = { viewModel.scanLibrary() }
                )
                1 -> SearchScreen(
                    topHits = topHits,
                    searchHits = searchHits,
                    isLoading = isLoadingOnline,
                    downloadStates = downloadStates,
                    onSearch = { viewModel.searchOnline(it) },
                    onPlayStream = { viewModel.playHitmoStream(it) },
                    onDownload = { viewModel.downloadHitmoTrack(it) }
                )
                2 -> PlaylistsScreen(
                    playlists = playlists,
                    tracks = tracks,
                    onPlayTrackList = { list, startIndex ->
                        if (list.isNotEmpty()) {
                            val track = list.getOrElse(startIndex) { list.first() }
                            viewModel.playTrack(track, list)
                        }
                    },
                    onImportM3uFile = { file -> viewModel.importM3uPlaylist(file) },
                    onCreatePlaylist = { name, paths -> viewModel.createPlaylist(name, paths) },
                    onDeletePlaylist = { playlist -> viewModel.deletePlaylist(playlist) },
                    onRemoveTrackFromPlaylist = { playlist, track -> viewModel.removeTrackFromPlaylist(playlist, track) },
                    onChangeCoverClick = { viewModel.openCoverPickerForTrack(it) }
                )
                4 -> SettingsScreen(
                    onBackupPlaylists = {
                        viewModel.exportBackup(context) { file ->
                            if (file != null) {
                                Toast.makeText(context, "Резервная копия сохранена в ${file.name}", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Ошибка резервного копирования", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onRestoreBackupFile = { file ->
                        viewModel.restoreBackup(file)
                        Toast.makeText(context, "Плейлисты восстановлены", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    // Fullscreen Player BottomSheet
    if (isPlayerExpanded && currentTrack != null) {
        PlayerBottomSheet(
            track = currentTrack!!,
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
            onPlayPauseClick = { viewModel.togglePlayPause() },
            onNextClick = { viewModel.next() },
            onPreviousClick = { viewModel.previous() },
            onSeekTo = { viewModel.seekTo(it) },
            onChangeCoverClick = { viewModel.openCoverPickerForTrack(currentTrack!!) },
            onAddToPlaylistClick = { trackForPlaylist = currentTrack },
            onSpeedPitchClick = { showSpeedPitchDialog = true },
            onDeletePermanentlyClick = {
                val toDelete = currentTrack!!
                isPlayerExpanded = false
                viewModel.deleteTrackPermanently(context, toDelete)
                Toast.makeText(context, "Файл удален", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { isPlayerExpanded = false }
        )
    }

    // Speed & Pitch Dialog
    if (showSpeedPitchDialog) {
        SpeedPitchDialog(
            currentSpeed = playbackSpeed,
            currentPitch = playbackPitch,
            isApplyToAll = isApplySpeedToAll,
            onApply = { speed, pitch, applyToAll ->
                viewModel.setPlaybackParameters(speed, pitch, applyToAll)
                showSpeedPitchDialog = false
            },
            onDismiss = { showSpeedPitchDialog = false }
        )
    }

    // Add To Playlist Dialog
    if (trackForPlaylist != null) {
        AddToPlaylistDialog(
            track = trackForPlaylist!!,
            playlists = playlists,
            onSelectPlaylist = { playlist ->
                viewModel.addToPlaylist(trackForPlaylist!!, playlist)
                trackForPlaylist = null
                Toast.makeText(context, "Добавлено в «${playlist.name}»", Toast.LENGTH_SHORT).show()
            },
            onCreatePlaylist = { name ->
                viewModel.createNewPlaylistWithTrack(name, trackForPlaylist!!)
                trackForPlaylist = null
                Toast.makeText(context, "Плейлист «$name» создан", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { trackForPlaylist = null }
        )
    }

    // iTunes Cover Picker Sheet
    if (coverTargetTrack != null) {
        CoverPickerSheet(
            track = coverTargetTrack!!,
            artworks = coverArtworks,
            isLoading = isSearchingCovers,
            onSelectArtwork = { artwork -> viewModel.applyCoverArt(coverTargetTrack!!, artwork) },
            onDismiss = { viewModel.closeCoverPicker() }
        )
    }
}
