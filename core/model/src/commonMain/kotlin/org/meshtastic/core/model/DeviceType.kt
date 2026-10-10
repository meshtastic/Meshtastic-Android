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

/**
 * The transports a radio can be reached over, one pane each in Connections. [NODE] is this device running as the mesh
 * node itself. Demo Mode reaches no radio and has no [DeviceType].
 */
enum class DeviceType {
    BLE,
    TCP,
    USB,
    NODE,
    ;

    companion object {
        fun fromAddress(address: String): DeviceType? =
            when (InterfaceId.forIdChar(address.firstOrNull() ?: return null)) {
                InterfaceId.BLUETOOTH -> BLE

                InterfaceId.SERIAL -> USB

                InterfaceId.TCP -> TCP

                InterfaceId.NODE -> NODE

                InterfaceId.NOP,
                InterfaceId.MOCK,
                InterfaceId.REPLAY,
                null,
                -> null
            }
    }
}
