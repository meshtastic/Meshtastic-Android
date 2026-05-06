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
package org.meshtastic.core.data.radio

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.ByteString as KByteString
import org.meshtastic.core.model.CongestionLevel
import org.meshtastic.core.testing.FakeServiceRepository
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.FromRadio
import org.meshtastic.sdk.CongestionMetrics
import org.meshtastic.sdk.DroppedFlow
import org.meshtastic.sdk.Frame
import org.meshtastic.sdk.MeshEvent
import org.meshtastic.sdk.RadioClient
import org.meshtastic.sdk.TransportIdentity
import org.meshtastic.sdk.testing.FakeRadioTransport
import org.meshtastic.sdk.testing.InMemoryStorageProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class SdkEventBridgeTest {

    @Test
    fun `device rebooted event emits notification`() = runTest {
        val serviceRepository = FakeServiceRepository()
        val bridge = SdkEventBridge(serviceRepository)
        val (transport, client) = connectedClient()

        bridge.observe(TestRadioClientAccessor(client), backgroundScope)
        client.connect()
        runCurrent()

        transport.injectFrame(encodeFromRadio(FromRadio(rebooted = true)))
        runCurrent()

        assertEquals("Device rebooted", serviceRepository.clientNotification.value?.message)
        client.disconnect()
    }

    @Test
    fun `congestion warning sets congestion level`() = runTest {
        val serviceRepository = FakeServiceRepository()
        val bridge = SdkEventBridge(serviceRepository)

        bridge.handleEvent(MeshEvent.CongestionWarning(CongestionMetrics(airUtilTx = 80f, channelUtil = 30f)))

        assertEquals(CongestionLevel.CRITICAL, serviceRepository.congestionLevel.value)
    }

    @Test
    fun `duplicated public key warning is logged without changing service state`() = runTest {
        val serviceRepository = FakeServiceRepository().apply {
            setClientNotification(ClientNotification(message = "existing"))
            setCongestionLevel(CongestionLevel.HIGH)
        }
        val bridge = SdkEventBridge(serviceRepository)

        bridge.handleEvent(MeshEvent.SecurityWarning.DuplicatedPublicKey)

        assertEquals("existing", serviceRepository.clientNotification.value?.message)
        assertEquals(CongestionLevel.HIGH, serviceRepository.congestionLevel.value)
    }

    @Test
    fun `low entropy key warning is logged without changing service state`() = runTest {
        val serviceRepository = FakeServiceRepository().apply {
            setCongestionLevel(CongestionLevel.MEDIUM)
        }
        val bridge = SdkEventBridge(serviceRepository)

        bridge.handleEvent(MeshEvent.SecurityWarning.LowEntropyKey)

        assertNull(serviceRepository.clientNotification.value)
        assertEquals(CongestionLevel.MEDIUM, serviceRepository.congestionLevel.value)
    }

    @Test
    fun `packets dropped event is handled without crashing`() = runTest {
        val serviceRepository = FakeServiceRepository().apply {
            setClientNotification(ClientNotification(message = "keep"))
        }
        val bridge = SdkEventBridge(serviceRepository)

        bridge.handleEvent(MeshEvent.PacketsDropped(flow = DroppedFlow.Events, count = 4))

        assertEquals("keep", serviceRepository.clientNotification.value?.message)
    }

    private fun TestScope.connectedClient(): Pair<FakeRadioTransport, RadioClient> {
        val transport = FakeRadioTransport(identity = TransportIdentity("fake:event-bridge"), autoHandshake = true)
        val client =
            RadioClient.Builder()
                .transport(transport)
                .storage(InMemoryStorageProvider())
                .coroutineContext(backgroundScope.coroutineContext)
                .autoSyncTimeOnConnect(false)
                .build()
        return transport to client
    }

    private fun encodeFromRadio(fromRadio: FromRadio): Frame {
        val proto = FromRadio.ADAPTER.encode(fromRadio)
        val frameBytes = ByteArray(4 + proto.size).apply {
            this[0] = 0x94.toByte()
            this[1] = 0xC3.toByte()
            this[2] = (proto.size shr 8).toByte()
            this[3] = (proto.size and 0xFF).toByte()
            proto.copyInto(this, destinationOffset = 4)
        }
        return Frame(KByteString(frameBytes))
    }

    private class TestRadioClientAccessor(client: RadioClient) : RadioClientAccessor {
        override val client = MutableStateFlow<RadioClient?>(client)

        override fun rebuildAndConnectAsync() = Unit

        override fun disconnect() = Unit
    }
}
