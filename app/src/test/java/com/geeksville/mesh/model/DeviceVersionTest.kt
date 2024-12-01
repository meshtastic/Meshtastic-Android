/*
 * Copyright (c) 2024 Meshtastic LLC
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

package com.geeksville.mesh.model

import org.junit.Assert.*
import org.junit.Test

class DeviceVersionTest {
    /** make sure we match the python and device code behavior */
    @Test
    fun canParse() {

        assertEquals(10000, DeviceVersion("1.0.0").asInt)
        assertEquals(10101, DeviceVersion("1.1.1").asInt)
        assertEquals(12357, DeviceVersion("1.23.57").asInt)
        assertEquals(12357, DeviceVersion("1.23.57.abde123").asInt)
    }
}