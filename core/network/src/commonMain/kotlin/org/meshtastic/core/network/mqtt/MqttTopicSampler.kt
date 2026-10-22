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
package org.meshtastic.core.network.mqtt

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.IOException
import org.meshtastic.core.network.mqtt.MqttTopicDiscovery.Activity
import org.meshtastic.core.network.repository.KermitMqttLogger
import org.meshtastic.core.network.repository.MQTT_KEEPALIVE_SECONDS
import org.meshtastic.core.network.repository.effectiveCredentials
import org.meshtastic.core.network.repository.effectiveTlsEnabled
import org.meshtastic.core.network.repository.mqttTlsConfig
import org.meshtastic.core.network.repository.resolveEndpoint
import org.meshtastic.mqtt.ConnectionState
import org.meshtastic.mqtt.MqttClient
import org.meshtastic.mqtt.MqttException
import org.meshtastic.mqtt.MqttLogLevel
import org.meshtastic.mqtt.QoS
import org.meshtastic.mqtt.packet.Subscription
import org.meshtastic.mqtt.plus
import org.meshtastic.mqtt.transport.tcp.TcpTransportFactory
import org.meshtastic.mqtt.transport.ws.WebSocketTransportFactory
import org.meshtastic.proto.ModuleConfig
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

/** The slice of an MQTT client a sample needs, so tests can drive the sampler without a broker. */
interface TopicSamplerClient {
    val messages: Flow<Pair<String, ByteArray>>
    val connectionState: StateFlow<ConnectionState>

    suspend fun connect()

    /** Returns the filters the broker granted; throws when it refused them all or closed the connection. */
    suspend fun subscribe(filters: List<String>): List<String>

    suspend fun unsubscribe(filter: String)

    suspend fun close()
}

/**
 * A subscribe-only broker connection that listens for a fixed time and tallies the roots it hears. Every topic it sees
 * is location-derived, so nothing here logs a topic, root or count tied to one.
 */
