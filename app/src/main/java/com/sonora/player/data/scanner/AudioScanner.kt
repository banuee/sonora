package com.sonora.player.data.scanner

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.sonora.player.data.model.Track
import com.sonora.player.data.tagger.Id3Tagger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class AudioScanner(
    private val context: Context,
    private val tagger: Id3Tagger
) {
    private val supportedExtensions = setOf("mp3", "flac", "m4a", "ogg", "wav", "aac")

    suspend fun scanAll(
        customDirs: List<String> = emptyList()
    ): List<Track> = withContext(Dispatchers.IO) {
        val tracksMap = mutableMapOf<String, Track>()

        // 1. MediaStore scan
        try {
            scanMediaStore(tracksMap)
        } catch (_: Exception) {}

        // 2. Direct directory scan for Download, Music, and custom paths
        val defaultDirs = listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.absolutePath,
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)?.absolutePath,
            File(Environment.getExternalStorageDirectory(), "Download").absolutePath,
            File(Environment.getExternalStorageDirectory(), "Music").absolutePath
        ).distinct()

        val allDirs = (defaultDirs + customDirs).distinct()
        for (dirPath in allDirs) {
            val dir = File(dirPath)
            if (dir.exists() && dir.isDirectory) {
                scanDirectory(dir, tracksMap)
            }
        }

        tracksMap.values.sortedBy { it.title.lowercase() }
    }

    private fun scanMediaStore(tracksMap: MutableMap<String, Track>) {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM_ID
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val queryUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        context.contentResolver.query(
            queryUri,
            projection,
            selection,
            null,
            null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

            while (cursor.moveToNext()) {
                val path = cursor.getString(dataCol) ?: continue
                val file = File(path)
                if (!file.exists()) continue

                val title = cursor.getString(titleCol) ?: file.nameWithoutExtension
                val artist = cursor.getString(artistCol)?.takeIf { it != "<unknown>" } ?: "Неизвестный исполнитель"
                val album = cursor.getString(albumCol).orEmpty()
                val duration = cursor.getLong(durationCol)
                val albumId = cursor.getLong(albumIdCol)

                val artUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    albumId
                ).toString()

                tracksMap[file.absolutePath] = Track(
                    id = file.absolutePath,
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = duration,
                    path = file.absolutePath,
                    coverUri = artUri,
                    isOnline = false
                )
            }
        }
    }

    private suspend fun scanDirectory(dir: File, tracksMap: MutableMap<String, Track>) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                // Don't recurse into Android/data or hidden dirs
                if (!file.name.startsWith(".") && file.name != "Android") {
                    scanDirectory(file, tracksMap)
                }
            } else if (file.isFile && file.extension.lowercase() in supportedExtensions) {
                if (!tracksMap.containsKey(file.absolutePath)) {
                    val track = parseAudioFile(file)
                    if (track != null) {
                        tracksMap[file.absolutePath] = track
                    }
                }
            }
        }
    }

    private suspend fun parseAudioFile(file: File): Track? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            var title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim()
            var artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim()
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.trim().orEmpty()
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val duration = durationStr?.toLongOrNull() ?: 0L

            if (title.isNullOrBlank() || artist.isNullOrBlank() || artist == "<unknown>") {
                val cleanFilename = file.nameWithoutExtension.replace(Regex("_(\\d{6,})$"), "")
                if (cleanFilename.contains("_-_")) {
                    val parts = cleanFilename.split("_-_", limit = 2)
                    if (artist.isNullOrBlank() || artist == "<unknown>") {
                        artist = parts[0].replace("_", " ").trim()
                    }
                    if (title.isNullOrBlank()) {
                        title = parts[1].replace("_", " ").trim()
                    }
                } else if (cleanFilename.contains(" - ")) {
                    val parts = cleanFilename.split(" - ", limit = 2)
                    if (artist.isNullOrBlank() || artist == "<unknown>") {
                        artist = parts[0].trim()
                    }
                    if (title.isNullOrBlank()) {
                        title = parts[1].trim()
                    }
                } else {
                    if (title.isNullOrBlank()) title = cleanFilename.replace("_", " ")
                    if (artist.isNullOrBlank()) artist = "Неизвестный исполнитель"
                }
            }

            // Extract cover if available
            val coverPath = tagger.extractCoverToCache(file.absolutePath)

            Track(
                id = file.absolutePath,
                title = title ?: file.nameWithoutExtension,
                artist = artist ?: "Неизвестный исполнитель",
                album = album,
                durationMs = duration,
                path = file.absolutePath,
                coverUri = coverPath,
                isOnline = false
            )
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }
}
