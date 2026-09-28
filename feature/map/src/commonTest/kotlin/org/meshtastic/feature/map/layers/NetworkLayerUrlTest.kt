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
package org.meshtastic.feature.map.layers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkLayerUrlTest {
    private val loopbackOnly: (String) -> Boolean = { it == "localhost" || it == "127.0.0.1" }

    @Test
    fun `plain http to a host the platform refuses is invalid and reported as refused cleartext`() {
        val url = "http://example.org/map.kml"
        assertFalse(isValidNetworkLayerUrl(url, loopbackOnly))
        assertTrue(isRefusedCleartextLayerUrl(url, loopbackOnly))
    }

    @Test
    fun `plain http to a host the platform allows is valid`() {
        assertTrue(isValidNetworkLayerUrl("http://localhost:8080/map.geojson", loopbackOnly))
        assertTrue(isValidNetworkLayerUrl("http://127.0.0.1/map.kml", loopbackOnly))
        assertFalse(isRefusedCleartextLayerUrl("http://localhost:8080/map.geojson", loopbackOnly))
    }

    @Test
    fun `https never consults the cleartext policy`() {
        val failIfAsked: (String) -> Boolean = { error("asked about $it") }
        assertTrue(isValidNetworkLayerUrl("https://example.org/map.kml", failIfAsked))
        assertFalse(isRefusedCleartextLayerUrl("https://example.org/map.kml", failIfAsked))
    }

    @Test
    fun `the cleartext policy is asked about the host alone`() {
        val asked = mutableListOf<String>()
        isValidNetworkLayerUrl("HTTP://Example.org:8080/map.kml?x=1") { host -> false.also { asked += host } }
        assertEquals(1, asked.size)
        assertEquals("example.org", asked.single().lowercase())
    }

    @Test
    fun `a url without an explicit scheme is invalid but not reported as refused cleartext`() {
        assertFalse(isValidNetworkLayerUrl("example.org/map.kml") { true })
        assertFalse(isRefusedCleartextLayerUrl("example.org/map.kml") { false })
    }
}
