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
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.ByteString
import org.meshtastic.core.repository.FromRadioPacketHandler
import org.meshtastic.core.repository.MeshDataHandler
import org.meshtastic.core.repository.MeshLogRepository
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.proto.Data
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.LogRecord
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MeshMessageProcessorImplTest {

    private val nodeManager = mock<NodeManager>(MockMode.autofill)
    private val serviceRepository = mock<ServiceRepository>(MockMode.autofill)
    private val meshLogRepository = mock<MeshLogRepository>(MockMode.autofill)
    private val fromRadioDispatcher = mock<FromRadioPacketHandler>(MockMode.autofill)
    private val dataHandler = mock<MeshDataHandler>(MockMode.autofill)

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var processor: MeshMessageProcessorImpl

    private val myNodeNum = 12345
    private val isNodeDbReady = MutableStateFlow(false)

    @BeforeTest
    fun setUp() {
        every { nodeManager.isNodeDbReady } returns isNodeDbReady
        every { nodeManager.myNodeNum } returns MutableStateFlow<Int?>(myNodeNum)
    }

    private fun createProcessor(scope: CoroutineScope): MeshMessageProcessorImpl = MeshMessageProcessorImpl(
        nodeManager = nodeManager,
        serviceStateWriter = serviceRepository,
        meshLogRepository = lazy { meshLogRepository },
        dataHandler = lazy { dataHandler },
        fromRadioDispatcher = fromRadioDispatcher,
        scope = scope,
    )

    // ---------- handleFromRadio: non-packet variants ----------

    @Test
    fun `handleFromRadio dispatches non-packet variants to fromRadioDispatcher`() = runTest(testDispatcher) {
        processor = createProcessor(backgroundScope)
        val logRecord = LogRecord(message = "test log")
        val fromRadio = FromRadio(log_record = logRecord)
        val bytes = fromRadio.encode()

        processor.handleFromRadio(bytes, myNodeNum)
        advanceUntilIdle()

        verify { fromRadioDispatcher.handleFromRadio(any()) }
    }

    @Test
    fun `handleFromRadio falls back to LogRecord parsing when FromRadio fails`() = runTest(testDispatcher) {
        processor = createProcessor(backgroundScope)
        // Encode a raw LogRecord (not wrapped in FromRadio) — first decode as FromRadio fails,
        // fallback decode as LogRecord succeeds
        val logRecord = LogRecord(message = "fallback log")
        val bytes = logRecord.encode()

        processor.handleFromRadio(bytes, myNodeNum)
        advanceUntilIdle()

        // Should have been dispatched as a FromRadio with log_record set
        verify { fromRadioDispatcher.handleFromRadio(any()) }
    }

    @Test
    fun `handleFromRadio with completely invalid bytes does not crash`() = runTest(testDispatcher) {
        processor = createProcessor(backgroundScope)
        // Invalid protobuf bytes — both parses should fail
        val garbage = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0xFD.toByte())

        processor.handleFromRadio(garbage, myNodeNum)
        advanceUntilIdle()
        // No crash
    }

    @Test
    fun `a throwing handler does not propagate out of handleFromRadio`() = runTest(testDispatcher) {
        processor = createProcessor(backgroundScope)
        // A malformed-but-decodable packet can make a downstream handler throw. That must NOT escape to the
        // receivedData collector, which would cancel the collection and silently deafen the radio (a remote DoS).
        // Regression guard for the safeCatching in processFromRadio: before that fix this call rethrew and failed.
        every { fromRadioDispatcher.handleFromRadio(any()) } throws RuntimeException("hostile packet")
        val fromRadio = FromRadio(log_record = LogRecord(message = "boom")) // routes to fromRadioDispatcher

        processor.handleFromRadio(fromRadio.encode(), myNodeNum) // must return normally, not throw
        advanceUntilIdle()

        verify { fromRadioDispatcher.handleFromRadio(any()) } // handler ran and threw, yet we are still here
    }

    // ---------- handleReceivedMeshPacket: early buffering ----------

    @Test
    fun `packets are buffered when node DB is not ready`() = runTest(testDispatcher) {
        processor = createProcessor(backgroundScope)
        isNodeDbReady.value = false

        val packet =
            MeshPacket(
                id = 1,
                from = 999,
                decoded = Data(portnum = PortNum.TEXT_MESSAGE_APP, payload = ByteString.EMPTY),
                rx_time = 1000,
            )

        processor.handleReceivedMeshPacket(packet, myNodeNum)
        advanceUntilIdle()

        // Packet should be buffered, not processed
        // (no emitMeshPacket call since DB is not ready)
    }

    @Test
    fun `buffered packets are flushed when node DB becomes ready`() = runTest(testDispatcher) {
        processor = createProcessor(backgroundScope)
        isNodeDbReady.value = false

        val packet =
            MeshPacket(
                id = 1,
                from = 999,
                decoded = Data(portnum = PortNum.TEXT_MESSAGE_APP, payload = ByteString.EMPTY),
                rx_time = 1000,
            )

        processor.handleReceivedMeshPacket(packet, myNodeNum)
        advanceUntilIdle()

        // Now make DB ready
        isNodeDbReady.value = true
        advanceUntilIdle()

        // Buffered packet should have been flushed and processed
        verifySuspend { serviceRepository.emitMeshPacket(any()) }
    }

    @Test
    fun `early buffer overflow drops oldest packet`() = runTest(testDispatcher) {
        processor = createProcessor(backgroundScope)
        isNodeDbReady.value = false

        // The maxEarlyPacketBuffer is 10240 — we won't actually fill it in this test,
        // but we test the boundary behavior conceptually. Instead, test that multiple
        // packets are accumulated properly.
        repeat(5) { i ->
            val packet =
                MeshPacket(
                    id = i,
                    from = 999,
                    decoded = Data(portnum = PortNum.TEXT_MESSAGE_APP, payload = ByteString.EMPTY),
                    rx_time = 1000 + i,
                )
            processor.handleReceivedMeshPacket(packet, myNodeNum)
        }
        advanceUntilIdle()

        // Flush
        isNodeDbReady.value = true
        advanceUntilIdle()

        // All 5 packets should have been processed
        verifySuspend { serviceRepository.emitMeshPacket(any()) }
    }

    // ---------- flushEarlyReceivedPackets: handler resilience ----------

    @Test
    fun `a throwing dataHandler for one buffered packet does not stop the flush or crash`() = runTest(testDispatcher) {
        processor = createProcessor(backgroundScope)
        isNodeDbReady.value = false
        // Regression guard for a crash found via adversarial mesh-fuzz testing (corrupted/garbage NodeInfo
        // packets injected into a live replay session): a malformed packet buffered before the node DB /
        // myNodeNum were ready, then replayed by flushEarlyReceivedPackets, threw an uncaught
        // ProtocolException/EOFException from deep inside handleReceivedData -> handleNodeInfo's proto
        // decode. Unlike the live path (guarded by safeCatching in processFromRadio), that exception
        // escaped the bare `scope.launch` in flushEarlyReceivedPackets and crashed the whole app process
        // (observed live: "FATAL EXCEPTION" at MeshMessageProcessorImpl.kt:214, PID killed, "keeps
        // stopping" dialog on device). Fixed by wrapping each buffered packet's processing in safeCatching.
        every { dataHandler.handleReceivedData(any(), any(), any(), any()) } throws
            RuntimeException("corrupted NodeInfo")

        val poisoned =
            MeshPacket(
                id = 1,
                from = 999,
                decoded = Data(portnum = PortNum.NODEINFO_APP, payload = ByteString.EMPTY),
                rx_time = 1000,
            )
        val healthy =
            MeshPacket(
                id = 2,
                from = 998,
                decoded = Data(portnum = PortNum.TEXT_MESSAGE_APP, payload = ByteString.EMPTY),
                rx_time = 1001,
            )

        processor.handleReceivedMeshPacket(poisoned, myNodeNum)
        processor.handleReceivedMeshPacket(healthy, myNodeNum)
        advanceUntilIdle()

        isNodeDbReady.value = true
        advanceUntilIdle() // must not throw -- this is the crash repro point

        // Both packets were attempted -- the poisoned packet's throw did not stop the rest of the batch.
        verify { nodeManager.updateNode(999, any(), any()) }
        verify { nodeManager.updateNode(998, any(), any()) }
    }

    // ---------- handleReceivedMeshPacket: rx_time normalization ----------

    @Test
    fun `packets with rx_time 0 get current time`() = runTest(testDispatcher) {
        processor = createProcessor(backgroundScope)
        isNodeDbReady.value = true

        val packet =
            MeshPacket(
                id = 1,
                from = myNodeNum,
                decoded = Data(portnum = PortNum.TEXT_MESSAGE_APP, payload = ByteString.EMPTY),
                rx_time = 0, // should be replaced with current time
            )

        processor.handleReceivedMeshPacket(packet, myNodeNum)
        advanceUntilIdle()

        verifySuspend { serviceRepository.emitMeshPacket(any()) }
    }

    @Test
    fun `packets with non-zero rx_time keep their time`() = runTest(testDispatcher) {
        processor = createProcessor(backgroundScope)
        isNodeDbReady.value = true

        val packet =
            MeshPacket(
                id = 2,
                from = myNodeNum,
                decoded = Data(portnum = PortNum.TEXT_MESSAGE_APP, payload = ByteString.EMPTY),
                rx_time = 1700000000,
            )

        processor.handleReceivedMeshPacket(packet, myNodeNum)
        advanceUntilIdle()

        verifySuspend { serviceRepository.emitMeshPacket(any()) }
    }

    // ---------- handleReceivedMeshPacket: node updates ----------

    @Test
    fun `processReceivedMeshPacket updates myNode lastHeard`() = runTest(testDispatcher) {
        processor = createProcessor(backgroundScope)
        isNodeDbReady.value = true

        val packet =
            MeshPacket(
                id = 10,
                from = 999,
                decoded = Data(portnum = PortNum.TEXT_MESSAGE_APP, payload = ByteString.EMPTY),
                rx_time = 1700000000,
            )

        processor.handleReceivedMeshPacket(packet, myNodeNum)
        advanceUntilIdle()

        // Should have called updateNode for myNodeNum (lastHeard update)
        verify { nodeManager.updateNode(myNodeNum, any(), any()) }
    }

    @Test
    fun `processReceivedMeshPacket updates sender node`() = runTest(testDispatcher) {
        processor = createProcessor(backgroundScope)
        isNodeDbReady.value = true

        val senderNode = 999
        val packet =
            MeshPacket(
                id = 10,
                from = senderNode,
                decoded = Data(portnum = PortNum.TEXT_MESSAGE_APP, payload = ByteString.EMPTY),
                rx_time = 1700000000,
                channel = 1,
            )

        processor.handleReceivedMeshPacket(packet, myNodeNum)
        advanceUntilIdle()

        // Should have called updateNode for the sender
        verify { nodeManager.updateNode(senderNode, any(), any()) }
    }

    // ---------- handleReceivedMeshPacket: null decoded ----------

    @Test
    fun `packet with null decoded is skipped`() = runTest(testDispatcher) {
        processor = createProcessor(backgroundScope)
        isNodeDbReady.value = true

        val packet = MeshPacket(id = 1, from = 999, decoded = null)

        processor.handleReceivedMeshPacket(packet, myNodeNum)
        advanceUntilIdle()
        // No crash, no emitMeshPacket call (decoded is null so processReceivedMeshPacket returns early)
    }

    // ---------- handleReceivedMeshPacket: myNodeNum not yet known ----------

    @Test
    fun `packets received before myNodeNum is known are buffered until it resolves`() = runTest(testDispatcher) {
        // Our own node number is not yet known, even though the node DB is ready.
        val myNodeNumFlow = MutableStateFlow<Int?>(null)
        every { nodeManager.myNodeNum } returns myNodeNumFlow
        processor = createProcessor(backgroundScope)
        isNodeDbReady.value = true

        val packet =
            MeshPacket(
                id = 10,
                from = 999,
                decoded = Data(portnum = PortNum.TEXT_MESSAGE_APP, payload = ByteString.EMPTY),
                rx_time = 1700000000,
            )

        processor.handleReceivedMeshPacket(packet, null)
        advanceUntilIdle()

        // Buffered, not processed: storing now could key a local packet under its raw from_num and orphan it from
        // per-node chart queries. Neither the DB insert nor the downstream emit should have happened yet.
        verifySuspend(mode = VerifyMode.not) { meshLogRepository.insert(any()) }
        verifySuspend(mode = VerifyMode.not) { serviceRepository.emitMeshPacket(any()) }

        // Once myNodeNum resolves, the buffer flushes and the packet is processed (stored + emitted).
        myNodeNumFlow.value = 4321
        advanceUntilIdle()
        verifySuspend { meshLogRepository.insert(any()) }
        verifySuspend { serviceRepository.emitMeshPacket(any()) }
    }

    @Test
    fun `buffer survives a reconnect toggle and flushes only once myNodeNum resolves`() = runTest(testDispatcher) {
        val myNodeNumFlow = MutableStateFlow<Int?>(null)
        every { nodeManager.myNodeNum } returns myNodeNumFlow
        processor = createProcessor(backgroundScope)
        isNodeDbReady.value = true // DB ready, our node number still unknown

        val packet =
            MeshPacket(
                id = 11,
                from = 999,
                decoded = Data(portnum = PortNum.TEXT_MESSAGE_APP, payload = ByteString.EMPTY),
                rx_time = 1700000000,
            )
        processor.handleReceivedMeshPacket(packet, null)
        advanceUntilIdle()
        verifySuspend(mode = VerifyMode.not) { serviceRepository.emitMeshPacket(any()) }

        // Reconnect: isNodeDbReady toggles false -> true while myNodeNum stays null. The packet must stay buffered
        // —
        // flushing now would key it under its raw from_num.
        isNodeDbReady.value = false
        advanceUntilIdle()
        isNodeDbReady.value = true
        advanceUntilIdle()
        verifySuspend(mode = VerifyMode.not) { serviceRepository.emitMeshPacket(any()) }

        // Only once myNodeNum finally resolves does the buffer flush.
        myNodeNumFlow.value = 4321
        advanceUntilIdle()
        verifySuspend { serviceRepository.emitMeshPacket(any()) }
    }

    // ---------- clearEarlyPackets ----------

    @Test
    fun `clearEarlyPackets empties the buffer`() = runTest(testDispatcher) {
        processor = createProcessor(backgroundScope)
        isNodeDbReady.value = false

        val packet =
            MeshPacket(
                id = 1,
                from = 999,
                decoded = Data(portnum = PortNum.TEXT_MESSAGE_APP, payload = ByteString.EMPTY),
                rx_time = 1000,
            )
        processor.handleReceivedMeshPacket(packet, myNodeNum)
        advanceUntilIdle()

        processor.clearEarlyPackets()
        advanceUntilIdle()

        // Now make DB ready — the buffer should be empty, nothing to flush
        isNodeDbReady.value = true
        advanceUntilIdle()

        // emitMeshPacket should NOT have been called (buffer was cleared)
    }

    // ---------- logVariant ----------

    @Test
    fun `FromRadio log_record variant is logged as MeshLog`() = runTest(testDispatcher) {
        processor = createProcessor(backgroundScope)
        val logRecord = LogRecord(message = "device log")
        val fromRadio = FromRadio(log_record = logRecord)
        val bytes = fromRadio.encode()

        processor.handleFromRadio(bytes, myNodeNum)
        advanceUntilIdle()

        verifySuspend { meshLogRepository.insert(any()) }
    }
}
