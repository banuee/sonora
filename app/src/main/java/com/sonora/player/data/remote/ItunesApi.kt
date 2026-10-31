package com.sonora.player.data.remote

import com.sonora.player.data.model.ItunesSearchResponse
import com.sonora.player.data.model.ItunesSongResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class ItunesApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun searchArtworks(
        artist: String,
        title: String,
        limit: Int = 6
    ): List<ItunesSongResult> = withContext(Dispatchers.IO) {
        val cleanArtist = artist.replace(Regex("""\(.*?\)|\{.*?\}|\[.*?\]"""), "").trim()
        val cleanTitle = title.replace(Regex("""\(.*?\)|\{.*?\}|\[.*?\]"""), "").trim()
        val query = "$cleanArtist $cleanTitle".trim().ifBlank { title.trim() }
        if (query.isBlank()) return@withContext emptyList()

        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://itunes.apple.com/search?term=$encoded&entity=song&limit=$limit"

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val body = response.body?.string().orEmpty()
            val parsed = json.decodeFromString<ItunesSearchResponse>(body)
            parsed.results.filter { it.artworkUrl100.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun downloadArtworkBytes(artworkUrl: String): ByteArray? = withContext(Dispatchers.IO) {
        if (artworkUrl.isBlank()) return@withContext null
        try {
            val request = Request.Builder()
                .url(artworkUrl)
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.bytes()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
