package com.sonora.player.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0L,
    val path: String,
    val coverUri: String? = null,
    val isOnline: Boolean = false,
    val dateAdded: Long = System.currentTimeMillis()
) {
    val durationFormatted: String
        get() {
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }
}

@Serializable
data class HitmoSong(
    val rank: Int = 0,
    val title: String,
    val artist: String,
    val duration: String,
    val coverUrl: String,
    val downloadUrl: String,
    val songId: String = ""
) {
    val hasStubCover: Boolean
        get() = coverUrl.isBlank() ||
                coverUrl.contains("no-cover") ||
                coverUrl.contains("default") ||
                coverUrl.contains("placeholder") ||
                coverUrl.endsWith(".svg")
}

@Serializable
data class ItunesSongResult(
    val wrapperType: String? = null,
    val kind: String? = null,
    val artistName: String = "",
    val trackName: String = "",
    val collectionName: String = "",
    val artworkUrl100: String = ""
) {
    val artworkUrlHd: String
        get() = artworkUrl100.replace("100x100bb.jpg", "1000x1000bb.jpg")
            .replace("100x100bb.png", "1000x1000bb.png")
}

@Serializable
data class ItunesSearchResponse(
    val resultCount: Int = 0,
    val results: List<ItunesSongResult> = emptyList()
)

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val trackPaths: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)
