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
package org.meshtastic.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The MQTT root directory the topic finder places a user with: envelope of `resource/mqttRoots` and the bundled
 * `mqtt_roots.json` seed asset, served verbatim from meshtastic/api `data/mqttRoots.json`.
 */
@Serializable
data class MqttRootsResponse(
    @SerialName("entries") val entries: List<MqttRootsEntry> = emptyList(),
    /** ISO 3166-2 code to the codes it borders, both directions present. */
    @SerialName("neighbors") val neighbors: Map<String, List<String>> = emptyMap(),
    /** ISO 3166-1 country to a geocoder's lowercased subdivision name, mapped to the topic segment. */
    @SerialName("subdivisionSegments") val subdivisionSegments: Map<String, Map<String, String>> = emptyMap(),
)

/**
 * A community root whose name says nothing about where it is, with the area it serves as ISO 3166-2 [subdivisions], a
 * [center] and [radiusKm], or both.
 *
 * @property center `[lat, lon]`.
 */
@Serializable
data class MqttRootsEntry(
    @SerialName("root") val root: String,
    @SerialName("name") val name: String,
    @SerialName("subdivisions") val subdivisions: List<String> = emptyList(),
    @SerialName("center") val center: List<Double>? = null,
    @SerialName("radiusKm") val radiusKm: Double? = null,
)
