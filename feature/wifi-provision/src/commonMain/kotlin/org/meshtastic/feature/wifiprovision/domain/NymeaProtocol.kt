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
package org.meshtastic.feature.wifiprovision.domain

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * kotlinx.serialization models for the nymea-networkmanager JSON-over-BLE protocol.
 *
 * All messages are compact JSON objects terminated with a newline (`\n`) and chunked into ≤20-byte BLE
 * notification/write packets.
 *
 * Reference: https://github.com/nymea/nymea-networkmanager#bluetooth-gatt-profile
 */

// ---------------------------------------------------------------------------
// Shared JSON codec — lenient so unknown fields are silently ignored
// ---------------------------------------------------------------------------

@OptIn(ExperimentalSerializationApi::class)
internal val NymeaJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    exceptionsWithDebugInfo = false
}

// ---------------------------------------------------------------------------
// Commands (app → device)
// ---------------------------------------------------------------------------

/** A command with no parameters (e.g. GetNetworks, TriggerScan). */
@Serializable internal data class NymeaSimpleCommand(@SerialName("c") val command: Int)

/** The parameter payload for the Connect / ConnectHidden commands. */
@Serializable
internal data class NymeaConnectParams(
    /** SSID (nymea key: `e`). */
    @SerialName("e") val ssid: String,
    /** Password (nymea key: `p`). */
    @SerialName("p") val password: String,
)

/** A command that carries a [NymeaConnectParams] payload. */
@Serializable
internal data class NymeaConnectCommand(
    @SerialName("c") val command: Int,
    @SerialName("p") val params: NymeaConnectParams,
)

// ---------------------------------------------------------------------------
// Responses (device → app)
// ---------------------------------------------------------------------------

/** Generic response — present in every reply from the device. */
@Serializable
internal data class NymeaResponse(
    /** Echo of the command code. */
    @SerialName("c") val command: Int = -1,
    /** 0 = success; non-zero = error code. */
    @SerialName("r") val responseCode: Int = 0,
    /** Optional payload (used by GetConnection and custom Connect responses). */
    @SerialName("p") val connectionInfo: NymeaConnectionInfo? = null,
)

/** One entry in the GetNetworks (`c=0`) response payload. */
@Serializable
internal data class NymeaNetworkEntry(
    /** SSID (nymea key: `e`). */
    @SerialName("e") val ssid: String,
    /** BSSID / MAC address (nymea key: `m`). */
    @SerialName("m") val bssid: String = "",
    /** Signal strength in dBm (nymea key: `s`). */
    @SerialName("s") val signalStrength: Int = 0,
    /** 0 = open, 1 = protected (nymea key: `p`). */
    @SerialName("p") val protection: Int = 0,
)

/** Full GetNetworks response including the network list. */
@Serializable
internal data class NymeaNetworksResponse(
    @SerialName("c") val command: Int = -1,
    @SerialName("r") val responseCode: Int = 0,
    @SerialName("p") val networks: List<NymeaNetworkEntry> = emptyList(),
)

/** Connection info payload (`p`) returned by GetConnection (`c=5`). */
@Serializable
internal data class NymeaConnectionInfo(
    /** ESSID / network name (nymea key: `e`). */
    @SerialName("e") val ssid: String = "",
    /** BSSID / MAC address (nymea key: `m`). */
    @SerialName("m") val bssid: String = "",
    /** Signal strength in dBm (nymea key: `s`). */
    @SerialName("s") val signalStrength: Int = 0,
    /** 0 = open, 1 = protected (nymea key: `p`). */
    @SerialName("p") val protection: Int = 0,
    /** IPv4 address of current connection (nymea key: `i`). */
    @SerialName("i") val ipAddress: String = "",
)
