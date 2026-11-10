/*
 * Copyright (c) 2026 Meshtastic LLC
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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.color_picker_presets
import org.meshtastic.core.resources.color_rgb_description
import org.meshtastic.core.ui.theme.AppTheme
import kotlin.math.roundToInt

private const val PRESET_HUES = 6
private const val HUE_STEP_DEGREES = 360f / PRESET_HUES
private val SwatchSize = 56.dp
private val PresetTouchSize = 48.dp
private val PresetDotSize = 32.dp

/** Filled rounded square showing the RGB value as stored, not themed. */
@Composable
fun ColorSwatch(red: Int, green: Int, blue: Int, modifier: Modifier = Modifier) {
    val description = stringResource(Res.string.color_rgb_description, red, green, blue)
    Box(
        modifier =
        modifier
            .size(SwatchSize)
            .semantics { contentDescription = description }
            .background(rgbToColor(red, green, blue), RoundedCornerShape(12.dp))
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(12.dp)),
    )
}

@Composable
fun RgbColorPicker(
    red: Int,
    green: Int,
    blue: Int,
    redLabel: String,
    greenLabel: String,
    blueLabel: String,
    enabled: Boolean,
    onColorChange: (red: Int, green: Int, blue: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ColorSwatch(red = red, green = green, blue = blue)
        Text(text = stringResource(Res.string.color_picker_presets), style = MaterialTheme.typography.labelLarge)
        PresetPalette(enabled = enabled, onColorChange = onColorChange)
        ChannelSlider(label = redLabel, value = red, enabled = enabled) { onColorChange(it, green, blue) }
        ChannelSlider(label = greenLabel, value = green, enabled = enabled) { onColorChange(red, it, blue) }
        ChannelSlider(label = blueLabel, value = blue, enabled = enabled) { onColorChange(red, green, it) }
    }
}

@Composable
private fun PresetPalette(enabled: Boolean, onColorChange: (Int, Int, Int) -> Unit) {
    val presets = remember { presetColors() }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        presets.forEach { color ->
            val (r, g, b) = color.toRgbChannels()
            val description = stringResource(Res.string.color_rgb_description, r, g, b)
            Box(
                modifier =
                Modifier.size(PresetTouchSize)
                    .semantics { contentDescription = description }
                    .clickable(enabled = enabled, role = Role.Button) { onColorChange(r, g, b) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(PresetDotSize)
                        .background(color, CircleShape)
                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), CircleShape),
                )
            }
        }
    }
}

@Composable
private fun ChannelSlider(label: String, value: Int, enabled: Boolean, onValueChange: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(text = value.toString(), style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            modifier = Modifier.semantics { contentDescription = label },
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt().coerceIn(0, RGB_CHANNEL_MAX)) },
            valueRange = 0f..RGB_CHANNEL_MAX.toFloat(),
            enabled = enabled,
        )
    }
}

internal fun presetColors(): List<Color> = List(PRESET_HUES) { Color.hsv(it * HUE_STEP_DEGREES, 1f, 1f) } + Color.White

@Suppress("MagicNumber")
@Preview(showBackground = true)
@Composable
private fun RgbColorPickerPreview() {
    AppTheme {
        RgbColorPicker(
            red = 255,
            green = 128,
            blue = 0,
            redLabel = "Red",
            greenLabel = "Green",
            blueLabel = "Blue",
            enabled = true,
            onColorChange = { _, _, _ -> },
        )
    }
}
