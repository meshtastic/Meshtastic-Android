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

import dev.mokkery.mock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.common.di.asServiceScope
import org.meshtastic.core.model.MqttConnectionState
import org.meshtastic.core.network.repository.MQTTRepository
import org.meshtastic.core.repository.PacketHandler
import org.meshtastic.core.repository.ServiceStateWriter
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.mqtt.ConnectionState
import org.meshtastic.mqtt.MqttException
import org.meshtastic.mqtt.ReasonCode
import org.meshtastic.proto.MqttClientProxyMessage
import kotlin.test.Test
import kotlin.test.assertEquals

class MqttManagerImplTest {

    @Test
    fun `a connection with every filter granted reads as connected`() = runTest {
        val harness = createHarness()
        harness.manager.startProxy(enabled = true, proxyToClientEnabled = true)
        harness.repository.connectionState.value = ConnectionState.Connected
        runCurrent()

        assertEquals(MqttConnectionState.Connected, harness.manager.mqttConnectionState.value)
    }

    @Test
    fun `a partly refused subscription names the refused filters and how many were granted`() = runTest {
        val harness = createHarness()
        harness.manager.startProxy(enabled = true, proxyToClientEnabled = true)
        harness.repository.connectionState.value = ConnectionState.Connected
        harness.repository.subscriptionRefusal.value =
            refusal(refused = mapOf("msh/2/e/alpha/+" to ReasonCode.NOT_AUTHORIZED), granted = listOf("msh/2/e/PKI/+"))
        runCurrent()

        assertEquals(
            MqttConnectionState.SubscriptionRefused(
                refused = mapOf("msh/2/e/alpha/+" to "NOT_AUTHORIZED"),
                granted = 1,
            ),
            harness.manager.mqttConnectionState.value,
        )
    }

    @Test
    fun `a subscription refused in full reads as zero granted rather than connected`() = runTest {
        val harness = createHarness()
        harness.manager.startProxy(enabled = true, proxyToClientEnabled = true)
        harness.repository.connectionState.value = ConnectionState.Connected
        harness.repository.subscriptionRefusal.value =
            refusal(
                refused =
                mapOf("msh/2/e/alpha/+" to ReasonCode.NOT_AUTHORIZED, "msh/2/e/PKI/+" to ReasonCode.QUOTA_EXCEEDED),
                granted = emptyList(),
            )
        runCurrent()

        val state = harness.manager.mqttConnectionState.value
        assertEquals(
            MqttConnectionState.SubscriptionRefused(
                refused = mapOf("msh/2/e/alpha/+" to "NOT_AUTHORIZED", "msh/2/e/PKI/+" to "QUOTA_EXCEEDED"),
                granted = 0,
            ),
            state,
        )
    }

    @Test
    fun `a refusal is only reported while the connection is up`() = runTest {
        val harness = createHarness()
        harness.manager.startProxy(enabled = true, proxyToClientEnabled = true)
        harness.repository.subscriptionRefusal.value =
            refusal(refused = mapOf("msh/2/e/alpha/+" to ReasonCode.NOT_AUTHORIZED), granted = emptyList())
        harness.repository.connectionState.value = ConnectionState.Reconnecting(attempt = 1, lastError = null)
        runCurrent()

        assertEquals(MqttConnectionState.Reconnecting(attempt = 1), harness.manager.mqttConnectionState.value)
    }

    private fun refusal(refused: Map<String, ReasonCode>, granted: List<String>) = MqttException.SubscriptionRefused(
        reasonCode = refused.values.first(),
        message = "refused",
        refused = refused,
        granted = granted,
    )

    private fun TestScope.createHarness(): Harness {
        val repository = FakeMqttRepository()
        val manager =
            MqttManagerImpl(
                mqttRepository = repository,
                packetHandler = mock<PacketHandler>(),
                serviceStateWriter = mock<ServiceStateWriter>(),
                nodeRepository = FakeNodeRepository(),
                scope = CoroutineScope(StandardTestDispatcher(testScheduler)).asServiceScope(),
            )
        return Harness(manager, repository)
    }

    private class Harness(val manager: MqttManagerImpl, val repository: FakeMqttRepository)

    private class FakeMqttRepository : MQTTRepository {
        override val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected.Idle)
        override val subscriptionRefusal = MutableStateFlow<MqttException.SubscriptionRefused?>(null)
        override val proxyMessageFlow: Flow<MqttClientProxyMessage> = emptyFlow()

        override fun disconnect() {}

        override fun publish(topic: String, data: ByteArray, retained: Boolean) {}
    }
}
