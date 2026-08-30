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
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.meshtastic.feature.map.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.last_heard_filter_label
import org.meshtastic.feature.map.LastHeardFilter
import kotlin.math.roundToInt

/**
 * The filter menu for one node's position track.
 *
 * Only the age cutoff, and a different preference from the main map's: that one hides stale *nodes*, this trims old
 * *points* off one node's history. Nothing else in the main menu means anything here — there are no other nodes to
 * favourite, no waypoints and no precision circles on a track.
 */
@Composable
fun NodeTrackFilterMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    selected: LastHeardFilter,
    onSelect: (LastHeardFilter) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
        LastHeardSlider(selected = selected, onSelect = onSelect)
    }
}

/**
 * The age cutoff, as a slider over [LastHeardFilter]'s own entries.
 *
 * Written out four times before this: twice in each engine, once for the map and once for a track. Shared with
 * [MapFilterSheet], which shows the same control for the map's own cutoff.
 */
@Composable
internal fun LastHeardSlider(selected: LastHeardFilter, onSelect: (LastHeardFilter) -> Unit) {
    val options = LastHeardFilter.entries
    val selectedIndex = options.indexOf(selected)
    var sliderPosition by remember(selectedIndex) { mutableFloatStateOf(selectedIndex.toFloat()) }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(Res.string.last_heard_filter_label, stringResource(selected.label)),
            style = MaterialTheme.typography.labelLarge,
        )
        Slider(
            value = sliderPosition,
            onValueChange = { sliderPosition = it },
            onValueChangeFinished = { onSelect(options[sliderPosition.roundToInt().coerceIn(0, options.lastIndex)]) },
            valueRange = 0f..options.lastIndex.toFloat(),
            steps = options.size - 2,
        )
    }
}
