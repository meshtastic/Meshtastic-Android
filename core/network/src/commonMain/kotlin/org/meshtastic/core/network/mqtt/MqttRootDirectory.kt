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
package org.meshtastic.core.network.mqtt

import org.meshtastic.core.common.util.latLongToMeter
import org.meshtastic.core.model.MqttRootsResponse

/**
 * A community root whose name says nothing about where it is ("msh/US/DMV", "msh/US/MeshSTL"), with the area it serves.
 * Subdivisions are ISO 3166-2 ("US-VA"); a metro mesh may give a center and radius instead or as well.
 */
data class MqttRootEntry(
    val root: String,
    val name: String,
    val subdivisions: Set<String> = emptySet(),
    val center: LatLon? = null,
    val radiusKm: Double? = null,
)

/**
 * The geography a sample cannot learn on its own: named community roots, which subdivisions border which, and the code
 * a geocoder's subdivision name maps to.
 */
data class MqttRootDirectory(
    val entries: List<MqttRootEntry>,
    /** ISO 3166-2 code to the codes it borders, both directions present. */
    val neighbors: Map<String, Set<String>>,
    /** ISO 3166-1 country to its subdivision names, lowercased, mapped to the topic segment the community uses. */
    val subdivisionSegments: Map<String, Map<String, String>>,
) {
    /** The segment for a geocoder's subdivision name: a known code, else the name itself. */
    fun subdivisionSegment(countryCode: String?, name: String): String =
        subdivisionSegments[countryCode?.uppercase()]?.get(name.lowercase()) ?: name

    fun entriesServing(subdivision: String?, at: LatLon?): List<MqttRootEntry> = entries.filter { entry ->
        val bySubdivision = subdivision != null && subdivision in entry.subdivisions
        val center = entry.center
        val radiusKm = entry.radiusKm
        val byRadius =
            at != null &&
                center != null &&
                radiusKm != null &&
                latLongToMeter(at.latitude, at.longitude, center.latitude, center.longitude) <= radiusKm * METERS_PER_KM
        bySubdivision || byRadius
    }

    companion object {
        private const val METERS_PER_KM = 1_000.0

        /** Before the bundled seed or a fetch has been read, and on a host with no bundled seed. */
        val EMPTY: MqttRootDirectory = MqttRootDirectory(emptyList(), emptyMap(), emptyMap())

        /** The published directory; a center that is not a `[lat, lon]` pair places nothing by radius. */
        fun from(response: MqttRootsResponse): MqttRootDirectory = MqttRootDirectory(
            entries =
            response.entries.map { entry ->
                MqttRootEntry(
                    root = entry.root,
                    name = entry.name,
                    subdivisions = entry.subdivisions.toSet(),
                    center = entry.center?.takeIf { it.size == 2 }?.let { (lat, lon) -> LatLon(lat, lon) },
                    radiusKm = entry.radiusKm,
                )
            },
            neighbors = response.neighbors.mapValues { (_, codes) -> codes.toSet() },
            subdivisionSegments =
            response.subdivisionSegments
                .mapKeys { (country, _) -> country.uppercase() }
                .mapValues { (_, names) -> names.mapKeys { (name, _) -> name.lowercase() } },
        )
    }
}
