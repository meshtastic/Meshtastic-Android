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
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.capture.capture
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.LocalNodeUnavailableException
import org.meshtastic.core.repository.PacketHandler
import org.meshtastic.core.repository.PacketQueueRejectedException
import org.meshtastic.core.testing.FakeMeshPrefs
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.StoreAndForward
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HistoryManagerImplTest {
    private val commandSender = mock<CommandSender>(MockMode.autofill)

    private fun manager(meshPrefs: FakeMeshPrefs, packetHandler: PacketHandler) =
        HistoryManagerImpl(meshPrefs, packetHandler, commandSender)

    @Test
    fun `buildStoreForwardHistoryRequest copies positive parameters`() {
        val request =
            HistoryManagerImpl.buildStoreForwardHistoryRequest(
                lastRequest = 42,
                historyReturnWindow = 15,
                historyReturnMax = 25,
            )

        assertEquals(StoreAndForward.RequestResponse.CLIENT_HISTORY, request.rr)
        assertEquals(42, request.history?.last_request)
        assertEquals(15, request.history?.window)
        assertEquals(25, request.history?.history_messages)
    }

    @Test
    fun `buildStoreForwardHistoryRequest clamps non-positive parameters`() {
        val request =
            HistoryManagerImpl.buildStoreForwardHistoryRequest(
                lastRequest = 0,
                historyReturnWindow = -1,
                historyReturnMax = 0,
            )

        assertEquals(StoreAndForward.RequestResponse.CLIENT_HISTORY, request.rr)
        assertEquals(0, request.history?.last_request)
        assertEquals(0, request.history?.window)
        assertEquals(0, request.history?.history_messages)
    }

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
    fun `requestHistoryReplay rejects a missing local node before queue admission`() = runTest {
        val packetHandler = mock<PacketHandler>(MockMode.autofill)
        val meshPrefs = FakeMeshPrefs().apply { setDeviceAddress("bAA:BB:CC:DD:EE:FF") }

        assertFailsWith<LocalNodeUnavailableException> {
            manager(meshPrefs, packetHandler)
                .requestHistoryReplay(
                    trigger = "test",
                    myNodeNum = null,
                    storeForwardConfig = ModuleConfig.StoreForwardConfig(enabled = true),
                    transport = "BLE",
                    expectedConnectionVersion = 17L,
                )
        }

        verifySuspend(exactly(0)) { packetHandler.sendToRadioForConnection(any(), any()) }
    }

    @Test
    fun `requestHistoryReplay rejects a missing device before queue admission`() = runTest {
        val packetHandler = mock<PacketHandler>(MockMode.autofill)
        val meshPrefs = FakeMeshPrefs()

        assertFailsWith<LocalNodeUnavailableException> {
            manager(meshPrefs, packetHandler)
                .requestHistoryReplay(
                    trigger = "test",
                    myNodeNum = 123,
                    storeForwardConfig = ModuleConfig.StoreForwardConfig(enabled = true),
                    transport = "BLE",
                    expectedConnectionVersion = 17L,
                )
        }

        verifySuspend(exactly(0)) { packetHandler.sendToRadioForConnection(any(), any()) }
    }

    @Test
    fun `requestHistoryReplay binds queue admission to the expected connection`() = runTest {
        val packetHandler = mock<PacketHandler>(MockMode.autofill)
        val meshPrefs = FakeMeshPrefs().apply { setDeviceAddress("bAA:BB:CC:DD:EE:FF") }
        val packets = mutableListOf<MeshPacket>()
        everySuspend { packetHandler.sendToRadioForConnection(capture(packets), 17L) } returns true
        every { commandSender.generatePacketId() } returns 42

        manager(meshPrefs, packetHandler)
            .requestHistoryReplay(
                trigger = "test",
                myNodeNum = 123,
                storeForwardConfig = ModuleConfig.StoreForwardConfig(enabled = true),
                transport = "BLE",
                expectedConnectionVersion = 17L,
            )

        verifySuspend { packetHandler.sendToRadioForConnection(any(), 17L) }
        verify { commandSender.generatePacketId() }
        val queued = packets.single()
        assertEquals(42, queued.id)
        assertEquals(123, queued.from)
        assertEquals(123, queued.to)
        assertEquals(PortNum.STORE_FORWARD_APP, queued.decoded?.portnum)
        assertEquals(MeshPacket.Priority.BACKGROUND, queued.priority)
        val request = StoreAndForward.ADAPTER.decode(checkNotNull(queued.decoded).payload)
        assertEquals(StoreAndForward.RequestResponse.CLIENT_HISTORY, request.rr)
    }

    @Test
    fun `requestHistoryReplay surfaces owned queue rejection`() = runTest {
        val packetHandler = mock<PacketHandler>(MockMode.autofill)
        val meshPrefs = FakeMeshPrefs().apply { setDeviceAddress("bAA:BB:CC:DD:EE:FF") }
        everySuspend { packetHandler.sendToRadioForConnection(any(), 23L) } returns false
        every { commandSender.generatePacketId() } returns 43

        assertFailsWith<PacketQueueRejectedException> {
            manager(meshPrefs, packetHandler)
                .requestHistoryReplay(
                    trigger = "test",
                    myNodeNum = 123,
                    storeForwardConfig = ModuleConfig.StoreForwardConfig(enabled = true),
                    transport = "BLE",
                    expectedConnectionVersion = 23L,
                )
        }
    }
}
