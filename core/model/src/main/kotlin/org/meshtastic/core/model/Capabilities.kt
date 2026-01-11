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

/**
 * Defines the capabilities and feature support based on the device firmware version.
 *
 * This class provides a centralized way to check if specific features are supported by the connected node's firmware.
 * Add new features here to ensure consistency across the app.
 */
data class Capabilities(val firmwareVersion: String?, internal val forceEnableAll: Boolean = BuildConfig.DEBUG) {
    private val version = firmwareVersion?.let { DeviceVersion(it) }

    private fun isSupported(minVersion: String): Boolean =
        forceEnableAll || (version != null && version >= DeviceVersion(minVersion))

    /**
     * Ability to mute notifications from specific nodes via admin messages.
     *
     * Note: This is currently not available in firmware but defined here for future support.
     */
    val canMuteNode: Boolean
        get() = isSupported("2.8.0")

    /** Ability to request neighbor information from other nodes. Supported since firmware v2.7.15. */
    val canRequestNeighborInfo: Boolean
        get() = isSupported("2.7.15")

    /** Ability to send verified shared contacts. Supported since firmware v2.7.12. */
    val canSendVerifiedContacts: Boolean
        get() = isSupported("2.7.12")

    /** Ability to toggle device telemetry globally via module config. Supported since firmware v2.7.12. */
    val canToggleTelemetryEnabled: Boolean
        get() = isSupported("2.7.12")

    /** Ability to toggle the 'is_unmessageable' flag in user config. Supported since firmware v2.6.9. */
    val canToggleUnmessageable: Boolean
        get() = isSupported("2.6.9")

    /** Support for sharing contact information via QR codes. Supported since firmware v2.6.8. */
    val supportsQrCodeSharing: Boolean
        get() = isSupported("2.6.8")
}
