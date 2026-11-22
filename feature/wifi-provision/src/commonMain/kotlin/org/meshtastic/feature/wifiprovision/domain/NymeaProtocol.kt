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
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * kotlinx.serialization models for the nymea-networkmanager JSON-over-BLE protocol.
 *
 * All messages are compact JSON objects terminated with a newline (`\n`) and chunked into ≤20-byte BLE
 * notification/write packets.
 *
 * Reference: https://github.com/nymea/nymea-networkmanager#bluetooth-gatt-profile. The reply shapes below are those the
 * service actually writes, `WirelessService::createResponse` and the `command*` methods in
 * libnymea-networkmanager/bluetooth/wirelessservice.cpp (nymea, and the identical mPWRD-OS fork); the README differs
 * from it on the hidden-connect command and on error codes 5 and 6.
 */

// ---------------------------------------------------------------------------
// Shared JSON codec — unknown fields are ignored, but nothing is coerced: a missing, null or wrongly typed field fails
// the decode instead of becoming a default that reads as success.
// ---------------------------------------------------------------------------

@OptIn(ExperimentalSerializationApi::class)
internal val NymeaJson = Json {
    ignoreUnknownKeys = true
    isLenient = false
    coerceInputValues = false
    explicitNulls = true
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

/** Only the command code of a reply, which every reply echoes whatever its payload (an object, a list or none). */
@Serializable
internal data class NymeaReplyHeader(@SerialName("c") @Serializable(with = StrictIntSerializer::class) val command: Int)

/**
 * An integer that must be a bare JSON number. `Json` accepts a quoted `"0"` for an `Int` even without leniency, and a
 * status or command read that way would match a reply the device never framed as one; null and fractional values fail
 * too.
 */
internal object StrictIntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("StrictInt", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Int {
        val number = ((decoder as? JsonDecoder)?.decodeJsonElement() as? JsonPrimitive)?.takeUnless { it.isString }
        return number?.intOrNull ?: throw SerializationException("Expected a whole JSON number")
    }

    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
}

/**
 * Generic response — present in every reply from the device. Both the command and the status are required: a reply
 * without a status says nothing about success, so it does not decode.
 */
@Serializable
internal data class NymeaResponse(
    /** Echo of the command code. */
    @SerialName("c") @Serializable(with = StrictIntSerializer::class) val command: Int,
    /** 0 = success; non-zero = error code. */
    @SerialName("r") @Serializable(with = StrictIntSerializer::class) val responseCode: Int,
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
    /**
     * Signal strength in percent, 0 to 100 (nymea key: `s`). The service always writes it as an integer, so it is
     * required: a missing reading is not a reading of 0.
     */
    @SerialName("s") @Serializable(with = StrictIntSerializer::class) val signalStrength: Int,
    /**
     * 0 = open, 1 = protected (nymea key: `p`). The service always writes it as the integer 0 or 1, so it is required:
     * a missing flag must not read as an open network.
     */
    @SerialName("p") @Serializable(with = StrictIntSerializer::class) val protection: Int,
)

/**
 * Full GetNetworks response. The status is required, and [networks] is null when the reply carries no list: an empty
 * list is `[]`, and a reply with no list at all is not "no networks".
 */
@Serializable
internal data class NymeaNetworksResponse(
    @SerialName("c") @Serializable(with = StrictIntSerializer::class) val command: Int,
    @SerialName("r") @Serializable(with = StrictIntSerializer::class) val responseCode: Int,
    @SerialName("p") val networks: List<NymeaNetworkEntry>? = null,
)

/** Connection info payload (`p`) returned by GetConnection (`c=5`). */
@Serializable
internal data class NymeaConnectionInfo(
    /** ESSID / network name (nymea key: `e`). */
    @SerialName("e") val ssid: String = "",
    /** BSSID / MAC address (nymea key: `m`). */
    @SerialName("m") val bssid: String = "",
    /** Signal strength in percent (nymea key: `s`). */
    @SerialName("s") val signalStrength: Int = 0,
    /** 0 = open, 1 = protected (nymea key: `p`). */
    @SerialName("p") val protection: Int = 0,
    /** IPv4 address of current connection (nymea key: `i`). */
    @SerialName("i") val ipAddress: String = "",
)
