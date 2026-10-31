package com.sonora.player.ui.viewmodel

import android.content.ComponentName
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.sonora.player.SonoraApp
import com.sonora.player.data.model.HitmoSong
import com.sonora.player.data.model.ItunesSongResult
import com.sonora.player.data.model.Playlist
import com.sonora.player.data.model.Track
import com.sonora.player.service.DownloadState
import com.sonora.player.service.SonoraMediaService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class PlayerViewModel : ViewModel() {

    private val app = SonoraApp.instance
    private val hitmoParser = app.hitmoParser
    private val itunesApi = app.itunesApi
    private val id3Tagger = app.id3Tagger
    private val audioScanner = app.audioScanner
    private val m3uParser = app.m3uParser
    private val downloadManager = app.downloadManager
    private val playlistRepository = app.playlistRepository

    private var mediaController: MediaController? = null

    // Library State
    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // Playback State
    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackPositionMs = MutableStateFlow(0L)
    val playbackPositionMs: StateFlow<Long> = _playbackPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    // Playback Parameters (Speed & Pitch)
    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _playbackPitch = MutableStateFlow(1.0f)
    val playbackPitch: StateFlow<Float> = _playbackPitch.asStateFlow()

    private val _isApplySpeedToAll = MutableStateFlow(true)
    val isApplySpeedToAll: StateFlow<Boolean> = _isApplySpeedToAll.asStateFlow()

    private val trackSpeedParams = mutableMapOf<String, Pair<Float, Float>>()

    // Online Hitmo State
    private val _topHits = MutableStateFlow<List<HitmoSong>>(emptyList())
    val topHits: StateFlow<List<HitmoSong>> = _topHits.asStateFlow()

    private val _searchHits = MutableStateFlow<List<HitmoSong>>(emptyList())
    val searchHits: StateFlow<List<HitmoSong>> = _searchHits.asStateFlow()

    private val _isLoadingOnline = MutableStateFlow(false)
    val isLoadingOnline: StateFlow<Boolean> = _isLoadingOnline.asStateFlow()

    val downloadStates = downloadManager.downloadStates

    // Playlists State
    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    // iTunes Cover Picker State
    private val _coverSearchResults = MutableStateFlow<List<ItunesSongResult>>(emptyList())
    val coverSearchResults: StateFlow<List<ItunesSongResult>> = _coverSearchResults.asStateFlow()

    private val _isSearchingCovers = MutableStateFlow(false)
    val isSearchingCovers: StateFlow<Boolean> = _isSearchingCovers.asStateFlow()

    private val _coverTargetTrack = MutableStateFlow<Track?>(null)
    val coverTargetTrack: StateFlow<Track?> = _coverTargetTrack.asStateFlow()

    fun initController(context: Context) {
        if (mediaController != null) return

        val sessionToken = SessionToken(
            context,
            ComponentName(context, SonoraMediaService::class.java)
        )

        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            try {
                val controller = controllerFuture.get()
                mediaController = controller
                setupPlayerListener(controller)
            } catch (_: Exception) {}
        }, ContextCompat.getMainExecutor(context))

        startPositionTicker()
        scanLibrary()
        loadTopToday()
        loadSavedPlaylists()
    }

    private fun loadSavedPlaylists() {
        viewModelScope.launch {
            val loaded = playlistRepository.loadPlaylists()
            _playlists.value = loaded
        }
    }

    private fun setupPlayerListener(player: Player) {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val mediaId = mediaItem?.mediaId
                if (mediaId != null) {
                    val found = _tracks.value.firstOrNull { it.id == mediaId }
                        ?: _queue.value.firstOrNull { it.id == mediaId }
                    if (found != null) {
                        _currentTrack.value = found
                        applyParametersForTrack(found)
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _durationMs.value = player.duration.coerceAtLeast(0L)
                }
            }
        })
    }

    private fun applyParametersForTrack(track: Track) {
        val controller = mediaController ?: return
        if (_isApplySpeedToAll.value) {
            controller.playbackParameters = PlaybackParameters(_playbackSpeed.value, _playbackPitch.value)
        } else {
            val specific = trackSpeedParams[track.id]
            if (specific != null) {
                _playbackSpeed.value = specific.first
                _playbackPitch.value = specific.second
                controller.playbackParameters = PlaybackParameters(specific.first, specific.second)
            } else {
                _playbackSpeed.value = 1.0f
                _playbackPitch.value = 1.0f
                controller.playbackParameters = PlaybackParameters(1.0f, 1.0f)
            }
        }
    }

    fun setPlaybackParameters(speed: Float, pitch: Float, applyToAll: Boolean) {
        _playbackSpeed.value = speed
        _playbackPitch.value = pitch
        _isApplySpeedToAll.value = applyToAll

        val cur = _currentTrack.value
        if (!applyToAll && cur != null) {
            trackSpeedParams[cur.id] = Pair(speed, pitch)
        }

        mediaController?.playbackParameters = PlaybackParameters(speed, pitch)
    }

    private fun startPositionTicker() {
        viewModelScope.launch {
            while (isActive) {
                mediaController?.let { controller ->
                    if (controller.isPlaying) {
                        _playbackPositionMs.value = controller.currentPosition.coerceAtLeast(0L)
                        _durationMs.value = controller.duration.coerceAtLeast(0L)
                    }
                }
                delay(500)
            }
        }
    }

    fun scanLibrary(customDirs: List<String> = emptyList()) {
        viewModelScope.launch {
            _isScanning.value = true
            val scanned = audioScanner.scanAll(customDirs)
            _tracks.value = scanned
            _isScanning.value = false
        }
    }

    fun playTrack(track: Track, newQueue: List<Track>? = null) {
        val controller = mediaController ?: return
        if (newQueue != null) {
            _queue.value = newQueue
        } else if (_queue.value.none { it.id == track.id }) {
            _queue.value = listOf(track)
        }

        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .apply {
                if (!track.coverUri.isNullOrBlank()) {
                    setArtworkUri(Uri.parse(track.coverUri))
                }
            }
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(if (track.path.startsWith("http")) track.path else Uri.fromFile(File(track.path)).toString())
            .setMediaMetadata(mediaMetadata)
            .build()

        controller.setMediaItem(mediaItem)
        controller.prepare()
        controller.play()
        _currentTrack.value = track
        applyParametersForTrack(track)
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _playbackPositionMs.value = positionMs
    }

    fun next() {
        val q = _queue.value
        val cur = _currentTrack.value ?: return
        val idx = q.indexOfFirst { it.id == cur.id }
        if (idx != -1 && idx + 1 < q.size) {
            playTrack(q[idx + 1])
        }
    }

    fun previous() {
        val q = _queue.value
        val cur = _currentTrack.value ?: return
        val idx = q.indexOfFirst { it.id == cur.id }
        if (idx > 0) {
            playTrack(q[idx - 1])
        } else {
            seekTo(0)
        }
    }

    fun loadTopToday() {
        viewModelScope.launch {
            _isLoadingOnline.value = true
            try {
                val songs = hitmoParser.getTopToday(1)
                _topHits.value = songs
            } catch (_: Exception) {}
            _isLoadingOnline.value = false
        }
    }

    fun searchOnline(query: String) {
        if (query.isBlank()) {
            _searchHits.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isLoadingOnline.value = true
            try {
                val songs = hitmoParser.search(query, 1)
                _searchHits.value = songs
            } catch (_: Exception) {}
            _isLoadingOnline.value = false
        }
    }

    fun playHitmoStream(hitmoSong: HitmoSong) {
        val onlineTrack = Track(
            id = hitmoSong.downloadUrl,
            title = hitmoSong.title,
            artist = hitmoSong.artist,
            durationMs = 0L,
            path = hitmoSong.downloadUrl,
            coverUri = hitmoSong.coverUrl.takeIf { !hitmoSong.hasStubCover },
            isOnline = true
        )
        playTrack(onlineTrack, listOf(onlineTrack))
    }

    fun downloadHitmoTrack(hitmoSong: HitmoSong) {
        viewModelScope.launch {
            val file = downloadManager.downloadSong(hitmoSong)
            if (file != null) {
                scanLibrary()
            }
        }
    }

    fun openCoverPickerForTrack(track: Track) {
        _coverTargetTrack.value = track
        _coverSearchResults.value = emptyList()
        viewModelScope.launch {
            _isSearchingCovers.value = true
            val results = itunesApi.searchArtworks(track.artist, track.title, limit = 8)
            _coverSearchResults.value = results
            _isSearchingCovers.value = false
        }
    }

    fun closeCoverPicker() {
        _coverTargetTrack.value = null
        _coverSearchResults.value = emptyList()
    }

    fun applyCoverArt(track: Track, artworkResult: ItunesSongResult) {
        viewModelScope.launch {
            val bytes = itunesApi.downloadArtworkBytes(artworkResult.artworkUrlHd)
            if (bytes != null && bytes.isNotEmpty()) {
                val success = id3Tagger.embedArtwork(
                    filePath = track.path,
                    imageBytes = bytes,
                    newArtist = track.artist,
                    newTitle = track.title
                )
                if (success) {
                    val newCover = id3Tagger.extractCoverToCache(track.path)
                    val updated = track.copy(coverUri = newCover)

                    _tracks.value = _tracks.value.map { if (it.id == track.id) updated else it }
                    if (_currentTrack.value?.id == track.id) {
                        _currentTrack.value = updated
                    }
                }
            }
            closeCoverPicker()
        }
    }

    fun deleteTrackPermanently(context: Context, track: Track) {
        viewModelScope.launch(Dispatchers.IO) {
            val isCurrent = _currentTrack.value?.id == track.id
            if (isCurrent) {
                withContext(Dispatchers.Main) {
                    val q = _queue.value
                    val idx = q.indexOfFirst { it.id == track.id }
                    if (idx != -1 && idx + 1 < q.size) {
                        playTrack(q[idx + 1])
                    } else {
                        mediaController?.stop()
                        _currentTrack.value = null
                    }
                }
            }

            // Delete file from disk
            try {
                val file = File(track.path)
                if (file.exists()) {
                    file.delete()
                }
            } catch (_: Exception) {}

            // Delete from MediaStore
            try {
                context.contentResolver.delete(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    "${MediaStore.Audio.Media.DATA} = ?",
                    arrayOf(track.path)
                )
            } catch (_: Exception) {}

            // Update library & playlists
            _tracks.value = _tracks.value.filter { it.id != track.id }
            _queue.value = _queue.value.filter { it.id != track.id }

            val updatedPlaylists = _playlists.value.map { pl ->
                pl.copy(trackPaths = pl.trackPaths.filter { it != track.path })
            }
            _playlists.value = updatedPlaylists
            playlistRepository.savePlaylists(updatedPlaylists)

            MediaScannerConnection.scanFile(
                context,
                arrayOf(track.path),
                null,
                null
            )
        }
    }

    fun importM3uPlaylist(file: File) {
        viewModelScope.launch {
            val imported = m3uParser.importPlaylist(file, _tracks.value)
            val updated = _playlists.value + imported
            _playlists.value = updated
            playlistRepository.savePlaylists(updated)
        }
    }

    fun createPlaylist(name: String, trackPaths: List<String>) {
        viewModelScope.launch {
            val newPlaylist = Playlist(
                id = UUID.randomUUID().toString(),
                name = name,
                trackPaths = trackPaths
            )
            val updated = _playlists.value + newPlaylist
            _playlists.value = updated
            playlistRepository.savePlaylists(updated)
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            val updated = _playlists.value.filter { it.id != playlist.id }
            _playlists.value = updated
            playlistRepository.savePlaylists(updated)
        }
    }

    fun removeTrackFromPlaylist(playlist: Playlist, track: Track) {
        viewModelScope.launch {
            val updatedPlaylist = playlist.copy(
                trackPaths = playlist.trackPaths.filter { it != track.path }
            )
            val updated = _playlists.value.map { if (it.id == playlist.id) updatedPlaylist else it }
            _playlists.value = updated
            playlistRepository.savePlaylists(updated)
        }
    }

    fun addToPlaylist(track: Track, playlist: Playlist) {
        viewModelScope.launch {
            if (track.path !in playlist.trackPaths) {
                val updatedPlaylist = playlist.copy(trackPaths = playlist.trackPaths + track.path)
                val updated = _playlists.value.map { if (it.id == playlist.id) updatedPlaylist else it }
                _playlists.value = updated
                playlistRepository.savePlaylists(updated)
            }
        }
    }

    fun createNewPlaylistWithTrack(name: String, track: Track) {
        createPlaylist(name, listOf(track.path))
    }

    fun exportBackup(context: Context, onResult: (File?) -> Unit) {
        viewModelScope.launch {
            val file = playlistRepository.exportBackup(_playlists.value)
            onResult(file)
        }
    }

    fun restoreBackup(file: File) {
        viewModelScope.launch {
            val restored = playlistRepository.restoreBackup(file)
            _playlists.value = restored
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaController?.release()
        mediaController = null
    }
}
