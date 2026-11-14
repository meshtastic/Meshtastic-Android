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
package org.meshtastic.core.model.util

import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.ModuleSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class PositionPrecisionTest {

    private fun settingsWithPrecision(bits: Int) = ChannelSettings.Builder()
        .also { wb ->
            wb.module_settings = ModuleSettings.Builder().also { mb -> mb.position_precision = bits }.build()
        }
        .build()

    @Test
    fun `a public key channel has precise location capped at the firmware ceiling`() {
        val capped = settingsWithPrecision(32).withOnWirePrecision(usesPublicKey = true)

        assertEquals(15, capped.module_settings?.position_precision)
    }

    @Test
    fun `a public key channel with no module settings shares nothing explicitly`() {
        val capped = ChannelSettings.Builder().build().withOnWirePrecision(usesPublicKey = true)

        assertEquals(0, capped.module_settings?.position_precision)
    }

    @Test
    fun `a public key channel keeps a coarser precision and position off`() {
        val coarse = settingsWithPrecision(13)
        val off = settingsWithPrecision(0)

        assertSame(coarse, coarse.withOnWirePrecision(usesPublicKey = true))
        assertSame(off, off.withOnWirePrecision(usesPublicKey = true))
    }

    @Test
    fun `a private key channel is left as configured`() {
        val precise = settingsWithPrecision(32)
        val unset = ChannelSettings.Builder().build()

        assertSame(precise, precise.withOnWirePrecision(usesPublicKey = false))
        assertSame(unset, unset.withOnWirePrecision(usesPublicKey = false))
    }

    @Test
    fun `radii match the values the OSMdroid marker used`() {
        assertEquals(23345.484932, precisionRadiusMetersOrNull(10))
        assertEquals(45.58554, precisionRadiusMetersOrNull(19))
    }

    @Test
    fun `an undegraded position has no uncertainty circle`() {
        // The whole reason this is nullable: a formula answers for these too, and the callers that used one drew a
        // circle 23,905 km across for `0` and 5 mm across for `32`.
        assertNull(precisionRadiusMetersOrNull(0))
        assertNull(precisionRadiusMetersOrNull(32))
        assertNull(precisionRadiusMetersOrNull(null))
    }

    @Test
    fun `every degraded depth has a radius`() {
        DEGRADED_PRECISION_BITS.forEach { bits ->
            assertNotNull(precisionRadiusMetersOrNull(bits), "no radius for precision_bits=$bits")
        }
    }

    @Test
    fun `a public key caps precision at the firmware ceiling`() {
        assertEquals(15, onWirePrecisionBits(32, usesPublicKey = true))
        assertEquals(15, onWirePrecisionBits(19, usesPublicKey = true))
        assertEquals(15, onWirePrecisionBits(15, usesPublicKey = true))
        assertEquals(13, onWirePrecisionBits(13, usesPublicKey = true))
    }

    @Test
    fun `a public key leaves position sharing off`() {
        assertEquals(0, onWirePrecisionBits(0, usesPublicKey = true))
    }

    @Test
    fun `a private key sends the configured precision`() {
        assertEquals(32, onWirePrecisionBits(32, usesPublicKey = false))
        assertEquals(19, onWirePrecisionBits(19, usesPublicKey = false))
        assertEquals(0, onWirePrecisionBits(0, usesPublicKey = false))
    }

    @Test
    fun `each extra bit roughly halves the radius`() {
        DEGRADED_PRECISION_BITS.zipWithNext { coarser, finer ->
            val ratio = precisionRadiusMetersOrNull(coarser)!! / precisionRadiusMetersOrNull(finer)!!
            assertEquals(2.0, ratio, absoluteTolerance = 0.001, message = "$coarser -> $finer halving broke")
        }
    }
}
