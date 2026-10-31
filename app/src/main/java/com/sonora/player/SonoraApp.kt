package com.sonora.player

import android.app.Application
import com.sonora.player.data.playlist.M3uParser
import com.sonora.player.data.playlist.PlaylistRepository
import com.sonora.player.data.remote.GithubUpdateChecker
import com.sonora.player.data.remote.HitmoParser
import com.sonora.player.data.remote.ItunesApi
import com.sonora.player.data.scanner.AudioScanner
import com.sonora.player.data.tagger.Id3Tagger
import com.sonora.player.service.EqualizerManager
import com.sonora.player.service.SonoraDownloadManager

class SonoraApp : Application() {

    lateinit var hitmoParser: HitmoParser
        private set
    lateinit var itunesApi: ItunesApi
        private set
    lateinit var id3Tagger: Id3Tagger
        private set
    lateinit var audioScanner: AudioScanner
        private set
    lateinit var m3uParser: M3uParser
        private set
    lateinit var playlistRepository: PlaylistRepository
        private set
    lateinit var downloadManager: SonoraDownloadManager
        private set
    lateinit var equalizerManager: EqualizerManager
        private set
    lateinit var updateChecker: GithubUpdateChecker
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        hitmoParser = HitmoParser()
        itunesApi = ItunesApi()
        id3Tagger = Id3Tagger(this)
        audioScanner = AudioScanner(this, id3Tagger)
        m3uParser = M3uParser()
        playlistRepository = PlaylistRepository(this)
        downloadManager = SonoraDownloadManager(this, hitmoParser, itunesApi, id3Tagger)
        equalizerManager = EqualizerManager(this)
        updateChecker = GithubUpdateChecker()
    }

    companion object {
        lateinit var instance: SonoraApp
            private set
    }
}
