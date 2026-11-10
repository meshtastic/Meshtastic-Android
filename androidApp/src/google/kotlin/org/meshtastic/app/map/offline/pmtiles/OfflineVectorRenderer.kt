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

import co.touchlab.kermit.Logger
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.protobuf.ProtoBuf

/** A polygon in real-world coordinates: an exterior ring and the holes cut out of it. */
internal data class OfflinePolygon(val outer: List<LatLng>, val holes: List<List<LatLng>>)

/**
 * One decoded feature, already placed in real-world coordinates and tagged with the basemap layer it came from. Polygon
 * features fill [polygons] and leave [rings] empty; points and lines fill [rings].
 */
internal data class OfflineFeature(
    val layerName: String,
    val geometryType: Int,
    val rings: List<List<LatLng>>,
    val polygons: List<OfflinePolygon> = emptyList(),
    val id: Long? = null,
    val properties: Map<String, Any> = emptyMap(),
    val tile: TileIndex? = null,
)

/**
 * Decodes an offline region's MVT tiles into drawable geometry, on demand and cached per tile.
 *
 * Only [RENDERED_LAYERS] are kept: buildings and POIs alone can be most of a tile's feature count at high zoom, and
 * this offline layer exists to keep the map legible without a network, not to reproduce it.
 *
 * Polygon rings are grouped into exterior-plus-holes polygons by winding in tile space ([MvtDecoder.groupPolygons]). A
 * feature carries the id and attributes its tile gave it; a copy lying wholly inside a neighboring tile that also has
 * the feature is that tile's buffer copy and is dropped.
 */
internal class OfflineVectorRenderer {

