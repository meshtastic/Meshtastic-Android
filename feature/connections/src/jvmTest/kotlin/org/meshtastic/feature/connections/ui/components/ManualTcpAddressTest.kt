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
package org.meshtastic.feature.connections.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ManualTcpAddressTest {

    @Test
    fun defaultPortIsLeftOffTheAddress() {
        assertEquals("192.168.1.20", manualTcpAddress("192.168.1.20", "4403"))
    }

    @Test
    fun otherValidPortIsAppended() {
        assertEquals("192.168.1.20:4404", manualTcpAddress("192.168.1.20", "4404"))
        assertEquals("192.168.1.20:65535", manualTcpAddress("192.168.1.20", "65535"))
    }

    @Test
    fun portOutsideTheValidRangeIsRejected() {
        assertNull(manualTcpAddress("192.168.1.20", "0"))
        assertNull(manualTcpAddress("192.168.1.20", "65536"))
        assertNull(manualTcpAddress("192.168.1.20", "-1"))
    }

    @Test
    fun invalidHostIsRejected() {
        assertNull(manualTcpAddress("", "4403"))
    }

    @Test
    fun nonNumericPortFallsBackToTheDefault() {
        assertEquals("192.168.1.20", manualTcpAddress("192.168.1.20", "abc"))
    }
}
