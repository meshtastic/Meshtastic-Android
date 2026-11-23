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

import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.repository.LocalNodeUnavailableException
import org.meshtastic.core.repository.PacketQueueRejectedException
import org.meshtastic.core.testing.FakeMeshPrefs
import org.meshtastic.core.testing.FakeServiceRepository
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.StoreAndForward
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HistoryManagerImplTest {
    private val serviceRepository = FakeServiceRepository().apply { setConnectionState(ConnectionState.Connected) }
    private val config =
        ModuleConfig.StoreForwardConfig.Builder()
            .also { wb ->
                wb.enabled = true
                wb.history_return_window = 30
                wb.history_return_max = 10
            }
            .build()

    @Test
    fun `resolveHistoryRequestParameters uses config values when positive`() {
        val (window, max) = HistoryManagerImpl.resolveHistoryRequestParameters(window = 30, max = 10)

        assertEquals(30, window)
        assertEquals(10, max)
    }

    @Test
    fun `resolveHistoryRequestParameters falls back to defaults when non-positive`() {
        val (window, max) = HistoryManagerImpl.resolveHistoryRequestParameters(window = 0, max = -5)

        assertEquals(1440, window)
        assertEquals(100, max)
    }

    @Test
    fun `requestHistoryReplay rejects a missing local node before sending`() = runTest {
        val fixture = sdkClientFixture(LOCAL_NODE)
        val meshPrefs = FakeMeshPrefs().apply { setDeviceAddress(ADDRESS) }

        assertFailsWith<LocalNodeUnavailableException> {
            HistoryManagerImpl(meshPrefs, fixture.radio, serviceRepository)
                .requestHistoryReplay("test", myNodeNum = null, config, "BLE", currentVersion())
        }

        assertTrue(fixture.storeForwardPackets().isEmpty())
        fixture.client.disconnect()
    }

    @Test
    fun `requestHistoryReplay rejects a missing device before sending`() = runTest {
        val fixture = sdkClientFixture(LOCAL_NODE)

        assertFailsWith<LocalNodeUnavailableException> {
            HistoryManagerImpl(FakeMeshPrefs(), fixture.radio, serviceRepository)
                .requestHistoryReplay("test", myNodeNum = LOCAL_NODE, config, "BLE", currentVersion())
        }

        assertTrue(fixture.storeForwardPackets().isEmpty())
        fixture.client.disconnect()
    }

    @Test
    fun `requestHistoryReplay asks our own node for history since the stored marker`() = runTest {
        val fixture = sdkClientFixture(LOCAL_NODE)
        val meshPrefs =
            FakeMeshPrefs().apply {
                setDeviceAddress(ADDRESS)
                setStoreForwardLastRequest(ADDRESS, 1_700_000_000)
            }

        HistoryManagerImpl(meshPrefs, fixture.radio, serviceRepository)
            .requestHistoryReplay("test", myNodeNum = LOCAL_NODE, config, "BLE", currentVersion())
        runCurrent()

        val sent = fixture.storeForwardPackets().single()
        assertEquals(LOCAL_NODE, sent.to)
        assertEquals(MeshPacket.Priority.BACKGROUND, sent.priority)
        val request = StoreAndForward.ADAPTER.decode(checkNotNull(sent.decoded).payload)
        assertEquals(StoreAndForward.RequestResponse.CLIENT_HISTORY, request.rr)
        assertEquals(1_700_000_000, request.history?.last_request)
        assertEquals(30, request.history?.window)
        assertEquals(10, request.history?.history_messages)
        fixture.client.disconnect()
    }

    @Test
    fun `requestHistoryReplay from a replaced connection sends nothing`() = runTest {
        val fixture = sdkClientFixture(LOCAL_NODE)
        val meshPrefs = FakeMeshPrefs().apply { setDeviceAddress(ADDRESS) }
        val captured = currentVersion()
        serviceRepository.setConnectionState(ConnectionState.Disconnected)
        serviceRepository.setConnectionState(ConnectionState.Connected)

        assertFailsWith<PacketQueueRejectedException> {
            HistoryManagerImpl(meshPrefs, fixture.radio, serviceRepository)
                .requestHistoryReplay("test", myNodeNum = LOCAL_NODE, config, "BLE", captured)
        }

        assertTrue(fixture.storeForwardPackets().isEmpty())
        fixture.client.disconnect()
    }

    private fun currentVersion(): Long = serviceRepository.connectionLifecycle.value.version

    private fun SdkClientFixture.storeForwardPackets(): List<MeshPacket> =
        transport.outboundPackets().filter { it.decoded?.portnum == PortNum.STORE_FORWARD_APP }

    private companion object {
        const val LOCAL_NODE = 123
        const val ADDRESS = "bAA:BB:CC:DD:EE:FF"
    }
}
