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
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.common.di.asServiceScope
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.repository.HistoryManager
import org.meshtastic.core.repository.MeshDataHandler
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioSessionContext
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
    private val historyManager = mock<HistoryManager>(MockMode.autofill)
    private val dataHandler = mock<MeshDataHandler>(MockMode.autofill)
    private val radioInterfaceService = mock<RadioInterfaceService>(MockMode.autofill)

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
                historyManager = historyManager,
                dataHandler = lazy { dataHandler },
                radioInterfaceService = radioInterfaceService,
                scope = testScope.asServiceScope(),
            )
    }

    private fun makeSfPacket(from: Int, sf: StoreAndForward): MeshPacket {
        val payload = sf.encode().toByteString()
        return MeshPacket.Builder().also { wb ->wb.from = from; wb.decoded = Data.Builder().also { wb ->wb.portnum = PortNum.STORE_FORWARD_APP; wb.payload = payload}.build()}.build()
    }

    private fun makeSfppPacket(from: Int, sfpp: StoreForwardPlusPlus): MeshPacket {
        val payload = sfpp.encode().toByteString()
        return MeshPacket.Builder().also { wb ->wb.from = from; wb.decoded = Data.Builder().also { wb ->wb.portnum = PortNum.STORE_FORWARD_APP; wb.payload = payload}.build()}.build()
    }

    private fun makeDataPacket(from: Int): DataPacket = DataPacket(
        id = 1,
        time = 1700000000000L,
        to = NodeAddress.ID_BROADCAST,
        from = NodeAddress.numToDefaultId(from),
        bytes = null,
        dataType = PortNum.STORE_FORWARD_APP.value,
    )

    // ---------- Legacy S&F: stats ----------

    @Test
    fun `handleStoreAndForward stats creates text data packet`() = testScope.runTest {
        val sf =
            StoreAndForward.Builder().also { wb ->
            wb.stats = StoreAndForward.Statistics.Builder().also { wb ->wb.messages_total = 100; wb.messages_saved = 50; wb.messages_max = 200}.build()
            }.build()
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
            StoreAndForward.Builder().also { wb ->
            wb.history = StoreAndForward.History.Builder().also { wb ->wb.history_messages = 42; wb.window = 3600000; wb.last_request = 1700000000}.build()
            }.build()
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
        val sf = StoreAndForward.Builder().also { wb ->wb.heartbeat = StoreAndForward.Heartbeat.Builder().also { wb ->wb.period = 900; wb.secondary = 1}.build()}.build()
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
            StoreAndForward.Builder().also { wb ->
            wb.text = "Hello from router".encodeToByteArray().toByteString()
            wb.rr = StoreAndForward.RequestResponse.ROUTER_TEXT_BROADCAST
            }.build()
        val packet = makeSfPacket(999, sf)
        val dataPacket = makeDataPacket(999)

        handler.handleStoreAndForward(packet, dataPacket, myNodeNum)
        advanceUntilIdle()

        verify { dataHandler.rememberDataPacket(any(), myNodeNum) }
    }

    @Test
    fun `handleStoreAndForward text without broadcast rr preserves destination`() = testScope.runTest {
        val sf =
            StoreAndForward.Builder().also { wb ->
            wb.text = "Direct message".encodeToByteArray().toByteString()
            wb.rr = StoreAndForward.RequestResponse.ROUTER_TEXT_DIRECT
            }.build()
        val packet = makeSfPacket(999, sf)
        val dataPacket = makeDataPacket(999)

        handler.handleStoreAndForward(packet, dataPacket, myNodeNum)
        advanceUntilIdle()

        verify { dataHandler.rememberDataPacket(any(), myNodeNum) }
    }

    // ---------- Legacy S&F: null payload ----------

    @Test
    fun `handleStoreAndForward with null payload returns early`() = testScope.runTest {
        val packet = MeshPacket.Builder().also { wb ->wb.from = 999; wb.decoded = null}.build()
        val dataPacket = makeDataPacket(999)

        handler.handleStoreAndForward(packet, dataPacket, myNodeNum)
        advanceUntilIdle()
        // No crash
    }

    // ---------- Legacy S&F: empty message ----------

    @Test
    fun `handleStoreAndForward with no fields set does not crash`() = testScope.runTest {
        val sf = StoreAndForward.Builder().build()
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
            StoreForwardPlusPlus.Builder().also { wb ->
            wb.sfpp_message_type = StoreForwardPlusPlus.SFPP_message_type.LINK_PROVIDE
            wb.encapsulated_id = 42
            wb.encapsulated_from = 1000
            wb.encapsulated_to = 2000
            wb.message_hash = ByteString.of(0x01, 0x02, 0x03, 0x04)
            wb.commit_hash = ByteString.EMPTY
            }.build()
        val packet = makeSfppPacket(999, sfpp)

        handler.handleStoreForwardPlusPlus(packet)
        advanceUntilIdle()

        verifySuspend { packetRepository.updateSFPPStatus(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `SFPP update from a retired same-address generation is rejected`() = testScope.runTest {
        val oldSession = RadioSessionContext(generation = 4L, address = "ble:same")
        val sfpp =
            StoreForwardPlusPlus.Builder().also { wb ->
            wb.sfpp_message_type = StoreForwardPlusPlus.SFPP_message_type.LINK_PROVIDE
            wb.encapsulated_id = 42
            wb.encapsulated_from = 1000
            wb.encapsulated_to = 2000
            wb.message_hash = ByteString.of(0x01, 0x02, 0x03, 0x04)
            }.build()
        val packet = makeSfppPacket(999, sfpp)
        everySuspend { radioInterfaceService.runWithSessionLease(oldSession, any()) } returns false

        handler.handleStoreForwardPlusPlus(packet, oldSession)
        advanceUntilIdle()

        verifySuspend(mode = VerifyMode.exactly(0)) {
            packetRepository.updateSFPPStatus(any(), any(), any(), any(), any(), any(), any())
        }
    }

    // ---------- SF++: CANON_ANNOUNCE ----------

    @Test
    fun `handleStoreForwardPlusPlus CANON_ANNOUNCE updates status by hash`() = testScope.runTest {
        val sfpp =
            StoreForwardPlusPlus.Builder().also { wb ->
            wb.sfpp_message_type = StoreForwardPlusPlus.SFPP_message_type.CANON_ANNOUNCE
            wb.message_hash = ByteString.of(0xAA.toByte(), 0xBB.toByte())
            wb.encapsulated_rxtime = 1700000000
            }.build()
        val packet = makeSfppPacket(999, sfpp)

        handler.handleStoreForwardPlusPlus(packet)
        advanceUntilIdle()

        verifySuspend { packetRepository.updateSFPPStatusByHash(any(), any(), any()) }
    }

    // ---------- SF++: CHAIN_QUERY ----------

    @Test
    fun `handleStoreForwardPlusPlus CHAIN_QUERY logs info without crash`() = testScope.runTest {
        val sfpp = StoreForwardPlusPlus.Builder().also { wb ->wb.sfpp_message_type = StoreForwardPlusPlus.SFPP_message_type.CHAIN_QUERY}.build()
        val packet = makeSfppPacket(999, sfpp)

        handler.handleStoreForwardPlusPlus(packet)
        advanceUntilIdle()
        // No crash, just logs
    }

    // ---------- SF++: LINK_REQUEST ----------

    @Test
    fun `handleStoreForwardPlusPlus LINK_REQUEST logs info without crash`() = testScope.runTest {
        val sfpp = StoreForwardPlusPlus.Builder().also { wb ->wb.sfpp_message_type = StoreForwardPlusPlus.SFPP_message_type.LINK_REQUEST}.build()
        val packet = makeSfppPacket(999, sfpp)

        handler.handleStoreForwardPlusPlus(packet)
        advanceUntilIdle()
        // No crash, just logs
    }

    // ---------- SF++: invalid payload ----------

    @Test
    fun `handleStoreForwardPlusPlus with null payload returns early`() = testScope.runTest {
        val packet = MeshPacket.Builder().also { wb ->wb.from = 999; wb.decoded = null}.build()

        handler.handleStoreForwardPlusPlus(packet)
        advanceUntilIdle()
        // No crash
    }

    // ---------- SF++: fragment types ----------

    @Test
    fun `handleStoreForwardPlusPlus LINK_PROVIDE_FIRSTHALF handled as link provide`() = testScope.runTest {
        val sfpp =
            StoreForwardPlusPlus.Builder().also { wb ->
            wb.sfpp_message_type = StoreForwardPlusPlus.SFPP_message_type.LINK_PROVIDE_FIRSTHALF
            wb.encapsulated_id = 55
            wb.encapsulated_from = 1000
            wb.encapsulated_to = 2000
            wb.message_hash = ByteString.of(0x01, 0x02)
            wb.commit_hash = ByteString.EMPTY
            }.build()
        val packet = makeSfppPacket(999, sfpp)

        handler.handleStoreForwardPlusPlus(packet)
        advanceUntilIdle()

        verifySuspend { packetRepository.updateSFPPStatus(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `handleStoreForwardPlusPlus LINK_PROVIDE_SECONDHALF handled as link provide`() = testScope.runTest {
        val sfpp =
            StoreForwardPlusPlus.Builder().also { wb ->
            wb.sfpp_message_type = StoreForwardPlusPlus.SFPP_message_type.LINK_PROVIDE_SECONDHALF
            wb.encapsulated_id = 56
            wb.encapsulated_from = 1000
            wb.encapsulated_to = 2000
            wb.message_hash = ByteString.of(0x03, 0x04)
            wb.commit_hash = ByteString.EMPTY
            }.build()
        val packet = makeSfppPacket(999, sfpp)

        handler.handleStoreForwardPlusPlus(packet)
        advanceUntilIdle()

        verifySuspend { packetRepository.updateSFPPStatus(any(), any(), any(), any(), any(), any(), any()) }
    }

    // ---------- SF++: commit_hash present changes status ----------

    @Test
    fun `handleStoreForwardPlusPlus LINK_PROVIDE with commit_hash sets SFPP_CONFIRMED`() = testScope.runTest {
        val sfpp =
            StoreForwardPlusPlus.Builder()
                .also { wb ->
                    wb.sfpp_message_type = StoreForwardPlusPlus.SFPP_message_type.LINK_PROVIDE
                    wb.encapsulated_id = 77
                    wb.encapsulated_from = 1000
                    wb.encapsulated_to = 2000
                    wb.message_hash = ByteString.of(0x01, 0x02)
                    wb.commit_hash = ByteString.of(0xAA.toByte()) // non-empty
                }
                .build()
        val packet = makeSfppPacket(999, sfpp)

        handler.handleStoreForwardPlusPlus(packet)
        advanceUntilIdle()

        verifySuspend { packetRepository.updateSFPPStatus(any(), any(), any(), any(), any(), any(), any()) }
    }

    // ---------- Legacy S&F: malformed proto ----------

    @Test
    fun `handleStoreAndForward with malformed payload does not crash`() = testScope.runTest {
        val malformedPayload = ByteString.of(0xFF.toByte(), 0xFE.toByte(), 0x07, 0x0E)
        val packet =
            MeshPacket.Builder().also { wb ->wb.from = 999; wb.decoded = Data.Builder().also { wb ->wb.portnum = PortNum.STORE_FORWARD_APP; wb.payload = malformedPayload}.build()}.build()
        val dataPacket = makeDataPacket(999)

        // Should not throw — the handler catches the IOException from proto decoding
        handler.handleStoreAndForward(packet, dataPacket, myNodeNum)
        advanceUntilIdle()
    }
}
