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
package org.meshtastic.feature.firmware.ota.dfu

import kotlin.test.Test
import kotlin.test.assertEquals

class DfuCrc32Test {

    @Test
    fun testChecksumCalculation() {
        // Simple test for known string "123456789"
        val data = "123456789".encodeToByteArray()
        val crc = DfuCrc32.calculate(data)

        // Expected CRC32 for "123456789" is 0xCBF43926
        assertEquals(0xCBF43926.toInt(), crc)
    }

    @Test
    fun testChecksumCalculationWithSeed() {
        // Splitting "123456789" into "1234" and "56789"
        val part1 = "1234".encodeToByteArray()
        val part2 = "56789".encodeToByteArray()

        val crc1 = DfuCrc32.calculate(part1)
        val crc2 = DfuCrc32.calculate(part2, seed = crc1)

        assertEquals(0xCBF43926.toInt(), crc2)
    }
}
