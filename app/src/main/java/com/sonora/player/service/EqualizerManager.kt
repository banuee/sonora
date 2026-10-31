package com.sonora.player.service

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class EqualizerManager(private val context: Context) {

    private var equalizer: Equalizer? = null
    private var currentSessionId: Int = 0

    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _bandLevels = MutableStateFlow<Map<Short, Short>>(emptyMap())
    val bandLevels: StateFlow<Map<Short, Short>> = _bandLevels.asStateFlow()

    private val _bandCenterFreqs = MutableStateFlow<Map<Short, Int>>(emptyMap())
    val bandCenterFreqs: StateFlow<Map<Short, Int>> = _bandCenterFreqs.asStateFlow()

    var minLevel: Short = -1500
        private set
    var maxLevel: Short = 1500
        private set

    fun attachToSession(sessionId: Int) {
        if (sessionId <= 0 || sessionId == currentSessionId) return
        currentSessionId = sessionId

        try {
            equalizer?.release()
            val eq = Equalizer(0, sessionId)
            equalizer = eq
            eq.enabled = true
            _isEnabled.value = true

            val range = eq.bandLevelRange
            if (range.size >= 2) {
                minLevel = range[0]
                maxLevel = range[1]
            }

            val numBands = eq.numberOfBands
            val freqs = mutableMapOf<Short, Int>()
            val levels = mutableMapOf<Short, Short>()

            for (i in 0 until numBands) {
                val band = i.toShort()
                freqs[band] = eq.getCenterFreq(band)
                levels[band] = eq.getBandLevel(band)
            }

            _bandCenterFreqs.value = freqs
            _bandLevels.value = levels
        } catch (_: Exception) {}
    }

    fun setBandLevel(band: Short, level: Short) {
        try {
            equalizer?.setBandLevel(band, level)
            val updated = _bandLevels.value.toMutableMap()
            updated[band] = level
            _bandLevels.value = updated
        } catch (_: Exception) {}
    }

    fun setEnabled(enabled: Boolean) {
        try {
            equalizer?.enabled = enabled
            _isEnabled.value = enabled
        } catch (_: Exception) {}
    }

    fun openSystemEqualizer(sessionId: Int): Boolean {
        return try {
            val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun release() {
        try {
            equalizer?.release()
        } catch (_: Exception) {}
        equalizer = null
    }
}
