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
package org.meshtastic.feature.wifiprovision.model

/** A WiFi access point returned by the nymea GetNetworks command. */
data class WifiNetwork(
    /** ESSID / network name. */
    val ssid: String,
    /** MAC address of the access point. */
    val bssid: String,
    /** Signal strength [0-100] %. */
    val signalStrength: Int,
    /** Whether the network requires a password. */
    val isProtected: Boolean,
)

/** Result of a WiFi provisioning attempt. */
sealed interface ProvisionResult {
    data class Success(
        /** IPv4 address reported by nymea for the active Wi-Fi connection. */
        val ipAddress: String? = null,
    ) : ProvisionResult

    data class Failure(val errorCode: Int, val message: String) : ProvisionResult
}
