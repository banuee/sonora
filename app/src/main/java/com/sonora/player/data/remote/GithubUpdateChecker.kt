package com.sonora.player.data.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class GithubReleaseInfo(
    val tagName: String,
    val title: String,
    val body: String,
    val downloadUrl: String,
    val isNewer: Boolean
)

class GithubUpdateChecker(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        const val REPO = "banuee/sonora"
        const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"
    }

    suspend fun checkForUpdates(currentVersion: String): GithubReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(LATEST_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "SonoraApp")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val jsonString = response.body?.string().orEmpty()
            if (jsonString.isBlank()) return@withContext null

            val json = JSONObject(jsonString)
            val tagName = json.optString("tag_name", "")
            val title = json.optString("name", tagName)
            val body = json.optString("body", "")

            var downloadUrl = ""
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk")) {
                        downloadUrl = asset.optString("browser_download_url", "")
                        break
                    }
                }
            }

            val cleanTag = tagName.removePrefix("v").trim()
            val cleanCurrent = currentVersion.removePrefix("v").trim()
            val isNewer = isVersionNewer(cleanTag, cleanCurrent)

            GithubReleaseInfo(
                tagName = tagName,
                title = title,
                body = body,
                downloadUrl = downloadUrl,
                isNewer = isNewer
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun isVersionNewer(remote: String, local: String): Boolean {
        if (remote.isBlank()) return false
        val rParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val lParts = local.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(rParts.size, lParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val l = lParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    suspend fun downloadAndInstallApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        if (downloadUrl.isBlank()) return@withContext false
        try {
            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "SonoraApp")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext false

            val body = response.body ?: return@withContext false
            val totalBytes = body.contentLength()

            val apkFile = File(context.cacheDir, "sonora-update.apk")
            body.byteStream().use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var bytesCopied = 0L
                    var read: Int
                    var lastUpdate = 0L

                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesCopied += read
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 250 && totalBytes > 0) {
                            onProgress(bytesCopied.toFloat() / totalBytes)
                            lastUpdate = now
                        }
                    }
                }
            }

            onProgress(1f)

            // Install APK via Intent
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
