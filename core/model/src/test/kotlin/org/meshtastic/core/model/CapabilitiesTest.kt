/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilitiesTest {

    @Test
    fun `canMuteNode requires v2 8 0`() {
        assertFalse(Capabilities("2.7.15").canMuteNode)
        assertFalse(Capabilities("2.7.99").canMuteNode)
        assertTrue(Capabilities("2.8.0").canMuteNode)
        assertTrue(Capabilities("2.8.1").canMuteNode)
    }

    @Test
    fun `canRequestNeighborInfo requires v2 7 15`() {
        assertFalse(Capabilities("2.7.14").canRequestNeighborInfo)
        assertTrue(Capabilities("2.7.15").canRequestNeighborInfo)
        assertTrue(Capabilities("2.8.0").canRequestNeighborInfo)
    }

    @Test
    fun `canSendVerifiedContacts requires v2 7 12`() {
        assertFalse(Capabilities("2.7.11").canSendVerifiedContacts)
        assertTrue(Capabilities("2.7.12").canSendVerifiedContacts)
        assertTrue(Capabilities("2.7.15").canSendVerifiedContacts)
    }

    @Test
    fun `canToggleTelemetryEnabled requires v2 7 12`() {
        assertFalse(Capabilities("2.7.11").canToggleTelemetryEnabled)
        assertTrue(Capabilities("2.7.12").canToggleTelemetryEnabled)
    }

    @Test
    fun `canToggleUnmessageable requires v2 6 9`() {
        assertFalse(Capabilities("2.6.8").canToggleUnmessageable)
        assertTrue(Capabilities("2.6.9").canToggleUnmessageable)
    }

    @Test
    fun `supportsQrCodeSharing requires v2 6 8`() {
        assertFalse(Capabilities("2.6.7").supportsQrCodeSharing)
        assertTrue(Capabilities("2.6.8").supportsQrCodeSharing)
    }

    @Test
    fun `null firmware returns all false`() {
        val caps = Capabilities(null)
        assertFalse(caps.canMuteNode)
        assertFalse(caps.canRequestNeighborInfo)
        assertFalse(caps.canSendVerifiedContacts)
        assertFalse(caps.canToggleTelemetryEnabled)
        assertFalse(caps.canToggleUnmessageable)
        assertFalse(caps.supportsQrCodeSharing)
    }

    @Test
    fun `invalid firmware returns all false`() {
        val caps = Capabilities("invalid")
        assertFalse(caps.canMuteNode)
        assertFalse(caps.canRequestNeighborInfo)
        assertFalse(caps.canSendVerifiedContacts)
        assertFalse(caps.canToggleTelemetryEnabled)
        assertFalse(caps.canToggleUnmessageable)
        assertFalse(caps.supportsQrCodeSharing)
    }
}
