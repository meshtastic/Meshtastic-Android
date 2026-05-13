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
package org.meshtastic.feature.wifiprovision

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * GATT UUIDs for the nymea-networkmanager Bluetooth provisioning profile.
 *
 * Reference: https://github.com/nymea/nymea-networkmanager#bluetooth-gatt-profile
 */
internal object NymeaBleConstants {

    // region Wireless Service
    /** Primary service for WiFi management. */
    val WIRELESS_SERVICE_UUID: Uuid = Uuid.parse("e081fec0-f757-4449-b9c9-bfa83133f7fc")

    /**
     * Write JSON commands (chunked into ≤20-byte packets, newline-terminated) to this characteristic. Each command
     * generates a response on [COMMANDER_RESPONSE_UUID].
     */
    val WIRELESS_COMMANDER_UUID: Uuid = Uuid.parse("e081fec1-f757-4449-b9c9-bfa83133f7fc")

    /**
     * Subscribe (notify) to receive JSON responses. Uses the same 20-byte chunked, newline-terminated framing as the
     * commander.
     */
    val COMMANDER_RESPONSE_UUID: Uuid = Uuid.parse("e081fec2-f757-4449-b9c9-bfa83133f7fc")

    /** Read/notify: current WiFi adapter connection state (1 byte). */
    val WIRELESS_CONNECTION_STATUS_UUID: Uuid = Uuid.parse("e081fec3-f757-4449-b9c9-bfa83133f7fc")
    // endregion

    // region Network Service

    /** Service for enabling/disabling networking and wireless. */
    val NETWORK_SERVICE_UUID: Uuid = Uuid.parse("ef6d6610-b8af-49e0-9eca-ab343513641c")

    /** Read/notify: overall NetworkManager state (1 byte). */
    val NETWORK_STATUS_UUID: Uuid = Uuid.parse("ef6d6611-b8af-49e0-9eca-ab343513641c")
    // endregion

    // region Protocol framing

    /** Maximum ATT payload per packet when MTU negotiation is unavailable. */
    const val MAX_PACKET_SIZE = 20

    /** JSON stream terminator — marks the end of a reassembled message. */
    const val STREAM_TERMINATOR = '\n'

    /** Scan + connect timeout. */
    val SCAN_TIMEOUT = 10.seconds

    /** Maximum time to wait for a command response. */
    val RESPONSE_TIMEOUT = 15.seconds

    /** Timeout for optional GetConnection metadata lookup after a successful connect command. */
    val CONNECTION_INFO_TIMEOUT = 2.seconds

    /** Settle time after subscribing to notifications before sending commands. */
    val SUBSCRIPTION_SETTLE = 300.milliseconds
    // endregion

    // region Wireless Commander command codes

    /** Request the list of visible WiFi networks. */
    const val CMD_GET_NETWORKS = 0

    /** Connect to a network using SSID + password. */
    const val CMD_CONNECT = 1

    /** Connect to a hidden network using SSID + password. */
    const val CMD_CONNECT_HIDDEN = 2

    /** Trigger a fresh WiFi scan. */
    const val CMD_SCAN = 4

    /** Request current connection details (includes IP address if connected). */
    const val CMD_GET_CONNECTION = 5
    // endregion

    // region Response error codes
    const val RESPONSE_SUCCESS = 0
    const val RESPONSE_INVALID_COMMAND = 1
    const val RESPONSE_INVALID_PARAMETER = 2
    const val RESPONSE_NETWORK_MANAGER_UNAVAILABLE = 3
    const val RESPONSE_WIRELESS_UNAVAILABLE = 4
    const val RESPONSE_NETWORKING_DISABLED = 5
    const val RESPONSE_WIRELESS_DISABLED = 6
    const val RESPONSE_UNKNOWN = 7
    // endregion
}
