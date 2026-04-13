/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
import androidx.compose.ui.unit.dp
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
import org.meshtastic.feature.map.BaseMapViewModel.MapFilterState
import org.meshtastic.feature.map.LastHeardFilter
import kotlin.math.roundToInt

/**
 * Dropdown menu for filtering map markers by favorites, waypoints, precision circles, and last-heard time.
 *
 * Mirrors the old Google/F-Droid `MapFilterDropdown` — checkboxes for boolean toggles and a slider for last-heard time
 * filter.
 */
@Composable
internal fun MapFilterDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    filterState: MapFilterState,
    onToggleFavorites: () -> Unit,
    onToggleWaypoints: () -> Unit,
    onTogglePrecisionCircle: () -> Unit,
    onSetLastHeardFilter: (LastHeardFilter) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.only_favorites)) },
            onClick = onToggleFavorites,
            leadingIcon = {
                Icon(
                    imageVector = MeshtasticIcons.Favorite,
                    contentDescription = stringResource(Res.string.only_favorites),
                )
            },
            trailingIcon = { Checkbox(checked = filterState.onlyFavorites, onCheckedChange = { onToggleFavorites() }) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.show_waypoints)) },
            onClick = onToggleWaypoints,
            leadingIcon = {
                Icon(
                    imageVector = MeshtasticIcons.PinDrop,
                    contentDescription = stringResource(Res.string.show_waypoints),
                )
            },
            trailingIcon = { Checkbox(checked = filterState.showWaypoints, onCheckedChange = { onToggleWaypoints() }) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.show_precision_circle)) },
            onClick = onTogglePrecisionCircle,
            leadingIcon = {
                Icon(
                    imageVector = MeshtasticIcons.Lens,
                    contentDescription = stringResource(Res.string.show_precision_circle),
                )
            },
            trailingIcon = {
                Checkbox(checked = filterState.showPrecisionCircle, onCheckedChange = { onTogglePrecisionCircle() })
            },
        )
        HorizontalDivider()
        LastHeardSlider(filterState.lastHeardFilter, onSetLastHeardFilter)
    }
}

@Composable
private fun LastHeardSlider(currentFilter: LastHeardFilter, onSetFilter: (LastHeardFilter) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        val filterOptions = LastHeardFilter.entries
        val selectedIndex = filterOptions.indexOf(currentFilter)
        var sliderPosition by remember(selectedIndex) { mutableFloatStateOf(selectedIndex.toFloat()) }

        Text(
            text = stringResource(Res.string.last_heard_filter_label, stringResource(currentFilter.label)),
            style = MaterialTheme.typography.labelLarge,
        )
        Slider(
            value = sliderPosition,
            onValueChange = { sliderPosition = it },
            onValueChangeFinished = {
                val newIndex = sliderPosition.roundToInt().coerceIn(0, filterOptions.size - 1)
                onSetFilter(filterOptions[newIndex])
            },
            valueRange = 0f..(filterOptions.size - 1).toFloat(),
            steps = filterOptions.size - 2,
        )
    }
}
