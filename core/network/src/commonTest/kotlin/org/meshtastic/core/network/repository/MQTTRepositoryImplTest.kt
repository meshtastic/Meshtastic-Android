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

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.common.BuildConfigProvider
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.MqttJsonPayload
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioConfigRepository
import org.meshtastic.mqtt.ConnectionState
import org.meshtastic.mqtt.MqttEndpoint
import org.meshtastic.mqtt.MqttException
import org.meshtastic.mqtt.MqttLogLevel
import org.meshtastic.mqtt.MqttMessage
import org.meshtastic.mqtt.QoS
import org.meshtastic.mqtt.ReasonCode
import org.meshtastic.mqtt.packet.Subscription
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Data
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.ServiceEnvelope
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MQTTRepositoryImplTest {

    private val buildConfigProvider: BuildConfigProvider = mock(MockMode.autofill)

    @BeforeTest
    fun setUp() {
        every { buildConfigProvider.isDebug } returns true
    }

    // region resolveEndpoint — every behavioral branch of address parsing.

    @Test
    fun `bare host without scheme is wrapped as plain Tcp on the standard MQTT port`() {
        val endpoint = resolveEndpoint(rawAddress = "broker.example.com", tlsEnabled = false)

        val tcp = assertIs<MqttEndpoint.Tcp>(endpoint)
        assertEquals("broker.example.com", tcp.host)
        assertEquals(1883, tcp.port)
        assertEquals(false, tcp.tls)
    }

    @Test
    fun `bare host with TLS enabled is wrapped as Tcp on the secure MQTT port`() {
        val endpoint = resolveEndpoint(rawAddress = "broker.example.com", tlsEnabled = true)

        val tcp = assertIs<MqttEndpoint.Tcp>(endpoint)
        assertEquals("broker.example.com", tcp.host)
        assertEquals(8883, tcp.port)
        assertEquals(true, tcp.tls)
    }

    @Test
    fun `host with explicit port is preserved when wrapped as Tcp`() {
        val endpoint = resolveEndpoint(rawAddress = "broker.example.com:9001", tlsEnabled = false)

        val tcp = assertIs<MqttEndpoint.Tcp>(endpoint)
        assertEquals("broker.example.com", tcp.host)
        assertEquals(9001, tcp.port)
        assertEquals(false, tcp.tls)
    }

    @Test
    fun `address with ws scheme is parsed as-is and tls flag is ignored`() {
        val endpoint = resolveEndpoint(rawAddress = "ws://broker.example.com:8080/custom-path", tlsEnabled = true)

        val ws = assertIs<MqttEndpoint.WebSocket>(endpoint)
        assertEquals("ws://broker.example.com:8080/custom-path", ws.url)
    }

    @Test
    fun `address with wss scheme is parsed as-is`() {
        val endpoint = resolveEndpoint(rawAddress = "wss://broker.example.com/secure-mqtt", tlsEnabled = false)

        val ws = assertIs<MqttEndpoint.WebSocket>(endpoint)
        assertEquals("wss://broker.example.com/secure-mqtt", ws.url)
    }

    @Test
    fun `address with mqtt tcp scheme is parsed as Tcp endpoint`() {
        val endpoint = resolveEndpoint(rawAddress = "mqtt://broker.example.com:1883", tlsEnabled = false)

        val tcp = assertIs<MqttEndpoint.Tcp>(endpoint)
        assertEquals("broker.example.com", tcp.host)
        assertEquals(1883, tcp.port)
        assertEquals(false, tcp.tls)
    }

    @Test
    fun `address with mqtts tcp scheme is parsed as Tcp endpoint with tls true`() {
        val endpoint = resolveEndpoint(rawAddress = "mqtts://broker.example.com:8883", tlsEnabled = false)

        val tcp = assertIs<MqttEndpoint.Tcp>(endpoint)
        assertEquals("broker.example.com", tcp.host)
        assertEquals(8883, tcp.port)
        assertEquals(true, tcp.tls)
    }

    // endregion

    // region effectiveTlsEnabled — TLS enforcement policy for the public server.

    @Test
    fun `default server forces TLS even when tlsEnabled is false`() {
        assertEquals(true, effectiveTlsEnabled("mqtt.meshtastic.org", tlsEnabled = false))
    }

    @Test
    fun `default server case-insensitive match forces TLS`() {
        assertEquals(true, effectiveTlsEnabled("MQTT.MESHTASTIC.ORG", tlsEnabled = false))
    }

    @Test
    fun `default server with explicit port still forces TLS`() {
        assertEquals(true, effectiveTlsEnabled("mqtt.meshtastic.org:1883", tlsEnabled = false))
    }

    @Test
    fun `default server with tcp scheme still forces TLS`() {
        assertEquals(true, effectiveTlsEnabled("tcp://mqtt.meshtastic.org:1883", tlsEnabled = false))
    }

    @Test
    fun `default server with ssl scheme still forces TLS`() {
        assertEquals(true, effectiveTlsEnabled("ssl://mqtt.meshtastic.org", tlsEnabled = false))
    }

    @Test
    fun `custom server respects tlsEnabled false`() {
        assertEquals(false, effectiveTlsEnabled("mqtt.myserver.pt", tlsEnabled = false))
    }

    @Test
    fun `custom server respects tlsEnabled true`() {
        assertEquals(true, effectiveTlsEnabled("mqtt.myserver.pt", tlsEnabled = true))
    }

    // endregion

    // region extractHost — address canonicalization tests.

    @Test
    fun `extractHost bare hostname`() {
        assertEquals("mqtt.meshtastic.org", extractHost("mqtt.meshtastic.org"))
    }

    @Test
    fun `extractHost with port`() {
        assertEquals("mqtt.meshtastic.org", extractHost("mqtt.meshtastic.org:8883"))
    }

    @Test
    fun `extractHost with tcp scheme and port`() {
        assertEquals("mqtt.meshtastic.org", extractHost("tcp://mqtt.meshtastic.org:1883"))
    }

    @Test
    fun `extractHost with ssl scheme no port`() {
        assertEquals("mqtt.meshtastic.org", extractHost("ssl://mqtt.meshtastic.org"))
    }

    @Test
    fun `extractHost with path`() {
        assertEquals("broker.example.com", extractHost("ws://broker.example.com:8080/mqtt"))
    }

    // endregion

    @Test
    fun `topic patterns are built from enabled channels with json topics and PKI`() = runTest {
        val radioConfigRepository =
            FakeRadioConfigRepository().apply {
                setChannelSet(
                    ChannelSet(
                        settings =
                        listOf(
                            ChannelSettings(
                                name = "alpha",
                                downlink_enabled = true,
                                psk = byteArrayOf(1).toByteString(),
                            ),
                            ChannelSettings(
                                name = "beta",
                                downlink_enabled = false,
                                psk = byteArrayOf(2).toByteString(),
                            ),
                            ChannelSettings(
                                name = "gamma",
                                downlink_enabled = true,
                                psk = byteArrayOf(3).toByteString(),
                            ),
                        ),
                    ),
                )
                setLocalModuleConfigDirect(
                    LocalModuleConfig(mqtt = ModuleConfig.MQTTConfig(root = "custom", json_enabled = true)),
                )
            }
        val harness = createHarness(radioConfigRepository = radioConfigRepository)

        val collector = startProxyCollection(harness.repository)
        runCurrent()

        val subscriptions = harness.client.subscribeCalls.single()
        assertEquals(
            listOf(
                "custom/2/e/alpha/+",
                "custom/2/json/alpha/+",
                "custom/2/e/gamma/+",
                "custom/2/json/gamma/+",
                "custom/2/e/PKI/+",
            ),
            subscriptions.map { it.topicFilter },
        )
        assertTrue(subscriptions.all { it.maxQos == QoS.AT_LEAST_ONCE && it.noLocal })
        assertTrue(
            harness.setups.single().ownerId.startsWith("MeshtasticAndroidMqttProxy-!12345678-"),
            "ownerId should be the node-scoped prefix plus a unique per-connection suffix, " +
                "was: ${harness.setups.single().ownerId}",
        )
        assertEquals(MqttLogLevel.DEBUG, harness.setups.single().logLevel)

        collector.cancelAndJoin()
        runCurrent()
        assertEquals(1, harness.client.closeCalls)
    }

    @Test
    fun `json mqtt messages are decoded into text proxy messages`() = runTest {
        val harness =
            createHarness(
                radioConfigRepository =
                FakeRadioConfigRepository().apply {
                    setLocalModuleConfigDirect(
                        LocalModuleConfig(mqtt = ModuleConfig.MQTTConfig(json_enabled = true)),
                    )
                },
            )
        val jsonPayload = """{"type":"text","from":1,"to":2,"payload":"hello","hop_limit":3,"id":4,"time":5}"""

        val nextMessage = backgroundScope.async { harness.repository.proxyMessageFlow.first() }
        runCurrent()
        harness.client.emitMessage(
            MqttMessage(topic = "msh/2/json/alpha/node", payload = jsonPayload.encodeToByteArray(), retain = true),
        )

        val proxyMessage = nextMessage.await()
        assertEquals("msh/2/json/alpha/node", proxyMessage.topic)
        assertEquals(jsonPayload, proxyMessage.text)
        assertEquals(true, proxyMessage.retained)
        assertNull(proxyMessage.data_)
    }

    @Test
    fun `protobuf mqtt messages are decoded into binary proxy messages`() = runTest {
        val harness = createHarness()
        val payload = byteArrayOf(0x01, 0x23, 0x45)

        val nextMessage = backgroundScope.async { harness.repository.proxyMessageFlow.first() }
        runCurrent()
        harness.client.emitMessage(MqttMessage(topic = "msh/2/e/alpha/node", payload = payload, retain = false))

        val proxyMessage = nextMessage.await()
        assertEquals("msh/2/e/alpha/node", proxyMessage.topic)
        assertContentEquals(payload, proxyMessage.data_?.toByteArray())
        assertEquals(false, proxyMessage.retained)
        assertNull(proxyMessage.text)
    }

    @Test
    fun `connect retries after a transient failure and succeeds when the network recovers`() = runTest {
        val harness = createHarness()
        harness.client.failConnectWith(MqttException.ConnectionLost(ReasonCode.UNSPECIFIED_ERROR, "offline"))

        val collector = startProxyCollection(harness.repository)
        runCurrent()
        assertEquals(1, harness.client.connectCalls.size)
        assertEquals(0, harness.client.subscribeCalls.size)

        advanceTimeBy(999)
        runCurrent()
        assertEquals(1, harness.client.connectCalls.size)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, harness.client.connectCalls.size)
        assertEquals(1, harness.client.subscribeCalls.size)

        collector.cancelAndJoin()
        runCurrent()
    }

    @Test
    fun `subscription failures trigger reconnect retry`() = runTest {
        val harness = createHarness()
        harness.client.failSubscribeWith(MqttException.ConnectionLost(ReasonCode.UNSPECIFIED_ERROR, "suback timeout"))

        val collector = startProxyCollection(harness.repository)
        runCurrent()
        assertEquals(1, harness.client.connectCalls.size)
        assertEquals(1, harness.client.subscribeCalls.size)

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(2, harness.client.connectCalls.size)
        assertEquals(2, harness.client.subscribeCalls.size)

        collector.cancelAndJoin()
        runCurrent()
    }

    @Test
    fun `connection state flow reflects repository state updates`() {
        val repository =
            MQTTRepositoryImpl(
                radioConfigRepository = FakeRadioConfigRepository(),
                nodeRepository = FakeNodeRepository().apply { setMyId("!12345678") },
                buildConfigProvider = buildConfigProvider,
                dispatchers =
                CoroutineDispatchers(
                    io = Dispatchers.Default,
                    main = Dispatchers.Default,
                    default = Dispatchers.Default,
                ),
                mqttClientFactory = { FakeMqttClientSession() },
            )
        val disconnectError = MqttException.ConnectionLost(ReasonCode.UNSPECIFIED_ERROR, "link lost")

        assertEquals(ConnectionState.Disconnected.Idle, repository.connectionState.value)

        repository.updateConnectionState(ConnectionState.Connecting)
        assertEquals(ConnectionState.Connecting, repository.connectionState.value)

        repository.updateConnectionState(ConnectionState.Connected)
        assertEquals(ConnectionState.Connected, repository.connectionState.value)

        repository.updateConnectionState(ConnectionState.Reconnecting(attempt = 2, lastError = disconnectError))
        val reconnecting = assertIs<ConnectionState.Reconnecting>(repository.connectionState.value)
        assertEquals(2, reconnecting.attempt)
        assertEquals("link lost", reconnecting.lastError?.message)

        repository.updateConnectionState(ConnectionState.Disconnected(reason = disconnectError))
        val disconnected = assertIs<ConnectionState.Disconnected>(repository.connectionState.value)
        assertEquals("link lost", disconnected.reason?.message)
    }

    // region MqttJsonPayload — keep the existing JSON contract tests.

    @Test
    fun `test json payload parsing`() {
        val jsonStr =
            """{"type":"text","from":12345678,"to":4294967295,"payload":"Hello World","hop_limit":3,"id":123,"time":1600000000}"""
        val json = Json { ignoreUnknownKeys = true }
        val payload = json.decodeFromString<MqttJsonPayload>(jsonStr)

        assertEquals("text", payload.type)
        assertEquals(12345678L, payload.from)
        assertEquals(4294967295L, payload.to)
        assertEquals("Hello World", payload.payload)
        assertEquals(3, payload.hopLimit)
        assertEquals(123L, payload.id)
        assertEquals(1600000000L, payload.time)
    }

    @Test
    fun `test json payload serialization`() {
        val payload =
            MqttJsonPayload(
                type = "text",
                from = 12345678,
                to = 4294967295,
                payload = "Hello World",
                hopLimit = 3,
                id = 123,
                time = 1600000000,
            )
        val json = Json { ignoreUnknownKeys = true }
        val jsonStr = json.encodeToString(MqttJsonPayload.serializer(), payload)

        assertTrue(jsonStr.contains("\"type\":\"text\""))
        assertTrue(jsonStr.contains("\"from\":12345678"))
        assertTrue(jsonStr.contains("\"payload\":\"Hello World\""))
    }

    // endregion

    // region isUndeliverableDownlink — Tier 1 drop filter for MQTT client-proxy downlink packets.

    private fun envelopeBytes(
        channelId: String = "LongFast",
        gatewayId: String = "!aabbccdd",
        packet: MeshPacket? = MeshPacket(),
    ): ByteArray =
        ServiceEnvelope.ADAPTER.encode(ServiceEnvelope(packet = packet, channel_id = channelId, gateway_id = gatewayId))

    @Test
    fun `payload-less packet is undeliverable`() {
        // The observed LongFast flood: a packet with neither decoded nor encrypted set.
        val bytes = envelopeBytes(packet = MeshPacket(from = 1, to = 2))
        assertTrue(isUndeliverableDownlink(bytes, myId = "!12345678"))
    }

    @Test
    fun `decoded packet is deliverable`() {
        val bytes = envelopeBytes(packet = MeshPacket(decoded = Data()))
        assertFalse(isUndeliverableDownlink(bytes, myId = "!12345678"))
    }

    @Test
    fun `encrypted packet is deliverable`() {
        val bytes = envelopeBytes(packet = MeshPacket(encrypted = byteArrayOf(1, 2, 3).toByteString()))
        assertFalse(isUndeliverableDownlink(bytes, myId = "!12345678"))
    }

    @Test
    fun `PKI payload-less packet is deliverable (guard)`() {
        val bytes = envelopeBytes(channelId = "PKI", packet = MeshPacket(from = 1))
        assertFalse(isUndeliverableDownlink(bytes, myId = "!12345678"))
    }

    @Test
    fun `own echo payload-less packet is deliverable (guard)`() {
        val bytes = envelopeBytes(gatewayId = "!12345678", packet = MeshPacket(from = 1))
        assertFalse(isUndeliverableDownlink(bytes, myId = "!12345678"))
    }

    @Test
    fun `a different gateway's payload-less packet is undeliverable`() {
        val bytes = envelopeBytes(gatewayId = "!deadbeef", packet = MeshPacket(from = 1))
        assertTrue(isUndeliverableDownlink(bytes, myId = "!12345678"))
    }

    @Test
    fun `null myId does not suppress dropping`() {
        val bytes = envelopeBytes(gatewayId = "!deadbeef", packet = MeshPacket(from = 1))
        assertTrue(isUndeliverableDownlink(bytes, myId = null))
    }

    @Test
    fun `packet-less envelope is deliverable (fail open)`() {
        val bytes = envelopeBytes(packet = null)
        assertFalse(isUndeliverableDownlink(bytes, myId = "!12345678"))
    }

    @Test
    fun `garbage bytes are deliverable (fail open)`() {
        // Non-protobuf bytes must never be dropped.
        val bytes = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x42)
        assertFalse(isUndeliverableDownlink(bytes, myId = "!12345678"))
    }

    @Test
    fun `payload-less downlink stubs are dropped before forwarding`() = runTest {
        val harness = createHarness()
        val stub = envelopeBytes(packet = MeshPacket(from = 1, to = 2)) // no payload → dropped
        val real = envelopeBytes(packet = MeshPacket(decoded = Data())) // has payload → forwarded

        val nextMessage = backgroundScope.async { harness.repository.proxyMessageFlow.first() }
        runCurrent()
        harness.client.emitMessage(MqttMessage(topic = "msh/2/e/alpha/node", payload = stub, retain = false))
        harness.client.emitMessage(MqttMessage(topic = "msh/2/e/alpha/node", payload = real, retain = false))

        // first() returns the first *forwarded* message; the stub was dropped, so it must be `real`.
        val proxyMessage = nextMessage.await()
        assertContentEquals(real, proxyMessage.data_?.toByteArray())
    }

    // endregion

    private fun TestScope.createHarness(
        radioConfigRepository: FakeRadioConfigRepository = defaultRadioConfigRepository(),
        nodeRepository: FakeNodeRepository = FakeNodeRepository().apply { setMyId("!12345678") },
        client: FakeMqttClientSession = FakeMqttClientSession(),
    ): RepositoryHarness {
        val dispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        val setups = mutableListOf<MqttClientSetup>()
        val repository =
            MQTTRepositoryImpl(
                radioConfigRepository = radioConfigRepository,
                nodeRepository = nodeRepository,
                buildConfigProvider = buildConfigProvider,
                dispatchers = CoroutineDispatchers(io = dispatcher, main = dispatcher, default = dispatcher),
                mqttClientFactory = { setup ->
                    setups += setup
                    client
                },
            )
        return RepositoryHarness(repository = repository, client = client, setups = setups)
    }

    private fun TestScope.startProxyCollection(repository: MQTTRepositoryImpl): Job =
        backgroundScope.launch { repository.proxyMessageFlow.collect {} }

    private fun defaultRadioConfigRepository(): FakeRadioConfigRepository = FakeRadioConfigRepository().apply {
        setChannelSet(
            ChannelSet(
                settings =
                listOf(
                    ChannelSettings(
                        name = "alpha",
                        downlink_enabled = true,
                        psk = byteArrayOf(1).toByteString(),
                    ),
                ),
            ),
        )
    }

    private data class RepositoryHarness(
        val repository: MQTTRepositoryImpl,
        val client: FakeMqttClientSession,
        val setups: List<MqttClientSetup>,
    )

    private class FakeMqttClientSession : MqttClientSession {
        private val mutableMessages = MutableSharedFlow<MqttMessage>(extraBufferCapacity = 8)
        override val messages: Flow<MqttMessage> = mutableMessages
        override val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected.Idle)
        val connectCalls = mutableListOf<MqttEndpoint>()
        val subscribeCalls = mutableListOf<List<Subscription>>()
        var closeCalls = 0
            private set

        private val connectFailures = ArrayDeque<Throwable>()
        private val subscribeFailures = ArrayDeque<Throwable>()

        override suspend fun connect(endpoint: MqttEndpoint) {
            connectCalls += endpoint
            if (connectFailures.isNotEmpty()) throw connectFailures.removeFirst()
        }

        override suspend fun subscribe(subscriptions: List<Subscription>) {
            subscribeCalls += subscriptions
            if (subscribeFailures.isNotEmpty()) throw subscribeFailures.removeFirst()
        }

        override suspend fun publish(message: MqttMessage) = Unit

        override suspend fun close() {
            closeCalls += 1
        }

        fun failConnectWith(throwable: Throwable) {
            connectFailures.addLast(throwable)
        }

        fun failSubscribeWith(throwable: Throwable) {
            subscribeFailures.addLast(throwable)
        }

        suspend fun emitMessage(message: MqttMessage) {
            mutableMessages.emit(message)
        }

        fun emitState(state: ConnectionState) {
            connectionState.value = state
        }
    }
}
