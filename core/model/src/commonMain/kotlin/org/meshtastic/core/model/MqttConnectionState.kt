/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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

/** App-level MQTT proxy connection state, decoupled from the MQTT library's internal type. */
enum class MqttConnectionState {
    /** The MQTT proxy has not been started (disabled or not yet initialized). */
    INACTIVE,

    /** The MQTT client is not connected to the broker. */
    DISCONNECTED,

    /** The MQTT client is actively connecting to the broker. */
    CONNECTING,

    /** The MQTT client is connected and subscribed to topics. */
    CONNECTED,

    /** The MQTT client lost connection and is attempting to reconnect. */
    RECONNECTING,
}
