package com.sonora.player

import com.sonora.player.data.remote.HitmoParser
import com.sonora.player.data.remote.ItunesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteServicesTest {

    @Test
    fun testItunesSearch() = runBlocking {
        val api = ItunesApi()
        val results = api.searchArtworks("Eminem", "Lose Yourself", 2)
        assertTrue(results.isNotEmpty())
        val first = results.first()
        println("iTunes found: ${first.artistName} - ${first.trackName}, HD: ${first.artworkUrlHd}")
        assertTrue(first.artworkUrlHd.contains("1000x1000bb"))
    }

    @Test
    fun testHitmoTopToday() = runBlocking {
        val parser = HitmoParser()
        val songs = parser.getTopToday(page = 1)
        println("Hitmo parsed ${songs.size} top songs")
        assertTrue(songs.isNotEmpty())
        val first = songs.first()
        println("First song: ${first.artist} - ${first.title}, DL: ${first.downloadUrl}")
        assertTrue(first.downloadUrl.contains("/get/music/"))
    }
}
