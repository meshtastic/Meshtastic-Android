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

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.protobuf.ProtoBuf
import org.meshtastic.feature.map.terrain.GeoBounds
import org.meshtastic.feature.map.terrain.TerrainTileMath
import org.meshtastic.feature.map.terrain.spatial.LatLon
import org.meshtastic.feature.map.terrain.spatial.contains
import org.meshtastic.feature.map.terrain.spatial.intersects
import org.meshtastic.feature.xr.BuildingFootprint

/** The downloaded basemaps' building outlines and heights, for the 3D terrain map to raise. */
internal object OfflineBuildings {
    /**
     * Most vector tiles decoded for one map. Buildings are only in the deeper zooms, so a box too wide for this many
     * tiles at [MIN_ZOOM] shows none, and they would be specks at that size anyway.
     */
    const val MAX_TILES = 36

    /** Protomaps puts every building in zoom 15, the bigger ones in 14 and only the largest in 13. */
    const val MAX_ZOOM = 15
    const val MIN_ZOOM = 13
    private const val LAYER = "buildings"
    private const val PART = "building_part"

    /** The deepest zoom of [zooms] that has buildings and covers [bounds] in at most [maxTiles] tiles, or null. */
    fun zoom(bounds: GeoBounds, zooms: IntRange, maxTiles: Int = MAX_TILES): Int? =
        (minOf(zooms.last, MAX_ZOOM) downTo maxOf(zooms.first, MIN_ZOOM)).firstOrNull {
            TerrainTileMath.tileCountAt(it, bounds) <= maxTiles
        }

    /** Whether any downloaded region is deep enough to hold buildings. */
    fun available(store: OfflineRegionStore): Boolean = store.list().any { it.maxZoom >= MIN_ZOOM }

    /**
     * The buildings in [bounds] from every downloaded region that touches it, deepest region first. A tile already
     * inside a region that was read is not read again from one that overlaps it.
     */
    fun footprints(bounds: GeoBounds, store: OfflineRegionStore): List<BuildingFootprint> {
        val regions = store.list().filter { it.geoBounds.intersects(bounds) }.sortedByDescending { it.maxZoom }
        val read = mutableListOf<GeoBounds>()
        val footprints = mutableListOf<BuildingFootprint>()
        for (region in regions) {
            val zoom = zoom(bounds, region.zoomRange) ?: continue
            val tiles =
                TerrainTileMath.tilesAt(zoom, bounds).filter { tile -> read.none { it.contains(boundsOf(tile)) } }
            OfflineVectorArchive.open(store.archiveFile(region.id))?.use { archive ->
                for (tile in tiles) {
                    archive.readTile(tile.zoom, tile.x, tile.y)?.let { footprints += decode(tile, it) }
                }
            }
            read += region.geoBounds
        }
        return footprints
    }

    /**
     * The buildings in one tile, each cut to the tile so one over a tile edge is not drawn twice from the neighbor's
     * buffer.
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun decode(tile: TileIndex, bytes: ByteArray): List<BuildingFootprint> {
        val layers =
            try {
                ProtoBuf.decodeFromByteArray(VectorTile.serializer(), bytes).layers
            } catch (_: SerializationException) {
                emptyList()
            }
        return layers
            .filter { it.name == LAYER }
            .flatMap { layer -> layer.features.flatMap { decode(tile, layer, it) } }
    }

    private fun decode(tile: TileIndex, layer: VectorTile.Layer, feature: VectorTile.Feature): List<BuildingFootprint> {
        if (feature.type != VectorTile.GEOM_POLYGON) return emptyList()
        val properties = feature.properties(layer)
        fun place(ring: List<TileCoord>): List<LatLon> = clip(ring, layer.extent).map { (x, y) ->
            val point = TerrainTileMath.lonLatAt(tile.zoom, tile.x + x / layer.extent, tile.y + y / layer.extent)
            LatLon(point.latitude, point.longitude)
        }
        return MvtDecoder.groupPolygons(
            MvtDecoder.decodeGeometry(feature.type, feature.geometry),
        ).mapNotNull { polygon ->
            val outline = place(polygon.outer)
            if (outline.size < MIN_CORNERS) {
                null
            } else {
                BuildingFootprint(
                    outline = outline,
                    heightMeters = (properties["height"] as? Number)?.toDouble(),
                    minHeightMeters = (properties["min_height"] as? Number)?.toDouble() ?: 0.0,
                    isPart = properties["kind"] == PART,
                    holes = polygon.holes.map(::place).filter { it.size >= MIN_CORNERS },
                )
            }
        }
    }

    /** [ring] cut to the tile's own square, `0..extent` on each axis, one edge of the square at a time. */
    fun clip(ring: List<TileCoord>, extent: Int): List<Pair<Double, Double>> {
        val max = extent.toDouble()
        var points = ring.map { it.x.toDouble() to it.y.toDouble() }
        if (points.size > 1 && points.first() == points.last()) points = points.dropLast(1)
        points = cut(points, { it.first >= 0 }) { a, b -> crossing(a, b, (0 - a.first) / (b.first - a.first)) }
        points = cut(points, { it.first <= max }) { a, b -> crossing(a, b, (max - a.first) / (b.first - a.first)) }
        points = cut(points, { it.second >= 0 }) { a, b -> crossing(a, b, (0 - a.second) / (b.second - a.second)) }
        return cut(points, { it.second <= max }) { a, b -> crossing(a, b, (max - a.second) / (b.second - a.second)) }
    }

    private fun crossing(a: Pair<Double, Double>, b: Pair<Double, Double>, t: Double) =
        (a.first + (b.first - a.first) * t) to (a.second + (b.second - a.second) * t)

    /** Keeps the part of a ring on the [inside] of one line, adding a corner wherever an edge crosses it. */
    private fun cut(
        points: List<Pair<Double, Double>>,
        inside: (Pair<Double, Double>) -> Boolean,
        cross: (Pair<Double, Double>, Pair<Double, Double>) -> Pair<Double, Double>,
    ): List<Pair<Double, Double>> = buildList {
        for (i in points.indices) {
            val current = points[i]
            val previous = points[(i + points.size - 1) % points.size]
            if (inside(current) != inside(previous)) add(cross(previous, current))
            if (inside(current)) add(current)
        }
    }

    private fun boundsOf(tile: TileIndex): GeoBounds {
        val northwest = TerrainTileMath.lonLatAt(tile.zoom, tile.x.toDouble(), tile.y.toDouble())
        val southeast = TerrainTileMath.lonLatAt(tile.zoom, tile.x + 1.0, tile.y + 1.0)
        return GeoBounds(southeast.latitude, northwest.longitude, northwest.latitude, southeast.longitude)
    }

    private val OfflineRegion.geoBounds: GeoBounds
        get() = GeoBounds(south = southLat, west = westLon, north = northLat, east = eastLon)

    private const val MIN_CORNERS = 3
}
