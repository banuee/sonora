package com.sonora.player.data.remote

import com.sonora.player.data.model.HitmoSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class HitmoParser(
    private val client: OkHttpClient = createDefaultClient()
) {
    companion object {
        const val BASE_URL = "https://eu.hitmoz.com"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        private val DL_PATTERN = Pattern.compile("/get/music/.*\\.mp3", Pattern.CASE_INSENSITIVE)
        private val TIME_PATTERN = Pattern.compile("\\d{1,2}:\\d{2}")

        fun createDefaultClient(): OkHttpClient {
            val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .cookieJar(object : CookieJar {
                    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                        cookieStore.getOrPut(url.host) { mutableListOf() }.addAll(cookies)
                    }

                    override fun loadForRequest(url: HttpUrl): List<Cookie> {
                        return cookieStore[url.host] ?: emptyList()
                    }
                })
                .build()
        }
    }

    private fun newBaseRequest(url: String, referer: String = "$BASE_URL/"): Request {
        return Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.7")
            .header("Referer", referer)
            .build()
    }

    suspend fun getTopToday(page: Int = 1): List<HitmoSong> = withContext(Dispatchers.IO) {
        val url = if (page <= 1) {
            "$BASE_URL/songs/top-today"
        } else {
            "$BASE_URL/songs/top-today/start/${(page - 1) * 48}"
        }
        val request = newBaseRequest(url)
        val response = client.newCall(request).execute()
        val html = response.body?.string().orEmpty()
        parseSongs(html, rankOffset = (page - 1) * 48)
    }

    suspend fun search(query: String, page: Int = 1): List<HitmoSong> = withContext(Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
        val baseSearchUrl = "$BASE_URL/search?q=$encodedQuery"
        val url = if (page <= 1) baseSearchUrl else "$baseSearchUrl&start=${(page - 1) * 48}"

        val request = newBaseRequest(url)
        val response = client.newCall(request).execute()
        val html = response.body?.string().orEmpty()
        parseSongs(html, rankOffset = (page - 1) * 48)
    }

    private fun parseSongs(html: String, rankOffset: Int): List<HitmoSong> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html, BASE_URL)
        val songs = mutableListOf<HitmoSong>()
        val seenDl = mutableSetOf<String>()

        // Hitmo renders each track in an element with class .track or with attribute data-musmeta
        val trackElements = doc.select(".track, [data-musmeta]")
        for (el in trackElements) {
            var metaTitle = ""
            var metaArtist = ""
            var metaImg = ""
            var metaUrl = ""

            val musmetaRaw = el.attr("data-musmeta")
            if (musmetaRaw.isNotBlank()) {
                try {
                    val json = JSONObject(musmetaRaw)
                    metaTitle = json.optString("title")
                    metaArtist = json.optString("artist")
                    metaImg = json.optString("img")
                    metaUrl = json.optString("url")
                } catch (_: Exception) {}
            }

            val domTitle = el.select(".track__title").text().trim()
            val domArtist = el.select(".track__desc").text().trim()
            val domDuration = el.select(".track__fulltime").text().trim()
            val domCover = extractCover(el)

            // Prefer data-musmeta JSON (clean Russian strings) then fallback to DOM
            var title = metaTitle.ifBlank { domTitle }
            var artist = metaArtist.ifBlank { domArtist }
            var duration = domDuration
            var cover = metaImg.ifBlank { domCover }

            // Download URL: look for download button or /get/music/ link
            val dlBtn = el.selectFirst("a.track__download-btn[href], a[href*='/get/music/']")
            val rawDl = dlBtn?.attr("href") ?: metaUrl
            val absDlHref = toAbsoluteUrl(rawDl)

            if (absDlHref.isBlank() || absDlHref in seenDl) continue
            seenDl.add(absDlHref)

            // Fallback from filename only if title/artist couldn't be extracted from HTML or JSON
            if (title.isBlank() || artist.isBlank()) {
                val (fArtist, fTitle) = parseFilename(absDlHref)
                if (title.isBlank()) title = fTitle
                if (artist.isBlank()) artist = fArtist
            }

            val songIdMatch = Regex("""_(\d{6,})\.mp3""").find(absDlHref)
            val songId = songIdMatch?.groupValues?.get(1).orEmpty()

            songs.add(
                HitmoSong(
                    rank = rankOffset + songs.size + 1,
                    title = title,
                    artist = artist,
                    duration = duration,
                    coverUrl = toAbsoluteUrl(cover),
                    downloadUrl = absDlHref,
                    songId = songId
                )
            )
        }

        // Fallback for older or alternative Hitmo HTML layouts
        if (songs.isEmpty()) {
            val links = doc.select("a[href]")
            for (a in links) {
                val href = a.attr("href")
                if (!DL_PATTERN.matcher(href).find()) continue

                val absHref = toAbsoluteUrl(href)
                if (absHref.isBlank() || absHref in seenDl) continue
                seenDl.add(absHref)

                val row = findSongRow(a)
                val (title, artist, duration, cover) = extractSongData(row, absHref)

                val songIdMatch = Regex("""_(\d{6,})\.mp3""").find(absHref)
                val songId = songIdMatch?.groupValues?.get(1).orEmpty()

                songs.add(
                    HitmoSong(
                        rank = rankOffset + songs.size + 1,
                        title = title,
                        artist = artist,
                        duration = duration,
                        coverUrl = cover,
                        downloadUrl = absHref,
                        songId = songId
                    )
                )
            }
        }

        return songs
    }

    private fun findSongRow(tag: Element): Element {
        var row = tag
        var current: Element? = tag
        for (i in 0..7) {
            val parent = current?.parent() ?: break
            val count = parent.select("a[href]").count {
                DL_PATTERN.matcher(it.attr("href")).find()
            }
            if (count > 1) {
                return current
            }
            current = parent
            row = parent
        }
        return row
    }

    private data class ExtractedData(
        val title: String,
        val artist: String,
        val duration: String,
        val cover: String
    )

    private fun extractSongData(row: Element, dlUrl: String): ExtractedData {
        val domTitle = row.select(".track__title").text().trim()
        val domArtist = row.select(".track__desc").text().trim()
        val domDuration = row.select(".track__fulltime").text().trim()

        if (domTitle.isNotBlank() && domArtist.isNotBlank()) {
            return ExtractedData(domTitle, domArtist, domDuration, extractCover(row))
        }

        val rawTexts = mutableListOf<String>()
        for (child in row.children()) {
            val t = child.ownText().trim()
            if (t.length >= 2 && !t.contains("скачать", ignoreCase = true)) {
                rawTexts.add(t)
            }
        }

        var duration = domDuration
        val content = mutableListOf<String>()
        for (t in rawTexts) {
            if (TIME_PATTERN.matcher(t).matches()) {
                duration = t
            } else {
                content.add(t)
            }
        }

        var title = if (content.isNotEmpty()) content[0] else ""
        var artist = if (content.size >= 2) content[1] else ""

        val (fallbackArtist, fallbackTitle) = parseFilename(dlUrl)
        if (title.isBlank()) title = fallbackTitle
        if (artist.isBlank()) artist = fallbackArtist

        val cover = extractCover(row)
        return ExtractedData(title, artist, duration, cover)
    }

    private fun extractCover(row: Element): String {
        val imgTags = row.select("img")
        val attrs = listOf(
            "src", "data-src", "data-lazy-src", "data-original",
            "data-bg", "data-image", "data-thumb"
        )

        for (img in imgTags) {
            for (attr in attrs) {
                val src = img.attr(attr).trim()
                if (src.isNotBlank() && !src.startsWith("data:")) {
                    return toAbsoluteUrl(src)
                }
            }
        }

        for (el in row.allElements) {
            val style = el.attr("style")
            if (style.contains("background")) {
                val m = Regex("""url\(["']?([^"']+)["']?\)""").find(style)
                if (m != null) {
                    return toAbsoluteUrl(m.groupValues[1])
                }
            }
            for (a in el.attributes()) {
                if (a.key.startsWith("data-") && Regex("""\.(jpg|jpeg|png|webp)""", RegexOption.IGNORE_CASE).containsMatchIn(a.value)) {
                    return toAbsoluteUrl(a.value)
                }
            }
        }

        return ""
    }

    private fun parseFilename(dlUrl: String): Pair<String, String> {
        val filename = dlUrl.substringAfterLast("/").removeSuffix(".mp3")
        val cleanName = filename.replace(Regex("_(\\d{6,})$"), "")
        return if (cleanName.contains("_-_")) {
            val parts = cleanName.split("_-_", limit = 2)
            val artist = parts[0].replace("_", " ").trim()
            val title = parts[1].replace("_", " ").trim()
            Pair(artist, title)
        } else {
            Pair("", cleanName.replace("_", " ").trim())
        }
    }

    private fun toAbsoluteUrl(href: String): String {
        val trimmed = href.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.startsWith("http")) return trimmed
        if (trimmed.startsWith("//")) return "https:$trimmed"
        return if (trimmed.startsWith("/")) "$BASE_URL$trimmed" else "$BASE_URL/$trimmed"
    }

    fun getDownloadRequest(downloadUrl: String): Request {
        return Request.Builder()
            .url(downloadUrl)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "*/*")
            .header("Referer", "$BASE_URL/")
            .build()
    }
}
