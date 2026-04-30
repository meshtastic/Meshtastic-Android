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

import org.meshtastic.core.model.util.isDebug

/**
 * Defines the capabilities and feature support based on the device firmware version.
 *
 * This class provides a centralized way to check if specific features are supported by the connected node's firmware.
 * Add new features here to ensure consistency across the app.
 *
 * Note: Properties are calculated once during initialization for efficiency.
 */
data class Capabilities(val firmwareVersion: String?, internal val forceEnableAll: Boolean = isDebug) {
    private val version = firmwareVersion?.let { DeviceVersion(it) }

    private fun atLeast(min: DeviceVersion): Boolean = forceEnableAll || (version != null && version >= min)

    /** Ability to mute notifications from specific nodes via admin messages. */
    val canMuteNode = atLeast(V2_7_18)

    /** Ability to request neighbor information from other nodes. Gated to [UNRELEASED] until working reliably. */
    val canRequestNeighborInfo = atLeast(UNRELEASED)

    /** Ability to send verified shared contacts. Supported since firmware v2.7.12. */
    val canSendVerifiedContacts = atLeast(V2_7_12)

    /** Ability to toggle device telemetry globally via module config. Supported since firmware v2.7.12. */
    val canToggleTelemetryEnabled = atLeast(V2_7_12)

    /** Ability to toggle the 'is_unmessageable' flag in user config. Supported since firmware v2.6.9. */
    val canToggleUnmessageable = atLeast(V2_6_9)

    /** Support for sharing contact information via QR codes. Supported since firmware v2.6.8. */
    val supportsQrCodeSharing = atLeast(V2_6_8)

    /** Support for Status Message module. Supported since firmware v2.8.0. */
    val supportsStatusMessage = atLeast(V2_8_0)

    /** Support for Traffic Management module. Supported since firmware v3.0.0. */
    val supportsTrafficManagementConfig = atLeast(V3_0_0)

    /** Support for TAK (ATAK) module configuration. Supported since firmware v2.7.19. */
    val supportsTakConfig = atLeast(V2_7_19)

    /** Support for location sharing on secondary channels. Supported since firmware v2.6.10. */
    val supportsSecondaryChannelLocation = atLeast(V2_6_10)

    /** Support for ESP32 Unified OTA. Supported since firmware v2.7.18. */
    val supportsEsp32Ota = atLeast(V2_7_18)

    companion object {
        private val V2_6_8 = DeviceVersion("2.6.8")
        private val V2_6_9 = DeviceVersion("2.6.9")
        private val V2_6_10 = DeviceVersion("2.6.10")
        private val V2_7_12 = DeviceVersion("2.7.12")
        private val V2_7_18 = DeviceVersion("2.7.18")
        private val V2_7_19 = DeviceVersion("2.7.19")
        private val V2_8_0 = DeviceVersion("2.8.0")
        private val V3_0_0 = DeviceVersion("3.0.0")
        private val UNRELEASED = DeviceVersion("9.9.9")
    }
}
