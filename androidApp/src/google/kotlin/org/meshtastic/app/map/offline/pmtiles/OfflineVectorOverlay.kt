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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PatternItem
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.Polyline
import kotlinx.coroutines.withContext
import org.meshtastic.core.common.util.ioDispatcher
import java.io.File
import kotlin.math.floor

private val WATER_FILL = Color(0x33_4FC3F7)
private val WATER_STROKE = Color(0xFF_4FC3F7)
private val ROADS_STROKE = Color(0xFF_9E9E9E)
private val TUNNEL_STROKE = ROADS_STROKE.copy(alpha = 0.6f)
private val BOUNDARIES_STROKE = Color(0xFF_BDBDBD)
private const val BOUNDARIES_WIDTH_DP = 0.75f
private const val WATER_STROKE_WIDTH_DP = 0.5f
private const val WATERWAY_WIDTH_DP = 1f
private const val DASH_PX = 12f
private const val GAP_PX = 8f
private const val ZOOM_STEPS_PER_LEVEL = 4f
private val DASHED = listOf<PatternItem>(Dash(DASH_PX), Gap(GAP_PX))
private val WATERWAY_KINDS = setOf("river", "stream", "canal")

/**
 * Draws a downloaded offline region's water, roads and boundaries as native `Polygon`/`Polyline` overlays — the same
 * technique the sibling iOS app uses (`PMTilesMapView.swift`'s `OfflineVectorTileProvider`), decoding straight to
 * shapes rather than rasterizing, so the offline basemap draws in the same coordinate space as node markers with no
 * compositing seam. Only mounted while [region] covers the visible map and the user has manually enabled the offline
 * overlay — a toggle, not a network-down gate; see `MapView.kt`'s `offlineOverlayEnabled` caller. With [showLabels] it
 * also names places and water ([OfflineLabelMarkers]).
 */
@Composable
internal fun OfflineVectorOverlay(
    region: OfflineRegion,
    archiveFile: File,
    cameraPositionState: CameraPositionState,
    showLabels: Boolean,
    dark: Boolean,
) {
    val archive = remember(region.id) { OfflineVectorArchive.open(archiveFile) } ?: return
    DisposableEffect(archive) { onDispose { archive.close() } }

    val renderer = remember(region.id) { OfflineVectorRenderer() }
    var features by remember(region.id) { mutableStateOf<List<OfflineFeature>>(emptyList()) }

    LaunchedEffect(region.id, cameraPositionState.position) {
        val bounds = cameraPositionState.projection?.visibleRegion?.latLngBounds ?: return@LaunchedEffect
        val zoom = cameraPositionState.position.zoom.toInt()
        features = withContext(ioDispatcher) { renderer.featuresFor(region, archive, zoom, bounds) }
    }

    val density = LocalDensity.current.density
    // Quantized so a pinch doesn't restyle every shape on each frame; rounded down so no style or label shows
    // before the zoom it is meant for.
    val zoom by remember {
        derivedStateOf {
            floor(cameraPositionState.position.zoom * ZOOM_STEPS_PER_LEVEL) / ZOOM_STEPS_PER_LEVEL
        }
    }

    if (showLabels) {
        val ranked = remember(features) { OfflineMapLabels.ranked(features) }
        val projection = cameraPositionState.projection
        val bounds = projection?.visibleRegion?.latLngBounds
        val labels =
            remember(ranked, bounds, zoom) {
                if (projection == null || bounds == null) {
                    emptyList()
                } else {
                    val corner = projection.toScreenLocation(projection.visibleRegion.nearRight)
                    OfflineMapLabels.visible(ranked, bounds, zoom.toDouble()) { position ->
                        val point = projection.toScreenLocation(position)
                        point.x in 0..corner.x && point.y in 0..corner.y
                    }
                }
            }
        OfflineLabelMarkers(labels, dark)
    }

    features.forEach { feature ->
        key(feature.layerName, feature.tile, feature.id, feature.rings, feature.polygons) {
            when (feature.layerName) {
                "water" -> WaterFeature(feature, zoom, density)

                "roads" -> RoadFeature(feature, zoom, density)

                "boundaries" ->
                    feature.rings.forEach { line ->
                        LineFeature(line, BOUNDARIES_STROKE, BOUNDARIES_WIDTH_DP * density, Z_BOUNDARY)
                    }
            }
        }
    }
}

@Composable
private fun WaterFeature(feature: OfflineFeature, zoom: Float, density: Float) {
    if (feature.geometryType == VectorTile.GEOM_POLYGON) {
        feature.polygons.forEach { polygon ->
            Polygon(
                points = polygon.outer,
                holes = polygon.holes,
                fillColor = WATER_FILL,
                strokeColor = WATER_STROKE,
                strokeWidth = WATER_STROKE_WIDTH_DP * density,
                zIndex = Z_WATER,
            )
        }
    } else if (
        OfflineMapStyle.kind(feature.properties) in WATERWAY_KINDS &&
        OfflineMapStyle.lineVisible(OfflineMapStyle.minZoom(feature.properties), zoom.toDouble())
    ) {
        feature.rings.forEach { line -> LineFeature(line, WATER_STROKE, WATERWAY_WIDTH_DP * density, Z_WATERWAY) }
    }
}

@Composable
private fun RoadFeature(feature: OfflineFeature, zoom: Float, density: Float) {
    val roadClass =
        OfflineMapStyle.roadClass(
            OfflineMapStyle.kind(feature.properties),
            OfflineMapStyle.kindDetail(feature.properties),
        )
    val width = OfflineMapStyle.roadWidthDp(roadClass, zoom.toDouble()) * density
    val minZoom = OfflineMapStyle.minZoom(feature.properties)
    val hidden = width <= 0f || (roadClass == RoadClass.Rail && !OfflineMapStyle.lineVisible(minZoom, zoom.toDouble()))
    if (hidden) return

    val structure = OfflineMapStyle.roadStructure(feature.properties)
    val color = if (structure == RoadStructure.Tunnel) TUNNEL_STROKE else ROADS_STROKE
    val dashed = structure == RoadStructure.Tunnel || roadClass == RoadClass.Rail
    val zIndex =
        when (structure) {
            RoadStructure.Tunnel -> Z_TUNNEL
            RoadStructure.Surface -> Z_ROAD
            RoadStructure.Bridge -> Z_BRIDGE
        }
    feature.rings.forEach { line -> LineFeature(line, color, width, zIndex, if (dashed) DASHED else null) }
}

@Composable
@NonRestartableComposable
private fun LineFeature(
    points: List<LatLng>,
    color: Color,
    widthPx: Float,
    zIndex: Float,
    pattern: List<PatternItem>? = null,
) {
    Polyline(points = points, color = color, width = widthPx, pattern = pattern, zIndex = zIndex)
}

/** Below node markers and waypoints, above the (absent, offline) live basemap. */
private const val OFFLINE_Z_INDEX = -0.75f

// Equal-zIndex shapes draw in insertion order, which is sort_rank order; these tiers sit above that.
private const val Z_WATER = OFFLINE_Z_INDEX
private const val Z_WATERWAY = OFFLINE_Z_INDEX + 0.001f
private const val Z_BOUNDARY = OFFLINE_Z_INDEX + 0.002f
private const val Z_TUNNEL = OFFLINE_Z_INDEX + 0.003f
private const val Z_ROAD = OFFLINE_Z_INDEX + 0.004f
private const val Z_BRIDGE = OFFLINE_Z_INDEX + 0.005f
