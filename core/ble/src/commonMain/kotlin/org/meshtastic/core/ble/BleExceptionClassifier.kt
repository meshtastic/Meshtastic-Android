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
package org.meshtastic.core.ble

import com.juul.kable.GattStatusException
import com.juul.kable.NotConnectedException
import com.juul.kable.UnmetRequirementException
import org.meshtastic.core.model.TransportFailure
import org.meshtastic.core.model.TransportFailureKind

/**
 * Classifies why a BLE connection attempt failed, for the connection card. Walks the cause chain and keeps the most
 * specific kind found, so a dropped link whose cause was a refused subscription reads as pairing rather than a drop.
 */
fun Throwable.classifyBleFailure(): TransportFailure = classifyBleFailureWith { it.platformMessageFailureKind() }

/**
 * Kind for a failure the platform reports only as message text. Web Bluetooth rejects with a DOMException that
 * Kotlin/Wasm surfaces as a `JsException` common code cannot name, so only the wasmJs actual reads the message; every
 * other platform classifies by type alone and returns null.
 */
internal expect fun Throwable.platformMessageFailureKind(): TransportFailureKind?

/** [classifyBleFailure] with the message hook passed in, so the Web Bluetooth table can be tested off the browser. */
internal fun Throwable.classifyBleFailureWith(messageKind: (Throwable) -> TransportFailureKind?): TransportFailure {
    val (kind, source) = mostSpecificFailure(messageKind) ?: (TransportFailureKind.Unknown to this)
    return TransportFailure(kind = kind, detail = source.message ?: source::class.simpleName)
}

/** Most specific first: the first kind in this list that any link of a cause chain maps to wins. */
private val FAILURE_KIND_PRIORITY =
    listOf(
        TransportFailureKind.PairingFailed,
        TransportFailureKind.PairingRequired,
        TransportFailureKind.ConnectionRejected,
        TransportFailureKind.BluetoothUnavailable,
        TransportFailureKind.OutOfRange,
        TransportFailureKind.ConnectFailed,
        TransportFailureKind.LinkLost,
    )

private const val MAX_CAUSE_DEPTH = 10

// Deepest link first, so a tie keeps the platform's own message rather than a wrapper's.
private fun Throwable.mostSpecificFailure(
    messageKind: (Throwable) -> TransportFailureKind?,
): Pair<TransportFailureKind, Throwable>? = generateSequence(this) { it.cause }
    .take(MAX_CAUSE_DEPTH)
    .toList()
    .asReversed()
    .mapNotNull { link -> (link.typedFailureKind() ?: messageKind(link))?.let { it to link } }
    .minByOrNull { (kind, _) -> FAILURE_KIND_PRIORITY.indexOf(kind) }

private fun Throwable.typedFailureKind(): TransportFailureKind? = when (this) {
    is UnmetRequirementException,
    is BluetoothUnsupportedException,
    -> TransportFailureKind.BluetoothUnavailable

    is GattStatusException -> GATT_STATUSES[status]

    is NotConnectedException -> TransportFailureKind.LinkLost

    else -> null
}

/** What each Bluetooth GATT status code the stack recognizes tells the user. */
@Suppress("MagicNumber")
private val GATT_STATUSES: Map<Int, TransportFailureKind> =
    mapOf(
        // 0x05 GATT_INSUFFICIENT_AUTHENTICATION: the characteristic needs a bonded link
        5 to TransportFailureKind.PairingRequired,
        // 0x08 GATT_CONN_TIMEOUT: link supervision timeout, the peer went out of range or asleep
        8 to TransportFailureKind.OutOfRange,
        // 0x0F GATT_INSUFFICIENT_ENCRYPTION: the characteristic needs an encrypted link
        15 to TransportFailureKind.PairingRequired,
        // 0x13 GATT_CONN_TERMINATE_PEER_USER: the radio ended the link (reboot, power-cycle, nRF52 link drop)
        19 to TransportFailureKind.LinkLost,
        // 0x16 GATT_CONN_TERMINATE_LOCAL_HOST: this side's stack ended the link
        22 to TransportFailureKind.LinkLost,
        // 0x22 GATT_CONN_LMP_TIMEOUT: link manager protocol timeout, a hung radio firmware or controller
        34 to TransportFailureKind.LinkLost,
        // 0x3E GATT_CONN_FAIL_ESTABLISH: the connection was never established
        62 to TransportFailureKind.ConnectFailed,
        // 0x85 GATT_ERROR, the infamous 133: generic connection failure, also raised against a stale GATT handle
        133 to TransportFailureKind.ConnectFailed,
        // 0x89 GATT_AUTH_FAIL: pairing was attempted and failed
        137 to TransportFailureKind.PairingFailed,
        // 0x93 GATT_CONNECTION_TIMEOUT: the connect request timed out
        147 to TransportFailureKind.ConnectFailed,
    )

/**
 * Chrome's Web Bluetooth rejection messages, matched by the wasmJs [platformMessageFailureKind] only. Chrome words them
 * in English whatever the browser language.
 */
private val WEB_BLUETOOTH_MESSAGES =
    listOf(
        // gatt.connect(): "Bluetooth Device is no longer in range."
        "no longer in range" to TransportFailureKind.OutOfRange,
        // gatt.connect(): "Connection Error: Connection attempt failed."
        "Connection attempt failed" to TransportFailureKind.ConnectFailed,
        // gatt.connect() on a pairing the user or the node refused: "Authentication failed." and its siblings
        "Authentication failed" to TransportFailureKind.PairingFailed,
        "Authentication rejected" to TransportFailureKind.PairingFailed,
        "Authentication canceled" to TransportFailureKind.PairingFailed,
        "Authentication timeout" to TransportFailureKind.PairingFailed,
        // "GATT Error: Not paired."
        "Not paired" to TransportFailureKind.PairingRequired,
        // Chrome's catch-all for a GATT status it does not map, "GATT Error Unknown.". A node that needs an encrypted
        // link answers startNotifications() with it, but so do other refusals, so it only suggests pairing.
        "GATT Error Unknown" to TransportFailureKind.ConnectionRejected,
        // Any call on a dropped link: "GATT Server is disconnected. Cannot perform GATT operations. ..."
        "GATT Server is disconnected" to TransportFailureKind.LinkLost,
    )

/** The kind Chrome's Web Bluetooth [message] names, or null when it is not one of [WEB_BLUETOOTH_MESSAGES]. */
internal fun webBluetoothMessageFailureKind(message: String): TransportFailureKind? =
    WEB_BLUETOOTH_MESSAGES.firstOrNull { (fragment, _) -> fragment in message }?.second
