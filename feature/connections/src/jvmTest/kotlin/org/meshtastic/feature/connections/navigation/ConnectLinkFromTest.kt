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
package org.meshtastic.feature.connections.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConnectLinkFromTest {

    @Test
    fun `a scanned meshtastic link yields its address and PIN`() {
        val link = connectLinkFrom("meshtastic://meshtastic/connections?address=xAA:BB:CC:DD:EE:FF&pin=012345")
        assertEquals("xAA:BB:CC:DD:EE:FF", link?.address)
        assertEquals("012345", link?.pin)
    }

    @Test
    fun `the https form and surrounding whitespace are accepted`() {
        val link = connectLinkFrom("  https://meshtastic.org/connections?address=t192.168.1.20:4403\n")
        assertEquals("t192.168.1.20:4403", link?.address)
        assertNull(link?.pin)
    }

    @Test
    fun `a connections link on another host is ignored`() {
        assertNull(connectLinkFrom("https://example.com/connections?address=xAA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun `a link without a usable address is ignored`() {
        assertNull(connectLinkFrom("meshtastic://meshtastic/connections"))
        assertNull(connectLinkFrom("meshtastic://meshtastic/connections?address=zAA:BB:CC:DD:EE:FF"))
        assertNull(connectLinkFrom("meshtastic://meshtastic/connections?address=xAA:BB"))
    }

    @Test
    fun `other Meshtastic links and plain text are ignored`() {
        assertNull(connectLinkFrom("https://meshtastic.org/e/#CgMSAQE"))
        assertNull(connectLinkFrom("meshtastic://meshtastic/settings"))
        assertNull(connectLinkFrom("hello"))
    }
}
