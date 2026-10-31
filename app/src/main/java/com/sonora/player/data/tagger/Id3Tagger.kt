package com.sonora.player.data.tagger

import android.content.Context
import android.media.MediaScannerConnection
import com.mpatric.mp3agic.ID3v2
import com.mpatric.mp3agic.ID3v24Tag
import com.mpatric.mp3agic.Mp3File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class Id3Tagger(private val context: Context) {

    suspend fun extractCoverToCache(filePath: String): String? = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists() || !file.canRead()) return@withContext null

        try {
            val mp3 = Mp3File(file.absolutePath)
            if (mp3.hasId3v2Tag()) {
                val tag = mp3.id3v2Tag
                val imageBytes = tag.albumImage
                if (imageBytes != null && imageBytes.isNotEmpty()) {
                    val mime = tag.albumImageMimeType ?: "image/jpeg"
                    val ext = if (mime.contains("png")) "png" else "jpg"
                    val cacheDir = File(context.cacheDir, "covers").apply { mkdirs() }
                    val coverFile = File(cacheDir, "${file.nameWithoutExtension}_cover.$ext")
                    coverFile.writeBytes(imageBytes)
                    return@withContext coverFile.absolutePath
                }
            }
        } catch (e: Exception) {
            // Ignore corruption
        }
        null
    }

    suspend fun embedArtwork(
        filePath: String,
        imageBytes: ByteArray,
        mimeType: String = "image/jpeg",
        newArtist: String? = null,
        newTitle: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists() || !file.canWrite()) return@withContext false

        val tempFile = File(file.parentFile, "${file.name}.sonora.tmp")
        try {
            val mp3 = Mp3File(file.absolutePath)
            val tag = if (mp3.hasId3v2Tag()) mp3.id3v2Tag else ID3v24Tag()

            tag.setAlbumImage(imageBytes, mimeType)
            if (!newArtist.isNullOrBlank()) tag.artist = newArtist
            if (!newTitle.isNullOrBlank()) tag.title = newTitle

            mp3.id3v2Tag = tag
            mp3.save(tempFile.absolutePath)

            if (tempFile.exists() && tempFile.length() > 0) {
                if (file.delete()) {
                    tempFile.renameTo(file)
                } else {
                    tempFile.copyTo(file, overwrite = true)
                    tempFile.delete()
                }

                // Update cache
                val cacheDir = File(context.cacheDir, "covers").apply { mkdirs() }
                val ext = if (mimeType.contains("png")) "png" else "jpg"
                val coverFile = File(cacheDir, "${file.nameWithoutExtension}_cover.$ext")
                coverFile.writeBytes(imageBytes)

                // Scan with MediaScanner
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath),
                    arrayOf("audio/mpeg"),
                    null
                )
                return@withContext true
            }
        } catch (e: Exception) {
            tempFile.delete()
        }
        false
    }
}
