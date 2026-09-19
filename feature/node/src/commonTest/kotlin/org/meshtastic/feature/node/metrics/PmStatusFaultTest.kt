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
package org.meshtastic.feature.node.metrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Pins the SEN6X bit layout the proto documents for `pm_status_flags`, and the pass-through of bits it does not. */
class PmStatusFaultTest {

    @Test
    fun `a clean register decodes to no faults`() {
        assertEquals(emptyList(), PmStatusFault.decode(0))
        assertEquals(0, PmStatusFault.unknownBits(0))
        assertNull(PmStatusFault.unknownBitsHex(0))
    }

    @Test
    fun `each documented bit decodes to its fault in bit order`() {
        val flags = (1 shl 21) or (1 shl 11) or (1 shl 4)
        assertEquals(
            listOf(PmStatusFault.FAN_ERROR, PmStatusFault.PM_ERROR, PmStatusFault.FAN_SPEED_WARNING),
            PmStatusFault.decode(flags),
        )
        assertEquals(0, PmStatusFault.unknownBits(flags))
    }

    @Test
    fun `the two CO2 error bits report one fault`() {
        val flags = (1 shl 9) or (1 shl 12)
        assertEquals(listOf(PmStatusFault.CO2_ERROR_SEN66), PmStatusFault.decode(flags))
    }

    @Test
    fun `bits no sensor documents are kept as unknown`() {
        val flags = (1 shl 0) or (1 shl 6) or (1 shl 30)
        assertEquals(listOf(PmStatusFault.RHT_ERROR), PmStatusFault.decode(flags))
        assertEquals((1 shl 0) or (1 shl 30), PmStatusFault.unknownBits(flags))
        assertEquals("40000001", PmStatusFault.unknownBitsHex(flags))
    }

    @Test
    fun `bit 31 renders as an unsigned register`() {
        val flags = 1 shl 31
        assertEquals(emptyList(), PmStatusFault.decode(flags))
        assertEquals("80000000", PmStatusFault.unknownBitsHex(flags))
    }

    @Test
    fun `only the fan speed bit is a warning`() {
        assertEquals(listOf(PmStatusFault.FAN_SPEED_WARNING), PmStatusFault.entries.filter { it.isWarning })
    }
}
