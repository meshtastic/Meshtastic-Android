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

package org.meshtastic.core.service

enum class ConnectionState {
    /** We are disconnected from the device, and we should be trying to reconnect. */
    DISCONNECTED,

    /** We are connected to the device and communicating normally. */
    CONNECTED,

    /** The device is in a light sleep state, and we are waiting for it to wake up and reconnect to us. */
    DEVICE_SLEEP,

    ;

    fun isConnected() = this != DISCONNECTED
}
