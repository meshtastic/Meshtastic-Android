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
package org.meshtastic.app.map.offline.pmtiles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.Polyline
import kotlinx.coroutines.withContext
import org.meshtastic.core.common.util.ioDispatcher
import java.io.File

private val WATER_FILL = Color(0x33_4FC3F7)
private val WATER_STROKE = Color(0xFF_4FC3F7)
private val ROADS_STROKE = Color(0xFF_9E9E9E)
private val BOUNDARIES_STROKE = Color(0xFF_BDBDBD)
private const val ROADS_WIDTH_PX = 3f
private const val BOUNDARIES_WIDTH_PX = 2f
private const val WATER_STROKE_WIDTH_PX = 1.5f

/**
 * Draws a downloaded offline region's water, roads and boundaries as native `Polygon`/`Polyline` overlays — the same
 * technique the sibling iOS app uses (`PMTilesMapView.swift`'s `OfflineVectorTileProvider`), decoding straight to
 * shapes rather than rasterizing, so the offline basemap draws in the same coordinate space as node markers with no
 * compositing seam. Only mounted while [region] covers the visible map and the network is down — see the caller in
 * `MapView.kt`.
 */
@Composable
internal fun OfflineVectorOverlay(region: OfflineRegion, archiveFile: File, cameraPositionState: CameraPositionState) {
    val archive = remember(region.id) { OfflineVectorArchive.open(archiveFile) } ?: return
    DisposableEffect(archive) { onDispose { archive.close() } }

    val renderer = remember(region.id) { OfflineVectorRenderer() }
    var features by remember(region.id) { mutableStateOf<List<OfflineFeature>>(emptyList()) }

    LaunchedEffect(region.id, cameraPositionState.position) {
        val bounds = cameraPositionState.projection?.visibleRegion?.latLngBounds ?: return@LaunchedEffect
        val zoom = cameraPositionState.position.zoom.toInt()
        features = withContext(ioDispatcher) { renderer.featuresFor(region, archive, zoom, bounds) }
    }

    features.forEach { feature ->
        key(feature.layerName, feature.rings) {
            when (feature.layerName) {
                "water" -> feature.rings.forEach { ring -> WaterPolygon(ring) }

                "roads" -> feature.rings.forEach { line -> LineFeature(line, ROADS_STROKE, ROADS_WIDTH_PX) }

                "boundaries" ->
                    feature.rings.forEach { line -> LineFeature(line, BOUNDARIES_STROKE, BOUNDARIES_WIDTH_PX) }
            }
        }
    }
}

@Composable
@NonRestartableComposable
private fun WaterPolygon(ring: List<LatLng>) {
    Polygon(
        points = ring,
        fillColor = WATER_FILL,
        strokeColor = WATER_STROKE,
        strokeWidth = WATER_STROKE_WIDTH_PX,
        zIndex = OFFLINE_Z_INDEX,
    )
}

@Composable
@NonRestartableComposable
private fun LineFeature(points: List<LatLng>, color: Color, width: Float) {
    Polyline(points = points, color = color, width = width, zIndex = OFFLINE_Z_INDEX)
}

/** Below node markers and waypoints, above the (absent, offline) live basemap. */
private const val OFFLINE_Z_INDEX = -0.75f
