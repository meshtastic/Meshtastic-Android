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
package org.meshtastic.app.map.offline.terrain

import androidx.compose.runtime.Composable
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
import com.google.maps.android.compose.Polyline
import kotlinx.coroutines.withContext
import org.meshtastic.app.map.offline.pmtiles.OfflineRegion
import org.meshtastic.app.map.offline.pmtiles.WebMercatorTileMath
import org.meshtastic.core.common.util.ioDispatcher
import org.meshtastic.feature.map.terrain.ContourGenerator
import org.meshtastic.feature.map.terrain.ContourIntervals
import org.meshtastic.feature.map.terrain.ContourLine
import org.meshtastic.feature.map.terrain.ContourStyle
import org.meshtastic.feature.map.terrain.ContourStyling
import org.meshtastic.feature.map.terrain.ElevationTile
import org.meshtastic.feature.map.terrain.GeoBounds
import org.meshtastic.feature.map.terrain.MapterhornEndpoints
import org.meshtastic.feature.map.terrain.TerrainSource
import org.meshtastic.feature.map.terrain.TerrainTileMath
import org.meshtastic.feature.map.terrain.TerrainTileStore
import org.meshtastic.feature.map.terrain.TileIndex
import org.meshtastic.feature.map.terrain.decodeTerrariumTile

/** One contour line already placed at real [LatLng]s and styled, ready to draw. */
private data class StyledContour(val points: List<LatLng>, val style: ContourStyle, val elevationMeters: Float)

/**
 * Draws elevation contours for whatever's visible on screen, decoding [store]'s downloaded Terrarium tiles and running
 * marching-squares ([ContourGenerator]) on demand.
 *
 * Two things bound the cost of this, since — unlike [HillshadeTileProvider], which only ever runs for the tiles
 * Google's own `TileProvider` machinery asks for — this walks its own tile set:
 * - **Viewport, not the whole region**: only tiles covering the current visible bounds (plus a one-tile ring so lines
 *   don't visibly clip right at the edge while panning) are decoded and marched, never the full downloaded region.
 * - **Recomputed only once the camera settles** ([CameraPositionState.isMoving] false), not every movement frame —
 *   marching squares over a couple dozen tiles isn't cheap enough to redo per frame the way the vector overlay's plain
 *   tile-cache lookup is.
 * - **[MAX_CONTOUR_LINES] caps the total line count** actually rendered (Compose has no batched-multi-polyline
 *   primitive here — confirmed no such thing exists in either MapLibre's or android-maps-utils' changelogs — so every
 *   line is its own `Polyline` composable). Index lines (thicker, sparser, the ones a glance actually reads) are kept
 *   preferentially over minor lines via a stable sort, so which lines survive a cap doesn't depend on arbitrary
 *   tile-iteration order.
 */
@Composable
internal fun ContourOverlay(
    region: OfflineRegion,
    store: TerrainTileStore,
    cameraPositionState: CameraPositionState,
    metric: Boolean,
) {
    var contours by remember(region.id) { mutableStateOf<List<StyledContour>>(emptyList()) }

    LaunchedEffect(region.id, cameraPositionState.position, cameraPositionState.isMoving) {
        if (cameraPositionState.isMoving) return@LaunchedEffect
        val bounds = cameraPositionState.projection?.visibleRegion?.latLngBounds ?: return@LaunchedEffect
        val zoom = cameraPositionState.position.zoom.toInt()
        contours =
            withContext(ioDispatcher) {
                computeContours(
                    region = region,
                    store = store,
                    geoBounds =
                    GeoBounds(
                        south = bounds.southwest.latitude,
                        west = bounds.southwest.longitude,
                        north = bounds.northeast.latitude,
                        east = bounds.northeast.longitude,
                    ),
                    mapZoom = zoom,
                    metric = metric,
                )
            }
    }

    contours.forEach { contour -> key(contour.elevationMeters, contour.points) { ContourPolyline(contour) } }
}

@Composable
@NonRestartableComposable
private fun ContourPolyline(contour: StyledContour) {
    val baseColor = Color(android.graphics.Color.parseColor(contour.style.strokeHexColor))
    Polyline(
        points = contour.points,
        color = baseColor.copy(alpha = contour.style.strokeOpacity),
        width = contour.style.strokeWidth,
        zIndex = CONTOUR_Z_INDEX,
    )
}

