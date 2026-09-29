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
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.common.di.asServiceScope
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.FromRadioPacketHandler
import org.meshtastic.core.repository.MeshDataHandler
import org.meshtastic.core.repository.MeshLogRepository
import org.meshtastic.core.repository.MeshNotificationManager
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.repository.ReceivedRadioFrame
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioInterfaceService
import org.meshtastic.proto.Data
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Drives a real [MeshMessageProcessorImpl] and [NodeManagerImpl] over [FakeRadioInterfaceService], whose
 * `runWhileSessionActive` serializes handlers on one mutex as production does, with a node repository whose writes wait
 * on a gate that stands in for a stalled database writer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MeshMessageProcessorNodeWriteTest {

    private val testScope = TestScope(StandardTestDispatcher())
    private val nodeRepository = GatedNodeRepository(FakeNodeRepository())
    private val dataHandler = mock<MeshDataHandler>(MockMode.autofill)

    private class GatedNodeRepository(private val delegate: FakeNodeRepository) : NodeRepository by delegate {
        val writeStarted = CompletableDeferred<Unit>()
        val releaseWrites = CompletableDeferred<Unit>()

        fun persisted(num: Int): Node? = delegate.nodeDBbyNum.value[num]

        override suspend fun upsert(node: Node) {
            writeStarted.complete(Unit)
            releaseWrites.await()
            delegate.upsert(node)
        }
    }

    private class Fixture(scope: CoroutineScope, nodeRepository: NodeRepository, dataHandler: MeshDataHandler) {
        val radio = FakeRadioInterfaceService(scope)
        val nodeManager =
            NodeManagerImpl(
                nodeRepository,
                mock<MeshNotificationManager>(MockMode.autofill),
                radio,
                scope.asServiceScope(),
            )
                .apply {
                    notificationTitleFormatter = { shortName -> "New node seen: $shortName" }
                    setNodeDbReady(true)
                    setAllowNodeDbWrites(true)
                }
        val processor =
            MeshMessageProcessorImpl(
                nodeManager = nodeManager,
                serviceStateWriter = mock<ServiceRepository>(MockMode.autofill),
                meshLogRepository = lazy { mock<MeshLogRepository>(MockMode.autofill) },
                dataHandler = lazy { dataHandler },
                fromRadioDispatcher = mock<FromRadioPacketHandler>(MockMode.autofill),
                radioInterfaceService = radio,
                scope = scope.asServiceScope(),
            )

        val session: RadioSessionContext

        init {
            radio.setDeviceAddress("tcp:test")
            radio.connect()
            session = checkNotNull(radio.activeSession.value)
        }

        suspend fun receive(packet: MeshPacket) {
            val bytes = FromRadio.Builder().also { wb -> wb.packet = packet }.build().encode()
            processor.handleFromRadio(ReceivedRadioFrame(bytes.toByteString(), session), MY_NODE)
        }
    }

    private fun packetFrom(from: Int, id: Int) = MeshPacket.Builder()
        .also { wb ->
            wb.id = id
            wb.from = from
            wb.hop_start = 3
            wb.hop_limit = 1
            wb.rx_time = nowSeconds.toInt()
            wb.decoded =
                Data.Builder()
                    .also { d ->
                        d.portnum = PortNum.TEXT_MESSAGE_APP
                        d.payload = ByteString.EMPTY
                    }
                    .build()
        }
        .build()

    @Test
    fun `the inbound handler returns while its node write waits on the database`() = testScope.runTest {
        val fixture = Fixture(backgroundScope, nodeRepository, dataHandler)

        val first = async { fixture.receive(packetFrom(SENDER, id = 1)) }
        runCurrent()
        assertTrue(nodeRepository.writeStarted.isCompleted, "the node write has started and is held")
        assertTrue(first.isCompleted, "the handler must not wait for the node write")

        val second = async { fixture.receive(packetFrom(OTHER_SENDER, id = 2)) }
        runCurrent()
        assertTrue(second.isCompleted, "the next frame must not queue behind the held write")
        assertNull(nodeRepository.persisted(SENDER), "nothing is written while the database is held")

        // The writes run in backgroundScope, which advanceUntilIdle does not drive.
        nodeRepository.releaseWrites.complete(Unit)
        runCurrent()

        assertEquals(fixture.nodeManager.nodeDBbyNodeNum[SENDER], nodeRepository.persisted(SENDER))
        assertEquals(2, nodeRepository.persisted(SENDER)?.hopsAway)
        assertNotNull(nodeRepository.persisted(OTHER_SENDER))
    }

    @Test
    fun `the data handler reads the node update from memory before the write lands`() = testScope.runTest {
        val fixture = Fixture(backgroundScope, nodeRepository, dataHandler)
        var hopsAwaySeen: Int? = null
        every { dataHandler.handleReceivedData(any(), any(), any(), any(), any()) } calls
            {
                hopsAwaySeen = fixture.nodeManager.nodeDBbyNodeNum[SENDER]?.hopsAway
            }

        launch { fixture.receive(packetFrom(SENDER, id = 1)) }
        runCurrent()

        assertEquals(2, hopsAwaySeen)
        assertNull(nodeRepository.persisted(SENDER), "the write is still held")
        nodeRepository.releaseWrites.complete(Unit)
    }

    @Test
    fun `teardown still waits for a node write the handler left running`() = testScope.runTest {
        val fixture = Fixture(backgroundScope, nodeRepository, dataHandler)
        launch { fixture.receive(packetFrom(SENDER, id = 1)) }
        runCurrent()

        val teardown = launch { fixture.radio.disconnect() }
        runCurrent()
        assertFalse(teardown.isCompleted, "teardown must drain the write's session lease")

        nodeRepository.releaseWrites.complete(Unit)
        runCurrent()

        assertTrue(teardown.isCompleted)
        assertNotNull(nodeRepository.persisted(SENDER))
    }

    private companion object {
        const val MY_NODE = 12345
        const val SENDER = 999
        const val OTHER_SENDER = 998
    }
}
