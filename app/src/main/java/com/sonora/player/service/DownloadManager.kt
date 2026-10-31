package com.sonora.player.service

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import com.sonora.player.data.model.HitmoSong
import com.sonora.player.data.remote.HitmoParser
import com.sonora.player.data.remote.ItunesApi
import com.sonora.player.data.tagger.Id3Tagger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Float) : DownloadState()
    data class Success(val savedPath: String) : DownloadState()
    data class Failed(val error: String) : DownloadState()
}

class SonoraDownloadManager(
    private val context: Context,
    private val hitmoParser: HitmoParser,
    private val itunesApi: ItunesApi,
    private val id3Tagger: Id3Tagger
) {
    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    suspend fun downloadSong(song: HitmoSong): File? = withContext(Dispatchers.IO) {
        val songKey = song.downloadUrl
        updateState(songKey, DownloadState.Downloading(0.05f))

        try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                ?: File(Environment.getExternalStorageDirectory(), "Download")
            if (!downloadDir.exists()) downloadDir.mkdirs()

            val cleanArtist = song.artist.replace("[/\\\\:*?\"<>|]".toRegex(), "_").trim()
            val cleanTitle = song.title.replace("[/\\\\:*?\"<>|]".toRegex(), "_").trim()
            val safeFileName = if (cleanArtist.isNotBlank()) {
                "${cleanArtist}_-_${cleanTitle}_${song.songId}.mp3"
            } else {
                "${cleanTitle}_${song.songId}.mp3"
            }
            val destinationFile = File(downloadDir, safeFileName)

            val request = hitmoParser.getDownloadRequest(song.downloadUrl)
            val client = HitmoParser.createDefaultClient()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                updateState(songKey, DownloadState.Failed("HTTP ${response.code}"))
                return@withContext null
            }

            val body = response.body ?: throw IllegalStateException("Empty response body")
            val totalBytes = body.contentLength()

            body.byteStream().use { input ->
                FileOutputStream(destinationFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var bytesCopied = 0L
                    var read: Int
                    var lastProgressUpdate = 0L

                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesCopied += read

                        val now = System.currentTimeMillis()
                        if (now - lastProgressUpdate > 300 && totalBytes > 0) {
                            val progress = (bytesCopied.toFloat() / totalBytes).coerceIn(0f, 0.95f)
                            updateState(songKey, DownloadState.Downloading(progress))
                            lastProgressUpdate = now
                        }
                    }
                }
            }

            // Step 2: Auto Cover Art Search via iTunes API
            try {
                var imageBytes: ByteArray? = null
                // Query iTunes API for official crisp album art
                val itunesResults = itunesApi.searchArtworks(song.artist, song.title, limit = 3)
                val bestMatch = itunesResults.firstOrNull()
                if (bestMatch != null && bestMatch.artworkUrlHd.isNotBlank()) {
                    imageBytes = itunesApi.downloadArtworkBytes(bestMatch.artworkUrlHd)
                }

                // If iTunes didn't find anything and Hitmo has a non-stub cover, use Hitmo's cover
                if (imageBytes == null && !song.hasStubCover && song.coverUrl.isNotBlank()) {
                    imageBytes = itunesApi.downloadArtworkBytes(song.coverUrl)
                }

                // Embed into ID3v2 tags
                if (imageBytes != null && imageBytes.isNotEmpty()) {
                    id3Tagger.embedArtwork(
                        filePath = destinationFile.absolutePath,
                        imageBytes = imageBytes,
                        mimeType = "image/jpeg",
                        newArtist = song.artist,
                        newTitle = song.title
                    )
                }
            } catch (e: Exception) {
                // Ignore artwork failure, track is still downloaded
            }

            // Scan file with MediaScanner
            MediaScannerConnection.scanFile(
                context,
                arrayOf(destinationFile.absolutePath),
                arrayOf("audio/mpeg"),
                null
            )

            updateState(songKey, DownloadState.Success(destinationFile.absolutePath))
            destinationFile
        } catch (e: Exception) {
            updateState(songKey, DownloadState.Failed(e.localizedMessage ?: "Ошибка скачивания"))
            null
        }
    }

    private fun updateState(key: String, state: DownloadState) {
        val current = _downloadStates.value.toMutableMap()
        current[key] = state
        _downloadStates.value = current
    }
}
