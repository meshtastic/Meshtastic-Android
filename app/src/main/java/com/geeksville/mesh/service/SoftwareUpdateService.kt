/*
 * Copyright (c) 2024 Meshtastic LLC
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

package com.geeksville.mesh.service

import android.bluetooth.BluetoothGattCharacteristic

/**
 * Some misformatted ESP32s have problems
 */
class DeviceRejectedException : BLEException("Device rejected filesize")

/**
 * Move this somewhere as a generic network byte order function
 */
fun toNetworkByteArray(value: Int, formatType: Int): ByteArray {

    val len = when (formatType) {
        BluetoothGattCharacteristic.FORMAT_UINT8 -> 1
        BluetoothGattCharacteristic.FORMAT_UINT32 -> 4
        else -> TODO()
    }

    val mValue = ByteArray(len)

    when (formatType) {
        /* BluetoothGattCharacteristic.FORMAT_SINT8 -> {
            value = intToSignedBits(value, 8)
            mValue.get(offset) = (value and 0xFF).toByte()
        }
        BluetoothGattCharacteristic.FORMAT_UINT8 -> mValue.get(offset) =
            (value and 0xFF).toByte()
        BluetoothGattCharacteristic.FORMAT_SINT16 -> {
            value = intToSignedBits(value, 16)
            mValue.get(offset++) = (value and 0xFF).toByte()
            mValue.get(offset) = (value shr 8 and 0xFF).toByte()
        }
        BluetoothGattCharacteristic.FORMAT_UINT16 -> {
            mValue.get(offset++) = (value and 0xFF).toByte()
            mValue.get(offset) = (value shr 8 and 0xFF).toByte()
        }
        BluetoothGattCharacteristic.FORMAT_SINT32 -> {
            value = intToSignedBits(value, 32)
            mValue.get(offset++) = (value and 0xFF).toByte()
            mValue.get(offset++) = (value shr 8 and 0xFF).toByte()
            mValue.get(offset++) = (value shr 16 and 0xFF).toByte()
            mValue.get(offset) = (value shr 24 and 0xFF).toByte()
        } */
        BluetoothGattCharacteristic.FORMAT_UINT8 ->
            mValue[0] = (value and 0xFF).toByte()

        BluetoothGattCharacteristic.FORMAT_UINT32 -> {
            mValue[0] = (value and 0xFF).toByte()
            mValue[1] = (value shr 8 and 0xFF).toByte()
            mValue[2] = (value shr 16 and 0xFF).toByte()
            mValue[3] = (value shr 24 and 0xFF).toByte()
        }
        else -> TODO()
    }
    return mValue
}

data class UpdateFilenames(val appLoad: String?, val littlefs: String?)