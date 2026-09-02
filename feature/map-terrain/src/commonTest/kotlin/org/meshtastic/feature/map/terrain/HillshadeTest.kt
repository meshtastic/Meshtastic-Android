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
package org.meshtastic.feature.map.terrain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HillshadeTest {

    /** A 3x3 padded tile (margin=1 around a single 1x1 output pixel) from a flat row-major 3x3 array. */
    private fun padded3x3(values: List<Float>): ElevationTile = ElevationTile(3, 3, values.toFloatArray())

    @Test
    fun `flat terrain casts no shadow - regardless of elevation`() {
        val flat = padded3x3(List(9) { 100f })
        val shadow = Hillshade.shade(flat, width = 1, height = 1, maxShadowAlpha = 1f)
        assertEquals(0f, shadow[0])
    }

    @Test
    fun `a slope facing away from the fixed NW light casts a nonzero shadow`() {
        // Elevation rises to the west (falls to the east) — the light is fixed at azimuth 315 (NW), so this
        // slope's uphill/lit side faces away from it. A slope rising the other way (east) instead faces toward
        // the light and is legitimately *brighter* than flat ground (illumination > sinAltitude, clamped to zero
        // shadow) — that's correct hillshade behavior, not a bug, which is why this test picks the orientation
        // that should actually be shadowed rather than asserting "any slope shadows."
        val ramp = padded3x3(listOf(110f, 100f, 90f, 110f, 100f, 90f, 110f, 100f, 90f))
        val shadow = Hillshade.shade(ramp, width = 1, height = 1, maxShadowAlpha = 1f)
        assertTrue(shadow[0] > 0f, "expected a nonzero shadow for a slope facing away from the light, got ${shadow[0]}")
    }

    @Test
    fun `sea-level fade zeroes shading for a slope entirely under half a meter`() {
        val shallowRamp = padded3x3(listOf(0.1f, 0.2f, 0.3f, 0.1f, 0.2f, 0.3f, 0.1f, 0.2f, 0.3f))
        val shadow = Hillshade.shade(shallowRamp, width = 1, height = 1, maxShadowAlpha = 1f)
        assertEquals(0f, shadow[0])
    }

    @Test
    fun `local-relief fade zeroes shading for a pixel barely above its own neighborhood minimum`() {
        // center 10.1 sits only 0.1m above its 10.0 neighbors — under LOCAL_RELIEF_FADE_START (0.3m) — even
        // though 10m is nowhere near the sea-level fade's own threshold.
        val nearFlatAtAltitude = padded3x3(listOf(10f, 10f, 10f, 10f, 10.1f, 10f, 10f, 10f, 10f))
        val shadow = Hillshade.shade(nearFlatAtAltitude, width = 1, height = 1, maxShadowAlpha = 1f)
        assertEquals(0f, shadow[0])
    }

    @Test
    fun `an isolated single-pixel spike is despiked to its neighborhood's median before shading`() {
        // The output pixel itself spikes 20m above 8 otherwise-flat neighbors; despike should replace it with
        // the neighborhood median (the flat value), leaving nothing left to shade.
        val spike = padded3x3(listOf(50f, 50f, 50f, 50f, 70f, 50f, 50f, 50f, 50f))
        val shadow = Hillshade.shade(spike, width = 1, height = 1, maxShadowAlpha = 1f)
        assertEquals(0f, shadow[0])
    }

    @Test
    fun `maxShadowAlpha scales the output linearly`() {
        // Same shadow-facing orientation as the test above — this needs a genuinely nonzero shadow, or "full *
        // 0.5 == half" would hold trivially at 0 == 0 without exercising the scaling at all.
        val ramp = padded3x3(listOf(110f, 100f, 90f, 110f, 100f, 90f, 110f, 100f, 90f))
        val full = Hillshade.shade(ramp, width = 1, height = 1, maxShadowAlpha = 1f)[0]
        val half = Hillshade.shade(ramp, width = 1, height = 1, maxShadowAlpha = 0.5f)[0]
        assertTrue(full > 0f, "test setup invariant: expected a nonzero shadow to scale, got $full")
        assertEquals(full * 0.5f, half, absoluteTolerance = 1e-6f)
    }

    @Test
    fun `an incorrectly padded tile is rejected rather than read out of bounds`() {
        val wrongSize = ElevationTile(2, 2, floatArrayOf(1f, 2f, 3f, 4f))
        assertFailsWith<IllegalArgumentException> {
            Hillshade.shade(wrongSize, width = 1, height = 1, maxShadowAlpha = 1f)
        }
    }

    @Test
    fun `despike leaves the margin ring's original values untouched`() {
        // 5x5 padded tile (3x3 output + a 1px margin ring): flat at 100 everywhere except the tile's own top-left
        // margin corner, spiked far above its clamped-at-the-edge neighborhood's median. If despike touched the
        // margin ring it would flatten that corner back to 100 — it must not, since the margin exists to carry real
        // neighbor-tile elevation data into edge-pixel shading, not to be cleaned up itself.
        val values = FloatArray(25) { 100f }
        values[0] = 1000f
        val padded = ElevationTile(5, 5, values)

        val despiked = Hillshade.despike(padded)

        assertEquals(1000f, despiked.elevationAt(0, 0))
    }
}
