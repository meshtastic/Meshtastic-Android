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
package org.meshtastic.core.repository

import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.core.model.MqttConnectionState
import org.meshtastic.core.model.MqttProbeStatus
import org.meshtastic.proto.MqttClientProxyMessage

/** Interface for managing MQTT proxy communication. */
interface MqttManager {
    /** Observable MQTT proxy connection state for UI consumption. */
    val mqttConnectionState: StateFlow<MqttConnectionState>

    /** Starts the MQTT proxy with the given settings. */
    fun startProxy(enabled: Boolean, proxyToClientEnabled: Boolean)

    /** Stops the MQTT manager. */
    fun stop()

    /** Handles an MQTT proxy message from the radio. */
    fun handleMqttProxyMessage(message: MqttClientProxyMessage)

    /**
     * Probe an MQTT broker to verify connectivity and credentials without joining the proxy lifecycle. Intended for UI
     * "Test Connection" affordances.
     *
     * @param address Raw broker address as the user would type it (host, host:port, or full URL).
     * @param tlsEnabled `true` to upgrade bare addresses to `wss://` (ignored when [address] already has a scheme).
     * @param username Optional MQTT username.
     * @param password Optional MQTT password.
     */
    suspend fun probe(address: String, tlsEnabled: Boolean, username: String?, password: String?): MqttProbeStatus
}
