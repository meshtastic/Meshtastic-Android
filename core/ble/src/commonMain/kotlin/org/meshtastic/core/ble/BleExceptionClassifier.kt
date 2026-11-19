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

import com.juul.kable.GattRequestRejectedException
import com.juul.kable.GattStatusException
import com.juul.kable.NotConnectedException
import com.juul.kable.UnmetRequirementException
import org.meshtastic.core.model.TransportFailure
import org.meshtastic.core.model.TransportFailureKind

/**
 * Inspects this [Throwable] and returns a [BleExceptionInfo] if it is a known platform BLE exception, or `null` if it
 * is unrelated to the BLE layer.
 *
 * This keeps platform BLE type knowledge inside `core:ble` so that `core:network` (and other consumers) can classify
 * BLE exceptions without depending on Kable directly.
 */
fun Throwable.classifyBleException(): BleExceptionInfo? = when (this) {
    is GattStatusException ->
        BleExceptionInfo(
            isPermanent = false,
            gattStatus = status,
            message = "GATT error (status $status): $message",
        )

    is NotConnectedException -> BleExceptionInfo(isPermanent = false, message = "Not connected")

    is GattRequestRejectedException ->
        BleExceptionInfo(isPermanent = false, message = "GATT request rejected (busy)")

    is UnmetRequirementException ->
        // Kable raises this only for the two preconditions its UnmetRequirementReason enumerates: Bluetooth
        // disabled
        // and location services disabled. A missing runtime permission is NOT one of them — Android throws
        // SecurityException for that, which falls through to the caller's generic handling. Both reasons here
        // resolve
        // without re-selecting the device, so surface them as transient and let the transport keep retrying; the
        // Connections screen carries a live card for each.
        BleExceptionInfo(isPermanent = false, message = message ?: "Bluetooth LE unavailable")

    else -> null
}

/**
 * Classifies why a BLE connection attempt failed, for the connection card. Walks the cause chain and keeps the most
 * specific kind found, so a dropped link whose cause was a refused subscription reads as pairing rather than a drop.
 *
 * @param setupHint a failure the platform reported out of band during this attempt (a suppressed notification
 *   subscription), consulted only when [this] alone says no more than that the link dropped
 */
fun Throwable.classifyBleFailure(setupHint: Throwable? = null): TransportFailure =
    classifyBleFailureWith(setupHint) { it.platformMessageFailureKind() }

/**
 * Kind for a failure the platform reports only as message text. Web Bluetooth rejects with a DOMException that
 * Kotlin/Wasm surfaces as a `JsException` common code cannot name, so only the wasmJs actual reads the message; every
 * other platform classifies by type alone and returns null.
 */
internal expect fun Throwable.platformMessageFailureKind(): TransportFailureKind?

