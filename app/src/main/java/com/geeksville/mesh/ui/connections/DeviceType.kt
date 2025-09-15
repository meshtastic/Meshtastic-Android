/*
 * Copyright (c) 2025 Meshtastic LLC
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

package com.geeksville.mesh.ui.connections

import com.geeksville.mesh.model.NO_DEVICE_SELECTED

enum class DeviceType {
    BLE,
    TCP,
    USB,
    ;

    companion object {
        fun fromAddress(address: String): DeviceType? = when (address.firstOrNull()) {
            'x' -> BLE
            's' -> USB
            't' -> TCP
            'm' -> USB // Treat mock as USB for UI purposes
            'n' ->
                when (address) {
                    NO_DEVICE_SELECTED -> null
                    else -> null
                }

            else -> null
        }
    }
}
