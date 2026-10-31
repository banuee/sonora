package com.sonora.player.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sonora.player.service.EqualizerManager

@Composable
fun EqualizerDialog(
    equalizerManager: EqualizerManager,
    onDismiss: () -> Unit
) {
    val isEnabled by equalizerManager.isEnabled.collectAsState()
    val bandLevels by equalizerManager.bandLevels.collectAsState()
    val bandFreqs by equalizerManager.bandCenterFreqs.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Эквалайзер", style = MaterialTheme.typography.titleLarge)
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { equalizerManager.setEnabled(it) }
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (bandLevels.isEmpty()) {
                    Text(
                        text = "Эквалайзер недоступен для текущего потока",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    bandFreqs.keys.sorted().forEach { band ->
                        val freqHz = bandFreqs[band] ?: 0
                        val currentLevel = bandLevels[band] ?: 0
                        val label = if (freqHz >= 1000000) {
                            "${freqHz / 1000000} kHz"
                        } else if (freqHz >= 1000) {
                            "${freqHz / 1000} Hz"
                        } else {
                            "$freqHz Hz"
                        }

                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(label, style = MaterialTheme.typography.labelMedium)
                                Text("${currentLevel / 100} dB", style = MaterialTheme.typography.labelSmall)
                            }

                            Slider(
                                value = currentLevel.toFloat(),
                                onValueChange = {
                                    equalizerManager.setBandLevel(band, it.toInt().toShort())
                                },
                                valueRange = equalizerManager.minLevel.toFloat()..equalizerManager.maxLevel.toFloat(),
                                enabled = isEnabled,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Готово")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                bandFreqs.keys.forEach { band ->
                    equalizerManager.setBandLevel(band, 0)
                }
            }) {
                Text("Сброс")
            }
        }
    )
}
