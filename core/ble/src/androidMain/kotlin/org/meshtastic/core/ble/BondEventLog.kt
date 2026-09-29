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

import android.bluetooth.BluetoothDevice
import android.os.Build
import co.touchlab.kermit.Severity
import org.meshtastic.core.model.util.anonymize

/** HCI Encryption Change status for success, as carried by `EXTRA_ENCRYPTION_STATUS`. */
private const val HCI_SUCCESS = 0

/** A Bluetooth bond broadcast for one device, reduced to the fields worth recording. */
internal sealed interface BondEvent {
    val address: String

    /** `ACTION_KEY_MISSING`. [lossReason] is `EXTRA_BOND_LOSS_REASON`, which only newer releases send. */
    data class KeyMissing(override val address: String, val lossReason: Int?) : BondEvent

    /** `ACTION_ENCRYPTION_CHANGE`. [status] is the controller's HCI status. */
    data class EncryptionChange(override val address: String, val encrypted: Boolean, val status: Int) : BondEvent

    /** `ACTION_BOND_STATE_CHANGED`, as `BluetoothDevice.BOND_*` values. */
    data class BondStateChanged(override val address: String, val previous: Int, val current: Int) : BondEvent
}

internal data class BondEventLogLine(val severity: Severity, val message: String)

/** The bond broadcasts worth registering for on [sdkInt]; KEY_MISSING and ENCRYPTION_CHANGE exist from API 36. */
internal fun bondEventActions(sdkInt: Int): List<String> = buildList {
    add(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
    if (sdkInt >= Build.VERSION_CODES.BAKLAVA) {
        add(BluetoothDevice.ACTION_KEY_MISSING)
        add(BluetoothDevice.ACTION_ENCRYPTION_CHANGE)
    }
}

/**
 * Warn when the radio is left without a bond or an encrypted link, Info otherwise. A lost bond is the user's radio, not
 * a defect, so it never logs at Error. The device is named only by [anonymize].
 */
internal fun BondEvent.toLogLine(sdk: String): BondEventLogLine {
    val device = address.anonymize
    return when (this) {
        is BondEvent.KeyMissing -> {
            val reason = lossReason?.let { " lossReason=$it" }.orEmpty()
            BondEventLogLine(Severity.Warn, "BLE bond event key-missing sdk=$sdk$reason device=$device")
        }

        is BondEvent.EncryptionChange -> {
            val severity = if (encrypted && status == HCI_SUCCESS) Severity.Info else Severity.Warn
            BondEventLogLine(
                severity,
                "BLE bond event encryption-change sdk=$sdk encrypted=$encrypted status=$status device=$device",
            )
        }

        is BondEvent.BondStateChanged -> {
            val severity = if (current == BluetoothDevice.BOND_NONE) Severity.Warn else Severity.Info
            BondEventLogLine(
                severity,
                "BLE bond event bond-state ${previous.bondStateName()}->${current.bondStateName()} " +
                    "sdk=$sdk device=$device",
            )
        }
    }
}

private fun Int.bondStateName(): String = when (this) {
    BluetoothDevice.BOND_NONE -> "NONE"
    BluetoothDevice.BOND_BONDING -> "BONDING"
    BluetoothDevice.BOND_BONDED -> "BONDED"
    else -> toString()
}
