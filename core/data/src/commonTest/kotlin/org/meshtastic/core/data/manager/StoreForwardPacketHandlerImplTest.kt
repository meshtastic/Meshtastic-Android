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
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verifySuspend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.repository.HistoryManager
import org.meshtastic.core.repository.MeshDataHandler
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.ServiceBroadcasts
import org.meshtastic.proto.Data
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.StoreAndForward
import org.meshtastic.proto.StoreForwardPlusPlus
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StoreForwardPacketHandlerImplTest {

    private val nodeManager = mock<NodeManager>(MockMode.autofill)
    private val packetRepository = mock<PacketRepository>(MockMode.autofill)
    private val serviceBroadcasts = mock<ServiceBroadcasts>(MockMode.autofill)
    private val historyManager = mock<HistoryManager>(MockMode.autofill)
    private val dataHandler = mock<MeshDataHandler>(MockMode.autofill)

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var handler: StoreForwardPacketHandlerImpl

    private val myNodeNum = 12345

    @BeforeTest
    fun setUp() {
        every { nodeManager.myNodeNum } returns MutableStateFlow<Int?>(myNodeNum)

        handler =
            StoreForwardPacketHandlerImpl(
                nodeManager = nodeManager,
                packetRepository = lazy { packetRepository },
                serviceBroadcasts = serviceBroadcasts,
                historyManager = historyManager,
                dataHandler = lazy { dataHandler },
            )
        handler.start(testScope)
    }

    private fun makeSfPacket(from: Int, sf: StoreAndForward): MeshPacket {
        val payload = StoreAndForward.ADAPTER.encode(sf).toByteString()
        return MeshPacket(from = from, decoded = Data(portnum = PortNum.STORE_FORWARD_APP, payload = payload))
    }

    private fun makeSfppPacket(from: Int, sfpp: StoreForwardPlusPlus): MeshPacket {
        val payload = StoreForwardPlusPlus.ADAPTER.encode(sfpp).toByteString()
        return MeshPacket(from = from, decoded = Data(portnum = PortNum.STORE_FORWARD_APP, payload = payload))
    }

    private fun makeDataPacket(from: Int): DataPacket = DataPacket(
        id = 1,
        time = 1700000000000L,
        to = DataPacket.ID_BROADCAST,
        from = DataPacket.nodeNumToDefaultId(from),
        bytes = null,
        dataType = PortNum.STORE_FORWARD_APP.value,
    )

    // ---------- Legacy S&F: stats ----------

    @Test
    fun `handleStoreAndForward stats creates text data packet`() = testScope.runTest {
        val sf =
            StoreAndForward(
                stats = StoreAndForward.Statistics(messages_total = 100, messages_saved = 50, messages_max = 200),
            )
        val packet = makeSfPacket(999, sf)
        val dataPacket = makeDataPacket(999)

        handler.handleStoreAndForward(packet, dataPacket, myNodeNum)
        advanceUntilIdle()

        verify { dataHandler.rememberDataPacket(any(), myNodeNum) }
    }

    // ---------- Legacy S&F: history ----------

    @Test
    fun `handleStoreAndForward history creates text packet and updates last request`() = testScope.runTest {
        val sf =
            StoreAndForward(
                history =
                StoreAndForward.History(history_messages = 42, window = 3600000, last_request = 1700000000),
            )
        val packet = makeSfPacket(999, sf)
        val dataPacket = makeDataPacket(999)

        handler.handleStoreAndForward(packet, dataPacket, myNodeNum)
        advanceUntilIdle()

        verify { dataHandler.rememberDataPacket(any(), myNodeNum) }
        verify { historyManager.updateStoreForwardLastRequest("router_history", 1700000000, "Unknown") }
    }

    // ---------- Legacy S&F: heartbeat ----------

    @Test
    fun `handleStoreAndForward heartbeat does not crash`() = testScope.runTest {
        val sf = StoreAndForward(heartbeat = StoreAndForward.Heartbeat(period = 900, secondary = 1))
        val packet = makeSfPacket(999, sf)
        val dataPacket = makeDataPacket(999)

        handler.handleStoreAndForward(packet, dataPacket, myNodeNum)
        advanceUntilIdle()
        // No crash, just logs
    }

    // ---------- Legacy S&F: text ----------

    @Test
    fun `handleStoreAndForward text with broadcast rr sets to broadcast`() = testScope.runTest {
        val sf =
            StoreAndForward(
                text = "Hello from router".encodeToByteArray().toByteString(),
                rr = StoreAndForward.RequestResponse.ROUTER_TEXT_BROADCAST,
            )
        val packet = makeSfPacket(999, sf)
        val dataPacket = makeDataPacket(999)

        handler.handleStoreAndForward(packet, dataPacket, myNodeNum)
        advanceUntilIdle()

        verify { dataHandler.rememberDataPacket(any(), myNodeNum) }
    }

    @Test
    fun `handleStoreAndForward text without broadcast rr preserves destination`() = testScope.runTest {
        val sf =
            StoreAndForward(
                text = "Direct message".encodeToByteArray().toByteString(),
                rr = StoreAndForward.RequestResponse.ROUTER_TEXT_DIRECT,
            )
        val packet = makeSfPacket(999, sf)
        val dataPacket = makeDataPacket(999)

        handler.handleStoreAndForward(packet, dataPacket, myNodeNum)
        advanceUntilIdle()

        verify { dataHandler.rememberDataPacket(any(), myNodeNum) }
    }

    // ---------- Legacy S&F: null payload ----------

    @Test
    fun `handleStoreAndForward with null payload returns early`() = testScope.runTest {
        val packet = MeshPacket(from = 999, decoded = null)
        val dataPacket = makeDataPacket(999)

        handler.handleStoreAndForward(packet, dataPacket, myNodeNum)
        advanceUntilIdle()
        // No crash
    }

    // ---------- Legacy S&F: empty message ----------

    @Test
    fun `handleStoreAndForward with no fields set does not crash`() = testScope.runTest {
        val sf = StoreAndForward()
        val packet = makeSfPacket(999, sf)
        val dataPacket = makeDataPacket(999)

        handler.handleStoreAndForward(packet, dataPacket, myNodeNum)
        advanceUntilIdle()
        // No crash — falls through to else branch
    }

    // ---------- SF++: LINK_PROVIDE ----------

    @Test
    fun `handleStoreForwardPlusPlus LINK_PROVIDE with message_hash updates status`() = testScope.runTest {
        val sfpp =
            StoreForwardPlusPlus(
                sfpp_message_type = StoreForwardPlusPlus.SFPP_message_type.LINK_PROVIDE,
                encapsulated_id = 42,
                encapsulated_from = 1000,
                encapsulated_to = 2000,
                message_hash = ByteString.of(0x01, 0x02, 0x03, 0x04),
                commit_hash = ByteString.EMPTY,
            )
        val packet = makeSfppPacket(999, sfpp)

        handler.handleStoreForwardPlusPlus(packet)
        advanceUntilIdle()

        verifySuspend { packetRepository.updateSFPPStatus(any(), any(), any(), any(), any(), any(), any()) }
        verify { serviceBroadcasts.broadcastMessageStatus(42, any()) }
    }

    // ---------- SF++: CANON_ANNOUNCE ----------

    @Test
    fun `handleStoreForwardPlusPlus CANON_ANNOUNCE updates status by hash`() = testScope.runTest {
        val sfpp =
            StoreForwardPlusPlus(
                sfpp_message_type = StoreForwardPlusPlus.SFPP_message_type.CANON_ANNOUNCE,
                message_hash = ByteString.of(0xAA.toByte(), 0xBB.toByte()),
                encapsulated_rxtime = 1700000000,
            )
        val packet = makeSfppPacket(999, sfpp)

        handler.handleStoreForwardPlusPlus(packet)
        advanceUntilIdle()

        verifySuspend { packetRepository.updateSFPPStatusByHash(any(), any(), any()) }
    }

    // ---------- SF++: CHAIN_QUERY ----------

    @Test
    fun `handleStoreForwardPlusPlus CHAIN_QUERY logs info without crash`() = testScope.runTest {
        val sfpp = StoreForwardPlusPlus(sfpp_message_type = StoreForwardPlusPlus.SFPP_message_type.CHAIN_QUERY)
        val packet = makeSfppPacket(999, sfpp)

        handler.handleStoreForwardPlusPlus(packet)
        advanceUntilIdle()
        // No crash, just logs
    }

    // ---------- SF++: LINK_REQUEST ----------

    @Test
    fun `handleStoreForwardPlusPlus LINK_REQUEST logs info without crash`() = testScope.runTest {
        val sfpp = StoreForwardPlusPlus(sfpp_message_type = StoreForwardPlusPlus.SFPP_message_type.LINK_REQUEST)
        val packet = makeSfppPacket(999, sfpp)

        handler.handleStoreForwardPlusPlus(packet)
        advanceUntilIdle()
        // No crash, just logs
    }

    // ---------- SF++: invalid payload ----------

    @Test
    fun `handleStoreForwardPlusPlus with null payload returns early`() = testScope.runTest {
        val packet = MeshPacket(from = 999, decoded = null)

        handler.handleStoreForwardPlusPlus(packet)
        advanceUntilIdle()
        // No crash
    }

    // ---------- SF++: fragment types ----------

    @Test
    fun `handleStoreForwardPlusPlus LINK_PROVIDE_FIRSTHALF handled as link provide`() = testScope.runTest {
        val sfpp =
            StoreForwardPlusPlus(
                sfpp_message_type = StoreForwardPlusPlus.SFPP_message_type.LINK_PROVIDE_FIRSTHALF,
                encapsulated_id = 55,
                encapsulated_from = 1000,
                encapsulated_to = 2000,
                message_hash = ByteString.of(0x01, 0x02),
                commit_hash = ByteString.EMPTY,
            )
        val packet = makeSfppPacket(999, sfpp)

        handler.handleStoreForwardPlusPlus(packet)
        advanceUntilIdle()

        verifySuspend { packetRepository.updateSFPPStatus(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `handleStoreForwardPlusPlus LINK_PROVIDE_SECONDHALF handled as link provide`() = testScope.runTest {
        val sfpp =
            StoreForwardPlusPlus(
                sfpp_message_type = StoreForwardPlusPlus.SFPP_message_type.LINK_PROVIDE_SECONDHALF,
                encapsulated_id = 56,
                encapsulated_from = 1000,
                encapsulated_to = 2000,
                message_hash = ByteString.of(0x03, 0x04),
                commit_hash = ByteString.EMPTY,
            )
        val packet = makeSfppPacket(999, sfpp)

        handler.handleStoreForwardPlusPlus(packet)
        advanceUntilIdle()

        verifySuspend { packetRepository.updateSFPPStatus(any(), any(), any(), any(), any(), any(), any()) }
    }

    // ---------- SF++: commit_hash present changes status ----------

    @Test
    fun `handleStoreForwardPlusPlus LINK_PROVIDE with commit_hash sets SFPP_CONFIRMED`() = testScope.runTest {
        val sfpp =
            StoreForwardPlusPlus(
                sfpp_message_type = StoreForwardPlusPlus.SFPP_message_type.LINK_PROVIDE,
                encapsulated_id = 77,
                encapsulated_from = 1000,
                encapsulated_to = 2000,
                message_hash = ByteString.of(0x01, 0x02),
                commit_hash = ByteString.of(0xAA.toByte()), // non-empty
            )
        val packet = makeSfppPacket(999, sfpp)

        handler.handleStoreForwardPlusPlus(packet)
        advanceUntilIdle()

        verifySuspend { packetRepository.updateSFPPStatus(any(), any(), any(), any(), any(), any(), any()) }
    }
}
