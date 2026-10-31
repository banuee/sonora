package com.sonora.player.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SpeedPitchDialog(
    currentSpeed: Float,
    currentPitch: Float,
    isApplyToAll: Boolean,
    onApply: (speed: Float, pitch: Float, applyToAll: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var speed by remember { mutableFloatStateOf(currentSpeed) }
    var pitch by remember { mutableFloatStateOf(currentPitch) }
    var applyToAll by remember { mutableStateOf(isApplyToAll) }

    val presetSpeeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Скорость и тональность", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Speed
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Скорость", style = MaterialTheme.typography.titleSmall)
                    Text("%.2fx".format(speed), style = MaterialTheme.typography.labelLarge)
                }

                Slider(
                    value = speed,
                    onValueChange = { speed = (it * 20).toInt() / 20f },
                    valueRange = 0.25f..2.0f,
                    modifier = Modifier.fillMaxWidth()
                )

                // Presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presetSpeeds.forEach { p ->
                        FilterChip(
                            selected = kotlin.math.abs(speed - p) < 0.05f,
                            onClick = { speed = p },
                            label = { Text("${p}x") },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Pitch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Высота звука", style = MaterialTheme.typography.titleSmall)
                    Text("%.2fx".format(pitch), style = MaterialTheme.typography.labelLarge)
                }

                Slider(
                    value = pitch,
                    onValueChange = { pitch = (it * 20).toInt() / 20f },
                    valueRange = 0.5f..1.5f,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Apply to all toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Для всех треков", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = applyToAll,
                        onCheckedChange = { applyToAll = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onApply(speed, pitch, applyToAll)
            }) {
                Text("Применить")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    speed = 1.0f
                    pitch = 1.0f
                    onApply(1.0f, 1.0f, applyToAll)
                }) {
                    Text("Сброс")
                }
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(onClick = onDismiss) {
                    Text("Отмена")
                }
            }
        }
    )
}
