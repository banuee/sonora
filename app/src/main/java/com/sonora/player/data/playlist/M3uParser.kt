package com.sonora.player.data.playlist

import com.sonora.player.data.model.Playlist
import com.sonora.player.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.UUID

class M3uParser {

    data class M3uEntry(
        val durationSec: Int,
        val displayTitle: String,
        val path: String
    )

    fun parse(stream: InputStream): List<M3uEntry> {
        val entries = mutableListOf<M3uEntry>()
        var currentDuration = 0
        var currentTitle = ""

        stream.bufferedReader(Charsets.UTF_8).forEachLine { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEachLine

            if (trimmed.startsWith("#EXTINF:", ignoreCase = true)) {
                val meta = trimmed.removePrefix("#EXTINF:").removePrefix("#extinf:").trim()
                val commaIndex = meta.indexOf(',')
                if (commaIndex != -1) {
                    val durationPart = meta.substring(0, commaIndex).trim()
                    currentDuration = durationPart.toIntOrNull() ?: 0
                    currentTitle = meta.substring(commaIndex + 1).trim()
                } else {
                    currentDuration = meta.toIntOrNull() ?: 0
                    currentTitle = ""
                }
            } else if (!trimmed.startsWith("#")) {
                // Audio path or URL
                entries.add(
                    M3uEntry(
                        durationSec = currentDuration,
                        displayTitle = currentTitle,
                        path = trimmed
                    )
                )
                currentDuration = 0
                currentTitle = ""
            }
        }
        return entries
    }

    suspend fun importPlaylist(
        file: File,
        libraryTracks: List<Track>
    ): Playlist = withContext(Dispatchers.IO) {
        val playlistName = file.nameWithoutExtension
        val entries = file.inputStream().use { parse(it) }

        val tracksByPath = libraryTracks.associateBy { it.path }
        val tracksByName = libraryTracks.groupBy { File(it.path).name }

        val matchedTrackPaths = mutableListOf<String>()

        for (entry in entries) {
            val entryFileName = File(entry.path).name

            // 1. Exact path match
            val exact = tracksByPath[entry.path]
            if (exact != null) {
                matchedTrackPaths.add(exact.path)
                continue
            }

            // 2. Ends with relative path (e.g. /storage/emulated/0/Download/... ends with Download/...)
            val endsWith = libraryTracks.firstOrNull { it.path.endsWith(entry.path) }
            if (endsWith != null) {
                matchedTrackPaths.add(endsWith.path)
                continue
            }

            // 3. Filename match
            val byName = tracksByName[entryFileName]?.firstOrNull()
            if (byName != null) {
                matchedTrackPaths.add(byName.path)
                continue
            }

            // 4. Match by Artist - Title
            if (entry.displayTitle.isNotBlank()) {
                val byTitle = libraryTracks.firstOrNull { track ->
                    val full = "${track.artist} - ${track.title}"
                    full.equals(entry.displayTitle, ignoreCase = true) ||
                            entry.displayTitle.contains(track.title, ignoreCase = true)
                }
                if (byTitle != null) {
                    matchedTrackPaths.add(byTitle.path)
                }
            }
        }

        Playlist(
            id = UUID.randomUUID().toString(),
            name = playlistName,
            trackPaths = matchedTrackPaths.distinct()
        )
    }

    suspend fun exportPlaylist(
        playlist: Playlist,
        tracks: List<Track>,
        destinationFile: File
    ) = withContext(Dispatchers.IO) {
        val tracksMap = tracks.associateBy { it.path }
        destinationFile.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("#EXTM3U\n")
            for (path in playlist.trackPaths) {
                val track = tracksMap[path]
                if (track != null) {
                    val durationSec = track.durationMs / 1000
                    writer.write("#EXTINF:$durationSec,${track.artist} - ${track.title}\n")
                    writer.write("${track.path}\n")
                } else {
                    writer.write("$path\n")
                }
            }
        }
    }
}
