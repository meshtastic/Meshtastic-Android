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

import android.graphics.PointF
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.sources.GeoJsonSource
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.ui.theme.StatusColors.StatusRed
import org.meshtastic.feature.map.BaseMapViewModel.MapFilterState
import org.meshtastic.feature.map.component.MapButton
import org.meshtastic.feature.map.maplibre.BaseMapStyle
import org.meshtastic.feature.map.maplibre.MapLibreConstants.DEG_D
import org.meshtastic.feature.map.maplibre.MapLibreConstants.NODES_CLUSTER_SOURCE_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.NODES_SOURCE_ID
import org.meshtastic.feature.map.maplibre.core.nodesToFeatureCollectionJsonWithSelection
import org.meshtastic.feature.map.maplibre.core.safeSetGeoJson
import org.meshtastic.feature.map.maplibre.utils.protoShortName
import org.meshtastic.feature.map.maplibre.utils.roleColor
import org.meshtastic.feature.map.maplibre.utils.shortNameFallback
import org.meshtastic.proto.ConfigProtos
import timber.log.Timber

/**
 * Role legend overlay showing colors for different node roles
 */
@Composable
fun RoleLegend(
    nodes: List<Node>,
    modifier: Modifier = Modifier,
) {
    val rolesPresent = nodes.map { it.user.role }.toSet()

    if (rolesPresent.isNotEmpty()) {
        Surface(
            modifier = modifier,
            tonalElevation = 4.dp,
            shadowElevation = 4.dp,
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                rolesPresent.take(6).forEach { role ->
                    val fakeNode =
                        Node(
                            num = 0,
                            user = org.meshtastic.proto.MeshProtos.User.newBuilder().setRole(role).build(),
                        )
                    Row(
                        modifier = Modifier.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = roleColor(fakeNode),
                            modifier = Modifier.size(12.dp),
                        ) {}
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = role.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Map toolbar with GPS, filter, map style, and layers controls
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MapToolbar(
    hasLocationPermission: Boolean,
    isLocationTrackingEnabled: Boolean,
    followBearing: Boolean,
    onLocationTrackingChanged: (enabled: Boolean, follow: Boolean) -> Unit,
    mapFilterState: MapFilterState,
    onToggleOnlyFavorites: () -> Unit,
    onToggleShowPrecisionCircle: () -> Unit,
    nodes: List<Node>,
    enabledRoles: Set<ConfigProtos.Config.DeviceConfig.Role>,
    onRoleToggled: (ConfigProtos.Config.DeviceConfig.Role) -> Unit,
    clusteringEnabled: Boolean,
    onClusteringToggled: (Boolean) -> Unit,
    baseStyles: List<BaseMapStyle>,
    baseStyleIndex: Int,
    usingCustomTiles: Boolean,
    onStyleSelected: (Int) -> Unit,
    customTileUrl: String,
    onCustomTileClicked: () -> Unit,
    onShowLayersClicked: () -> Unit,
    onShowCacheClicked: () -> Unit,
    onShowLegendToggled: () -> Unit,
    heatmapEnabled: Boolean,
    onHeatmapToggled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var mapFilterExpanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var mapTypeMenuExpanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    HorizontalFloatingToolbar(
        modifier = modifier,
        expanded = true,
        content = {
            // Consolidated GPS button (cycles through: Off -> On -> On with bearing)
            if (hasLocationPermission) {
                val gpsIcon = when {
                    isLocationTrackingEnabled && followBearing -> Icons.Filled.MyLocation
                    isLocationTrackingEnabled -> Icons.Filled.MyLocation
                    else -> Icons.Outlined.MyLocation
                }
                MapButton(
                    onClick = {
                        when {
                            !isLocationTrackingEnabled -> {
                                // Off -> On
                                onLocationTrackingChanged(true, false)
                                Timber.tag("MapLibrePOC").d("GPS tracking enabled")
                            }
                            isLocationTrackingEnabled && !followBearing -> {
                                // On -> On with bearing
                                onLocationTrackingChanged(true, true)
                                Timber.tag("MapLibrePOC").d("GPS tracking with bearing enabled")
                            }
                            else -> {
                                // On with bearing -> Off
                                onLocationTrackingChanged(false, false)
                                Timber.tag("MapLibrePOC").d("GPS tracking disabled")
                            }
                        }
                    },
                    icon = gpsIcon,
                    contentDescription = null,
                    iconTint = MaterialTheme.colorScheme.StatusRed.takeIf { isLocationTrackingEnabled && !followBearing },
                )
            }

            // Filter menu
            Box {
                MapButton(onClick = { mapFilterExpanded = true }, icon = Icons.Outlined.Tune, contentDescription = null)
                DropdownMenu(expanded = mapFilterExpanded, onDismissRequest = { mapFilterExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Only favorites") },
                        onClick = {
                            onToggleOnlyFavorites()
                            mapFilterExpanded = false
                        },
                        trailingIcon = {
                            Checkbox(
                                checked = mapFilterState.onlyFavorites,
                                onCheckedChange = { onToggleOnlyFavorites() },
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Show precision circle") },
                        onClick = {
                            onToggleShowPrecisionCircle()
                            mapFilterExpanded = false
                        },
                        trailingIcon = {
                            Checkbox(
                                checked = mapFilterState.showPrecisionCircle,
                                onCheckedChange = { onToggleShowPrecisionCircle() },
                            )
                        },
                    )
                    androidx.compose.material3.HorizontalDivider()
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
                            onClick = {
                                onRoleToggled(role)
                            },
                            trailingIcon = {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { onRoleToggled(role) },
                                )
                            },
                        )
                    }
                    androidx.compose.material3.HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Enable clustering") },
                        onClick = {
                            onClusteringToggled(!clusteringEnabled)
                        },
                        trailingIcon = {
                            Checkbox(
                                checked = clusteringEnabled,
                                onCheckedChange = { onClusteringToggled(it) },
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Show heatmap") },
                        onClick = {
                            onHeatmapToggled()
                            mapFilterExpanded = false
                        },
                        trailingIcon = {
                            Checkbox(
                                checked = heatmapEnabled,
                                onCheckedChange = { onHeatmapToggled() },
                            )
                        },
                    )
                }
            }

            // Map style selector
            Box {
                MapButton(
                    onClick = { mapTypeMenuExpanded = true },
                    icon = Icons.Outlined.Map,
                    contentDescription = null,
                )
                DropdownMenu(expanded = mapTypeMenuExpanded, onDismissRequest = { mapTypeMenuExpanded = false }) {
                    Text(
                        text = "Map Style",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    baseStyles.forEachIndexed { index, style ->
                        DropdownMenuItem(
                            text = { Text(style.label) },
                            onClick = {
                                onStyleSelected(index)
                                mapTypeMenuExpanded = false
                            },
                            trailingIcon = {
                                if (index == baseStyleIndex && !usingCustomTiles) {
                                    Icon(imageVector = Icons.Outlined.Check, contentDescription = "Selected")
                                }
                            },
                        )
                    }
                    androidx.compose.material3.HorizontalDivider()
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (customTileUrl.isEmpty()) {
                                    "Custom Tile URL..."
                                } else {
                                    "Custom: ${customTileUrl.take(30)}..."
                                },
                            )
                        },
                        onClick = {
                            mapTypeMenuExpanded = false
                            onCustomTileClicked()
                        },
                        trailingIcon = {
                            if (usingCustomTiles && customTileUrl.isNotEmpty()) {
                                Icon(imageVector = Icons.Outlined.Check, contentDescription = "Selected")
                            }
                        },
                    )
                }
            }

            // Map layers button
            MapButton(
                onClick = onShowLayersClicked,
                icon = Icons.Outlined.Layers,
                contentDescription = null,
            )

            // Cache management button
            MapButton(
                onClick = onShowCacheClicked,
                icon = Icons.Outlined.Storage,
                contentDescription = null,
            )

            // Legend button
            MapButton(onClick = onShowLegendToggled, icon = Icons.Outlined.Info, contentDescription = null)
        },
    )
}

/**
 * Zoom controls (zoom in/out buttons)
 */
@Composable
fun ZoomControls(
    mapRef: MapLibreMap?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
        ) {
            // Zoom in button
            MapButton(
                onClick = {
                    mapRef?.let { map ->
                        map.animateCamera(CameraUpdateFactory.zoomIn())
                        Timber.tag("MapLibrePOC").d("Zoom in")
                    }
                },
                icon = Icons.Outlined.Add,
                contentDescription = "Zoom in",
            )

            Spacer(modifier = Modifier.size(4.dp))

            // Zoom out button
            MapButton(
                onClick = {
                    mapRef?.let { map ->
                        map.animateCamera(CameraUpdateFactory.zoomOut())
                        Timber.tag("MapLibrePOC").d("Zoom out")
                    }
                },
                icon = Icons.Outlined.Remove,
                contentDescription = "Zoom out",
            )
        }
    }
}

