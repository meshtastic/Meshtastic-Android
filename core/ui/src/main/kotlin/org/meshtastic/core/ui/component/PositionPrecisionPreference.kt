/*
 * Copyright (c) 2025 Meshtastic LLC
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

package org.meshtastic.core.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.meshtastic.core.model.util.DistanceUnit
import org.meshtastic.core.model.util.toDistanceString
import kotlin.math.pow
import kotlin.math.roundToInt
import org.meshtastic.core.strings.R as Res

private const val POSITION_ENABLED = 32
private const val POSITION_DISABLED = 0

private const val POSITION_PRECISION_MIN = 10
private const val POSITION_PRECISION_MAX = 19
private const val POSITION_PRECISION_DEFAULT = 13

@Suppress("MagicNumber")
fun precisionBitsToMeters(bits: Int): Double = 23905787.925008 * 0.5.pow(bits.toDouble())

@Composable
fun PositionPrecisionPreference(
    value: Int,
    enabled: Boolean,
    onValueChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val unit = remember { DistanceUnit.getFromLocale() }

    Column(modifier = modifier) {
        SwitchPreference(
            title = stringResource(Res.string.position_enabled),
            checked = value != POSITION_DISABLED,
            enabled = enabled,
            onCheckedChange = { enabled ->
                val newValue = if (enabled) POSITION_ENABLED else POSITION_DISABLED
                onValueChanged(newValue)
            },
            padding = PaddingValues(0.dp),
        )
        if (value != POSITION_DISABLED) {
            SwitchPreference(
                title = stringResource(Res.string.precise_location),
                checked = value == POSITION_ENABLED,
                enabled = enabled,
                onCheckedChange = { enabled ->
                    val newValue = if (enabled) POSITION_ENABLED else POSITION_PRECISION_DEFAULT
                    onValueChanged(newValue)
                },
                padding = PaddingValues(0.dp),
            )
        }
        if (value in (POSITION_DISABLED + 1) until POSITION_ENABLED) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Slider(
                    value = value.toFloat(),
                    onValueChange = { onValueChanged(it.roundToInt()) },
                    enabled = enabled,
                    valueRange = POSITION_PRECISION_MIN.toFloat()..POSITION_PRECISION_MAX.toFloat(),
                    steps = POSITION_PRECISION_MAX - POSITION_PRECISION_MIN - 1,
                )

                val precisionMeters = precisionBitsToMeters(value).toInt()
                Text(
                    text = precisionMeters.toDistanceString(unit),
                    modifier = Modifier.padding(bottom = 16.dp),
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize,
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
        value = POSITION_PRECISION_DEFAULT,
        enabled = true,
        onValueChanged = {},
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}