/** [classifyBleFailure] with the message hook passed in, so the Web Bluetooth table can be tested off the browser. */
internal fun Throwable.classifyBleFailureWith(
    setupHint: Throwable?,
    messageKind: (Throwable) -> TransportFailureKind?,
): TransportFailure {
    val own = mostSpecificFailure(messageKind)
    val hinted = setupHint?.mostSpecificFailure(messageKind)?.takeIf { it.first in PAIRING_KINDS }
    val (kind, source) =
        when {
            hinted != null && (own == null || own.first == TransportFailureKind.LinkLost) -> hinted
            own != null -> own
            else -> TransportFailureKind.Unknown to this
        }
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

private val PAIRING_KINDS =
    setOf(
        TransportFailureKind.PairingFailed,
        TransportFailureKind.PairingRequired,
        TransportFailureKind.ConnectionRejected,
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
    is BlePairingFailedException -> TransportFailureKind.PairingFailed

    is BleDeviceNotFoundException -> TransportFailureKind.OutOfRange

    is BleConnectFailedException -> TransportFailureKind.ConnectFailed

    is UnmetRequirementException,
    is BluetoothUnsupportedException,
    -> TransportFailureKind.BluetoothUnavailable

    is GattStatusException -> GATT_STATUSES[status]?.kind

    is NotConnectedException -> TransportFailureKind.LinkLost

    else -> null
}

/**
 * A Bluetooth GATT status code the stack acts on: what it tells the user, and whether it means the BLE session is
 * irrecoverably broken (see [isSessionFatalBleException]).
 */
private class GattStatus(val code: Int, val kind: TransportFailureKind?, val sessionFatal: Boolean)

/** Every GATT status classified anywhere in the BLE stack, so the card and the session teardown never disagree. */
@Suppress("MagicNumber")
private val GATT_STATUSES: Map<Int, GattStatus> =
    listOf(
        // 0x05 GATT_INSUFFICIENT_AUTHENTICATION: the characteristic needs a bonded link
        GattStatus(5, TransportFailureKind.PairingRequired, sessionFatal = false),
        // 0x08 GATT_CONN_TIMEOUT: link supervision timeout, the peer went out of range or asleep
        GattStatus(8, TransportFailureKind.OutOfRange, sessionFatal = true),
        // 0x0F GATT_INSUFFICIENT_ENCRYPTION: the characteristic needs an encrypted link
        GattStatus(15, TransportFailureKind.PairingRequired, sessionFatal = false),
        // 0x13 GATT_CONN_TERMINATE_PEER_USER: the radio ended the link (reboot, power-cycle, nRF52 link drop).
        // The most common Meshtastic disconnect; without it fromRadio would spin-retry a dead link.
        GattStatus(19, TransportFailureKind.LinkLost, sessionFatal = true),
        // 0x16 GATT_CONN_TERMINATE_LOCAL_HOST: this side's stack ended the link
        GattStatus(22, TransportFailureKind.LinkLost, sessionFatal = true),
        // 0x22 GATT_CONN_LMP_TIMEOUT: link manager protocol timeout, a hung radio firmware or controller
        GattStatus(34, TransportFailureKind.LinkLost, sessionFatal = true),
        // 0x3E GATT_CONN_FAIL_ESTABLISH: the connection was never established
        GattStatus(62, TransportFailureKind.ConnectFailed, sessionFatal = true),
        // 0x81 GATT_INTERNAL_ERROR: unrecoverable stack failure
        GattStatus(129, null, sessionFatal = true),
        // 0x85 GATT_ERROR, the infamous 133: generic connection failure, also raised against a stale GATT handle
        GattStatus(133, TransportFailureKind.ConnectFailed, sessionFatal = true),
        // 0x89 GATT_AUTH_FAIL: pairing was attempted and failed
        GattStatus(137, TransportFailureKind.PairingFailed, sessionFatal = false),
        // 0x93 GATT_CONNECTION_TIMEOUT: the connect request timed out
        GattStatus(147, TransportFailureKind.ConnectFailed, sessionFatal = false),
    )
        .associateBy { it.code }

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

/**
 * Returns `true` if this throwable indicates the BLE session is irrecoverably broken and should be torn down
 * (triggering reconnection), as opposed to a transient condition that can be retried.
 *
 * Also checks the cause chain — if a session-fatal exception is wrapped inside another exception (e.g., by coroutine
 * machinery or retry logic), it is still detected. Depth-limited to prevent stack overflow on malformed cause chains.
 */
fun Throwable.isSessionFatalBleException(): Boolean = isSessionFatalBleExceptionInternal(maxDepth = 10)

private fun Throwable.isSessionFatalBleExceptionInternal(maxDepth: Int): Boolean {
    if (maxDepth <= 0) return false
    return when (this) {
        is NotConnectedException -> true

        is GattStatusException ->
            GATT_STATUSES[status]?.sessionFatal == true ||
                cause?.isSessionFatalBleExceptionInternal(maxDepth - 1) ?: false

        else -> cause?.isSessionFatalBleExceptionInternal(maxDepth - 1) ?: false
    }
}
