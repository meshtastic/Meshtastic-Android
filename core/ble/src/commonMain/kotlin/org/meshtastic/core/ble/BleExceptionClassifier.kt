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
@file:Suppress("MatchingDeclarationName") // File groups the classifier function and its result type.

package org.meshtastic.core.ble

import com.juul.kable.GattRequestRejectedException
import com.juul.kable.GattStatusException
import com.juul.kable.NotConnectedException
import com.juul.kable.UnmetRequirementException

/**
 * Classification of a BLE-layer exception for the transport layer to act on.
 *
 * @property isPermanent `true` if the condition cannot resolve without explicit user re-selection of the device.
 *   Currently always `false` — all known BLE exceptions can resolve without user intervention (BT toggling, permission
 *   grants, transient GATT errors). Reserved for future use.
 * @property gattStatus the platform GATT status code when available (Android-specific).
 * @property message a human-readable description of the failure.
 */
data class BleExceptionInfo(val isPermanent: Boolean, val gattStatus: Int? = null, val message: String)

/**
 * Inspects this [Throwable] and returns a [BleExceptionInfo] if it is a known Kable exception, or `null` if it is
 * unrelated to the BLE layer.
 *
 * This keeps Kable type knowledge inside `core:ble` so that `core:network` (and other consumers) can classify BLE
 * exceptions without depending on Kable directly.
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
        // Bluetooth disabled or runtime permission missing. Both can resolve without re-selecting the
        // device (user re-enables BT, or grants permission). Surface as transient so the transport keeps
        // retrying; UI can show a hint based on the message.
        BleExceptionInfo(isPermanent = false, message = message ?: "Bluetooth LE unavailable")
    else -> null
}
