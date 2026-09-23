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

import org.meshtastic.core.common.log.ExpectedCondition

/**
 * The device has no Bluetooth LE hardware, so no scan or connection can ever succeed (e.g. an Android XR headset).
 *
 * An [ExpectedCondition]: the hardware said no, which is not a defect anyone can fix.
 */
class BluetoothUnsupportedException(cause: Throwable) :
    IllegalStateException("Bluetooth LE is not supported on this device", cause),
    ExpectedCondition {
    override val expectedConditionLabel: String = "ble-unsupported"
}

/**
 * Wraps Kable's "no Bluetooth adapter" failure as a [BluetoothUnsupportedException], or returns `null` for anything
 * else. Kable reports it only as an [IllegalStateException] message, so the match is on its two exact texts.
 */
internal fun Throwable.asBluetoothUnsupportedExceptionOrNull(): BluetoothUnsupportedException? = when {
    this is BluetoothUnsupportedException -> this

    this is IllegalStateException && message in KABLE_BLUETOOTH_UNSUPPORTED_MESSAGES ->
        BluetoothUnsupportedException(this)

    else -> null
}

private val KABLE_BLUETOOTH_UNSUPPORTED_MESSAGES =
    setOf("Bluetooth not supported", "BluetoothManager is not a supported system service")
