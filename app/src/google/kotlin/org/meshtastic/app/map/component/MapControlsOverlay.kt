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
package org.meshtastic.app.map.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.app.map.MapViewModel
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.manage_map_layers
import org.meshtastic.core.resources.map_filter
import org.meshtastic.core.resources.map_tile_source
import org.meshtastic.core.resources.orient_north
import org.meshtastic.core.resources.refresh
import org.meshtastic.core.resources.toggle_my_position
import org.meshtastic.core.ui.icon.Layers
import org.meshtastic.core.ui.icon.LocationDisabled
import org.meshtastic.core.ui.icon.Map
import org.meshtastic.core.ui.icon.MapCompass
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.MyLocation
import org.meshtastic.core.ui.icon.Refresh
import org.meshtastic.core.ui.icon.Tune
import org.meshtastic.core.ui.theme.StatusColors.StatusRed

@Composable
fun MapControlsOverlay(
    modifier: Modifier = Modifier,
    mapFilterMenuExpanded: Boolean,
    onMapFilterMenuDismissRequest: () -> Unit,
    onToggleMapFilterMenu: () -> Unit,
    mapViewModel: MapViewModel, // For MapFilterDropdown and MapTypeDropdown
    mapTypeMenuExpanded: Boolean,
    onMapTypeMenuDismissRequest: () -> Unit,
    onToggleMapTypeMenu: () -> Unit,
    onManageLayersClicked: () -> Unit,
    onManageCustomTileProvidersClicked: () -> Unit,
    // Location tracking parameters
    isLocationTrackingEnabled: Boolean = false,
    onToggleLocationTracking: () -> Unit = {},
    bearing: Float = 0f,
    onCompassClick: () -> Unit = {},
    followPhoneBearing: Boolean,
    showRefresh: Boolean = false,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
) {
    Row(modifier = modifier) {
        CompassButton(onClick = onCompassClick, bearing = bearing, isFollowing = followPhoneBearing)
        Box {
            MapButton(
                icon = MeshtasticIcons.Tune,
                contentDescription = stringResource(Res.string.map_filter),
                onClick = onToggleMapFilterMenu,
            )
            MapFilterDropdown(
                expanded = mapFilterMenuExpanded,
                onDismissRequest = onMapFilterMenuDismissRequest,
                mapViewModel = mapViewModel,
            )
        }

        Box {
            MapButton(
                icon = MeshtasticIcons.Map,
                contentDescription = stringResource(Res.string.map_tile_source),
                onClick = onToggleMapTypeMenu,
            )
            MapTypeDropdown(
                expanded = mapTypeMenuExpanded,
                onDismissRequest = onMapTypeMenuDismissRequest,
                mapViewModel = mapViewModel, // Pass mapViewModel
                onManageCustomTileProvidersClicked = onManageCustomTileProvidersClicked, // Pass new callback
            )
        }

        MapButton(
            icon = MeshtasticIcons.Layers,
            contentDescription = stringResource(Res.string.manage_map_layers),
            onClick = onManageLayersClicked,
        )

        if (showRefresh) {
            if (isRefreshing) {
                Box(modifier = Modifier.padding(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            } else {
                MapButton(
                    icon = MeshtasticIcons.Refresh,
                    contentDescription = stringResource(Res.string.refresh),
                    onClick = onRefresh,
                )
            }
        }

        // Location tracking button
        MapButton(
            icon =
            if (isLocationTrackingEnabled) {
                MeshtasticIcons.LocationDisabled
            } else {
                MeshtasticIcons.MyLocation
            },
            contentDescription = stringResource(Res.string.toggle_my_position),
            onClick = onToggleLocationTracking,
        )
    }
}

@Composable
private fun CompassButton(onClick: () -> Unit, bearing: Float, isFollowing: Boolean) {
    val icon = if (isFollowing) MeshtasticIcons.MapCompass else MeshtasticIcons.MapCompass

    MapButton(
        modifier = Modifier.rotate(-bearing),
        icon = icon,
        iconTint = MaterialTheme.colorScheme.StatusRed.takeIf { bearing == 0f },
        contentDescription = stringResource(Res.string.orient_north),
        onClick = onClick,
    )
}
