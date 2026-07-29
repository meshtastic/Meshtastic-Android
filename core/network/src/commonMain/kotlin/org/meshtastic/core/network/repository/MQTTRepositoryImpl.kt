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
import kotlinx.io.IOException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecodingException
import okio.ByteString.Companion.toByteString
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.MqttJsonPayload
import org.meshtastic.core.model.util.decodeOrNull
import org.meshtastic.core.model.util.subscribeList
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.mqtt.ConnectionState
import org.meshtastic.mqtt.MqttClient
import org.meshtastic.mqtt.MqttEndpoint
import org.meshtastic.mqtt.MqttException
import org.meshtastic.mqtt.MqttLogLevel
import org.meshtastic.mqtt.MqttMessage
import org.meshtastic.mqtt.QoS
import org.meshtastic.mqtt.ReasonCode
import org.meshtastic.mqtt.packet.Subscription
import org.meshtastic.mqtt.plus
import org.meshtastic.mqtt.transport.tcp.TcpTransportFactory
import org.meshtastic.mqtt.transport.ws.WebSocketTransportFactory
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.MqttClientProxyMessage
import org.meshtastic.proto.ServiceEnvelope
import kotlin.concurrent.Volatile
import kotlin.uuid.Uuid

@Single(binds = [MQTTRepository::class])
class MQTTRepositoryImpl(
    private val radioConfigRepository: RadioConfigRepository,
    private val nodeRepository: NodeRepository,
    private val buildConfigProvider: org.meshtastic.core.common.BuildConfigProvider,
    dispatchers: CoroutineDispatchers,
) : MQTTRepository {

    internal constructor(
        radioConfigRepository: RadioConfigRepository,
        nodeRepository: NodeRepository,
        buildConfigProvider: org.meshtastic.core.common.BuildConfigProvider,
        dispatchers: CoroutineDispatchers,
        mqttClientFactory: (MqttClientSetup) -> MqttClientSession,
    ) : this(
        radioConfigRepository = radioConfigRepository,
        nodeRepository = nodeRepository,
        buildConfigProvider = buildConfigProvider,
        dispatchers = dispatchers,
    ) {
        this.mqttClientFactory = mqttClientFactory
    }

    companion object {
        private const val DEFAULT_TOPIC_ROOT = "msh"
        private const val DEFAULT_TOPIC_LEVEL = "/2/e/"
        private const val JSON_TOPIC_LEVEL = "/2/json/"
        private const val DEFAULT_SERVER_ADDRESS = "mqtt.meshtastic.org"
        private const val INITIAL_RECONNECT_DELAY_MS = 1000L
        private const val MAX_RECONNECT_DELAY_MS = 30_000L
        private const val RECONNECT_BACKOFF_MULTIPLIER = 2
    }

    @Volatile private var client: MqttClientSession? = null
    private var mqttClientFactory: (MqttClientSetup) -> MqttClientSession = ::defaultMqttClientFactory

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

    // json_enabled is deprecated in the protobuf schema but remains the only way to toggle MQTT JSON
    // publish/consume, so we must keep reading it until the firmware/proto provides a replacement.
    @Suppress("DEPRECATION")
    @OptIn(ExperimentalSerializationApi::class)
    override val proxyMessageFlow: Flow<MqttClientProxyMessage> = callbackFlow {
        // Append a per-connection random id. myId identifies the *node* (and is null →
        // "unknown" before the local node record loads), so it is not unique per client:
        // clients sharing a node id — and the whole "unknown" pool on the public broker —
        // evict each other (SESSION_TAKEN_OVER), storming reconnects. A fresh id per client
        // (captured for its lifetime, so auto-reconnects stay stable) prevents that.
        val ownerId = "MeshtasticAndroidMqttProxy-${nodeRepository.myId.value ?: "unknown"}-${Uuid.random()}"
        val channelSet = radioConfigRepository.channelSetFlow.first()
        val mqttConfig = radioConfigRepository.moduleConfigFlow.first().mqtt

        val rootTopic = mqttConfig?.root?.ifEmpty { DEFAULT_TOPIC_ROOT } ?: DEFAULT_TOPIC_ROOT

        val rawAddress = mqttConfig?.address?.ifEmpty { DEFAULT_SERVER_ADDRESS } ?: DEFAULT_SERVER_ADDRESS
        val endpoint = resolveEndpoint(rawAddress, effectiveTlsEnabled(rawAddress, mqttConfig?.tls_enabled == true))

        val newClient =
            mqttClientFactory(
                MqttClientSetup(
                    ownerId = ownerId,
                    mqttConfig = mqttConfig,
                    logLevel = if (buildConfigProvider.isDebug) MqttLogLevel.DEBUG else MqttLogLevel.WARN,
                ),
            )
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
            add(
                Subscription(
                    "$rootTopic$DEFAULT_TOPIC_LEVEL$PKI_CHANNEL_ID/+",
                    maxQos = QoS.AT_LEAST_ONCE,
                    noLocal = true,
                ),
            )
        }

        // Collect from the SharedFlow before connecting to avoid missing retained messages
        // that arrive immediately after SUBSCRIBE.
        launch { newClient.messages.collect { msg -> processMessage(msg) } }

        // Forward the client's connection state to the repo-level StateFlow for UI observation.
        // Also emit structured log messages on transitions so reconnect attempt counts and
        // disconnect reason codes are visible in Crashlytics/Datadog without any PII.
        launch { newClient.connectionState.collect { state -> updateConnectionState(state) } }

        // Retry the initial connect with exponential backoff. Once established,
        // autoReconnect handles subsequent drops and re-subscribes internally.
        launch {
            var reconnectDelay = INITIAL_RECONNECT_DELAY_MS
            while (true) {
                val result = safeCatching {
                    Logger.i {
                        if (buildConfigProvider.isDebug) "MQTT Connecting to $endpoint" else "MQTT Connecting..."
                    }
                    newClient.connect(endpoint)
                    if (subscriptions.isNotEmpty()) {
                        Logger.d { "MQTT subscribing to ${subscriptions.size} topics" }
                        newClient.subscribe(subscriptions)
                    }
                    Logger.i { "MQTT connected and subscribed" }
                }
                val failure = result.exceptionOrNull()
                when {
                    result.isSuccess -> return@launch

                    failure is MqttException.ConnectionRejected && failure.isCredentialRejection() -> {
                        Logger.e(failure) { "MQTT connection rejected (unrecoverable), stopping" }
                        close(failure)
                        return@launch
                    }

                    else -> {
                        // Broker- and network-side failures are what this retry loop exists to absorb — an
                        // unreachable host, a TLS problem, a dropped connection, or a broker that violates the
                        // MQTT 5 spec (e.g. the topic-alias limit). None are defects in this app, and reporting
                        // every retry as a non-fatal drowned real regressions.
                        //
                        // Anything else landing here is unexpected — a fault in our own connect/subscribe setup
                        // rather than the peer's — so it keeps reporting.
                        if (failure.isExpectedMqttRetryFailure()) {
                            Logger.w(failure) { "MQTT connect failed, retrying in ${reconnectDelay}ms" }
                        } else {
                            Logger.e(failure) { "MQTT connect failed unexpectedly, retrying in ${reconnectDelay}ms" }
                        }
                        delay(reconnectDelay)
                        reconnectDelay =
                            (reconnectDelay * RECONNECT_BACKOFF_MULTIPLIER).coerceAtMost(MAX_RECONNECT_DELAY_MS)
                    }
                }
            }
        }

        awaitClose { disconnect() }
    }

    internal fun updateConnectionState(state: ConnectionState) {
        _connectionState.value = state
        when (state) {
            ConnectionState.Connecting -> Logger.i { "MQTT connecting" }

            ConnectionState.Connected -> Logger.i { "MQTT connected" }

            is ConnectionState.Reconnecting -> {
                val errorDetail = state.lastError?.message?.let { ": $it" } ?: ""
                Logger.w { "MQTT reconnecting (attempt ${state.attempt}$errorDetail)" }
            }

            is ConnectionState.Disconnected -> {
                state.reason?.let { Logger.w { "MQTT disconnected: ${it.message}" } }
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun ProducerScope<MqttClientProxyMessage>.processMessage(msg: MqttMessage) {
        val topic = msg.topic
        val payload = msg.payload.toByteArray()

        if (topic.contains("/json/")) {
            try {
                val jsonStr = payload.decodeToString()
                json.decodeFromString<MqttJsonPayload>(jsonStr)
                trySend(MqttClientProxyMessage(topic = topic, text = jsonStr, retained = msg.retain))
            } catch (e: JsonDecodingException) {
                Logger.e(e) { "Failed to parse MQTT JSON: ${e.shortMessage} (path: ${e.path})" }
            } catch (e: SerializationException) {
                Logger.e(e) { "Failed to parse MQTT JSON: ${e.message}" }
            } catch (e: IllegalArgumentException) {
                Logger.e(e) { "Failed to parse MQTT JSON: ${e.message}" }
            }
        } else {
            // Drop provably-undeliverable downlink packets before spending BLE bandwidth on
            // them. In client-proxy mode the public broker floods payload-less packet-header
            // stubs the node can only decrypt-fail and discard; stopping them here saves BLE
            // airtime, a decrypt attempt, and a node-side warning per packet.
            if (isUndeliverableDownlink(payload, nodeRepository.myId.value)) {
                Logger.d {
                    if (buildConfigProvider.isDebug) {
                        "MQTT downlink dropped (no payload): $topic"
                    } else {
                        "MQTT downlink dropped (no payload)"
                    }
                }
                return
            }
            trySend(MqttClientProxyMessage(topic = topic, data_ = payload.toByteString(), retained = msg.retain))
        }
    }

    override fun publish(topic: String, data: ByteArray, retained: Boolean) {
        val currentClient = client
        if (currentClient == null) {
            Logger.w {
                if (buildConfigProvider.isDebug) {
                    "MQTT publish to $topic dropped: client not connected"
                } else {
                    "MQTT publish dropped: client not connected"
                }
            }
            return
        }
        scope.launch {
            publishSemaphore.withPermit {
                safeCatching {
                    currentClient.publish(
                        MqttMessage(topic = topic, payload = data, qos = QoS.AT_LEAST_ONCE, retain = retained),
                    )
                }
                    .onFailure { e ->
                        Logger.w(e) {
                            if (buildConfigProvider.isDebug) {
                                "MQTT publish to $topic failed"
                            } else {
                                "MQTT publish (${data.size} bytes) failed"
                            }
                        }
                    }
            }
        }
    }
}

/**
 * `true` only for CONNACK reason codes where retrying can never help — the broker examined our credentials or client
 * identity and refused them.
 *
 * A [MqttException.ConnectionRejected] can still carry a *transient* broker verdict (`SERVER_BUSY`,
 * `SERVER_UNAVAILABLE`, `CONNECTION_RATE_EXCEEDED`), which must stay in the retry loop rather than stop the proxy with
 * a "check credentials" dialog. Public so `MqttManagerImpl` in `:core:data` can phrase its user-facing error from the
 * same classification.
 */
fun MqttException.ConnectionRejected.isCredentialRejection(): Boolean = when (reasonCode) {
    ReasonCode.BAD_USER_NAME_OR_PASSWORD,
    ReasonCode.NOT_AUTHORIZED,
    ReasonCode.BAD_AUTHENTICATION_METHOD,
    ReasonCode.CLIENT_IDENTIFIER_NOT_VALID,
    ReasonCode.BANNED,
    -> true

    else -> false
}

/**
 * `true` when an MQTT connect/subscribe failure is one the retry loop is designed to absorb, rather than a defect worth
 * reporting to Crashlytics/Datadog.
 *
 * Covers the MQTT client's own sealed error hierarchy — a lost connection, and protocol violations by the broker such
 * as an inbound topic alias above the advertised maximum — plus transport-level I/O failures (unreachable host, TLS,
 * socket reset). All are peer- or network-side and not actionable by this app.
 *
 * Deliberately narrow: an unexpected exception escaping our own client/state setup is a real bug and must keep
 * reporting, so anything outside these families returns `false`.
 */
private fun Throwable?.isExpectedMqttRetryFailure(): Boolean = when (this) {
    null -> false
    is MqttException -> true
    is IOException -> true
    else -> false
}

internal data class MqttClientSetup(
    val ownerId: String,
    val mqttConfig: ModuleConfig.MQTTConfig?,
    val logLevel: MqttLogLevel,
)

internal interface MqttClientSession {
    val messages: Flow<MqttMessage>
    val connectionState: StateFlow<ConnectionState>

    suspend fun connect(endpoint: MqttEndpoint)

    suspend fun subscribe(subscriptions: List<Subscription>)

    suspend fun publish(message: MqttMessage)

    suspend fun close()
}

private class DefaultMqttClientSession(private val delegate: MqttClient) : MqttClientSession {
    override val messages: Flow<MqttMessage> = delegate.messages
    override val connectionState: StateFlow<ConnectionState> = delegate.connectionState

    override suspend fun connect(endpoint: MqttEndpoint) {
        delegate.connect(endpoint)
    }

    override suspend fun subscribe(subscriptions: List<Subscription>) {
        delegate.subscribe(subscriptions)
    }

    override suspend fun publish(message: MqttMessage) {
        delegate.publish(message)
    }

    override suspend fun close() {
        delegate.close()
    }
}

private fun defaultMqttClientFactory(setup: MqttClientSetup): MqttClientSession = DefaultMqttClientSession(
    MqttClient(setup.ownerId) {
        // mqtt-client 0.4.0 makes transport a required SPI: the client throws at connect if unset.
        // Register TCP/TLS (the default) + WebSocket (for user-entered ws://-/wss:// brokers).
        // Both get the same private-CA trust hook so the grant stays scoped to this socket — see [mqttTlsConfig].
        val tls = mqttTlsConfig()
        transportFactory = TcpTransportFactory(tls) + WebSocketTransportFactory(tls)
        keepAliveSeconds = MQTT_KEEPALIVE_SECONDS
        autoReconnect = true
        val (user, pass) = effectiveCredentials(setup.mqttConfig)
        username = user
        pass?.let { password(it) }
        logger = KermitMqttLogger()
        // WARN for production: the library emits endpoint addresses and topic strings at
        // INFO level. WARN messages (reconnect, timeout, retry) contain no PII and are
        // exactly the signals needed for production diagnostics.
        logLevel = setup.logLevel
    },
)

// Public (not internal/private) so MqttManagerImpl's probe in :core:data can mirror the live client —
// some brokers reject a keepalive-0 CONNECT (misleadingly, as CLIENT_IDENTIFIER_NOT_VALID).
const val MQTT_KEEPALIVE_SECONDS = 30
private const val MQTT_PORT_PLAIN = 1883
private const val MQTT_PORT_TLS = 8883

/**
 * Resolve a user-supplied broker address into an [MqttEndpoint].
 *
 * Address resolution rules:
 * - If [rawAddress] already contains a URI scheme (`scheme://…`), parse it directly via [MqttEndpoint.parse] and
 *   respect whatever transport / port the user encoded.
 * - Otherwise wrap it as a TCP endpoint using standard MQTT ports: port 8883 if [tlsEnabled] is `true`, port 1883
 *   otherwise. This allows standard MQTT brokers to work out of the box.
 *
 * Extracted as a top-level function so [MQTTRepositoryImplTest] can exercise every branch without spinning up the full
 * repository, and so `MqttManagerImpl` (in `:core:data`) can reuse the same parsing rules for the probe API. Visibility
 * is `public` because Kotlin's `internal` is scoped per Gradle module.
 */
fun resolveEndpoint(rawAddress: String, tlsEnabled: Boolean): MqttEndpoint = if (rawAddress.contains("://")) {
    MqttEndpoint.parse(rawAddress)
} else {
    val scheme = if (tlsEnabled) "ssl" else "tcp"
    val defaultPort = if (tlsEnabled) MQTT_PORT_TLS else MQTT_PORT_PLAIN
    // Preserve the user-supplied port (if any) instead of naively appending the default.
    val hostAndPort = if (rawAddress.contains(":")) rawAddress else "$rawAddress:$defaultPort"
    MqttEndpoint.parse("$scheme://$hostAndPort")
}

private const val DEFAULT_PUBLIC_SERVER = "mqtt.meshtastic.org"

// The public broker's well-known credentials, same values as the firmware's Default.h.
private const val DEFAULT_MQTT_USERNAME = "meshdev"
private const val DEFAULT_MQTT_PASSWORD = "large4cats"

/**
 * Mirrors the firmware's `PubSubConfig` rule: an empty `address` means "the public broker with its well-known
 * credentials", substituting username and password together with the server — the stored username/password are ignored
 * in that case, whatever they contain. Substituting only the address (as this repository does for the endpoint) while
 * passing the stored empty credentials through makes the proxy connect anonymously, which the public broker rejects
 * with BAD_USER_NAME_OR_PASSWORD — surfaced to the user as a bogus "check credentials" error on configs the firmware
 * itself connects with happily. Empty-address configs occur in the wild: lockdown-enabled firmware hands
 * unauthenticated clients a zeroed MQTTConfig.
 */
internal fun effectiveCredentials(config: ModuleConfig.MQTTConfig?): Pair<String?, String?> =
    if (config?.address.isNullOrEmpty()) {
        DEFAULT_MQTT_USERNAME to DEFAULT_MQTT_PASSWORD
    } else {
        config?.username to config?.password
    }

fun effectiveTlsEnabled(address: String, tlsEnabled: Boolean): Boolean =
    tlsEnabled || extractHost(address).equals(DEFAULT_PUBLIC_SERVER, ignoreCase = true)

/**
 * Extracts the bare hostname from an address that may include a scheme, port, or path. Examples:
 * - `mqtt.meshtastic.org` → `mqtt.meshtastic.org`
 * - `mqtt.meshtastic.org:1883` → `mqtt.meshtastic.org`
 * - `tcp://mqtt.meshtastic.org:1883` → `mqtt.meshtastic.org`
 * - `ssl://mqtt.meshtastic.org` → `mqtt.meshtastic.org`
 */
internal fun extractHost(address: String): String {
    val afterScheme =
        if (address.contains("://")) {
            address.substringAfter("://")
        } else {
            address
        }
    // Remove path (if any), then remove port
    return afterScheme.substringBefore("/").substringBefore(":")
}

private const val PKI_CHANNEL_ID = "PKI"

/**
 * Returns true when a downlink [ServiceEnvelope] is provably un-usable by the node and should be dropped before
 * forwarding over BLE (MQTT client-proxy mode).
 *
 * Fails open — returns false (forward) for anything it cannot positively prove undeliverable: unparseable bytes,
 * traffic on the `PKI` channel (public-key-encrypted direct messages the node accepts without first decrypting), our
 * own echoed-back packets (used as implicit ACKs), a packet-less envelope, or any packet that actually carries a
 * payload. The only drop case is Tier 1: a [MeshPacket] with neither `decoded` nor `encrypted` set — no legitimate
 * Meshtastic packet is payload-less.
 *
 * Extracted as an internal top-level function so [MQTTRepositoryImplTest] can exercise every branch without spinning up
 * the full repository.
 *
 * @param payload the raw MQTT payload bytes (a serialized ServiceEnvelope on the binary topic).
 * @param myId this device's node id in `!xxxxxxxx` hex form, or null before the local node loads.
 */
internal fun isUndeliverableDownlink(payload: ByteArray, myId: String?): Boolean {
    // Decode without a logger: this is fail-open and, on a public broker, may see arbitrary bytes on the binary
    // topic. Passing Logger would emit an error log per unparseable downlink — expensive noise for an expected case.
    val envelope = ServiceEnvelope.ADAPTER.decodeOrNull(payload) ?: return false // fail open on garbage
    // Guards — never drop: PKI-channel traffic (accepted without decryption) or our own echoed-back
    // packets (used as implicit ACKs).
    val isGuarded = envelope.channel_id == PKI_CHANNEL_ID || (myId != null && envelope.gateway_id == myId)
    // Tier 1: a packet with neither `decoded` nor `encrypted` carries nothing the node can use.
    // A packet-less envelope (packet == null) has nothing to prove, so it is treated as deliverable.
    val packet = envelope.packet
    val hasNoPayload = packet != null && packet.decoded == null && packet.encrypted == null
    return hasNoPayload && !isGuarded
}
