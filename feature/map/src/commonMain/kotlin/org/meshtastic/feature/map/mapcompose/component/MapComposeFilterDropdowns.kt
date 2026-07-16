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

package org.meshtastic.feature.map.mapcompose.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
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
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.last_heard_filter_label
import org.meshtastic.core.resources.only_favorites
import org.meshtastic.core.resources.show_precision_circle
import org.meshtastic.core.resources.show_waypoints
import org.meshtastic.core.ui.icon.Favorite
import org.meshtastic.core.ui.icon.Lens
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.PinDrop
import org.meshtastic.feature.map.BaseMapViewModel
import org.meshtastic.feature.map.LastHeardFilter
import kotlin.math.roundToInt

/** Main-map filter menu — the shared twin of the flavor-specific `MapFilterDropdown`s, bound to [BaseMapViewModel]. */
@Composable
internal fun MapComposeFilterDropdown(expanded: Boolean, onDismissRequest: () -> Unit, viewModel: BaseMapViewModel) {
    val mapFilterState by viewModel.mapFilterStateFlow.collectAsStateWithLifecycle()
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
        DropdownMenuGroup(shapes = MenuDefaults.groupShapes()) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.only_favorites)) },
                onClick = { viewModel.toggleOnlyFavorites() },
                leadingIcon = {
                    Icon(
                        imageVector = MeshtasticIcons.Favorite,
                        contentDescription = stringResource(Res.string.only_favorites),
                    )
                },
                trailingIcon = {
                    Checkbox(
                        checked = mapFilterState.onlyFavorites,
                        onCheckedChange = { viewModel.toggleOnlyFavorites() },
                    )
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.show_waypoints)) },
                onClick = { viewModel.toggleShowWaypointsOnMap() },
                leadingIcon = {
                    Icon(
                        imageVector = MeshtasticIcons.PinDrop,
                        contentDescription = stringResource(Res.string.show_waypoints),
                    )
                },
                trailingIcon = {
                    Checkbox(
                        checked = mapFilterState.showWaypoints,
                        onCheckedChange = { viewModel.toggleShowWaypointsOnMap() },
                    )
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.show_precision_circle)) },
                onClick = { viewModel.toggleShowPrecisionCircleOnMap() },
                leadingIcon = {
                    Icon(
                        imageVector = MeshtasticIcons.Lens,
                        contentDescription = stringResource(Res.string.show_precision_circle),
                    )
                },
                trailingIcon = {
                    Checkbox(
                        checked = mapFilterState.showPrecisionCircle,
                        onCheckedChange = { viewModel.toggleShowPrecisionCircleOnMap() },
                    )
                },
            )
        }
        LastHeardSlider(
            selected = mapFilterState.lastHeardFilter,
            onSelected = { viewModel.setLastHeardFilter(it) },
        )
    }
}

/** Track-map filter menu: just the last-heard track slider. */
@Composable
internal fun MapComposeTrackFilterDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    viewModel: BaseMapViewModel,
) {
    val mapFilterState by viewModel.mapFilterStateFlow.collectAsStateWithLifecycle()
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
        LastHeardSlider(
            selected = mapFilterState.lastHeardTrackFilter,
            onSelected = { viewModel.setLastHeardTrackFilter(it) },
        )
    }
}

@Composable
private fun LastHeardSlider(selected: LastHeardFilter, onSelected: (LastHeardFilter) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        val filterOptions = LastHeardFilter.entries
        val selectedIndex = filterOptions.indexOf(selected)
        var sliderPosition by remember(selectedIndex) { mutableFloatStateOf(selectedIndex.toFloat()) }

        Text(
            text = stringResource(Res.string.last_heard_filter_label, stringResource(selected.label)),
            style = MaterialTheme.typography.labelLarge,
        )
        Slider(
            value = sliderPosition,
            onValueChange = { sliderPosition = it },
            onValueChangeFinished = {
                val newIndex = sliderPosition.roundToInt().coerceIn(0, filterOptions.size - 1)
                onSelected(filterOptions[newIndex])
            },
            valueRange = 0f..(filterOptions.size - 1).toFloat(),
            steps = filterOptions.size - 2,
        )
    }
}
