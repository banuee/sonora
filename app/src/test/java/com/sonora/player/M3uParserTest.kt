package com.sonora.player

import com.sonora.player.data.playlist.M3uParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class M3uParserTest {

    @Test
    fun testParseMusicoletM3u() {
        val sampleFile = File("/home/banue/Загрузки/нови плейлист.m3u")
        if (!sampleFile.exists()) return

        val parser = M3uParser()
        val entries = sampleFile.inputStream().use { parser.parse(it) }

        assertTrue(entries.isNotEmpty())
        println("Parsed ${entries.size} entries from Musicolet m3u")

        val first = entries.first()
        assertEquals(229, first.durationSec)
        assertEquals("Валентин Стрыкало - 92", first.displayTitle)
        assertEquals("Download/Valentin_Strykalo_-_92_47844601.mp3", first.path)
    }
}
