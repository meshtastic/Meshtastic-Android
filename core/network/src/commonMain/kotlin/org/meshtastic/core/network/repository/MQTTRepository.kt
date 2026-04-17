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
package org.meshtastic.core.network.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.mqtt.ConnectionState
import org.meshtastic.proto.MqttClientProxyMessage

/** Interface defining the MQTT interactions used for proxying messages to and from the mesh. */
interface MQTTRepository {
    /** Disconnects the MQTT client and cleans up resources. */
    fun disconnect()

    /**
     * A flow of incoming messages from the subscribed MQTT topics. Connecting/subscribing is initiated when this flow
     * is collected.
     */
    val proxyMessageFlow: Flow<MqttClientProxyMessage>

    /**
     * Publishes a message to the given MQTT topic.
     *
     * @param topic The MQTT topic to publish to.
     * @param data The binary payload.
     * @param retained Whether the message should be retained by the broker.
     */
    fun publish(topic: String, data: ByteArray, retained: Boolean)

    /** Observable MQTT connection lifecycle state (DISCONNECTED → CONNECTING → CONNECTED → RECONNECTING). */
    val connectionState: StateFlow<ConnectionState>
}
