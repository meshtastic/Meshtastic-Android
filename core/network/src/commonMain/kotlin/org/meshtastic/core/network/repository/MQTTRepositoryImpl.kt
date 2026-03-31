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
import io.github.davidepianca98.mqtt.MQTTVersion
import io.github.davidepianca98.mqtt.Subscription
import io.github.davidepianca98.mqtt.packets.Qos
import io.github.davidepianca98.mqtt.packets.mqttv5.ReasonCode
import io.github.davidepianca98.mqtt.packets.mqttv5.SubscriptionOptions
import io.github.davidepianca98.socket.tls.TLSClientSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.koin.core.annotation.Single
import org.meshtastic.core.model.MqttJsonPayload
import org.meshtastic.core.model.util.subscribeList
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.proto.MqttClientProxyMessage

@Single(binds = [MQTTRepository::class])
class MQTTRepositoryImpl(
    private val radioConfigRepository: RadioConfigRepository,
    private val nodeRepository: NodeRepository,
) : MQTTRepository {

    companion object {
        private const val DEFAULT_TOPIC_ROOT = "msh"
        private const val DEFAULT_TOPIC_LEVEL = "/2/e/"
        private const val JSON_TOPIC_LEVEL = "/2/json/"
        private const val DEFAULT_SERVER_ADDRESS = "mqtt.meshtastic.org"
    }

    private var client: MQTTClient? = null
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var clientJob: Job? = null
    private val publishSemaphore = Semaphore(20)
    private val reconnectMutex = Mutex()
    private var reconnectAttempt = 0

    override fun disconnect() {
        Logger.i { "MQTT Disconnecting" }
        clientJob?.cancel()
        clientJob = null
        client = null
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    /**
     * Cold flow. MUST be collected by exactly one subscriber. Multiple collectors will create duplicate MQTT clients
     * with same clientId, causing broker to disconnect previous connections.
     */
    override val proxyMessageFlow: Flow<MqttClientProxyMessage> = callbackFlow {
        val ownerId = "MeshtasticAndroidMqttProxy-${nodeRepository.myId.value ?: "unknown"}"
        val channelSet = radioConfigRepository.channelSetFlow.first()
        val mqttConfig = radioConfigRepository.moduleConfigFlow.first().mqtt

        val rootTopic = mqttConfig?.root?.ifEmpty { DEFAULT_TOPIC_ROOT } ?: DEFAULT_TOPIC_ROOT

        val (host, port) =
            (mqttConfig?.address ?: DEFAULT_SERVER_ADDRESS).split(":", limit = 2).let {
                it[0] to (it.getOrNull(1)?.toIntOrNull() ?: if (mqttConfig?.tls_enabled == true) 8883 else 1883)
            }

        // Subscriptions (out of loop)
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

        // Using IO-dispatcher since we use blocking MQTTClient.run()
        clientJob =
            scope.launch(Dispatchers.IO) {
                @Suppress("MagicNumber")
                val baseDelay = 2_000L

                // Base backoff value
                @Suppress("MagicNumber")
                val maxDelay = 64_000L // Maximal backoff value

                // Reconnection loop
                while (isActive) {
                    val attempt =
                        reconnectMutex.withLock {
                            ++reconnectAttempt // Don't really think we will ever get overflow here since it will take
                            // 4300 years
                        }

                    // Exponential backoff
                    @Suppress("MagicNumber")
                    val delayMs =
                        when {
                            attempt == 1 -> 0
                            attempt >= 7 -> maxDelay
                            else -> baseDelay * (1L shl (attempt - 2)) // Backoff 2→4→8→16→32→64 seconds
                        }

                    if (delayMs > 0) {
                        Logger.w { "MQTT reconnect #$attempt in ${delayMs / 1000}s" }
                        delay(delayMs)
                    }

                    // Creating client on each iteration
                    var newClient: MQTTClient? = null
                    @Suppress("TooGenericExceptionCaught")
                    try {
                        newClient =
                            MQTTClient(
                                mqttVersion = MQTTVersion.MQTT5,
                                address = host,
                                port = port,
                                tls = if (mqttConfig?.tls_enabled == true) TLSClientSettings() else null,
                                userName = mqttConfig?.username,
                                password = mqttConfig?.password?.encodeToByteArray()?.toUByteArray(),
                                clientId = ownerId,
                                onConnected = {
                                    Logger.i { "MQTT connected" }
                                    scope.launch {
                                        // Reset backoff
                                        reconnectMutex.withLock { reconnectAttempt = 0 }
                                    }
                                },
                                onDisconnected = { Logger.w { "MQTT disconnected" } },
                                publishReceived = { packet ->
                                    val topic = packet.topicName
                                    val payload = packet.payload?.toByteArray()
                                    Logger.d {
                                        "MQTT received message on topic $topic (size: ${payload?.size ?: 0} bytes)"
                                    }

                                    val result =
                                        trySend(
                                            (
                                                if (topic.contains("/json/")) {
                                                    try {
                                                        val jsonStr = payload?.decodeToString() ?: ""
                                                        // Validate JSON by parsing it
                                                        json.decodeFromString<MqttJsonPayload>(jsonStr)
                                                        Logger.d { "MQTT parsed JSON payload successfully" }
                                                        MqttClientProxyMessage(
                                                            topic = topic,
                                                            text = jsonStr,
                                                            retained = packet.retain,
                                                        )
                                                    } catch (e: SerializationException) {
                                                        Logger.e(e) { "Failed to parse MQTT JSON: ${e.message}" }
                                                    } catch (e: IllegalArgumentException) {
                                                        Logger.e(e) { "Failed to parse MQTT JSON: ${e.message}" }
                                                    }
                                                } else {
                                                    MqttClientProxyMessage(
                                                        topic = topic,
                                                        data_ = payload?.toByteString() ?: ByteString.EMPTY,
                                                        retained = packet.retain,
                                                    )
                                                }
                                                )
                                                as MqttClientProxyMessage,
                                        )
                                    if (result.isFailure) {
                                        Logger.w { "MQTT message dropped: flow channel closed" }
                                    }
                                },
                            )

                        if (subscriptions.isNotEmpty()) {
                            Logger.d { "MQTT subscribing to ${subscriptions.size} topics" }
                            newClient.subscribe(subscriptions)
                        }

                        // Renew client for publish()
                        client = newClient

                        Logger.i { "MQTT run loop start ($host:$port)" }

                        // Note: run() is blocking. Cancellation via clientJob.cancel()
                        // will be processed when run() returns or if the library checks for interruption.
                        // Test with actual network disconnect to verify timely shutdown.
                        newClient.run() // Blocking

                        Logger.w { "MQTT run() exited normally — reconnecting" }
                    } catch (e: io.github.davidepianca98.mqtt.MQTTException) {
                        Logger.e(e) { "MQTT protocol error (attempt #$attempt)" }
                        // Continue loop
                    } catch (e: io.github.davidepianca98.socket.IOException) {
                        Logger.e(e) { "MQTT IO error (attempt #$attempt)" }
                        // Continue loop
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        Logger.i { "MQTT reconnect loop cancelled" }
                        throw e // Stop
                    } catch (e: Exception) {
                        Logger.e(e) { "MQTT unexpected error (attempt #$attempt): ${e::class.simpleName}" }
                    } finally {
                        // Cleanup
                        newClient?.let {
                            if (client === it) {
                                client = null
                            }
                            try {
                                newClient.disconnect(ReasonCode.SUCCESS) // Success?
                            } catch (_: Exception) {}
                        }
                    }
                }
            }

        awaitClose { disconnect() }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    override fun publish(topic: String, data: ByteArray, retained: Boolean) {
        Logger.d { "MQTT publishing message to topic $topic (size: ${data.size} bytes, retained: $retained)" }
        scope.launch {
            val c = client

            if (c == null || !c.isConnackReceived()) {
                Logger.w { "MQTT not connected, dropping message" }
                return@launch
            }

            publishSemaphore.withPermit {
                @Suppress("TooGenericExceptionCaught")
                try {
                    c.publish(
                        retain = retained,
                        qos = Qos.AT_LEAST_ONCE,
                        topic = topic,
                        payload = data.toUByteArray(),
                    ) // Potential TOCTOU.
                    Logger.d { "MQTT publish succeeded" }
                } catch (e: Exception) {
                    Logger.w(e) { "MQTT publish failed" }
                }
            }
        }
    }
}
