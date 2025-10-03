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

package com.geeksville.mesh.ui.node

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.model.MetricsViewModel
import org.meshtastic.feature.map.addCopyright
import org.meshtastic.feature.map.addPolyline
import org.meshtastic.feature.map.addPositionMarkers
import org.meshtastic.feature.map.addScaleBarOverlay
import org.meshtastic.feature.map.node.NodeMapViewModel
import org.meshtastic.feature.map.rememberMapViewWithLifecycle
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint

private const val DEG_D = 1e-7

@Composable
fun NodeMapScreen(
    metricsViewModel: MetricsViewModel = hiltViewModel(),
    nodeMapViewModel: NodeMapViewModel = hiltViewModel(),
    onNavigateUp: () -> Unit,
) {
    val density = LocalDensity.current
    val state by metricsViewModel.state.collectAsStateWithLifecycle()
    val geoPoints = state.positionLogs.map { GeoPoint(it.latitudeI * DEG_D, it.longitudeI * DEG_D) }
    val cameraView = remember { BoundingBox.fromGeoPoints(geoPoints) }
    val mapView =
        rememberMapViewWithLifecycle(
            applicationId = nodeMapViewModel.applicationId,
            box = cameraView,
            tileSource = metricsViewModel.tileSource,
        )

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { mapView },
        update = { map ->
            map.overlays.clear()
            map.addCopyright()
            map.addScaleBarOverlay(density)

            map.addPolyline(density, geoPoints) {}
            map.addPositionMarkers(state.positionLogs) {}
        },
    )
}
