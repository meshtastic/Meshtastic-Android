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
package org.meshtastic.feature.coverage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The Display section's palette picker, which the local engine ignored entirely while the sheet went on offering it —
 * every estimate came out in the same hardcoded red-amber-green whatever was chosen.
 */
class CoveragePaletteTest {

    @Test
    fun everyPaletteThePlannerOffersResolves() {
        // Mirrors SitePlannerParams.COLOR_SCALES; a name the sheet can produce must not fall back.
        for (key in listOf("plasma", "viridis", "CMRmap", "cool", "turbo", "jet")) {
            assertEquals(key, CoveragePalette.forKey(key).key, "planner offers '$key'")
        }
    }

    @Test
    fun anUnknownOrAbsentNameFallsBackToThePlannersOwnDefault() {
        assertEquals(CoveragePalette.PLASMA, CoveragePalette.forKey(null))
        assertEquals(CoveragePalette.PLASMA, CoveragePalette.forKey("nonesuch"))
    }

    @Test
    fun theRampSpansItsEndpointsAndStaysWellFormed() {
        for (palette in CoveragePalette.entries) {
            for (t in listOf(0.0, 0.25, 0.5, 0.75, 1.0)) {
                val color = palette.colorAt(t)
                assertTrue(Regex("^#[0-9a-f]{6}$").matches(color), "${palette.key} at $t produced '$color'")
            }
            assertNotEquals(
                palette.colorAt(0.0),
                palette.colorAt(1.0),
                "${palette.key} should not start and end on the same colour",
            )
        }
    }

    @Test
    fun outOfRangePositionsClampRatherThanWrap() {
        for (palette in CoveragePalette.entries) {
            assertEquals(palette.colorAt(0.0), palette.colorAt(-1.0))
            assertEquals(palette.colorAt(1.0), palette.colorAt(2.0))
        }
    }

    @Test
    fun knownAnchorsComeBackExactly() {
        // The ends are anchors, so no interpolation should touch them.
        assertEquals("#0d0887", CoveragePalette.PLASMA.colorAt(0.0))
        assertEquals("#f0f921", CoveragePalette.PLASMA.colorAt(1.0))
        assertEquals("#440154", CoveragePalette.VIRIDIS.colorAt(0.0))
        assertEquals("#fde725", CoveragePalette.VIRIDIS.colorAt(1.0))
        // cool is a straight two-stop ramp #00ffff -> #ff00ff, so its midpoint is exactly halfway.
        assertEquals("#8080ff", CoveragePalette.COOL.colorAt(0.5))
    }

    @Test
    fun transparencyBecomesOpacityTheRightWayRound() {
        // The planner calls it transparency: 0 means solid, 100 means invisible.
        assertEquals(1.0, CoverageStyle.fromTransparency("plasma", -130.0, -80.0, 0).opacity)
        assertEquals(0.5, CoverageStyle.fromTransparency("plasma", -130.0, -80.0, 50).opacity)
        assertEquals(0.0, CoverageStyle.fromTransparency("plasma", -130.0, -80.0, 100).opacity)
    }

    @Test
    fun theStyleCarriesThePickedPaletteAndRange() {
        val style = CoverageStyle.fromTransparency("turbo", minDbm = -120.0, maxDbm = -70.0, transparencyPercent = 25)
        assertEquals(CoveragePalette.TURBO, style.palette)
        assertEquals(-120.0, style.minDbm)
        assertEquals(-70.0, style.maxDbm)
        assertEquals(0.75, style.opacity)
    }
}
