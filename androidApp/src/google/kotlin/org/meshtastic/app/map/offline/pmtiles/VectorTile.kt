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

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * The Mapbox Vector Tile schema (mapbox/vector-tile-spec, v2.1) — the format every layer of a Protomaps PMTiles basemap
 * is stored in. Decoded with `kotlinx-serialization-protobuf` against hand-written `@Serializable` classes rather than
 * a generated or vendored parser: the schema is nine fields across four small messages and has been stable since 2015,
 * and this keeps the offline-map feature off an unpublished dependency (the natural Java vector-tile parsers on GitHub
 * — e.g. ElectronicChartCentre/java-vector-tile — never shipped to Maven Central; JitPack would be the only way to pull
 * them in, which is a supply-chain trade this feature doesn't need to make for a ~60-line schema). Field numbers below
 * are copied verbatim from https://github.com/mapbox/vector-tile-spec/blob/master/2.1/vector_tile.proto.
 */
@Suppress("detekt:MagicNumber") // ProtoBuffer field numbers from mapbox/vector-tile-spec/2.1/vector_tile.proto
@Serializable
internal data class VectorTile(@ProtoNumber(3) val layers: List<Layer> = emptyList()) {

    @Serializable
    internal data class Layer(
        @ProtoNumber(1) val name: String = "",
        @ProtoNumber(2) val features: List<Feature> = emptyList(),
        @ProtoNumber(3) val keys: List<String> = emptyList(),
        @ProtoNumber(4) val values: List<Value> = emptyList(),
        @ProtoNumber(5) val extent: Int = DEFAULT_EXTENT,
        @ProtoNumber(15) val version: Int = 1,
    )

    @Serializable
    internal data class Feature(
        @ProtoNumber(1) val id: Long = 0,
        @ProtoNumber(2) val tags: List<Int> = emptyList(),
        @ProtoNumber(3) val type: Int = GEOM_UNKNOWN,
        @ProtoNumber(4) val geometry: List<Int> = emptyList(),
    )

    /** Exactly one field is set in a valid tile; attribute values are unused by the offline renderer today. */
    @Serializable
    internal data class Value(
        @ProtoNumber(1) val stringValue: String? = null,
        @ProtoNumber(2) val floatValue: Float? = null,
        @ProtoNumber(3) val doubleValue: Double? = null,
        @ProtoNumber(4) val intValue: Long? = null,
        @ProtoNumber(5) val uintValue: Long? = null,
        @ProtoNumber(6) val sintValue: Long? = null,
        @ProtoNumber(7) val boolValue: Boolean? = null,
    )

    internal companion object {
        const val DEFAULT_EXTENT = 4096
        const val GEOM_UNKNOWN = 0
        const val GEOM_POINT = 1
        const val GEOM_LINESTRING = 2
        const val GEOM_POLYGON = 3
    }
}
