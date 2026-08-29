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
import kotlin.test.assertTrue

class LayerOpacityTest {

    @Test
    fun `an unset layer is fully opaque`() {
        assertEquals(1f, emptyMap<String, Float>().opacityOf("hillshade"))
    }

    @Test
    fun `a stored opacity survives a round trip`() {
        val encoded = encodeLayerOpacity(mapOf("hillshade" to 0.4f, "file:///a.kml" to 0.75f))
        assertEquals(mapOf("hillshade" to 0.4f, "file:///a.kml" to 0.75f), decodeLayerOpacity(encoded))
    }

    @Test
    fun `a fully opaque layer is not stored`() {
        // The default is opaque, so writing it back would grow the set by one entry per layer the user ever touched.
        assertTrue(encodeLayerOpacity(mapOf("hillshade" to 1f)).isEmpty())
    }

    @Test
    fun `a key containing the delimiter still decodes`() {
        // Keys are imported-layer URIs as well as catalogue ids, and a URI is user-supplied.
        val encoded = encodeLayerOpacity(mapOf("file:///odd|:|name.kml" to 0.5f))
        assertEquals(mapOf("file:///odd|:|name.kml" to 0.5f), decodeLayerOpacity(encoded))
    }

    @Test
    fun `a malformed entry is dropped rather than failing the load`() {
        assertEquals(emptyMap<String, Float>(), decodeLayerOpacity(setOf("no-delimiter", "bad|:|notafloat", "|:|0.5")))
    }

    @Test
    fun `an out of range value is clamped`() {
        assertEquals(mapOf("a" to 0f, "b" to 1f), decodeLayerOpacity(setOf("a|:|-3.0", "b|:|7.5")))
    }
}
