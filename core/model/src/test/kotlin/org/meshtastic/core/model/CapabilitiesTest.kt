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

    private fun caps(version: String?) = Capabilities(version, forceEnableAll = false)

    @Test
    fun `canMuteNode requires v2 7 18`() {
        assertFalse(caps("2.7.15").canMuteNode)
        assertTrue(caps("2.7.18").canMuteNode)
        assertTrue(caps("2.8.0").canMuteNode)
        assertTrue(caps("2.8.1").canMuteNode)
    }

    // FIXME: needs updating when NeighborInfo is working properly
    @Test
    fun `canRequestNeighborInfo disabled`() {
        assertFalse(caps("2.7.14").canRequestNeighborInfo)
        assertFalse(caps("2.7.15").canRequestNeighborInfo)
        assertFalse(caps("2.8.0").canRequestNeighborInfo)
    }

    @Test
    fun `canSendVerifiedContacts requires v2 7 12`() {
        assertFalse(caps("2.7.11").canSendVerifiedContacts)
        assertTrue(caps("2.7.12").canSendVerifiedContacts)
        assertTrue(caps("2.7.15").canSendVerifiedContacts)
    }

    @Test
    fun `canToggleTelemetryEnabled requires v2 7 12`() {
        assertFalse(caps("2.7.11").canToggleTelemetryEnabled)
        assertTrue(caps("2.7.12").canToggleTelemetryEnabled)
    }

    @Test
    fun `canToggleUnmessageable requires v2 6 9`() {
        assertFalse(caps("2.6.8").canToggleUnmessageable)
        assertTrue(caps("2.6.9").canToggleUnmessageable)
    }

    @Test
    fun `supportsQrCodeSharing requires v2 6 8`() {
        assertFalse(caps("2.6.7").supportsQrCodeSharing)
        assertTrue(caps("2.6.8").supportsQrCodeSharing)
    }

    @Test
    fun `supportsSecondaryChannelLocation requires v2 6 10`() {
        assertFalse(caps("2.6.9").supportsSecondaryChannelLocation)
        assertTrue(caps("2.6.10").supportsSecondaryChannelLocation)
    }

    @Test
    fun `supportsStatusMessage requires v2 7 17`() {
        assertFalse(caps("2.7.16").supportsStatusMessage)
        assertTrue(caps("2.7.17").supportsStatusMessage)
    }

    @Test
    fun `null firmware returns all false`() {
        val c = caps(null)
        assertFalse(c.canMuteNode)
        assertFalse(c.canRequestNeighborInfo)
        assertFalse(c.canSendVerifiedContacts)
        assertFalse(c.canToggleTelemetryEnabled)
        assertFalse(c.canToggleUnmessageable)
        assertFalse(c.supportsQrCodeSharing)
        assertFalse(c.supportsSecondaryChannelLocation)
        assertFalse(c.supportsStatusMessage)
    }

    @Test
    fun `invalid firmware returns all false`() {
        val c = caps("invalid")
        assertFalse(c.canMuteNode)
        assertFalse(c.canRequestNeighborInfo)
        assertFalse(c.canSendVerifiedContacts)
        assertFalse(c.canToggleTelemetryEnabled)
        assertFalse(c.canToggleUnmessageable)
        assertFalse(c.supportsQrCodeSharing)
        assertFalse(c.supportsSecondaryChannelLocation)
        assertFalse(c.supportsStatusMessage)
    }

    @Test
    fun `forceEnableAll returns true for everything regardless of version`() {
        val c = Capabilities(firmwareVersion = null, forceEnableAll = true)
        assertTrue(c.canMuteNode)
        assertTrue(c.canRequestNeighborInfo)
        assertTrue(c.canSendVerifiedContacts)
        assertTrue(c.canToggleTelemetryEnabled)
        assertTrue(c.canToggleUnmessageable)
        assertTrue(c.supportsQrCodeSharing)
        assertTrue(c.supportsSecondaryChannelLocation)
        assertTrue(c.supportsStatusMessage)
    }

    @Test
    fun `forceEnableAll returns true even for invalid versions`() {
        val c = Capabilities(firmwareVersion = "invalid", forceEnableAll = true)
        assertTrue(c.canMuteNode)
        assertTrue(c.canRequestNeighborInfo)
        assertTrue(c.canSendVerifiedContacts)
        assertTrue(c.canToggleTelemetryEnabled)
        assertTrue(c.canToggleUnmessageable)
        assertTrue(c.supportsQrCodeSharing)
        assertTrue(c.supportsSecondaryChannelLocation)
        assertTrue(c.supportsStatusMessage)
    }
}
