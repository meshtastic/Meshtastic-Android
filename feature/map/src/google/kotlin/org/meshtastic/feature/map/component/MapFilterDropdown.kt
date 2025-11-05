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

package org.meshtastic.feature.map.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.meshtastic.feature.map.LastHeardFilter
import org.meshtastic.feature.map.MapViewModel
import kotlin.math.roundToInt
import org.meshtastic.core.strings.R as Res

@Composable
internal fun MapFilterDropdown(expanded: Boolean, onDismissRequest: () -> Unit, mapViewModel: MapViewModel) {
    val mapFilterState by mapViewModel.mapFilterStateFlow.collectAsStateWithLifecycle()
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.only_favorites)) },
            onClick = { mapViewModel.toggleOnlyFavorites() },
            leadingIcon = {
                Icon(imageVector = Icons.Filled.Star, contentDescription = stringResource(Res.string.only_favorites))
            },
            trailingIcon = {
                Checkbox(
                    checked = mapFilterState.onlyFavorites,
                    onCheckedChange = { mapViewModel.toggleOnlyFavorites() },
                )
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.show_waypoints)) },
            onClick = { mapViewModel.toggleShowWaypointsOnMap() },
            leadingIcon = {
                Icon(imageVector = Icons.Filled.Place, contentDescription = stringResource(Res.string.show_waypoints))
            },
            trailingIcon = {
                Checkbox(
                    checked = mapFilterState.showWaypoints,
                    onCheckedChange = { mapViewModel.toggleShowWaypointsOnMap() },
                )
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.show_precision_circle)) },
            onClick = { mapViewModel.toggleShowPrecisionCircleOnMap() },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.RadioButtonUnchecked, // Placeholder icon
                    contentDescription = stringResource(Res.string.show_precision_circle),
                )
            },
            trailingIcon = {
                Checkbox(
                    checked = mapFilterState.showPrecisionCircle,
                    onCheckedChange = { mapViewModel.toggleShowPrecisionCircleOnMap() },
                )
            },
        )
        HorizontalDivider()
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            val filterOptions = LastHeardFilter.entries
            val selectedIndex = filterOptions.indexOf(mapFilterState.lastHeardFilter)
            var sliderPosition by remember(selectedIndex) { mutableFloatStateOf(selectedIndex.toFloat()) }

            Text(
                text =
                stringResource(
                    Res.string.last_heard_filter_label,
                    stringResource(mapFilterState.lastHeardFilter.label),
                ),
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = sliderPosition,
                onValueChange = { sliderPosition = it },
                onValueChangeFinished = {
                    val newIndex = sliderPosition.roundToInt().coerceIn(0, filterOptions.size - 1)
                    mapViewModel.setLastHeardFilter(filterOptions[newIndex])
                },
                valueRange = 0f..(filterOptions.size - 1).toFloat(),
                steps = filterOptions.size - 2,
            )
        }
    }
}

@Composable
internal fun NodeMapFilterDropdown(expanded: Boolean, onDismissRequest: () -> Unit, mapViewModel: MapViewModel) {
    val mapFilterState by mapViewModel.mapFilterStateFlow.collectAsStateWithLifecycle()
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            val filterOptions = LastHeardFilter.entries
            val selectedIndex = filterOptions.indexOf(mapFilterState.lastHeardTrackFilter)
            var sliderPosition by remember(selectedIndex) { mutableFloatStateOf(selectedIndex.toFloat()) }

            Text(
                text =
                stringResource(
                    Res.string.last_heard_filter_label,
                    stringResource(mapFilterState.lastHeardTrackFilter.label),
                ),
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = sliderPosition,
                onValueChange = { sliderPosition = it },
                onValueChangeFinished = {
                    val newIndex = sliderPosition.roundToInt().coerceIn(0, filterOptions.size - 1)
                    mapViewModel.setLastHeardTrackFilter(filterOptions[newIndex])
                },
                valueRange = 0f..(filterOptions.size - 1).toFloat(),
                steps = filterOptions.size - 2,
            )
        }
    }
}
