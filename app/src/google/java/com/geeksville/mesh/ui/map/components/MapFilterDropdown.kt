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

package com.geeksville.mesh.ui.map.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.ui.map.MapViewModel
import org.meshtastic.core.strings.R
import java.util.concurrent.TimeUnit

@Composable
internal fun MapFilterDropdown(expanded: Boolean, onDismissRequest: () -> Unit, mapViewModel: MapViewModel) {
    val mapFilterState by mapViewModel.mapFilterStateFlow.collectAsStateWithLifecycle()
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
        DropdownMenuItem(
            text = { Text(stringResource(id = R.string.only_favorites)) },
            onClick = { mapViewModel.toggleOnlyFavorites() },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = stringResource(id = R.string.only_favorites),
                )
            },
            trailingIcon = {
                Checkbox(
                    checked = mapFilterState.onlyFavorites,
                    onCheckedChange = { mapViewModel.toggleOnlyFavorites() },
                )
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(id = R.string.show_waypoints)) },
            onClick = { mapViewModel.toggleShowWaypointsOnMap() },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Place,
                    contentDescription = stringResource(id = R.string.show_waypoints),
                )
            },
            trailingIcon = {
                Checkbox(
                    checked = mapFilterState.showWaypoints,
                    onCheckedChange = { mapViewModel.toggleShowWaypointsOnMap() },
                )
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(id = R.string.show_precision_circle)) },
            onClick = { mapViewModel.toggleShowPrecisionCircleOnMap() },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.RadioButtonUnchecked, // Placeholder icon
                    contentDescription = stringResource(id = R.string.show_precision_circle),
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
        Text(
            text = stringResource(R.string.last_heard_filter),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
        )
        mapViewModel.lastHeardFilterOptions.forEach { seconds ->
            val text =
                when (seconds) {
                    0L -> stringResource(R.string.any)
                    TimeUnit.HOURS.toSeconds(1) -> stringResource(R.string.one_hour)
                    TimeUnit.HOURS.toSeconds(8) -> stringResource(R.string.eight_hours)
                    TimeUnit.DAYS.toSeconds(1) -> stringResource(R.string.one_day)
                    else -> seconds.toString()
                }
            DropdownMenuItem(
                text = { Text(text) },
                onClick = {
                    mapViewModel.setLastHeardFilter(seconds)
                    onDismissRequest()
                },
                trailingIcon = {
                    RadioButton(
                        selected = mapFilterState.lastHeardFilter == seconds,
                        onClick = {
                            mapViewModel.setLastHeardFilter(seconds)
                            onDismissRequest()
                        },
                    )
                },
            )
        }
    }
}
