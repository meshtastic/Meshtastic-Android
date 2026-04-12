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

import co.touchlab.kermit.Logger
import io.github.davidepianca98.MQTTClient
import io.github.davidepianca98.mqtt.MQTTException
import io.github.davidepianca98.mqtt.MQTTVersion
import io.github.davidepianca98.mqtt.Subscription
import io.github.davidepianca98.mqtt.packets.Qos
import io.github.davidepianca98.mqtt.packets.mqttv5.ReasonCode
import io.github.davidepianca98.mqtt.packets.mqttv5.SubscriptionOptions
import io.github.davidepianca98.socket.IOException
import io.github.davidepianca98.socket.tls.TLSClientSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecodingException
import okio.ByteString.Companion.toByteString
import org.koin.core.annotation.Single
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.MqttJsonPayload
import org.meshtastic.core.model.util.subscribeList
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.proto.MqttClientProxyMessage
import kotlin.concurrent.Volatile

@Single(binds = [MQTTRepository::class])
class MQTTRepositoryImpl(
    private val radioConfigRepository: RadioConfigRepository,
    private val nodeRepository: NodeRepository,
    dispatchers: CoroutineDispatchers,
) : MQTTRepository {

    companion object {
        private const val DEFAULT_TOPIC_ROOT = "msh"
        private const val DEFAULT_TOPIC_LEVEL = "/2/e/"
        private const val JSON_TOPIC_LEVEL = "/2/json/"
        private const val DEFAULT_SERVER_ADDRESS = "mqtt.meshtastic.org"
        private const val INITIAL_RECONNECT_DELAY_MS = 1000L
        private const val MAX_RECONNECT_DELAY_MS = 30_000L
        private const val RECONNECT_BACKOFF_MULTIPLIER = 2
    }

    @Volatile private var client: MQTTClient? = null

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        exceptionsWithDebugInfo = false
    }
    private val scope = CoroutineScope(dispatchers.default + SupervisorJob())

    @Volatile private var clientJob: Job? = null
    private val publishSemaphore = Semaphore(20)

    @Suppress("TooGenericExceptionCaught")
    override fun disconnect() {
        Logger.i { "MQTT Disconnecting" }
        val c = client
        client = null // Null first to prevent re-entrant disconnect
        try {
            c?.disconnect(ReasonCode.SUCCESS)
        } catch (e: Exception) {
            Logger.w(e) { "MQTT clean disconnect failed" }
        }
        clientJob?.cancel()
        clientJob = null
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    override val proxyMessageFlow: Flow<MqttClientProxyMessage> = callbackFlow {
        val ownerId = "MeshtasticAndroidMqttProxy-${nodeRepository.myId.value ?: "unknown"}"
        val channelSet = radioConfigRepository.channelSetFlow.first()
        val mqttConfig = radioConfigRepository.moduleConfigFlow.first().mqtt

        val rootTopic = mqttConfig?.root?.ifEmpty { DEFAULT_TOPIC_ROOT } ?: DEFAULT_TOPIC_ROOT

        val (host, port) =
            (mqttConfig?.address ?: DEFAULT_SERVER_ADDRESS).split(":", limit = 2).let {
                it[0] to (it.getOrNull(1)?.toIntOrNull() ?: if (mqttConfig?.tls_enabled == true) 8883 else 1883)
            }

        val newClient =
            MQTTClient(
                mqttVersion = MQTTVersion.MQTT5,
                address = host,
                port = port,
                tls = if (mqttConfig?.tls_enabled == true) TLSClientSettings() else null,
                userName = mqttConfig?.username,
                password = mqttConfig?.password?.encodeToByteArray()?.toUByteArray(),
                clientId = ownerId,
                publishReceived = { packet ->
                    val topic = packet.topicName
                    val payload = packet.payload?.toByteArray()
                    Logger.d { "MQTT received message on topic $topic (size: ${payload?.size ?: 0} bytes)" }

                    if (topic.contains("/json/")) {
                        try {
                            val jsonStr = payload?.decodeToString() ?: ""
                            // Validate JSON by parsing it
                            json.decodeFromString<MqttJsonPayload>(jsonStr)
                            Logger.d { "MQTT parsed JSON payload successfully" }

                            trySend(MqttClientProxyMessage(topic = topic, text = jsonStr, retained = packet.retain))
                        } catch (e: JsonDecodingException) {
                            @OptIn(ExperimentalSerializationApi::class)
                            Logger.e(e) { "Failed to parse MQTT JSON: ${e.shortMessage} (path: ${e.path})" }
                        } catch (e: SerializationException) {
                            Logger.e(e) { "Failed to parse MQTT JSON: ${e.message}" }
                        } catch (e: IllegalArgumentException) {
                            Logger.e(e) { "Failed to parse MQTT JSON: ${e.message}" }
                        }
                    } else {
                        trySend(
                            MqttClientProxyMessage(
                                topic = topic,
                                data_ = payload?.toByteString() ?: okio.ByteString.EMPTY,
                                retained = packet.retain,
                            ),
                        )
                    }
                },
            )

        client = newClient

        // Subscribe before starting the event loop. KMQTT's subscribe() calls send(),
        // which queues the SUBSCRIBE packet in pendingSendMessages while connackReceived
        // is false. Once the event loop receives CONNACK, it flushes the queue — so
        // subscriptions are guaranteed to be sent immediately after the connection is
        // established, with no timing races. This replaces a previous yield()-based
        // approach that was unreliable on lightly loaded dispatchers.
        val subscriptions = mutableListOf<Subscription>()
        channelSet.subscribeList.forEach { globalId ->
            subscriptions.add(
                Subscription("$rootTopic$DEFAULT_TOPIC_LEVEL$globalId/+", SubscriptionOptions(Qos.AT_LEAST_ONCE)),
            )
            if (mqttConfig?.json_enabled == true) {
                subscriptions.add(
                    Subscription("$rootTopic$JSON_TOPIC_LEVEL$globalId/+", SubscriptionOptions(Qos.AT_LEAST_ONCE)),
                )
            }
        }
        subscriptions.add(Subscription("$rootTopic${DEFAULT_TOPIC_LEVEL}PKI/+", SubscriptionOptions(Qos.AT_LEAST_ONCE)))

        if (subscriptions.isNotEmpty()) {
            Logger.d { "MQTT subscribing to ${subscriptions.size} topics" }
            newClient.subscribe(subscriptions)
        }

        clientJob =
            scope.launch {
                var reconnectDelay = INITIAL_RECONNECT_DELAY_MS
                while (true) {
                    try {
                        Logger.i { "MQTT Starting client loop for $host:$port" }
                        newClient.runSuspend()
                        // runSuspend returned normally — broker closed connection cleanly.
                        // Reset backoff so the next reconnect starts with the minimum delay.
                        reconnectDelay = INITIAL_RECONNECT_DELAY_MS
                        Logger.w { "MQTT client loop ended normally, reconnecting in ${reconnectDelay}ms" }
                    } catch (e: MQTTException) {
                        Logger.e(e) { "MQTT Client loop error (MQTT), reconnecting in ${reconnectDelay}ms" }
                    } catch (e: IOException) {
                        Logger.e(e) { "MQTT Client loop error (IO), reconnecting in ${reconnectDelay}ms" }
                    } catch (e: CancellationException) {
                        Logger.i { "MQTT Client loop cancelled" }
                        throw e
                    }
                    delay(reconnectDelay)
                    reconnectDelay =
                        (reconnectDelay * RECONNECT_BACKOFF_MULTIPLIER).coerceAtMost(MAX_RECONNECT_DELAY_MS)
                }
            }

        awaitClose { disconnect() }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    override fun publish(topic: String, data: ByteArray, retained: Boolean) {
        val currentClient = client
        if (currentClient == null) {
            Logger.w { "MQTT publish to $topic dropped: client not connected" }
            return
        }
        Logger.d { "MQTT publishing message to topic $topic (size: ${data.size} bytes, retained: $retained)" }
        scope.launch {
            publishSemaphore.withPermit {
                @Suppress("TooGenericExceptionCaught")
                try {
                    currentClient.publish(
                        retain = retained,
                        qos = Qos.AT_LEAST_ONCE,
                        topic = topic,
                        payload = data.toUByteArray(),
                    )
                } catch (e: Exception) {
                    Logger.w(e) { "MQTT publish to $topic failed" }
                }
            }
        }
    }
}
