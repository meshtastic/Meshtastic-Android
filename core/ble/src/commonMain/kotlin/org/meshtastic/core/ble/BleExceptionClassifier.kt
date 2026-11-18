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
fun Throwable.classifyBleFailure(setupHint: Throwable? = null): TransportFailure {
    val own = mostSpecificFailure()
    val hinted = setupHint?.mostSpecificFailure()?.takeIf { it.first in PAIRING_KINDS }
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
        TransportFailureKind.BluetoothUnavailable,
        TransportFailureKind.OutOfRange,
        TransportFailureKind.ConnectFailed,
        TransportFailureKind.LinkLost,
    )

private val PAIRING_KINDS = setOf(TransportFailureKind.PairingFailed, TransportFailureKind.PairingRequired)

private const val MAX_CAUSE_DEPTH = 10

// Deepest link first, so a tie keeps the platform's own message rather than a wrapper's.
private fun Throwable.mostSpecificFailure(): Pair<TransportFailureKind, Throwable>? =
    generateSequence(this) { it.cause }
        .take(MAX_CAUSE_DEPTH)
        .toList()
        .asReversed()
        .mapNotNull { link -> link.ownFailureKind()?.let { it to link } }
        .minByOrNull { (kind, _) -> FAILURE_KIND_PRIORITY.indexOf(kind) }

private fun Throwable.ownFailureKind(): TransportFailureKind? = typedFailureKind() ?: webBluetoothFailureKind()

private fun Throwable.typedFailureKind(): TransportFailureKind? = when (this) {
    is BlePairingFailedException -> TransportFailureKind.PairingFailed

    is BleDeviceNotFoundException -> TransportFailureKind.OutOfRange

    is BleConnectFailedException -> TransportFailureKind.ConnectFailed

    is UnmetRequirementException,
    is BluetoothUnsupportedException,
    -> TransportFailureKind.BluetoothUnavailable

    is GattStatusException -> gattStatusFailureKind(status)

    is NotConnectedException -> TransportFailureKind.LinkLost

    else -> null
}

@Suppress("MagicNumber")
private fun gattStatusFailureKind(status: Int): TransportFailureKind? = when (status) {
    // GATT_INSUFFICIENT_AUTHENTICATION, GATT_INSUFFICIENT_ENCRYPTION: the characteristic needs a bonded link
    5,
    15,
    -> TransportFailureKind.PairingRequired

    // GATT_AUTH_FAIL
    137 -> TransportFailureKind.PairingFailed

    // GATT_CONN_TIMEOUT: link supervision timeout, the peer went out of range
    8 -> TransportFailureKind.OutOfRange

    // GATT_CONN_TERMINATE_PEER_USER, GATT_CONN_LMP_TIMEOUT
    19,
    22,
    -> TransportFailureKind.LinkLost

    // GATT_CONN_FAIL_ESTABLISH, GATT_ERROR (the infamous 133), GATT_CONNECTION_TIMEOUT
    62,
    133,
    147,
    -> TransportFailureKind.ConnectFailed

    else -> null
}

/**
 * Web Bluetooth rejects with a DOMException that Kotlin/Wasm surfaces as `JsException`, a type common code cannot name,
 * so these match Chrome's own message text. Every other platform is classified by type above.
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
        // startNotifications() or readValue() on a node that needs an encrypted link: "GATT Error Unknown."
        "GATT Error Unknown" to TransportFailureKind.PairingRequired,
        // "GATT Error: Not paired."
        "Not paired" to TransportFailureKind.PairingRequired,
        // Any call on a dropped link: "GATT Server is disconnected. Cannot perform GATT operations. ..."
        "GATT Server is disconnected" to TransportFailureKind.LinkLost,
    )

private fun Throwable.webBluetoothFailureKind(): TransportFailureKind? {
    val text = message ?: return null
    return WEB_BLUETOOTH_MESSAGES.firstOrNull { (fragment, _) -> fragment in text }?.second
}

/**
 * GATT status codes that indicate the BLE session is irrecoverably broken.
 *
 * Used by [isSessionFatalBleException] and shared across the BLE stack so classification stays in one place.
 */
@Suppress("MagicNumber")
private val FATAL_GATT_STATUSES =
    setOf(
        // 0x08 — link-layer supervision timeout (peer out of range or asleep)
        8, // GATT_CONN_TIMEOUT
        // 0x13 — peer-initiated disconnect (firmware reboot, user power-cycle, nRF52 link drop).
        // The most common Meshtastic disconnect signal; without it fromRadio would spin-retry a dead link.
        19, // GATT_CONN_TERMINATE_PEER_USER
        // 0x16 — link manager protocol timeout (radio firmware/hardware hang)
        22, // GATT_CONN_LMP_TIMEOUT
        // 0x3E — connection establishment failed (discovered during connect handshake)
        62, // GATT_CONN_FAIL_ESTABLISH
        // 0x85 — generic connection failure; commonly fires at runtime against a stale GATT handle
        133, // GATT_ERROR
        // 0x81 — unrecoverable operation failure
        129, // GATT_FAILURE
    )

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
            status in FATAL_GATT_STATUSES || cause?.isSessionFatalBleExceptionInternal(maxDepth - 1) ?: false

        else -> cause?.isSessionFatalBleExceptionInternal(maxDepth - 1) ?: false
    }
}
