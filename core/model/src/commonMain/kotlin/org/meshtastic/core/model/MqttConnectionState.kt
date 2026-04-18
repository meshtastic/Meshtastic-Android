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

/**
 * App-level MQTT proxy connection state, decoupled from the MQTT library's internal type.
 *
 * Modeled as a sealed class so disconnect / reconnect events can carry diagnostic context — the user-facing reason for
 * an unexpected disconnect, or the most recent reconnect attempt failure — without requiring downstream consumers to
 * depend on the MQTT library's exception types.
 */
sealed class MqttConnectionState {
    /** The MQTT proxy has not been started (disabled or not yet initialized). */
    data object Inactive : MqttConnectionState()

    /** The MQTT client is actively connecting to the broker. */
    data object Connecting : MqttConnectionState()

    /** The MQTT client is connected and subscribed to topics. */
    data object Connected : MqttConnectionState()

    /**
     * The MQTT client lost connection and is attempting to reconnect.
     *
     * @property attempt 1-based attempt counter for the current reconnect loop.
     * @property lastError Localized message from the most recent reconnect failure, if any.
     */
    data class Reconnecting(val attempt: Int = 0, val lastError: String? = null) : MqttConnectionState()

    /**
     * The MQTT client is not connected to the broker.
     *
     * @property reason Localized failure message for an unexpected disconnect, or `null` for the idle / initial /
     *   intentional-close case (use [Idle]).
     */
    data class Disconnected(val reason: String? = null) : MqttConnectionState() {
        companion object {
            /** Singleton for the idle / no-reason disconnected state. */
            val Idle: Disconnected = Disconnected(reason = null)
        }
    }
}