class MqttTopicSampler(
    private val clientFactory: (ModuleConfig.MQTTConfig?) -> TopicSamplerClient = ::defaultSamplerClient,
    private val duration: Duration = DEFAULT_DURATION,
    private val connectDeadline: Duration = 15.seconds,
    private val subscribeDeadline: Duration = 10.seconds,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    enum class Failure {
        CONNECT,
        FILTERS_REFUSED,
        CLOSED,
        ERROR,
    }

    sealed interface Phase {
        data object Idle : Phase

        data object Connecting : Phase

        data object Subscribing : Phase

        data class Sampling(val secondsRemaining: Int) : Phase

        data object Finished : Phase

        data class Failed(val failure: Failure) : Phase
    }

    data class State(
        val phase: Phase = Phase.Idle,
        val activity: Map<String, Activity> = emptyMap(),
        val messageCount: Int = 0,
    ) {
        val isRunning: Boolean
            get() = phase is Phase.Connecting || phase is Phase.Subscribing || phase is Phase.Sampling
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val lock = Mutex()
    private var tally = MqttTopicDiscovery.Tally()
    private var messageCount = 0
    private var regionPackets = 0

    /**
     * Runs one sample to completion. Cancelling the caller ends it early and keeps what was heard, as does the broker
     * closing the connection once something has arrived. Any other error fails it with [Failure.ERROR].
     */
    @Suppress("LongMethod")
    suspend fun run(config: ModuleConfig.MQTTConfig?, regionTopic: String) {
        lock.withLock {
            tally = MqttTopicDiscovery.Tally()
            messageCount = 0
            regionPackets = 0
        }
        _state.value = State(phase = Phase.Connecting)
        val client =
            try {
                clientFactory(config)
            } catch (e: IllegalArgumentException) {
                Logger.w { "MQTT topic sample: address (${e::class.simpleName})" }
                _state.value = State(phase = Phase.Failed(Failure.CONNECT))
                return
            }
        try {
            coroutineScope {
                val reachedCap = CompletableDeferred<Unit>()
                val regionFilter = regionPacketFilter(regionTopic)
                if (!attempt(Failure.CONNECT, connectDeadline) { client.connect() }) {
                    return@coroutineScope
                }
                _state.value = State(phase = Phase.Subscribing)
                // Collect before subscribing so nothing that arrives with the SUBACK is lost.
                val collector = launch {
                    client.messages.collect { (topic, payload) ->
                        when (record(topic, payload, regionTopic)) {
                            RecordResult.REGION_CAP ->
                                launch { quietly("unsubscribe") { client.unsubscribe(regionFilter) } }

                            RecordResult.MESSAGE_CAP -> reachedCap.complete(Unit)

                            RecordResult.RECORDED -> Unit
                        }
                    }
                }
                var granted = emptyList<String>()
                val subscribed =
                    attempt(Failure.CONNECT, subscribeDeadline) {
                        granted = client.subscribe(filters(regionTopic))
                    }
                if (!subscribed || granted.isEmpty()) {
                    if (subscribed) _state.value = State(phase = Phase.Failed(Failure.FILTERS_REFUSED))
                    collector.cancel()
                    return@coroutineScope
                }
                Logger.i { "MQTT topic sample subscribed: ${granted.size} of ${filters(regionTopic).size} filters" }
                val disconnected = launch {
                    client.connectionState.first { it is ConnectionState.Disconnected }
                    reachedCap.complete(Unit)
                }
                val end = timeSource.markNow() + duration
                while (!reachedCap.isCompleted && end.hasNotPassedNow()) {
                    publish(Phase.Sampling(secondsRemaining = (-end.elapsedNow()).inWholeSeconds.toInt() + 1))
                    select {
                        reachedCap.onAwait {}
                        onTimeout(TICK) {}
                    }
                }
                disconnected.cancel()
                collector.cancel()
                val closedEarly = client.connectionState.value is ConnectionState.Disconnected && end.hasNotPassedNow()
                if (closedEarly && lock.withLock { messageCount } == 0) {
                    _state.value = State(phase = Phase.Failed(Failure.CLOSED))
                } else {
                    publish(Phase.Finished)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Logger.w { "MQTT topic sample: unexpected (${e::class.simpleName})" }
            _state.value = State(phase = Phase.Failed(Failure.ERROR))
        } finally {
            withContext(NonCancellable) {
                if (_state.value.isRunning) publish(Phase.Finished)
                quietly("close") { client.close() }
            }
            Logger.i { "MQTT topic sample ended: ${_state.value.phase::class.simpleName}" }
        }
    }

    /** Runs one broker step under its deadline; a timeout or broker error fails the sample with [failure]. */
    private suspend fun attempt(failure: Failure, deadline: Duration, block: suspend () -> Unit): Boolean {
        val done = quietly(failure.name.lowercase()) { withTimeoutOrNull(deadline) { block() } != null } == true
        if (!done) _state.value = State(phase = Phase.Failed(failure))
        return done
    }

    /** Null when the broker or socket fails; cancellation still propagates. */
    private suspend fun <T> quietly(step: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        // An IllegalStateException too, so it must be rethrown before that catch.
        throw e
    } catch (e: MqttException) {
        Logger.w { "MQTT topic sample: $step (${e.reasonCode})" }
        null
    } catch (e: IOException) {
        Logger.w { "MQTT topic sample: $step (${e::class.simpleName})" }
        null
    } catch (e: IllegalStateException) {
        Logger.w { "MQTT topic sample: $step (${e::class.simpleName})" }
        null
    }

    private enum class RecordResult {
        RECORDED,
        REGION_CAP,
        MESSAGE_CAP,
    }

    private suspend fun record(topic: String, payload: ByteArray, regionTopic: String): RecordResult = lock.withLock {
        messageCount++
        val observation = tally.record(topic, payload)
        when {
            messageCount >= MESSAGE_CAP -> RecordResult.MESSAGE_CAP

            observation?.root == regionTopic && observation.kind is MqttTopicDiscovery.Kind.Packet -> {
                regionPackets++
                if (regionPackets == REGION_PACKET_CAP) {
                    tally.markTruncated(regionTopic)
                    RecordResult.REGION_CAP
                } else {
                    RecordResult.RECORDED
                }
            }

            else -> RecordResult.RECORDED
        }
    }

    private suspend fun publish(phase: Phase) {
        val (activity, count) = lock.withLock { tally.snapshot() to messageCount }
        _state.value = State(phase = phase, activity = activity, messageCount = count)
    }

    companion object {
        val DEFAULT_DURATION: Duration = 60.seconds
        private val TICK = 1.seconds

        const val MESSAGE_CAP = 20_000

        /** The region's own `/2/e/` stream (about 100 msg/s on msh/US) is unsubscribed after this many packets. */
        const val REGION_PACKET_CAP = 300

        /**
         * Eight filters in one SUBSCRIBE: the region's own map and packet topics, then `2/map` and `2/e` one to three
         * levels below it. Never "#": the public broker drops the whole connection for it.
         */
        fun filters(regionTopic: String): List<String> {
            val levels = listOf("", "/+", "/+/+", "/+/+/+")
            return levels.map { "$regionTopic$it/2/map/" } + levels.map { "$regionTopic$it/2/e/+/+" }
        }

        fun regionPacketFilter(regionTopic: String): String = "$regionTopic/2/e/+/+"
    }
}

private const val DEFAULT_SERVER_ADDRESS = "mqtt.meshtastic.org"

private fun defaultSamplerClient(config: ModuleConfig.MQTTConfig?): TopicSamplerClient {
    val rawAddress = config?.address?.ifEmpty { DEFAULT_SERVER_ADDRESS } ?: DEFAULT_SERVER_ADDRESS
    val endpoint = resolveEndpoint(rawAddress, effectiveTlsEnabled(rawAddress, config?.tls_enabled == true))
    val client =
        MqttClient("MeshtasticAndroidTopicFinder-${Uuid.random()}") {
            val tls = mqttTlsConfig()
            transportFactory = TcpTransportFactory(tls) + WebSocketTransportFactory(tls)
            keepAliveSeconds = MQTT_KEEPALIVE_SECONDS
            autoReconnect = false
            val (user, pass) = effectiveCredentials(config)
            username = user
            pass?.let { password(it) }
            logger = KermitMqttLogger()
            // WARN: the library logs topic strings at INFO, and every topic here is location-derived.
            logLevel = MqttLogLevel.WARN
        }
    return object : TopicSamplerClient {
        override val messages: Flow<Pair<String, ByteArray>> = flow {
            client.messages.collect { emit(it.topic to it.payload.toByteArray()) }
        }
        override val connectionState = client.connectionState

        override suspend fun connect() = client.connect(endpoint)

        override suspend fun subscribe(filters: List<String>): List<String> = try {
            client.subscribe(filters.map { Subscription(topicFilter = it, maxQos = QoS.AT_MOST_ONCE) })
            filters
        } catch (e: MqttException.SubscriptionRefused) {
            e.granted
        }

        override suspend fun unsubscribe(filter: String) = client.unsubscribe(filter)

        override suspend fun close() = client.close()
    }
}
