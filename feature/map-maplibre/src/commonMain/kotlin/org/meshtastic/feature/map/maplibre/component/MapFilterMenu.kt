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
package org.meshtastic.feature.map.maplibre.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.last_heard_filter_label
import org.meshtastic.core.resources.only_favorites
import org.meshtastic.core.resources.show_precision_circle
import org.meshtastic.core.resources.show_waypoints
import org.meshtastic.feature.map.LastHeardFilter
import org.meshtastic.feature.map.SharedMapViewModel
import kotlin.math.roundToInt

@Composable
internal fun FilterMenu(expanded: Boolean, onDismissRequest: () -> Unit) {
    val viewModel: SharedMapViewModel = koinViewModel()
    val filterState by viewModel.mapFilterStateFlow.collectAsStateWithLifecycle()

    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
        CheckableItem(
            label = stringResource(Res.string.only_favorites),
            checked = filterState.onlyFavorites,
            onClick = viewModel::toggleOnlyFavorites,
        )
        CheckableItem(
            label = stringResource(Res.string.show_waypoints),
            checked = filterState.showWaypoints,
            onClick = viewModel::toggleShowWaypointsOnMap,
        )
        CheckableItem(
            label = stringResource(Res.string.show_precision_circle),
            checked = filterState.showPrecisionCircle,
            onClick = viewModel::toggleShowPrecisionCircleOnMap,
        )

        // filterNodesForMap has always applied this one; until now nothing could change it, so it sat on whatever
        // happened to be persisted.
        LastHeardSlider(selected = filterState.lastHeardFilter, onSelect = viewModel::setLastHeardFilter)
    }
}

/**
 * The age cutoff for a single node's position track.
 *
 * A separate preference from the main map's, and separately applied: the main map hides stale *nodes*, this trims old
 * *points* off one node's history. Only the one control, because nothing else in the main map's menu means anything on
 * a track — there are no other nodes to favourite, no waypoints and no precision circles.
 */
@Composable
internal fun TrackFilterMenu(expanded: Boolean, onDismissRequest: () -> Unit) {
    val viewModel: SharedMapViewModel = koinViewModel()
    val filterState by viewModel.mapFilterStateFlow.collectAsStateWithLifecycle()

    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
        LastHeardSlider(selected = filterState.lastHeardTrackFilter, onSelect = viewModel::setLastHeardTrackFilter)
    }
}

/** The age cutoff for nodes on the map, presented as the Google flavor does: a slider over [LastHeardFilter]. */
@Composable
private fun LastHeardSlider(selected: LastHeardFilter, onSelect: (LastHeardFilter) -> Unit) {
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

@Composable
private fun CheckableItem(label: String, checked: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text = label) },
        leadingIcon = { Checkbox(checked = checked, onCheckedChange = { onClick() }) },
        onClick = onClick,
    )
}
