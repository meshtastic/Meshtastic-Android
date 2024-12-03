/*
 * Copyright (c) 2024 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.geeksville.mesh.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.util.DistanceUnit
import com.geeksville.mesh.util.toDistanceString
import kotlin.math.pow
import kotlin.math.roundToInt

private const val PositionEnabled = 32
private const val PositionDisabled = 0

const val PositionPrecisionMin = 10
const val PositionPrecisionMax = 19
const val PositionPrecisionDefault = 13

@Suppress("MagicNumber")
fun precisionBitsToMeters(bits: Int): Double = 23905787.925008 * 0.5.pow(bits.toDouble())

@Composable
fun PositionPrecisionPreference(
    title: String,
    value: Int,
    enabled: Boolean,
    onValueChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val unit = remember { DistanceUnit.getFromLocale() }

    Column(modifier = modifier) {
        SwitchPreference(
            title = title,
            checked = value != PositionDisabled,
            enabled = enabled,
            onCheckedChange = { enabled ->
                val newValue = if (enabled) PositionEnabled else PositionDisabled
                onValueChanged(newValue)
            },
            padding = PaddingValues(0.dp)
        )
        AnimatedVisibility(visible = value != PositionDisabled) {
            SwitchPreference(
                title = "Precise location",
                checked = value == PositionEnabled,
                enabled = enabled,
                onCheckedChange = { enabled ->
                    val newValue = if (enabled) PositionEnabled else PositionPrecisionDefault
                    onValueChanged(newValue)
                },
                padding = PaddingValues(0.dp)
            )
        }
        AnimatedVisibility(visible = value in (PositionDisabled + 1)..<PositionEnabled) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Slider(
                    value = value.toFloat(),
                    onValueChange = { onValueChanged(it.roundToInt()) },
                    enabled = enabled,
                    valueRange = PositionPrecisionMin.toFloat()..PositionPrecisionMax.toFloat(),
                    steps = PositionPrecisionMax - PositionPrecisionMin - 1,
                )

                val precisionMeters = precisionBitsToMeters(value).toInt()
                Text(
                    text = precisionMeters.toDistanceString(unit),
                    modifier = Modifier.padding(bottom = 16.dp),
                    fontSize = MaterialTheme.typography.body1.fontSize,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PositionPrecisionPreferencePreview() {
    PositionPrecisionPreference(
        title = "Position enabled",
        value = PositionPrecisionDefault,
        enabled = true,
        onValueChanged = {},
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}
