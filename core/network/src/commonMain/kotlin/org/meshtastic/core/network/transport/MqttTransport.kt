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
package org.meshtastic.core.network.transport

import co.touchlab.kermit.Logger
import io.github.davidepianca98.MQTTClient
import io.github.davidepianca98.mqtt.MQTTVersion
import io.github.davidepianca98.mqtt.Subscription
import io.github.davidepianca98.mqtt.packets.Qos
import io.github.davidepianca98.mqtt.packets.mqttv5.SubscriptionOptions
import io.github.davidepianca98.socket.tls.TLSClientSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.network.repository.MQTTRepository
import org.meshtastic.proto.MqttClientProxyMessage

class MqttTransport(
    private val config: MqttTransportConfig,
) : MQTTRepository {

    data class MqttTransportConfig(
        val address: String,
        val username: String? = null,
        val password: String? = null,
        val tlsEnabled: Boolean = false,
        val rootTopic: String = "msh",
        val subscribeTopics: List<String> = emptyList(),
    )

    private var client: MQTTClient? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var clientJob: Job? = null

    override fun disconnect() {
        Logger.i { "MQTT Disconnecting" }
        clientJob?.cancel()
        clientJob = null
        client = null
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    override val proxyMessageFlow: Flow<MqttClientProxyMessage> = callbackFlow {
        val (host, port) = config.address.split(":", limit = 2).let {
            it[0] to (it.getOrNull(1)?.toIntOrNull() ?: if (config.tlsEnabled) 8883 else 1883)
        }

        val newClient = MQTTClient(
            mqttVersion = MQTTVersion.MQTT5,
            address = host,
            port = port,
            tls = if (config.tlsEnabled) TLSClientSettings() else null,
            userName = config.username,
            password = config.password?.encodeToByteArray()?.toUByteArray(),
            publishReceived = { packet ->
                trySend(
                    MqttClientProxyMessage(
                        topic = packet.topicName,
                        data_ = packet.payload?.toByteArray()?.toByteString() ?: okio.ByteString.EMPTY,
                        retained = packet.retain,
                    )
                )
            }
        )

        client = newClient
        
        clientJob = scope.launch {
            try {
                Logger.i { "MQTT Starting client loop for $host:$port" }
                newClient.runSuspend()
            } catch (e: Exception) {
                Logger.e(e) { "MQTT Client loop error" }
                close(e)
            }
        }

        config.subscribeTopics.forEach { topic ->
            Logger.i { "MQTT Subscribing to $topic" }
            newClient.subscribe(listOf(Subscription(topic, SubscriptionOptions(Qos.AT_LEAST_ONCE))))
        }

        awaitClose {
            disconnect()
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    override fun publish(topic: String, data: ByteArray, retained: Boolean) {
        client?.publish(
            retain = retained,
            qos = Qos.AT_LEAST_ONCE,
            topic = topic,
            payload = data.toUByteArray()
        )
    }
}