/**
 * Custom tile URL configuration dialog
 */
@Composable
fun CustomTileDialog(
    customTileUrlInput: String,
    onCustomTileUrlInputChanged: (String) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom Tile URL") },
        text = {
            Column {
                Text(
                    text = "Enter tile URL with {z}/{x}/{y} placeholders:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(
                    text = "Example: https://tile.openstreetmap.org/{z}/{x}/{y}.png",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                OutlinedTextField(
                    value = customTileUrlInput,
                    onValueChange = onCustomTileUrlInputChanged,
                    label = { Text("Tile URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onApply) {
                Text("Apply")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Radial overlay showing cluster members in a circle
 */
@Composable
fun ClusterRadialOverlay(
    centerPx: PointF,
    members: List<Node>,
    density: Float,
    onNodeClicked: (Node) -> Unit,
    modifier: Modifier = Modifier,
) {
    val centerX = (centerPx.x / density).dp
    val centerY = (centerPx.y / density).dp
    val radiusPx = 72f * density
    val itemSize = 40.dp
    val n = members.size.coerceAtLeast(1)

    members.forEachIndexed { idx, node ->
        val theta = (2.0 * Math.PI * idx / n)
        val x = (centerPx.x + (radiusPx * kotlin.math.cos(theta))).toFloat()
        val y = (centerPx.y + (radiusPx * kotlin.math.sin(theta))).toFloat()
        val xDp = (x / density).dp
        val yDp = (y / density).dp
        val label = (protoShortName(node) ?: shortNameFallback(node)).take(4)
        val itemHeight = 36.dp
        val itemWidth = (40 + label.length * 10).dp

        Surface(
            modifier = modifier
                .offset(x = xDp - itemWidth / 2, y = yDp - itemHeight / 2)
                .size(width = itemWidth, height = itemHeight)
                .clickable { onNodeClicked(node) },
            shape = CircleShape,
            color = roleColor(node),
            shadowElevation = 6.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = label, color = Color.White, maxLines = 1)
            }
        }
    }
}
