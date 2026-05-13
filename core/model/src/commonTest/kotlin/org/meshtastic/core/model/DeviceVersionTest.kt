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
package org.meshtastic.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceVersionTest {

    @Test
    fun canParse() {
        assertEquals(10000, DeviceVersion("1.0.0").asInt)
        assertEquals(10101, DeviceVersion("1.1.1").asInt)
        assertEquals(12357, DeviceVersion("1.23.57").asInt)
        assertEquals(12357, DeviceVersion("1.23.57.abde123").asInt)
    }

    @Test
    fun twoPartVersionAppends_zero() {
        assertEquals(20700, DeviceVersion("2.7").asInt)
    }

    @Test
    fun invalidVersionReturns_zero() {
        assertEquals(0, DeviceVersion("invalid").asInt)
    }

    @Test
    fun comparisonIsCorrect() {
        kotlin.test.assertTrue(DeviceVersion("2.7.12") >= DeviceVersion("2.7.11"))
        kotlin.test.assertTrue(DeviceVersion("3.0.0") > DeviceVersion("2.8.1"))
        assertEquals(DeviceVersion("2.7.12"), DeviceVersion("2.7.12"))
        kotlin.test.assertFalse(DeviceVersion("2.6.9") >= DeviceVersion("2.7.0"))
    }
}
