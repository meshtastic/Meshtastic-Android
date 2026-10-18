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

        /** What the client ships before it has read the published directory. */
        val SEED: MqttRootDirectory =
            MqttRootDirectory(
                entries = emptyList(),
                neighbors =
                symmetric(US_NEIGHBORS.mapKeys { "US-${it.key}" }.mapValues { (_, v) -> v.map { "US-$it" } }),
                subdivisionSegments = mapOf("US" to US_STATES.associate { (code, name) -> name.lowercase() to code }),
            )

        private fun symmetric(pairs: Map<String, List<String>>): Map<String, Set<String>> {
            val result = mutableMapOf<String, MutableSet<String>>()
            pairs.forEach { (a, list) ->
                list.forEach { b ->
                    result.getOrPut(a) { mutableSetOf() } += b
                    result.getOrPut(b) { mutableSetOf() } += a
                }
            }
            return result
        }
    }
}

private val US_STATES =
    listOf(
        "AL" to "Alabama",
        "AK" to "Alaska",
        "AZ" to "Arizona",
        "AR" to "Arkansas",
        "CA" to "California",
        "CO" to "Colorado",
        "CT" to "Connecticut",
        "DE" to "Delaware",
        "DC" to "District of Columbia",
        "FL" to "Florida",
        "GA" to "Georgia",
        "HI" to "Hawaii",
        "ID" to "Idaho",
        "IL" to "Illinois",
        "IN" to "Indiana",
        "IA" to "Iowa",
        "KS" to "Kansas",
        "KY" to "Kentucky",
        "LA" to "Louisiana",
        "ME" to "Maine",
        "MD" to "Maryland",
        "MA" to "Massachusetts",
        "MI" to "Michigan",
        "MN" to "Minnesota",
        "MS" to "Mississippi",
        "MO" to "Missouri",
        "MT" to "Montana",
        "NE" to "Nebraska",
        "NV" to "Nevada",
        "NH" to "New Hampshire",
        "NJ" to "New Jersey",
        "NM" to "New Mexico",
        "NY" to "New York",
        "NC" to "North Carolina",
        "ND" to "North Dakota",
        "OH" to "Ohio",
        "OK" to "Oklahoma",
        "OR" to "Oregon",
        "PA" to "Pennsylvania",
        "PR" to "Puerto Rico",
        "RI" to "Rhode Island",
        "SC" to "South Carolina",
        "SD" to "South Dakota",
        "TN" to "Tennessee",
        "TX" to "Texas",
        "UT" to "Utah",
        "VT" to "Vermont",
        "VA" to "Virginia",
        "WA" to "Washington",
        "WV" to "West Virginia",
        "WI" to "Wisconsin",
        "WY" to "Wyoming",
    )

/** Land borders only; Four Corners point contacts are left out. Made symmetric in [MqttRootDirectory.SEED]. */
private val US_NEIGHBORS =
    mapOf(
        "AL" to listOf("FL", "GA", "MS", "TN"),
        "AZ" to listOf("CA", "NM", "NV", "UT"),
        "AR" to listOf("LA", "MO", "MS", "OK", "TN", "TX"),
        "CA" to listOf("AZ", "NV", "OR"),
        "CO" to listOf("KS", "NE", "NM", "OK", "UT", "WY"),
        "CT" to listOf("MA", "NY", "RI"),
        "DE" to listOf("MD", "NJ", "PA"),
        "DC" to listOf("MD", "VA"),
        "FL" to listOf("AL", "GA"),
        "GA" to listOf("AL", "FL", "NC", "SC", "TN"),
        "ID" to listOf("MT", "NV", "OR", "UT", "WA", "WY"),
        "IL" to listOf("IA", "IN", "KY", "MO", "WI"),
        "IN" to listOf("IL", "KY", "MI", "OH"),
        "IA" to listOf("IL", "MN", "MO", "NE", "SD", "WI"),
        "KS" to listOf("CO", "MO", "NE", "OK"),
        "KY" to listOf("IL", "IN", "MO", "OH", "TN", "VA", "WV"),
        "LA" to listOf("AR", "MS", "TX"),
        "ME" to listOf("NH"),
        "MD" to listOf("DC", "DE", "PA", "VA", "WV"),
        "MA" to listOf("CT", "NH", "NY", "RI", "VT"),
        "MI" to listOf("IN", "OH", "WI"),
        "MN" to listOf("IA", "ND", "SD", "WI"),
        "MS" to listOf("AL", "AR", "LA", "TN"),
        "MO" to listOf("AR", "IA", "IL", "KS", "KY", "NE", "OK", "TN"),
        "MT" to listOf("ID", "ND", "SD", "WY"),
        "NE" to listOf("CO", "IA", "KS", "MO", "SD", "WY"),
        "NV" to listOf("AZ", "CA", "ID", "OR", "UT"),
        "NH" to listOf("MA", "ME", "VT"),
        "NJ" to listOf("DE", "NY", "PA"),
        "NM" to listOf("AZ", "CO", "OK", "TX"),
        "NY" to listOf("CT", "MA", "NJ", "PA", "VT"),
        "NC" to listOf("GA", "SC", "TN", "VA"),
        "ND" to listOf("MN", "MT", "SD"),
        "OH" to listOf("IN", "KY", "MI", "PA", "WV"),
        "OK" to listOf("AR", "CO", "KS", "MO", "NM", "TX"),
        "OR" to listOf("CA", "ID", "NV", "WA"),
        "PA" to listOf("DE", "MD", "NJ", "NY", "OH", "WV"),
        "RI" to listOf("CT", "MA"),
        "SC" to listOf("GA", "NC"),
        "SD" to listOf("IA", "MN", "MT", "ND", "NE", "WY"),
        "TN" to listOf("AL", "AR", "GA", "KY", "MO", "MS", "NC", "VA"),
        "TX" to listOf("AR", "LA", "NM", "OK"),
        "UT" to listOf("AZ", "CO", "ID", "NV", "WY"),
        "VT" to listOf("MA", "NH", "NY"),
        "VA" to listOf("DC", "KY", "MD", "NC", "TN", "WV"),
        "WA" to listOf("ID", "OR"),
        "WV" to listOf("KY", "MD", "OH", "PA", "VA"),
        "WI" to listOf("IA", "IL", "MI", "MN"),
        "WY" to listOf("CO", "ID", "MT", "NE", "SD", "UT"),
    )
