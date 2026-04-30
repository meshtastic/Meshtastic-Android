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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CapabilitiesTest {

    private fun caps(version: String?) = Capabilities(version, forceEnableAll = false)

    @Test
    fun canMuteNode_requires_V2_7_18() {
        assertFalse(caps("2.7.15").canMuteNode)
        assertTrue(caps("2.7.18").canMuteNode)
        assertTrue(caps("2.8.0").canMuteNode)
    }

    @Test
    fun canRequestNeighborInfo_is_currently_disabled() {
        assertFalse(caps("2.7.14").canRequestNeighborInfo)
        assertFalse(caps("3.0.0").canRequestNeighborInfo)
    }

    @Test
    fun canSendVerifiedContacts_requires_V2_7_12() {
        assertFalse(caps("2.7.11").canSendVerifiedContacts)
        assertTrue(caps("2.7.12").canSendVerifiedContacts)
    }

    @Test
    fun canToggleTelemetryEnabled_requires_V2_7_12() {
        assertFalse(caps("2.7.11").canToggleTelemetryEnabled)
        assertTrue(caps("2.7.12").canToggleTelemetryEnabled)
    }

    @Test
    fun canToggleUnmessageable_requires_V2_6_9() {
        assertFalse(caps("2.6.8").canToggleUnmessageable)
        assertTrue(caps("2.6.9").canToggleUnmessageable)
    }

    @Test
    fun supportsQrCodeSharing_requires_V2_6_8() {
        assertFalse(caps("2.6.7").supportsQrCodeSharing)
        assertTrue(caps("2.6.8").supportsQrCodeSharing)
    }

    @Test
    fun supportsSecondaryChannelLocation_requires_V2_6_10() {
        assertFalse(caps("2.6.9").supportsSecondaryChannelLocation)
        assertTrue(caps("2.6.10").supportsSecondaryChannelLocation)
    }

    @Test
    fun supportsStatusMessage_requires_V2_8_0() {
        assertFalse(caps("2.7.21").supportsStatusMessage)
        assertTrue(caps("2.8.0").supportsStatusMessage)
    }

    @Test
    fun supportsTrafficManagementConfig_requires_V3_0_0() {
        assertFalse(caps("2.7.18").supportsTrafficManagementConfig)
        assertTrue(caps("3.0.0").supportsTrafficManagementConfig)
    }

    @Test
    fun supportsTakConfig_requires_V2_7_19() {
        assertFalse(caps("2.7.18").supportsTakConfig)
        assertTrue(caps("2.7.19").supportsTakConfig)
    }

    @Test
    fun supportsEsp32Ota_requires_V2_7_18() {
        assertFalse(caps("2.7.17").supportsEsp32Ota)
        assertTrue(caps("2.7.18").supportsEsp32Ota)
    }

    @Test
    fun nullFirmware_returns_all_false() {
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
    fun forceEnableAll_returns_true_regardless_of_version() {
        val c = Capabilities(firmwareVersion = null, forceEnableAll = true)
        assertTrue(c.canMuteNode)
        assertTrue(c.canSendVerifiedContacts)
        assertTrue(c.supportsStatusMessage)
        assertTrue(c.supportsTrafficManagementConfig)
        assertTrue(c.supportsTakConfig)
    }
}
