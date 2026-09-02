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
package org.meshtastic.feature.map.maplibre.geojson

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Position
import org.meshtastic.feature.map.terrain.ContourGenerator
import org.meshtastic.feature.map.terrain.ContourIntervals
import org.meshtastic.feature.map.terrain.ContourLine
import org.meshtastic.feature.map.terrain.ContourStyling
import org.meshtastic.feature.map.terrain.ElevationTile
import org.meshtastic.feature.map.terrain.MapterhornEndpoints
import org.meshtastic.feature.map.terrain.TerrainSource
import org.meshtastic.feature.map.terrain.TerrainTileMath
import org.meshtastic.feature.map.terrain.TerrainTileStore
import org.meshtastic.feature.map.terrain.TileIndex
import org.meshtastic.feature.map.terrain.decodeTerrariumTile

/**
 * A decoded tile's contour lines, converted into real-world GeoJSON [Feature]s.
 *
 * Pure and unit-testable on its own — the impure half (reading tile bytes, decoding WebP) is
 * [loadContourFeatureCollection], kept separate so this conversion can be tested without a [TerrainTileStore] or a real
 * Terrarium tile.
 *
 * One feature per contour line, styled individually via [ContourStyling.styleFor] rather than split into two layers
 * (index vs. minor): a single [ContourLayer] reading `stroke`/`stroke-width`/`stroke-opacity` off each feature is the
 * same simplestyle pattern this module's imported-layer rendering (`CustomLayers.kt`) already uses, so contour lines
 * style themselves the same way any other simplestyle-carrying import does.
 */
internal fun contourLinesToFeatures(
    tile: TileIndex,
    lines: List<ContourLine>,
    zoom: Int,
    metric: Boolean,
): List<Feature<LineString, JsonObject?>> = lines.mapNotNull { line ->
    // A contour needs at least two points to be a line; ContourGenerator.chain can in principle hand back a
    // degenerate single-point chain at a grid's own edge.
    if (line.points.size < 2) return@mapNotNull null

    val positions =
        line.points.map { point ->
            val lonLat = TerrainTileMath.lonLatAt(tile, point.x, point.y)
            Position(longitude = lonLat.longitude, latitude = lonLat.latitude)
        }
    val style = ContourStyling.styleFor(line, zoom, metric)

    Feature(
        geometry = LineString(positions),
        properties =
        buildJsonObject {
            put(ContourFeatureKeys.STROKE, style.strokeHexColor)
            put(ContourFeatureKeys.STROKE_WIDTH, style.strokeWidth)
            put(ContourFeatureKeys.STROKE_OPACITY, style.strokeOpacity)
        },
    )
}

/**
 * Decodes every downloaded elevation tile in [tiles] at [zoom] and turns their contours into one [FeatureCollection].
 *
 * Impure — reads from [store] and decodes WebP — so callers must run this off the composition thread; see
 * [ContourLayer][org.meshtastic.feature.map.maplibre.layers.ContourLayer]'s own `produceState`/`ioDispatcher` usage,
 * which follows the same precedent `GroundOverlayLayer` in `CustomLayers.kt` set for exactly this reason.
 *
 * A tile with nothing downloaded (outside the region, or above the region's own `maxZoom`) is skipped rather than
 * treated as an error — the caller only ever asks for the tiles a viewport touches, some of which may fall outside what
 * was actually downloaded.
 *
 * Per-tile [ContourIntervals.levelsForZoom]'s `maxElevationMeters` is that tile's own highest sample, not a shared
 * constant: it keeps the level count tight for a low-lying tile instead of generating levels no cell could ever reach.
 */
internal fun loadContourFeatureCollection(
    store: TerrainTileStore,
    tiles: List<TileIndex>,
    zoom: Int,
    metric: Boolean,
): FeatureCollection<LineString, JsonObject?> {
    val features =
        tiles.flatMap { tile ->
            val source =
                if (tile.zoom <= MapterhornEndpoints.GLOBAL_MAX_ZOOM) TerrainSource.GLOBAL else TerrainSource.REGIONAL
            val bytes = store.readTile(source, tile) ?: return@flatMap emptyList()
            val elevationTile = decodeElevationTileOrNull(bytes) ?: return@flatMap emptyList()

            val maxElevation = elevationTile.elevations.maxOrNull() ?: return@flatMap emptyList()
            val levels = ContourIntervals.levelsForZoom(zoom, metric, maxElevation)
            val lines = ContourGenerator.generate(elevationTile, levels)
            contourLinesToFeatures(tile, lines, zoom, metric)
        }
    return FeatureCollection(features)
}

/**
 * A corrupt or unrecognized tile is skipped, matching [TerrainTileStore]'s own "absent means null" convention.
 *
 * Broad on purpose: [decodeTerrariumTile]'s platform actuals throw whatever their own image decoder throws for
 * malformed bytes (`IllegalStateException` from the JVM/Android actuals' own `check()`/`error()` calls, or a
 * decoder-specific `RuntimeException` from Skia for bytes that aren't a WebP at all) — a corrupt tile on disk should
 * degrade to "no contours for this tile", never crash the map.
 */
private fun decodeElevationTileOrNull(bytes: ByteArray): ElevationTile? = try {
    decodeTerrariumTile(bytes)
} catch (_: RuntimeException) {
    null
}