private fun computeContours(
    region: OfflineRegion,
    store: TerrainTileStore,
    geoBounds: GeoBounds,
    mapZoom: Int,
    metric: Boolean,
): List<StyledContour> {
    val terrainZoom = terrainZoomFor(region, mapZoom)
    val source = terrainSourceForZoom(terrainZoom)

    val decodedTiles =
        expandedTiles(terrainZoom, geoBounds).mapNotNull { tile -> decodeTile(store, source, tile)?.let { tile to it } }
    // maxOfOrNull, not maxOf: an empty decodedTiles would otherwise throw before the emptiness check below runs.
    // Falling back to 0f is harmless either way — ContourIntervals.levelsForZoom returns no levels for it either.
    val maxElevation = decodedTiles.maxOfOrNull { (_, elevationTile) -> elevationTile.elevations.max() } ?: 0f
    val levels = ContourIntervals.levelsForZoom(mapZoom, metric, maxElevation)
    if (decodedTiles.isEmpty() || levels.isEmpty()) return emptyList()

    val allLines =
        decodedTiles.flatMap { (tile, elevationTile) ->
            ContourGenerator.generate(elevationTile, levels).map { line -> tile to line }
        }

    // Stable sort: lines that are equally "index or not" keep their original (tile-then-generation) order, so
    // which lines survive MAX_CONTOUR_LINES is deterministic rather than depending on HashMap iteration order.
    val ranked =
        allLines.sortedByDescending { (_, line) ->
            ContourIntervals.isIndexLevel(line.elevationMeters, mapZoom, metric)
        }

    return ranked.take(MAX_CONTOUR_LINES).map { (tile, line) -> line.toStyledContour(tile, mapZoom, metric) }
}

private fun ContourLine.toStyledContour(tile: TileIndex, zoom: Int, metric: Boolean): StyledContour {
    val points =
        points.map { point ->
            WebMercatorTileMath.tileFractionalToLatLng(
                zoom = tile.zoom,
                tileX = tile.x,
                tileY = tile.y,
                fracX = point.x.toDouble(),
                fracY = point.y.toDouble(),
            )
        }
    return StyledContour(points, ContourStyling.styleFor(this, zoom, metric), elevationMeters)
}

/** Clamps the map's own zoom to whatever depth this region's terrain download actually reached. */
private fun terrainZoomFor(region: OfflineRegion, mapZoom: Int): Int {
    val maxAvailable =
        if (region.terrainHasRegionalDetail) {
            MapterhornEndpoints.REGIONAL_MAX_ZOOM
        } else {
            MapterhornEndpoints.GLOBAL_MAX_ZOOM
        }
    return mapZoom.coerceIn(0, maxAvailable)
}

/** [TerrainTileMath.tilesAt] for [bounds], plus a one-tile ring so contours don't visibly clip at the viewport edge. */
private fun expandedTiles(zoom: Int, bounds: GeoBounds): List<TileIndex> {
    val base = TerrainTileMath.tilesAt(zoom, bounds)
    if (base.isEmpty()) return base

    val maxTileIndex = (1 shl zoom) - 1
    val minX = (base.minOf { it.x } - TILE_MARGIN).coerceAtLeast(0)
    val maxX = (base.maxOf { it.x } + TILE_MARGIN).coerceAtMost(maxTileIndex)
    val minY = (base.minOf { it.y } - TILE_MARGIN).coerceAtLeast(0)
    val maxY = (base.maxOf { it.y } + TILE_MARGIN).coerceAtMost(maxTileIndex)

    val tiles = mutableListOf<TileIndex>()
    for (x in minX..maxX) {
        for (y in minY..maxY) {
            tiles += TileIndex(zoom, x, y)
        }
    }
    return tiles
}

private fun decodeTile(store: TerrainTileStore, source: TerrainSource, tile: TileIndex): ElevationTile? =
    store.readTile(source, tile)?.let { bytes ->
        try {
            decodeTerrariumTile(bytes)
        } catch (_: IllegalStateException) {
            null
        }
    }

/** Above the offline vector layer's own water/roads/boundaries, still below markers and waypoints. */
private const val CONTOUR_Z_INDEX = -0.6f
private const val TILE_MARGIN = 1

/**
 * A generous, not scientifically tuned, ceiling on rendered contour lines — chosen so a steep-terrain region at a deep
 * zoom (many minor levels, many tiles) can't emit thousands of `Polyline` composables. Revisit once this renders on a
 * real device; no display was available to judge it against in this environment.
 */
private const val MAX_CONTOUR_LINES = 300
