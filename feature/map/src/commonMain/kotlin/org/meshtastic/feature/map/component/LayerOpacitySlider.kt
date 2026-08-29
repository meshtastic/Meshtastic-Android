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
package org.meshtastic.feature.map.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.map_layer_opacity
import org.meshtastic.feature.map.layers.LAYER_OPACITY_OPAQUE
import kotlin.math.roundToInt

/** The tag a layer's opacity slider carries, so a test can find the slider for one specific layer. */
fun layerOpacityTestTag(key: String): String = "layer-opacity-$key"

/**
 * One layer's opacity, as a slider that reports only when the drag ends.
 *
 * The reporting rule is load-bearing, not a nicety: every report is a DataStore write, and a slider that reported per
 * frame would write dozens of times across a single drag. [LastHeardSlider] holds its position the same way.
 *
 * @param layerKey identifies the layer to the caller and, as [layerOpacityTestTag], to a test. See
 *   [org.meshtastic.core.repository.MapPrefs.layerOpacity] for what a key is.
 */
@Composable
fun LayerOpacitySlider(
    layerKey: String,
    opacity: Float,
    onOpacityChange: (String, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keyed on the incoming value so an external change (another surface, a fresh read) is picked up, while a drag in
    // progress stays local.
    var position by remember(opacity) { mutableFloatStateOf(opacity) }

    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            text = stringResource(Res.string.map_layer_opacity, (position * PERCENT).roundToInt()),
            style = MaterialTheme.typography.labelMedium,
        )
        Slider(
            value = position,
            onValueChange = { position = it },
            onValueChangeFinished = { onOpacityChange(layerKey, position) },
            valueRange = 0f..LAYER_OPACITY_OPAQUE,
            modifier = Modifier.testTag(layerOpacityTestTag(layerKey)),
        )
    }
}

private const val PERCENT = 100
