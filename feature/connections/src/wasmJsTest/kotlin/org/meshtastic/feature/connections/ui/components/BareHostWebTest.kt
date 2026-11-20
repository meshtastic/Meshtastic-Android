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

// The browser's own address check, which takes an IPv6 literal only without its brackets.
class BareHostWebTest {
    @Test
    fun `a bracketed IPv6 host becomes its WebSocket endpoint`() {
        assertEquals("wws://[fd00::1]:80/api/v1/ws", manualAddress("[fd00::1]", "80", webSocketScheme = "ws"))
        assertEquals("wws://[fd00::1]/api/v1/ws", manualAddress("fd00::1", "", webSocketScheme = "ws"))
    }
}
