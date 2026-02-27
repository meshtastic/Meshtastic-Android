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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilitiesTest {

    private fun caps(version: String?) = Capabilities(version, forceEnableAll = false)

    @Test
    fun canMuteNodeRequiresV2718() {
        assertFalse(caps("2.7.15").canMuteNode)
        assertTrue(caps("2.7.18").canMuteNode)
        assertTrue(caps("2.8.0").canMuteNode)
    }

    @Test
    fun canRequestNeighborInfoIsCurrentlyDisabled() {
        assertFalse(caps("2.7.14").canRequestNeighborInfo)
        assertFalse(caps("3.0.0").canRequestNeighborInfo)
    }

    @Test
    fun canSendVerifiedContactsRequiresV2712() {
        assertFalse(caps("2.7.11").canSendVerifiedContacts)
        assertTrue(caps("2.7.12").canSendVerifiedContacts)
    }

    @Test
    fun canToggleTelemetryEnabledRequiresV2712() {
        assertFalse(caps("2.7.11").canToggleTelemetryEnabled)
        assertTrue(caps("2.7.12").canToggleTelemetryEnabled)
    }

    @Test
    fun canToggleUnmessageableRequiresV269() {
        assertFalse(caps("2.6.8").canToggleUnmessageable)
        assertTrue(caps("2.6.9").canToggleUnmessageable)
    }

    @Test
    fun supportsQrCodeSharingRequiresV268() {
        assertFalse(caps("2.6.7").supportsQrCodeSharing)
        assertTrue(caps("2.6.8").supportsQrCodeSharing)
    }

    @Test
    fun supportsSecondaryChannelLocationRequiresV2610() {
        assertFalse(caps("2.6.9").supportsSecondaryChannelLocation)
        assertTrue(caps("2.6.10").supportsSecondaryChannelLocation)
    }

    @Test
    fun supportsStatusMessageRequiresV2717() {
        assertFalse(caps("2.7.16").supportsStatusMessage)
        assertTrue(caps("2.7.17").supportsStatusMessage)
    }

    @Test
    fun supportsTrafficManagementConfigRequiresV300() {
        assertFalse(caps("2.7.18").supportsTrafficManagementConfig)
        assertTrue(caps("3.0.0").supportsTrafficManagementConfig)
    }

    @Test
    fun supportsTakConfigRequiresV2719() {
        assertFalse(caps("2.7.18").supportsTakConfig)
        assertTrue(caps("2.7.19").supportsTakConfig)
    }

    @Test
    fun supportsEsp32OtaRequiresV2718() {
        assertFalse(caps("2.7.17").supportsEsp32Ota)
        assertTrue(caps("2.7.18").supportsEsp32Ota)
    }

    @Test
    fun nullFirmwareReturnsAllFalse() {
        val c = caps(null)
        assertFalse(c.canMuteNode)
        assertFalse(c.canRequestNeighborInfo)
        assertFalse(c.canSendVerifiedContacts)
        assertFalse(c.canToggleTelemetryEnabled)
        assertFalse(c.canToggleUnmessageable)
        assertFalse(c.supportsQrCodeSharing)
        assertFalse(c.supportsSecondaryChannelLocation)
        assertFalse(c.supportsStatusMessage)
        assertFalse(c.supportsTrafficManagementConfig)
        assertFalse(c.supportsTakConfig)
        assertFalse(c.supportsEsp32Ota)
    }

    @Test
    fun forceEnableAllReturnsTrueForEverythingRegardlessOfVersion() {
        val c = Capabilities(firmwareVersion = null, forceEnableAll = true)
        assertTrue(c.canMuteNode)
        assertTrue(c.canSendVerifiedContacts)
        assertTrue(c.supportsStatusMessage)
        assertTrue(c.supportsTrafficManagementConfig)
        assertTrue(c.supportsTakConfig)
    }

    @Test
    fun deviceVersionParsingIsRobust() {
        assertEquals(20712, DeviceVersion("2.7.12").asInt)
        assertEquals(20712, DeviceVersion("2.7.12-beta").asInt)
        assertEquals(30000, DeviceVersion("3.0.0").asInt)
        assertEquals(20700, DeviceVersion("2.7").asInt) // Handles 2-part versions
        assertEquals(0, DeviceVersion("invalid").asInt)
    }

    @Test
    fun deviceVersionComparisonIsCorrect() {
        assertTrue(DeviceVersion("2.7.12") >= DeviceVersion("2.7.11"))
        assertTrue(DeviceVersion("3.0.0") > DeviceVersion("2.8.1"))
        assertTrue(DeviceVersion("2.7.12") == DeviceVersion("2.7.12"))
        assertFalse(DeviceVersion("2.6.9") >= DeviceVersion("2.7.0"))
    }
}
