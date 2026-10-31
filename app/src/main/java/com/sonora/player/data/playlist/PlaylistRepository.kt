package com.sonora.player.data.playlist

import android.content.Context
import android.os.Environment
import com.sonora.player.data.model.Playlist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PlaylistRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val playlistsFile: File
        get() = File(context.filesDir, "sonora_playlists.json")

    suspend fun loadPlaylists(): List<Playlist> = withContext(Dispatchers.IO) {
        if (!playlistsFile.exists()) return@withContext emptyList()
        try {
            val text = playlistsFile.readText()
            json.decodeFromString<List<Playlist>>(text)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun savePlaylists(playlists: List<Playlist>) = withContext(Dispatchers.IO) {
        try {
            val text = json.encodeToString(playlists)
            playlistsFile.writeText(text)
        } catch (_: Exception) {}
    }

    suspend fun exportBackup(playlists: List<Playlist>): File? = withContext(Dispatchers.IO) {
        try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                ?: File(Environment.getExternalStorageDirectory(), "Download")
            if (!downloadDir.exists()) downloadDir.mkdirs()

            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date())
            val backupFile = File(downloadDir, "Sonora_Backup_$timestamp.json")

            val text = json.encodeToString(playlists)
            backupFile.writeText(text)
            backupFile
        } catch (_: Exception) {
            null
        }
    }

    suspend fun restoreBackup(file: File): List<Playlist> = withContext(Dispatchers.IO) {
        try {
            val text = file.readText()
            val restored = json.decodeFromString<List<Playlist>>(text)
            val current = loadPlaylists().toMutableList()
            for (p in restored) {
                if (current.none { it.name == p.name }) {
                    current.add(p)
                }
            }
            savePlaylists(current)
            current
        } catch (_: Exception) {
            emptyList()
        }
    }
}
