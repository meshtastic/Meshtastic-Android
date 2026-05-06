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
package org.meshtastic.core.data.manager

import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode.Companion.exactly
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.MqttConnectionState
import org.meshtastic.core.network.repository.MQTTRepository
import org.meshtastic.core.repository.PacketHandler
import org.meshtastic.core.testing.FakeServiceRepository
import org.meshtastic.mqtt.ConnectionState
import org.meshtastic.mqtt.MqttException
import org.meshtastic.mqtt.ReasonCode
import org.meshtastic.proto.MqttClientProxyMessage
import org.meshtastic.proto.ToRadio
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MqttManagerImplTest {

    private data class PublishCall(
        val topic: String,
        val data: ByteArray,
        val retained: Boolean,
    )

    private lateinit var mqttRepository: MQTTRepository
    private lateinit var packetHandler: PacketHandler
    private lateinit var serviceRepository: FakeServiceRepository
    private lateinit var serviceScope: TestScope
    private lateinit var connectionStateFlow: MutableStateFlow<ConnectionState>
    private lateinit var proxyMessageFlow: MutableSharedFlow<MqttClientProxyMessage>
    private lateinit var mqttManager: MqttManagerImpl

    private val publishCalls = mutableListOf<PublishCall>()

    @BeforeTest
    fun setUp() {
        serviceScope = TestScope(UnconfinedTestDispatcher())
        connectionStateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected.Idle)
        proxyMessageFlow = MutableSharedFlow(extraBufferCapacity = 1)
        mqttRepository = mock(MockMode.autofill)
        packetHandler = mock(MockMode.autofill)
        serviceRepository = FakeServiceRepository()
        publishCalls.clear()

        every { mqttRepository.connectionState } returns connectionStateFlow
        every { mqttRepository.proxyMessageFlow } returns proxyMessageFlow
        every { mqttRepository.publish(any(), any(), any()) } calls { args ->
            publishCalls +=
                PublishCall(
                    topic = args.arg(0),
                    data = args.arg(1),
                    retained = args.arg(2),
                )
        }
        every { packetHandler.sendToRadio(any<ToRadio>()) } returns Unit

        mqttManager = MqttManagerImpl(mqttRepository, packetHandler, serviceRepository, serviceScope)
    }

    @AfterTest
    fun tearDown() {
        serviceScope.cancel()
    }

    @Test
    fun mqttConnectionState_whenInactive_emitsInactive() = runTest {
        connectionStateFlow.value = ConnectionState.Connected

        assertEquals(MqttConnectionState.Inactive, mqttManager.mqttConnectionState.value)
    }

    @Test
    fun mqttConnectionState_whenActive_mapsConnecting() = runTest {
        connectionStateFlow.value = ConnectionState.Connecting

        mqttManager.startProxy(enabled = true, proxyToClientEnabled = true)

        assertEquals(MqttConnectionState.Connecting, mqttManager.mqttConnectionState.value)
    }

    @Test
    fun mqttConnectionState_whenActive_mapsConnected() = runTest {
        connectionStateFlow.value = ConnectionState.Connected

        mqttManager.startProxy(enabled = true, proxyToClientEnabled = true)

        assertEquals(MqttConnectionState.Connected, mqttManager.mqttConnectionState.value)
    }

    @Test
    fun mqttConnectionState_whenActive_mapsReconnecting() = runTest {
        val error = MqttException.ConnectionLost(ReasonCode.SERVER_UNAVAILABLE, "network down")
        connectionStateFlow.value = ConnectionState.Reconnecting(attempt = 3, lastError = error)

        mqttManager.startProxy(enabled = true, proxyToClientEnabled = true)

        assertEquals(
            MqttConnectionState.Reconnecting(attempt = 3, lastError = "network down"),
            mqttManager.mqttConnectionState.value,
        )
    }

    @Test
    fun mqttConnectionState_whenActive_mapsDisconnectedWithReason() = runTest {
        val reason = MqttException.ConnectionLost(ReasonCode.KEEP_ALIVE_TIMEOUT, "timed out")
        connectionStateFlow.value = ConnectionState.Disconnected(reason = reason)

        mqttManager.startProxy(enabled = true, proxyToClientEnabled = true)

        assertEquals(
            MqttConnectionState.Disconnected(reason = "timed out"),
            mqttManager.mqttConnectionState.value,
        )
    }

    @Test
    fun mqttConnectionState_whenActive_mapsDisconnectedIdle() = runTest {
        connectionStateFlow.value = ConnectionState.Disconnected.Idle

        mqttManager.startProxy(enabled = true, proxyToClientEnabled = true)

        assertEquals(MqttConnectionState.Disconnected.Idle, mqttManager.mqttConnectionState.value)
    }

    @Test
    fun startProxy_whenAlreadyRunning_doesNotDuplicate() = runTest {
        val message = MqttClientProxyMessage(topic = "msh/test", text = "hello")

        mqttManager.startProxy(enabled = true, proxyToClientEnabled = true)
        mqttManager.startProxy(enabled = true, proxyToClientEnabled = true)
        proxyMessageFlow.emit(message)

        verify(exactly(1)) { packetHandler.sendToRadio(ToRadio(mqttClientProxyMessage = message)) }
    }

    @Test
    fun startProxy_whenNotEnabled_doesNotStart() = runTest {
        val message = MqttClientProxyMessage(topic = "msh/test", text = "hello")
        connectionStateFlow.value = ConnectionState.Connected

        mqttManager.startProxy(enabled = false, proxyToClientEnabled = true)
        assertTrue(proxyMessageFlow.tryEmit(message))

        assertEquals(MqttConnectionState.Inactive, mqttManager.mqttConnectionState.value)
        verify(exactly(0)) { packetHandler.sendToRadio(any<ToRadio>()) }
    }

    @Test
    fun startProxy_collectsProxyMessages_sendsToRadio() = runTest {
        val message = MqttClientProxyMessage(topic = "msh/test", text = "hello")

        mqttManager.startProxy(enabled = true, proxyToClientEnabled = true)
        proxyMessageFlow.emit(message)

        verify(exactly(1)) { packetHandler.sendToRadio(ToRadio(mqttClientProxyMessage = message)) }
    }

    @Test
    fun startProxy_onConnectionRejected_setsErrorMessage() = runTest {
        every { mqttRepository.proxyMessageFlow } returns
            flow {
                throw MqttException.ConnectionRejected(
                    reasonCode = ReasonCode.NOT_AUTHORIZED,
                    message = "bad credentials",
                )
            }

        mqttManager.startProxy(enabled = true, proxyToClientEnabled = true)

        assertEquals(
            "MQTT: connection rejected (check credentials)",
            serviceRepository.errorMessage.value,
        )
        assertEquals(MqttConnectionState.Inactive, mqttManager.mqttConnectionState.value)
    }

    @Test
    fun stop_cancelsJobAndSetsInactive() = runTest {
        val message = MqttClientProxyMessage(topic = "msh/test", text = "hello")

        mqttManager.startProxy(enabled = true, proxyToClientEnabled = true)
        mqttManager.stop()
        assertTrue(proxyMessageFlow.tryEmit(message))

        assertEquals(MqttConnectionState.Inactive, mqttManager.mqttConnectionState.value)
        verify(exactly(0)) { packetHandler.sendToRadio(any<ToRadio>()) }
    }

    @Test
    fun handleMqttProxyMessage_withText_publishesText() = runTest {
        val message = MqttClientProxyMessage(topic = "msh/json/test", text = "hello world", retained = true)

        mqttManager.handleMqttProxyMessage(message)

        assertEquals(1, publishCalls.size)
        assertEquals("msh/json/test", publishCalls.single().topic)
        assertTrue("hello world".encodeToByteArray().contentEquals(publishCalls.single().data))
        assertEquals(true, publishCalls.single().retained)
        verify(exactly(1)) { mqttRepository.publish(any(), any(), any()) }
    }

    @Test
    fun handleMqttProxyMessage_withData_publishesData() = runTest {
        val payload = byteArrayOf(1, 2, 3, 4)
        val message = MqttClientProxyMessage(topic = "msh/data/test", data_ = payload.toByteString(), retained = false)

        mqttManager.handleMqttProxyMessage(message)

        assertEquals(1, publishCalls.size)
        assertEquals("msh/data/test", publishCalls.single().topic)
        assertTrue(payload.contentEquals(publishCalls.single().data))
        assertEquals(false, publishCalls.single().retained)
        verify(exactly(1)) { mqttRepository.publish(any(), any(), any()) }
    }

    @Test
    fun handleMqttProxyMessage_withNeither_doesNotPublish() = runTest {
        mqttManager.handleMqttProxyMessage(MqttClientProxyMessage(topic = "msh/empty/test"))

        assertTrue(publishCalls.isEmpty())
        verify(exactly(0)) { mqttRepository.publish(any(), any(), any()) }
    }
}
