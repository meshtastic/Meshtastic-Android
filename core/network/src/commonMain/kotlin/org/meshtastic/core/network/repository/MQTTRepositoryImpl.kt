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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.MqttJsonPayload
import org.meshtastic.core.model.util.subscribeList
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.mqtt.ConnectionState
import org.meshtastic.mqtt.MqttClient
import org.meshtastic.mqtt.MqttEndpoint
import org.meshtastic.mqtt.MqttException
import org.meshtastic.mqtt.MqttMessage
import org.meshtastic.mqtt.QoS
import org.meshtastic.mqtt.packet.Subscription
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
        private const val KEEPALIVE_SECONDS = 30
        private const val INITIAL_RECONNECT_DELAY_MS = 1000L
        private const val MAX_RECONNECT_DELAY_MS = 30_000L
        private const val RECONNECT_BACKOFF_MULTIPLIER = 2
    }

    @Volatile private var client: MqttClient? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected.Idle)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        exceptionsWithDebugInfo = false
    }
    private val scope = CoroutineScope(dispatchers.default + SupervisorJob())
    private val publishSemaphore = Semaphore(20)

    override fun disconnect() {
        Logger.i { "MQTT Disconnecting" }
        val c = client
        client = null
        _connectionState.value = ConnectionState.Disconnected.Idle
        scope.launch { safeCatching { c?.close() }.onFailure { e -> Logger.w(e) { "MQTT clean disconnect failed" } } }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override val proxyMessageFlow: Flow<MqttClientProxyMessage> = callbackFlow {
        val ownerId = "MeshtasticAndroidMqttProxy-${nodeRepository.myId.value ?: "unknown"}"
        val channelSet = radioConfigRepository.channelSetFlow.first()
        val mqttConfig = radioConfigRepository.moduleConfigFlow.first().mqtt

        val rootTopic = mqttConfig?.root?.ifEmpty { DEFAULT_TOPIC_ROOT } ?: DEFAULT_TOPIC_ROOT

        val rawAddress = mqttConfig?.address ?: DEFAULT_SERVER_ADDRESS
        val endpoint = resolveEndpoint(rawAddress, mqttConfig?.tls_enabled == true)

        val newClient =
            MqttClient(ownerId) {
                keepAliveSeconds = KEEPALIVE_SECONDS
                autoReconnect = true
                username = mqttConfig?.username
                mqttConfig?.password?.let { password(it) }
            }
        client = newClient

        val subscriptions: List<Subscription> = buildList {
            channelSet.subscribeList.forEach { globalId ->
                add(
                    Subscription(
                        "$rootTopic$DEFAULT_TOPIC_LEVEL$globalId/+",
                        maxQos = QoS.AT_LEAST_ONCE,
                        noLocal = true,
                    ),
                )
                if (mqttConfig?.json_enabled == true) {
                    add(
                        Subscription(
                            "$rootTopic$JSON_TOPIC_LEVEL$globalId/+",
                            maxQos = QoS.AT_LEAST_ONCE,
                            noLocal = true,
                        ),
                    )
                }
            }
            add(Subscription("$rootTopic${DEFAULT_TOPIC_LEVEL}PKI/+", maxQos = QoS.AT_LEAST_ONCE, noLocal = true))
        }

        // Collect from the SharedFlow before connecting to avoid missing retained messages
        // that arrive immediately after SUBSCRIBE.
        launch { newClient.messages.collect { msg -> processMessage(msg) } }

        // Forward the client's connection state to the repo-level StateFlow for UI observation.
        launch { newClient.connectionState.collect { _connectionState.value = it } }

        // Retry the initial connect with exponential backoff. Once established,
        // autoReconnect handles subsequent drops and re-subscribes internally.
        launch {
            var reconnectDelay = INITIAL_RECONNECT_DELAY_MS
            while (true) {
                val result = safeCatching {
                    Logger.i { "MQTT Connecting to $endpoint" }
                    newClient.connect(endpoint)
                    if (subscriptions.isNotEmpty()) {
                        Logger.d { "MQTT subscribing to ${subscriptions.size} topics" }
                        newClient.subscribe(subscriptions)
                    }
                    Logger.i { "MQTT connected and subscribed" }
                }
                when {
                    result.isSuccess -> return@launch
                    result.exceptionOrNull() is MqttException.ConnectionRejected -> {
                        Logger.e(result.exceptionOrNull()) { "MQTT connection rejected (unrecoverable), stopping" }
                        close(result.exceptionOrNull()!!)
                        return@launch
                    }
                    else -> {
                        Logger.e(result.exceptionOrNull()) { "MQTT connect failed, retrying in ${reconnectDelay}ms" }
                        delay(reconnectDelay)
                        reconnectDelay =
                            (reconnectDelay * RECONNECT_BACKOFF_MULTIPLIER).coerceAtMost(MAX_RECONNECT_DELAY_MS)
                    }
                }
            }
        }

        awaitClose { disconnect() }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun ProducerScope<MqttClientProxyMessage>.processMessage(msg: MqttMessage) {
        val topic = msg.topic
        val payload = msg.payload.toByteArray()
        Logger.d { "MQTT received message on topic $topic (size: ${payload.size} bytes)" }

        if (topic.contains("/json/")) {
            try {
                val jsonStr = payload.decodeToString()
                json.decodeFromString<MqttJsonPayload>(jsonStr)
                Logger.d { "MQTT parsed JSON payload successfully" }
                trySend(MqttClientProxyMessage(topic = topic, text = jsonStr, retained = msg.retain))
            } catch (e: JsonDecodingException) {
                Logger.e(e) { "Failed to parse MQTT JSON: ${e.shortMessage} (path: ${e.path})" }
            } catch (e: SerializationException) {
                Logger.e(e) { "Failed to parse MQTT JSON: ${e.message}" }
            } catch (e: IllegalArgumentException) {
                Logger.e(e) { "Failed to parse MQTT JSON: ${e.message}" }
            }
        } else {
            trySend(MqttClientProxyMessage(topic = topic, data_ = payload.toByteString(), retained = msg.retain))
        }
    }

    override fun publish(topic: String, data: ByteArray, retained: Boolean) {
        val currentClient = client
        if (currentClient == null) {
            Logger.w { "MQTT publish to $topic dropped: client not connected" }
            return
        }
        Logger.d { "MQTT publishing message to topic $topic (size: ${data.size} bytes, retained: $retained)" }
        scope.launch {
            publishSemaphore.withPermit {
                safeCatching {
                    currentClient.publish(
                        MqttMessage(topic = topic, payload = data, qos = QoS.AT_LEAST_ONCE, retain = retained),
                    )
                }
                    .onFailure { e -> Logger.w(e) { "MQTT publish to $topic failed" } }
            }
        }
    }
}

/**
 * Resolve a user-supplied broker address into an [MqttEndpoint].
 *
 * Address resolution rules:
 * - If [rawAddress] already contains a URI scheme (`scheme://…`), parse it directly via [MqttEndpoint.parse] and
 *   respect whatever transport / port the user encoded.
 * - Otherwise wrap it as a WebSocket endpoint (`ws[s]://host${WEBSOCKET_PATH}`) so the proxy works over CDNs and
 *   firewall-restricted networks where raw 1883/8883 may be blocked. The scheme is `wss` when [tlsEnabled] is `true`,
 *   `ws` otherwise.
 *
 * Extracted as a top-level function so [MQTTRepositoryImplTest] can exercise every branch without spinning up the full
 * repository, and so `MqttManagerImpl` (in `:core:data`) can reuse the same parsing rules for the probe API. Visibility
 * is `public` because Kotlin's `internal` is scoped per Gradle module.
 */
fun resolveEndpoint(rawAddress: String, tlsEnabled: Boolean): MqttEndpoint = if (rawAddress.contains("://")) {
    MqttEndpoint.parse(rawAddress)
} else {
    val scheme = if (tlsEnabled) "wss" else "ws"
    MqttEndpoint.parse("$scheme://$rawAddress$WEBSOCKET_PATH")
}

private const val WEBSOCKET_PATH = "/mqtt"
