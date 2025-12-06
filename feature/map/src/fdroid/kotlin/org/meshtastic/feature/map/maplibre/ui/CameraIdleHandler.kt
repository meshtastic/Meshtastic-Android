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

import android.content.Context
import android.os.SystemClock
import android.view.View
import androidx.compose.runtime.MutableState
import org.maplibre.android.maps.MapLibreMap
import org.meshtastic.core.database.model.Node
import org.meshtastic.feature.map.BaseMapViewModel
import org.meshtastic.feature.map.maplibre.MapLibreConstants.CLUSTER_CIRCLE_LAYER_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.NODES_CLUSTER_SOURCE_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.NODES_LAYER_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.NODES_SOURCE_ID
import org.meshtastic.feature.map.maplibre.core.logStyleState
import org.meshtastic.feature.map.maplibre.core.nodesToFeatureCollectionJsonWithSelection
import org.meshtastic.feature.map.maplibre.core.safeSetGeoJson
import org.meshtastic.feature.map.maplibre.core.setClusterVisibilityHysteresis
import org.meshtastic.feature.map.maplibre.utils.applyFilters
import org.meshtastic.feature.map.maplibre.utils.selectLabelsForViewport
import org.meshtastic.proto.ConfigProtos
import timber.log.Timber

private const val CLUSTER_EVAL_DEBOUNCE_MS = 300L

private var lastClusterEvalMs = 0L

/**
 * Handles camera idle events to manage cluster visibility and label selection.
 *
 * This handler:
 * - Debounces rapid camera movements
 * - Filters nodes based on current map state
 * - Toggles cluster visibility based on zoom level
 * - Selects which nodes should display labels
 * - Updates both clustered and non-clustered data sources
 *
 * @param map The MapLibre map instance
 * @param context Android context for density calculations
 * @param mapViewRef Reference to the MapView for viewport calculations
 * @param nodes All available nodes
 * @param mapFilterState Current map filter settings
 * @param enabledRoles Set of enabled roles for filtering
 * @param ourNode The current device's node info
 * @param isLocationTrackingEnabled Whether location tracking is active
 * @param heatmapEnabled Whether heatmap mode is active
 * @param showingTracksRef Whether track visualization is active
 * @param clusteringEnabled Whether clustering is enabled
 * @param clustersShownState Mutable state tracking whether clusters are currently shown
 */
fun handleCameraIdle(
    map: MapLibreMap,
    context: Context,
    mapViewRef: View?,
    nodes: List<Node>,
    mapFilterState: BaseMapViewModel.MapFilterState,
    enabledRoles: Set<ConfigProtos.Config.DeviceConfig.Role>,
    ourNode: Node?,
    isLocationTrackingEnabled: Boolean,
    heatmapEnabled: Boolean,
    showingTracksRef: MutableState<Boolean>,
    clusteringEnabled: Boolean,
    clustersShownState: MutableState<Boolean>,
) {
    val st = map.style ?: return

    // Skip node updates when heatmap is enabled
    if (heatmapEnabled) return

    // Skip node updates when showing tracks
    if (showingTracksRef.value) {
        Timber.tag("CameraIdleHandler").d("Skipping node updates - showing tracks")
        return
    }

    // Debounce to avoid rapid toggling during kinetic flings/tiles loading
    val now = SystemClock.uptimeMillis()
    if (now - lastClusterEvalMs < CLUSTER_EVAL_DEBOUNCE_MS) return
    lastClusterEvalMs = now

    val filtered = applyFilters(nodes, mapFilterState, enabledRoles, ourNode?.num, isLocationTrackingEnabled)

    Timber.tag("CameraIdleHandler").d("Filtered nodes=%d (of %d)", filtered.size, nodes.size)

    clustersShownState.value =
        setClusterVisibilityHysteresis(
            map,
            st,
            filtered,
            clusteringEnabled,
            clustersShownState.value,
            mapFilterState.showPrecisionCircle,
        )

    // Compute which nodes get labels in viewport and update source
    val density = context.resources.displayMetrics.density
    val labelSet = selectLabelsForViewport(map, filtered, density)
    val jsonIdle = nodesToFeatureCollectionJsonWithSelection(filtered, labelSet)

    Timber.tag("CameraIdleHandler")
        .d(
            "Updating sources. labelSet=%d (nums=%s) jsonBytes=%d",
            labelSet.size,
            labelSet.take(5).joinToString(","),
            jsonIdle.length,
        )

    // Update both clustered and non-clustered sources
    safeSetGeoJson(st, NODES_CLUSTER_SOURCE_ID, jsonIdle)
    safeSetGeoJson(st, NODES_SOURCE_ID, jsonIdle)
    logStyleState("onCameraIdle(post-update)", st)

    try {
        val w = mapViewRef?.width ?: 0
        val h = mapViewRef?.height ?: 0
        val bbox = android.graphics.RectF(0f, 0f, w.toFloat(), h.toFloat())
        val rendered = map.queryRenderedFeatures(bbox, NODES_LAYER_ID, CLUSTER_CIRCLE_LAYER_ID)
        Timber.tag("CameraIdleHandler").d("Rendered features in viewport=%d", rendered.size)
    } catch (_: Throwable) {}
}
