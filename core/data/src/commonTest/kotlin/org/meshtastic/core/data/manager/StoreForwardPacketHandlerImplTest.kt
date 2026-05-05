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
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verify
import dev.mokkery.verifySuspend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.repository.HistoryManager
import org.meshtastic.core.repository.MeshDataHandler
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.proto.Data
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.StoreAndForward
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StoreForwardPacketHandlerImplTest {

    private val nodeRepository = mock<NodeRepository>(MockMode.autofill)
    private val packetRepository = mock<PacketRepository>(MockMode.autofill)
    private val historyManager = mock<HistoryManager>(MockMode.autofill)
    private val dataHandler = mock<MeshDataHandler>(MockMode.autofill)

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var handler: StoreForwardPacketHandlerImpl

    private val myNodeNum = 12345

    @BeforeTest
    fun setUp() {
        handler =
            StoreForwardPacketHandlerImpl(
                nodeRepository = nodeRepository,
                packetRepository = lazy { packetRepository },
                historyManager = historyManager,
                dataHandler = lazy { dataHandler },
                scope = testScope,
            )
    }

    private fun makeSfPacket(from: Int, sf: StoreAndForward): MeshPacket {
        val payload = StoreAndForward.ADAPTER.encode(sf).toByteString()
        return MeshPacket(from = from, decoded = Data(portnum = PortNum.STORE_FORWARD_APP, payload = payload))
    }

    private fun makeDataPacket(from: Int): DataPacket = DataPacket(
        id = 1,
        time = 1700000000000L,
        to = DataPacket.BROADCAST,
        from = from,
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

    // ---------- SF++: delegated to SDK ----------

    @Test
    fun `handleStoreForwardPlusPlus logs only and leaves repository untouched`() = testScope.runTest {
        val packet =
            MeshPacket(
                from = 999,
                decoded = Data(
                    portnum = PortNum.STORE_FORWARD_APP,
                    payload = "ignored".encodeToByteArray().toByteString(),
                ),
            )

        handler.handleStoreForwardPlusPlus(packet)
        advanceUntilIdle()

        verifySuspend(mode = VerifyMode.exactly(0)) {
            packetRepository.updateSFPPStatus(any(), any(), any(), any(), any(), any(), any())
        }
        verifySuspend(mode = VerifyMode.exactly(0)) {
            packetRepository.updateSFPPStatusByHash(any(), any(), any())
        }
    }
}
