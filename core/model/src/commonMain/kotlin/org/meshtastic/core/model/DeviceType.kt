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
package org.meshtastic.core.model

/** The physical transports a radio can be reached over. Demo Mode reaches no radio and has no [DeviceType]. */
enum class DeviceType {
    BLE,
    TCP,
    USB,
    ;

    companion object {
        fun fromAddress(address: String): DeviceType? {
            val interfaceId = address.firstOrNull()?.let(InterfaceId::forIdChar)
            return when {
                interfaceId == InterfaceId.BLUETOOTH -> BLE

                interfaceId == InterfaceId.SERIAL -> USB

                // A WebSocket is another route to the same device a TCP address reaches.
                interfaceId?.isNetwork == true -> TCP

                else -> null
            }
        }
    }
}
