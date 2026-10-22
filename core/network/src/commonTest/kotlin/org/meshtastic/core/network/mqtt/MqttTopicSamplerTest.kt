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

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import org.meshtastic.core.network.mqtt.MqttTopicSampler.Failure
import org.meshtastic.core.network.mqtt.MqttTopicSampler.Phase
import org.meshtastic.mqtt.ConnectionState
import org.meshtastic.proto.ModuleConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class MqttTopicSamplerTest {
    private class FakeClient(
        private val granted: (List<String>) -> List<String> = { it },
        private val connectError: Throwable? = null,
        private val subscribeError: Throwable? = null,
    ) : TopicSamplerClient {
        override val messages = MutableSharedFlow<Pair<String, ByteArray>>(extraBufferCapacity = 1_000)
        override val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected.Idle)
        val subscribed = mutableListOf<String>()
        val unsubscribed = mutableListOf<String>()
        var closed = false

        override suspend fun connect() {
            connectError?.let { throw it }
            connectionState.value = ConnectionState.Connected
        }

        override suspend fun subscribe(filters: List<String>): List<String> {
            subscribeError?.let { throw it }
            subscribed += filters
            return granted(filters)
        }

        override suspend fun unsubscribe(filter: String) {
            unsubscribed += filter
        }

        override suspend fun close() {
            closed = true
        }
    }

    private fun TestScope.sampler(client: FakeClient) =
        MqttTopicSampler(clientFactory = { client }, timeSource = testScheduler.timeSource)

    @Test
    fun aFullSampleFinishesWithWhatWasHeard() = runTest {
        val client = FakeClient()
        val sampler = sampler(client)
        val job = launch { sampler.run(null, "msh/US") }
        runCurrent()
        assertEquals(MqttTopicSampler.filters("msh/US"), client.subscribed)
        client.messages.emit("msh/US/WA/2/e/LongFast/!00000001" to ByteArray(0))
        advanceTimeBy(61.seconds)
        job.join()
        assertEquals(Phase.Finished, sampler.state.value.phase)
        assertEquals(setOf("msh/US/WA"), sampler.state.value.activity.keys)
        assertTrue(client.closed)
    }

    @Test
    fun theRegionStreamIsUnsubscribedAtItsCap() = runTest {
        val client = FakeClient()
        val sampler = sampler(client)
        val job = launch { sampler.run(null, "msh/US") }
        runCurrent()
        repeat(MqttTopicSampler.REGION_PACKET_CAP) {
            client.messages.emit("msh/US/2/e/LongFast/!00000001" to ByteArray(0))
        }
        runCurrent()
        assertEquals(listOf("msh/US/2/e/+/+"), client.unsubscribed)
        job.cancel()
        job.join()
        assertTrue(sampler.state.value.activity.getValue("msh/US").truncated)
    }

    @Test
    fun noGrantedFiltersFailsTheSample() = runTest {
        val client = FakeClient(granted = { emptyList() })
        val sampler = sampler(client)
        sampler.run(null, "msh/US")
        assertEquals(Phase.Failed(Failure.FILTERS_REFUSED), sampler.state.value.phase)
    }

    @Test
    fun aConnectErrorFailsTheSample() = runTest {
        val sampler = sampler(FakeClient(connectError = IOException("unreachable")))
        sampler.run(null, "msh/US")
        assertEquals(Phase.Failed(Failure.CONNECT), sampler.state.value.phase)
    }

    @Test
    fun aSubscribeErrorFailsAsConnect() = runTest {
        val client = FakeClient(subscribeError = IOException("reset"))
        val sampler = sampler(client)
        sampler.run(null, "msh/US")
        assertEquals(Phase.Failed(Failure.CONNECT), sampler.state.value.phase)
    }

    @Test
    fun anUnexpectedSubscribeErrorFailsTheSampleWithoutThrowing() = runTest {
        val client = FakeClient(subscribeError = IllegalArgumentException("bad filter"))
        val sampler = sampler(client)
        sampler.run(null, "msh/US")
        assertEquals(Phase.Failed(Failure.ERROR), sampler.state.value.phase)
        assertTrue(client.closed)
    }

    @Test
    fun anUnparseableAddressFailsTheSample() = runTest {
        listOf("broker.local:", "http://broker.local").forEach { address ->
            val sampler = MqttTopicSampler(timeSource = testScheduler.timeSource)
            sampler.run(ModuleConfig.MQTTConfig.Builder().also { it.address = address }.build(), "msh/US")
            assertEquals(Phase.Failed(Failure.CONNECT), sampler.state.value.phase, address)
        }
    }

    @Test
    fun aBrokerCloseBeforeAnyMessageFailsTheSample() = runTest {
        val client = FakeClient()
        val sampler = sampler(client)
        val job = launch { sampler.run(null, "msh/US") }
        runCurrent()
        client.connectionState.value = ConnectionState.Disconnected.Idle
        advanceTimeBy(2.seconds)
        job.join()
        assertEquals(Phase.Failed(Failure.CLOSED), sampler.state.value.phase)
    }

    @Test
    fun cancellingKeepsTheResults() = runTest {
        val client = FakeClient()
        val sampler = sampler(client)
        val job = launch { sampler.run(null, "msh/US") }
        runCurrent()
        client.messages.emit("msh/US/TX/2/map/" to ByteArray(0))
        runCurrent()
        job.cancel()
        job.join()
        assertEquals(Phase.Finished, sampler.state.value.phase)
        assertEquals(setOf("msh/US/TX"), sampler.state.value.activity.keys)
        assertTrue(client.closed)
    }
}
