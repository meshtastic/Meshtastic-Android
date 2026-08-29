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

import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * These assert compatibility, not correctness in the abstract.
 *
 * The expected strings are what Android's `Uri.fromFile` produces. A layer's URI is the key its hidden/shown state
 * persists under, so drift here silently un-hides every layer a user had hidden.
 */
class MapLayerUriTest {

    @Test
    fun `a plain path needs no escaping`() {
        assertEquals("file:///data/map_layers/route.kml", "/data/map_layers/route.kml".toPath().toFileUri())
    }

    @Test
    fun `a space becomes percent 20`() {
        // The common case, not an edge case: layerFileName keeps spaces, so any multi-word layer name lands here.
        assertEquals("file:///data/My%20Route.kml", "/data/My Route.kml".toPath().toFileUri())
    }

    @Test
    fun `the unreserved set Android allows is left alone`() {
        assertEquals("file:///data/a_b-c!d.e~f'g(h)i*j.kml", "/data/a_b-c!d.e~f'g(h)i*j.kml".toPath().toFileUri())
    }

    @Test
    fun `characters Android escapes are escaped`() {
        assertEquals("file:///data/a%2Cb%26c%2Bd.kml", "/data/a,b&c+d.kml".toPath().toFileUri())
    }

    @Test
    fun `non-latin names round-trip as utf-8 bytes`() {
        val uri = "/data/Ünïcødé.kml".toPath().toFileUri()

        assertEquals("file:///data/%C3%9Cn%C3%AFc%C3%B8d%C3%A9.kml", uri)
        assertEquals("/data/Ünïcødé.kml", uri.toLocalPath().toString())
    }

    @Test
    fun `every escaped form decodes back to its own path`() {
        listOf("/data/My Route.kml", "/data/a,b&c.kml", "/data/Ünïcødé.kml", "/data/plain.kml").forEach { path ->
            assertEquals(path, path.toPath().toFileUri().toLocalPath().toString())
        }
    }
}
