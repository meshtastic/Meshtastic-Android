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
import org.meshtastic.sdk.DeviceCapabilities as SdkCapabilities

/**
 * Defines the capabilities and feature support based on the device firmware version.
 *
 * This class provides a centralized way to check if specific features are supported by the connected node's firmware.
 * Add new features here to ensure consistency across the app.
 */
data class Capabilities(val firmwareVersion: String?, internal val forceEnableAll: Boolean = isDebug) {
    private val sdk = SdkCapabilities(firmwareVersion)

    private fun check(sdkValue: Boolean): Boolean = forceEnableAll || sdkValue

    /** Ability to mute notifications from specific nodes via admin messages. */
    val canMuteNode get() = check(sdk.canMuteNode)

    /**
     * Ability to request neighbor information from other nodes.
     * Gated to unreleased firmware until working reliably.
     */
    val canRequestNeighborInfo get() = false

    /** Ability to send verified shared contacts. Supported since firmware v2.7.12. */
    val canSendVerifiedContacts get() = check(sdk.canSendVerifiedContacts)

    /** Ability to toggle device telemetry globally via module config. Supported since firmware v2.7.12. */
    val canToggleTelemetryEnabled get() = check(sdk.canToggleTelemetryEnabled)

    /** Ability to toggle the 'is_unmessageable' flag in user config. Supported since firmware v2.6.9. */
    val canToggleUnmessageable get() = check(sdk.canToggleUnmessageable)

    /** Support for sharing contact information via QR codes. Supported since firmware v2.6.8. */
    val supportsQrCodeSharing get() = check(sdk.supportsQrCodeSharing)

    /** Support for Status Message module. Supported since firmware v2.8.0. */
    val supportsStatusMessage get() = check(sdk.supportsStatusMessage)

    /** Support for Traffic Management module. Supported since firmware v3.0.0. */
    val supportsTrafficManagementConfig get() = check(sdk.supportsTrafficManagementConfig)

    /** Support for TAK (ATAK) module configuration. Supported since firmware v2.7.19. */
    val supportsTakConfig get() = check(sdk.supportsTakConfig)

    /** Support for location sharing on secondary channels. Supported since firmware v2.6.10. */
    val supportsSecondaryChannelLocation get() = check(sdk.supportsSecondaryChannelLocation)

    /** Support for ESP32 Unified OTA. Supported since firmware v2.7.18. */
    val supportsEsp32Ota get() = check(sdk.supportsEsp32Ota)
}
