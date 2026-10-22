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

import org.meshtastic.core.model.MqttRootsResponse
import org.meshtastic.core.network.di.CoreNetworkModule
import org.meshtastic.core.network.mqtt.TopicSuggestion.Kind.COMMUNITY
import org.meshtastic.core.repository.GeocodedPlace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MqttRootDirectoryTest {
    private val json = CoreNetworkModule().provideJson()

    @Test
    fun apiShapeMapsToTheDirectory() {
        val directory = MqttRootDirectory.from(json.decodeFromString<MqttRootsResponse>(API_SAMPLE))

        assertEquals("WA", directory.subdivisionSegment("us", "Washington"))
        assertEquals(setOf("US-AR", "US-LA", "US-NM", "US-OK"), directory.neighbors["US-TX"])
        val stl = directory.entries.single { it.root == "msh/US/MeshSTL" }
        assertEquals(LatLon(38.581, -90.374), stl.center)
        assertEquals(50.0, stl.radiusKm)
        val dmv = directory.entries.single { it.root == "msh/US/DMV" }
        assertEquals(setOf("US-DC", "US-MD", "US-VA"), dmv.subdivisions)
        assertNull(dmv.center)
    }

    @Test
    fun aCenterThatIsNotALatLonPairPlacesNothingByRadius() {
        val response =
            json.decodeFromString<MqttRootsResponse>(
                """{"entries":[{"root":"msh/US/odd","name":"odd","center":[38.5],"radiusKm":50}]}""",
            )

        val directory = MqttRootDirectory.from(response)

        assertNull(directory.entries.single().center)
        assertTrue(directory.entriesServing(null, LatLon(38.5, -90.0)).isEmpty())
    }

    @Test
    fun fetchedEntriesBecomeCommunitySuggestions() {
        val directory = MqttRootDirectory.from(json.decodeFromString<MqttRootsResponse>(API_SAMPLE))

        val belleville =
            TopicPlan.build("US", GeocodedPlace("US", "Illinois", "Belleville"), LatLon(38.52, -89.98), directory)

        assertTrue(belleville.suggestions.any { it.kind == COMMUNITY && it.topic == "msh/US/MeshSTL" })
        assertTrue(MqttRootDirectory.EMPTY.entriesServing(null, LatLon(38.52, -89.98)).isEmpty())
    }

    private companion object {
        /** The `resource/mqttRoots` shape, trimmed, with fields this client does not know. */
        val API_SAMPLE =
            """
            {
              "schemaVersion": 1,
              "entries": [
                {"root": "msh/US/MeshSTL", "name": "MeshSTL", "center": [38.581, -90.374], "radiusKm": 50,
                 "contact": "ignored"},
                {"root": "msh/US/DMV", "name": "DMV", "subdivisions": ["US-DC", "US-MD", "US-VA"]}
              ],
              "neighbors": {
                "US-TX": ["US-AR", "US-LA", "US-NM", "US-OK"],
                "US-AR": ["US-TX"], "US-LA": ["US-TX"], "US-NM": ["US-TX"], "US-OK": ["US-TX"]
              },
              "subdivisionSegments": {"US": {"washington": "WA", "illinois": "IL", "texas": "TX"}}
            }
            """
                .trimIndent()
    }
}
