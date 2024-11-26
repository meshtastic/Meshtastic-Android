/*
 * Copyright (c) 2024 Meshtastic LLC
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

package com.geeksville.mesh.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.model.MetricsViewModel
import com.geeksville.mesh.ui.map.rememberMapViewWithLifecycle
import com.geeksville.mesh.util.addCopyright
import com.geeksville.mesh.util.addPolyline
import com.geeksville.mesh.util.addPositionMarkers
import com.geeksville.mesh.util.addScaleBarOverlay
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint

private const val DegD = 1e-7

@Composable
fun NodeMapScreen(
    viewModel: MetricsViewModel = hiltViewModel(),
) {
    val density = LocalDensity.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val geoPoints = state.positionLogs.map { GeoPoint(it.latitudeI * DegD, it.longitudeI * DegD) }
    val cameraView = remember { BoundingBox.fromGeoPoints(geoPoints) }
    val mapView = rememberMapViewWithLifecycle(cameraView, viewModel.tileSource)

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { mapView },
        update = { map ->
            map.overlays.clear()
            map.addCopyright()
            map.addScaleBarOverlay(density)

            map.addPolyline(density, geoPoints) {}
            map.addPositionMarkers(state.positionLogs) {}
        }
    )
}