    private val tileCache =
        object : LinkedHashMap<String, List<OfflineFeature>>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<OfflineFeature>>): Boolean =
                size > MAX_CACHED_TILES
        }

    /** Every rendered-layer feature from the tiles at [zoom] (clamped to what [region] actually has) over [bounds]. */
    fun featuresFor(
        region: OfflineRegion,
        archive: OfflineVectorArchive,
        zoom: Int,
        bounds: LatLngBounds,
    ): List<OfflineFeature> {
        val renderZoom = zoom.coerceIn(region.zoomRange)
        val perTile =
            OfflineRegionTileSet.tiles(bounds, renderZoom..renderZoom).map { tile ->
                tileFeatures(region.id, archive, tile)
            }
        return dropBufferCopies(perTile).sortedBy { OfflineMapStyle.sortRank(it.properties) }
    }

    private fun tileFeatures(regionId: String, archive: OfflineVectorArchive, tile: TileIndex): List<OfflineFeature> {
        val key = "$regionId/${tile.zoom}/${tile.x}/${tile.y}"
        // Access-ordered, so even get() is a structural modification — and a superseded LaunchedEffect's featuresFor
        // keeps running on the IO dispatcher while the next one starts. Same lock as HillshadeTileProvider's caches.
        synchronized(tileCache) { tileCache[key] }
            ?.let {
                return it
            }

        val bytes = archive.readTile(tile.zoom, tile.x, tile.y)
        val decoded = if (bytes == null) emptyList() else decodeTile(tile, bytes)
        synchronized(tileCache) { tileCache[key] = decoded }
        return decoded
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Suppress("detekt:UnreachableCode")
    private fun decodeTile(tile: TileIndex, bytes: ByteArray): List<OfflineFeature> {
        val vectorTile =
            try {
                ProtoBuf.decodeFromByteArray(VectorTile.serializer(), bytes)
            } catch (e: SerializationException) {
                // No tile coordinates in the message: zoom/x/y is location-adjacent data (AGENTS.md Privacy First).
                LOG.w(e) { "Malformed MVT tile" }
                return emptyList()
            }

        return vectorTile.layers
            .filter { it.name in RENDERED_LAYERS }
            .flatMap { layer ->
                layer.features.mapNotNull { feature -> placeFeature(tile, layer, feature) }
            }
    }

    private fun placeFeature(tile: TileIndex, layer: VectorTile.Layer, feature: VectorTile.Feature): OfflineFeature? {
        val localRings = MvtDecoder.decodeGeometry(feature.type, feature.geometry)
        fun place(ring: List<TileCoord>) = ring.map { WebMercatorTileMath.tileLocalToLatLng(tile, layer.extent, it) }
        val isPolygon = feature.type == VectorTile.GEOM_POLYGON
        val polygons =
            if (isPolygon) {
                MvtDecoder.groupPolygons(localRings).map { OfflinePolygon(place(it.outer), it.holes.map(::place)) }
            } else {
                emptyList()
            }
        if (localRings.isEmpty() || (isPolygon && polygons.isEmpty())) return null
        return OfflineFeature(
            layerName = layer.name,
            geometryType = feature.type,
            rings = if (isPolygon) emptyList() else localRings.map(::place),
            polygons = polygons,
            id = feature.id,
            properties = feature.properties(layer),
            tile = tile,
        )
    }

    internal companion object {
        /**
         * Drops features whose id also appears in another tile that wholly contains them: a tile's buffer repeats its
         * neighbors' geometry, and drawing both doubles translucent fills. A piece reaching past its own tile edge is
         * never contained by a neighbor, so a feature cut by a tile edge keeps each tile's piece. A feature on a shared
         * edge sits inside both tiles, so the lowest tile by (x, y) keeps it. Features without an id are kept.
         */
        fun dropBufferCopies(perTile: List<List<OfflineFeature>>): List<OfflineFeature> {
            val tilesByKey = mutableMapOf<Pair<String, Long>, MutableSet<TileIndex>>()
            perTile.flatten().forEach { feature ->
                val id = feature.id
                val tile = feature.tile
                if (id != null && tile != null) {
                    tilesByKey.getOrPut(feature.layerName to id) { mutableSetOf() } += tile
                }
            }
            return perTile.flatten().filterNot { feature ->
                val id = feature.id
                val own = feature.tile
                val box = GeoBox.of(feature)
                if (id == null || own == null || box == null) {
                    false
                } else {
                    val owners = tilesByKey[feature.layerName to id].orEmpty().filter { GeoBox.of(it).contains(box) }
                    owners.any { it != own } && own != owners.minWithOrNull(compareBy({ it.x }, { it.y }))
                }
            }
        }

        private val LOG = Logger.withTag("OfflineVectorRenderer")
        val RENDERED_LAYERS = setOf("water", "roads", "boundaries", "places")
        private const val MAX_CACHED_TILES = 96
        private const val INITIAL_CAPACITY = 16
        private const val LOAD_FACTOR = 0.75f
    }
}

private data class GeoBox(val south: Double, val west: Double, val north: Double, val east: Double) {
    fun contains(other: GeoBox) =
        other.south >= south && other.north <= north && other.west >= west && other.east <= east

    companion object {
        fun of(tile: TileIndex): GeoBox {
            val nw = WebMercatorTileMath.tileFractionalToLatLng(tile.zoom, tile.x, tile.y, 0.0, 0.0)
            val se = WebMercatorTileMath.tileFractionalToLatLng(tile.zoom, tile.x, tile.y, 1.0, 1.0)
            // LatLng wraps the easternmost tile's east edge from 180 to -180.
            val east = if (se.longitude <= nw.longitude) ANTIMERIDIAN else se.longitude
            return GeoBox(se.latitude, nw.longitude, nw.latitude, east)
        }

        private const val ANTIMERIDIAN = 180.0

        fun of(feature: OfflineFeature): GeoBox? {
            val points = feature.rings.flatten() + feature.polygons.flatMap { it.outer }
            if (points.isEmpty()) return null
            return GeoBox(
                south = points.minOf { it.latitude },
                west = points.minOf { it.longitude },
                north = points.maxOf { it.latitude },
                east = points.maxOf { it.longitude },
            )
        }
    }
}
