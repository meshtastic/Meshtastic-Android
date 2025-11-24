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

package org.meshtastic.feature.map.maplibre.core

import android.content.Context
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.TransitionOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.meshtastic.core.database.model.Node
import org.meshtastic.feature.map.BaseMapViewModel
import org.meshtastic.feature.map.maplibre.MapLibreConstants.NODES_CLUSTER_SOURCE_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.NODES_SOURCE_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.WAYPOINTS_SOURCE_ID
import org.meshtastic.feature.map.maplibre.utils.applyFilters
import org.meshtastic.feature.map.maplibre.utils.hasAnyLocationPermission
import org.meshtastic.feature.map.maplibre.utils.selectLabelsForViewport
import org.meshtastic.proto.ConfigProtos
import timber.log.Timber

/** Activates the location component after a style change */
fun activateLocationComponentForStyle(context: Context, map: MapLibreMap, style: Style) {
    try {
        if (hasAnyLocationPermission(context)) {
            val locationComponent = map.locationComponent
            locationComponent.activateLocationComponent(
                org.maplibre.android.location.LocationComponentActivationOptions.builder(context, style)
                    .useDefaultLocationEngine(true)
                    .build(),
            )
            locationComponent.isLocationComponentEnabled = true
            locationComponent.renderMode = RenderMode.COMPASS
        }
    } catch (_: SecurityException) {
        Timber.tag("MapLibrePOC").w("Location permissions not granted")
    }
}

/** Updates node data sources after filtering or style changes */
fun updateNodeDataSources(
    map: MapLibreMap,
    style: Style,
    nodes: List<Node>,
    mapFilterState: BaseMapViewModel.MapFilterState,
    enabledRoles: Set<ConfigProtos.Config.DeviceConfig.Role>,
    ourNodeNum: Int?,
    isLocationTrackingEnabled: Boolean,
    clusteringEnabled: Boolean,
    currentClustersShown: Boolean,
    density: Float,
): Boolean {
    val filtered = applyFilters(nodes, mapFilterState, enabledRoles, ourNodeNum, isLocationTrackingEnabled)
    val labelSet = selectLabelsForViewport(map, filtered, density)
    val json = nodesToFeatureCollectionJsonWithSelection(filtered, labelSet)
    safeSetGeoJson(style, NODES_CLUSTER_SOURCE_ID, json)
    safeSetGeoJson(style, NODES_SOURCE_ID, json)
    return setClusterVisibilityHysteresis(
        map,
        style,
        filtered,
        clusteringEnabled,
        currentClustersShown,
        mapFilterState.showPrecisionCircle,
    )
}

/** Reinitializes style after a style switch (base map change or custom tiles) */
fun reinitializeStyleAfterSwitch(
    context: Context,
    map: MapLibreMap,
    style: Style,
    waypoints: Map<Int, org.meshtastic.core.database.entity.Packet>,
    nodes: List<Node>,
    mapFilterState: BaseMapViewModel.MapFilterState,
    enabledRoles: Set<ConfigProtos.Config.DeviceConfig.Role>,
    ourNodeNum: Int?,
    isLocationTrackingEnabled: Boolean,
    clusteringEnabled: Boolean,
    currentClustersShown: Boolean,
    density: Float,
): Boolean {
    style.setTransition(TransitionOptions(1000, 0))
    ensureSourcesAndLayers(style)
    // Repopulate waypoints
    (style.getSource(WAYPOINTS_SOURCE_ID) as? GeoJsonSource)?.setGeoJson(
        waypointsToFeatureCollectionFC(waypoints.values),
    )
    // Re-enable location component
    activateLocationComponentForStyle(context, map, style)
    // Update node data
    return updateNodeDataSources(
        map,
        style,
        nodes,
        mapFilterState,
        enabledRoles,
        ourNodeNum,
        isLocationTrackingEnabled,
        clusteringEnabled,
        currentClustersShown,
        density,
    )
}
