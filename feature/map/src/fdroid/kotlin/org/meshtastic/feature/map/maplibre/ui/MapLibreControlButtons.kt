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

package org.meshtastic.feature.map.maplibre.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationDisabled
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.sources.GeoJsonSource
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.ui.theme.StatusColors.StatusRed
import org.meshtastic.feature.map.BaseMapViewModel
import org.meshtastic.feature.map.MapViewModel
import org.meshtastic.feature.map.component.MapButton
import org.meshtastic.feature.map.maplibre.BaseMapStyle
import org.meshtastic.feature.map.maplibre.MapLibreConstants.NODES_CLUSTER_SOURCE_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.NODES_SOURCE_ID
import org.meshtastic.feature.map.maplibre.core.nodesToFeatureCollectionJsonWithSelection
import org.meshtastic.feature.map.maplibre.utils.applyFilters
import org.meshtastic.proto.ConfigProtos
import timber.log.Timber

/** Control buttons displayed on the right side of the map */
@Composable
fun MapLibreControlButtons(
    isLocationTrackingEnabled: Boolean,
    onLocationTrackingToggle: () -> Unit,
    hasLocationPermission: Boolean,
    followBearing: Boolean,
    onFollowBearingToggle: () -> Unit,
    onCompassClick: () -> Unit,
    onFilterClick: () -> Unit,
    onLegendClick: () -> Unit,
    onStyleClick: () -> Unit,
    onLayersClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        // Compass button (matches Google Maps style - appears first, rotates with map bearing)
        val compassIcon = if (followBearing) Icons.Filled.Navigation else Icons.Outlined.Navigation
        MapButton(
            onClick = {
                if (isLocationTrackingEnabled) {
                    onFollowBearingToggle()
                    Timber.tag("MapLibrePOC").d("Follow bearing toggled: %s", !followBearing)
                } else {
                    onCompassClick()
                }
            },
            icon = compassIcon,
            contentDescription = null,
            iconTint = MaterialTheme.colorScheme.StatusRed.takeIf { !followBearing },
        )

        Spacer(modifier = Modifier.size(8.dp))

        MapButton(onClick = onFilterClick, icon = Icons.Outlined.Tune, contentDescription = null)

        Spacer(modifier = Modifier.size(8.dp))

        MapButton(onClick = onStyleClick, icon = Icons.Outlined.Map, contentDescription = null)

        Spacer(modifier = Modifier.size(8.dp))

        MapButton(onClick = onLayersClick, icon = Icons.Outlined.Layers, contentDescription = null)

        Spacer(modifier = Modifier.size(8.dp))

        // Location tracking button (matches Google Maps style)
        if (hasLocationPermission) {
            MapButton(
                onClick = {
                    onLocationTrackingToggle()
                    Timber.tag("MapLibrePOC").d("Location tracking toggled: %s", !isLocationTrackingEnabled)
                },
                icon = if (isLocationTrackingEnabled) Icons.Filled.LocationDisabled else Icons.Outlined.MyLocation,
                contentDescription = null,
            )
        }

        Spacer(modifier = Modifier.size(8.dp))

        MapButton(onClick = onLegendClick, icon = Icons.Outlined.Info, contentDescription = null)
    }
}

/** Filter dropdown menu for the map */
@Composable
fun MapFilterMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    mapFilterState: BaseMapViewModel.MapFilterState,
    mapViewModel: MapViewModel,
    nodes: List<Node>,
    enabledRoles: Set<ConfigProtos.Config.DeviceConfig.Role>,
    onRoleToggle: (ConfigProtos.Config.DeviceConfig.Role) -> Unit,
    mapRef: MapLibreMap?,
    onClusteringToggle: () -> Unit,
    clusteringEnabled: Boolean,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
        DropdownMenuItem(
            text = { Text("Only favorites") },
            onClick = {
                mapViewModel.toggleOnlyFavorites()
                onDismissRequest()
            },
            trailingIcon = {
                Checkbox(
                    checked = mapFilterState.onlyFavorites,
                    onCheckedChange = {
                        mapViewModel.toggleOnlyFavorites()
                        // Refresh both sources when filters change
                        mapRef?.style?.let { st ->
                            val filtered =
                                applyFilters(
                                    nodes,
                                    mapFilterState.copy(onlyFavorites = !mapFilterState.onlyFavorites),
                                    enabledRoles,
                                )
                            (st.getSource(NODES_SOURCE_ID) as? GeoJsonSource)?.setGeoJson(
                                nodesToFeatureCollectionJsonWithSelection(filtered, emptySet()),
                            )
                            (st.getSource(NODES_CLUSTER_SOURCE_ID) as? GeoJsonSource)?.setGeoJson(
                                nodesToFeatureCollectionJsonWithSelection(filtered, emptySet()),
                            )
                        }
                    },
                )
            },
        )
        DropdownMenuItem(
            text = { Text("Show precision circle") },
            onClick = {
                mapViewModel.toggleShowPrecisionCircleOnMap()
                onDismissRequest()
            },
            trailingIcon = {
                Checkbox(
                    checked = mapFilterState.showPrecisionCircle,
                    onCheckedChange = { mapViewModel.toggleShowPrecisionCircleOnMap() },
                )
            },
        )
        DropdownMenuItem(
            text = { Text("Enable clustering") },
            onClick = {
                onClusteringToggle()
                onDismissRequest()
            },
            trailingIcon = { Checkbox(checked = clusteringEnabled, onCheckedChange = { onClusteringToggle() }) },
        )
        HorizontalDivider()
        Text(
            text = "Roles",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        val roles = nodes.map { it.user.role }.distinct().sortedBy { it.name }
        roles.forEach { role ->
            val checked = if (enabledRoles.isEmpty()) true else enabledRoles.contains(role)
            DropdownMenuItem(
                text = { Text(role.name.lowercase().replaceFirstChar { it.uppercase() }) },
                onClick = { onRoleToggle(role) },
                trailingIcon = { Checkbox(checked = checked, onCheckedChange = { onRoleToggle(role) }) },
            )
        }
    }
}

/** Map style selection dropdown menu */
@Composable
fun MapStyleMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    baseStyles: List<BaseMapStyle>,
    baseStyleIndex: Int,
    usingCustomTiles: Boolean,
    customTileUrl: String,
    onStyleSelect: (Int) -> Unit,
    onCustomTileClick: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
        Text(
            text = "Map Style",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        baseStyles.forEachIndexed { index, style ->
            DropdownMenuItem(
                text = { Text(style.label) },
                onClick = {
                    onStyleSelect(index)
                    onDismissRequest()
                },
                trailingIcon = {
                    if (index == baseStyleIndex && !usingCustomTiles) {
                        Icon(imageVector = Icons.Outlined.Check, contentDescription = "Selected")
                    }
                },
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = {
                Text(if (customTileUrl.isEmpty()) "Custom Tile URL..." else "Custom: ${customTileUrl.take(30)}...")
            },
            onClick = {
                onDismissRequest()
                onCustomTileClick()
            },
            trailingIcon = {
                if (usingCustomTiles && customTileUrl.isNotEmpty()) {
                    Icon(imageVector = Icons.Outlined.Check, contentDescription = "Selected")
                }
            },
        )
    }
}
