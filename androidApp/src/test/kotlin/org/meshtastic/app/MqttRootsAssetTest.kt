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
package org.meshtastic.app

import kotlinx.serialization.json.Json
import org.meshtastic.core.model.MqttRootsResponse
import org.meshtastic.core.network.mqtt.MqttRootDirectory
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** The bundled `mqtt_roots.json` seed decodes to a directory the topic finder can place a US user with. */
class MqttRootsAssetTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `the bundled seed decodes with symmetric neighbors`() {
        val directory = MqttRootDirectory.from(json.decodeFromString<MqttRootsResponse>(asset().readText()))

        val neighbors = directory.neighbors
        neighbors.forEach { (a, set) -> set.forEach { b -> assertTrue(a in neighbors.getValue(b), "$b lacks $a") } }
        assertEquals(setOf("US-AR", "US-LA", "US-NM", "US-OK"), neighbors.getValue("US-TX"))
        assertEquals("WA", directory.subdivisionSegment("US", "Washington"))
        assertTrue(directory.entries.isNotEmpty())
    }

    private fun asset(): File =
        listOf("src/main/assets/$NAME", "androidApp/src/main/assets/$NAME").map(::File).firstOrNull(File::exists)
            ?: fail("Could not locate $NAME from working directory ${File(".").absolutePath}")

    private companion object {
        const val NAME = "mqtt_roots.json"
    }
}
