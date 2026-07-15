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
package org.meshtastic.core.data.repository

import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.data.datasource.NodeInfoReadDataSource
import org.meshtastic.core.data.manager.MeshMessageProcessorImpl
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.MeshLog
import org.meshtastic.core.repository.FromRadioPacketHandler
import org.meshtastic.core.repository.MeshDataHandler
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.repository.RadioSessionLease
import org.meshtastic.core.repository.ServiceStateWriter
import org.meshtastic.core.testing.FakeDatabaseProvider
import org.meshtastic.core.testing.FakeMeshLogPrefs
import org.meshtastic.proto.AirQualityMetrics
import org.meshtastic.proto.Data
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Telemetry
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Repro for the field report: a node's air-quality telemetry shows up in the in-app Debug log but never appears in the
 * Air Quality chart (PR #5701).
 *
 * Uses Brian's real packet: a TRANSPORT_INTERNAL TELEMETRY_APP packet from the locally-connected node (num [localNum])
 * carrying non-zero PM (pm10_standard=1, pm25_standard=2, pm100_standard=2).
 *
 * The chart reads [MeshLogRepositoryImpl.getTelemetryFrom], which resolves the viewed node through `effectiveLogId` to
 * [MeshLog.NODE_NUM_LOCAL] and filters `WHERE from_num = :fromNum`. The Debug screen reads the unfiltered
 * `getAllLogsUnbounded`. So the chart is sensitive to the stored `from_num` column; the Debug log is not.
 *
 * `MeshMessageProcessorImpl.processReceivedMeshPacket` stores `fromNum = if (packet.from == myNodeNum) NODE_NUM_LOCAL
 * else packet.from`, where the insert-time `myNodeNum` is `nodeManager.myNodeNum.value` read at packet arrival
 * (MeshServiceOrchestrator). That StateFlow starts null and is only set once MyNodeInfo is processed, so a local packet
 * that arrives during the null window is stored under its raw `from_num` and orphaned from the chart while still
 * visible in the Debug log.
 */
class AirQualityChartReproTest {

    private lateinit var dbProvider: FakeDatabaseProvider
    private lateinit var meshLogPrefs: FakeMeshLogPrefs
    private lateinit var nodeInfoReadDataSource: NodeInfoReadDataSource
    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = CoroutineDispatchers(main = testDispatcher, io = testDispatcher, default = testDispatcher)
    private lateinit var repository: MeshLogRepositoryImpl

    private val nowMillis = 1_000_000_000L

    /** Brian's connected node number, taken verbatim from his Debug log (`from=-93009324`). */
    private val localNum = -93009324

    private fun setup(myNodeNum: Int?) {
        dbProvider = FakeDatabaseProvider()
        meshLogPrefs = FakeMeshLogPrefs().apply { setLoggingEnabled(true) }
        nodeInfoReadDataSource = mock(MockMode.autofill)
        every { nodeInfoReadDataSource.myNodeInfoFlow() } returns MutableStateFlow(myNodeNum?.let(::myNodeEntity))
        repository = MeshLogRepositoryImpl(dbProvider, dispatchers, meshLogPrefs, nodeInfoReadDataSource)
    }

    @AfterTest
    fun tearDown() {
        if (::dbProvider.isInitialized) dbProvider.close()
    }

    private fun myNodeEntity(num: Int) = MyNodeEntity(
        myNodeNum = num,
        model = "model",
        firmwareVersion = "1.0",
        couldUpdate = false,
        shouldUpdate = false,
        currentPacketId = 0L,
        messageTimeoutMsec = 0,
        minAppVersion = 0,
        maxChannels = 0,
        hasWifi = false,
    )

    /** Brian's real reading: pm10_standard=1, pm25_standard=2, pm100_standard=2. */
    private fun brianAirQualityTelemetry() = Telemetry(
        air_quality_metrics =
        AirQualityMetrics(
            pm10_standard = 1,
            pm25_standard = 2,
            pm100_standard = 2,
            pm10_environmental = 1,
            pm25_environmental = 2,
            pm100_environmental = 2,
        ),
    )

    private fun airQualityPacket() = MeshPacket(
        from = localNum,
        rx_time = 1_700_000_000,
        decoded =
        Data(payload = brianAirQualityTelemetry().encode().toByteString(), portnum = PortNum.TELEMETRY_APP),
    )

    private fun airQualityLog(fromNum: Int) = MeshLog(
        uuid = "aq-$fromNum",
        message_type = "Packet",
        received_date = nowMillis,
        raw_message = "",
        fromNum = fromNum,
        portNum = PortNum.TELEMETRY_APP.value,
        fromRadio = FromRadio(packet = airQualityPacket()),
    )

    /** Checkpoint 1: the parse + query round-trip preserves the air-quality payload (rules out content loss). */
    @Test
    fun `checkpoint 1 - air quality payload survives the telemetry round-trip`() = runTest(testDispatcher) {
        setup(myNodeNum = null) // effectiveLogId(0) -> 0
        repository.insert(airQualityLog(fromNum = 0))

        val result = repository.getTelemetryFrom(0).first()

        assertEquals(1, result.size, "the air-quality telemetry row should round-trip")
        assertEquals(2, result[0].air_quality_metrics?.pm25_standard, "pm25_standard must survive decode")
    }

    /** Checkpoint 2: local-node air-quality stored under NODE_NUM_LOCAL is returned to the chart (the happy path). */
    @Test
    fun `checkpoint 2 - local AQ stored under NODE_NUM_LOCAL is charted`() = runTest(testDispatcher) {
        setup(myNodeNum = localNum) // viewing the local node -> effectiveLogId -> NODE_NUM_LOCAL
        // myNodeNum known at insert -> processReceivedMeshPacket would store NODE_NUM_LOCAL.
        repository.insert(airQualityLog(fromNum = MeshLog.NODE_NUM_LOCAL))

        val result = repository.getTelemetryFrom(localNum).first()

        assertEquals(1, result.size, "AQ stored under NODE_NUM_LOCAL should be visible to the local node's chart")
    }

    /**
     * Checkpoint 3 (query invariant — the rationale for the insert-side fix): the per-node chart query keys the local
     * node on NODE_NUM_LOCAL, so a row stored under the raw myNodeNum is not returned (though the unfiltered Debug log
     * still shows it). This is *why* the insert path must key local packets under NODE_NUM_LOCAL — verified in
     * checkpoint 4. This query behavior is intentional and unchanged by the fix.
     */
    @Test
    fun `checkpoint 3 - query keys local node on NODE_NUM_LOCAL not the raw from_num`() = runTest(testDispatcher) {
        setup(myNodeNum = localNum) // viewing the local node -> effectiveLogId -> NODE_NUM_LOCAL
        repository.insert(airQualityLog(fromNum = localNum)) // a hypothetical mis-keyed row

        // Debug screen (unfiltered) sees it:
        assertEquals(
            1,
            repository.getAllLogsUnbounded().first().size,
            "Debug log shows rows regardless of from_num",
        )

        // Chart query (from_num = NODE_NUM_LOCAL) does not — hence the insert must never produce a raw-keyed local
        // row:
        val charted = repository.getTelemetryFrom(localNum).first()
        assertTrue(charted.isEmpty(), "the local-node query only matches NODE_NUM_LOCAL")
    }

    /**
     * Checkpoint 4 (regression test for the fix, end-to-end through the real insert path): a local air-quality packet
     * received while myNodeNum is still null is BUFFERED (not stored under its raw from_num). Once myNodeNum resolves,
     * the buffer flushes and the packet is stored under NODE_NUM_LOCAL, so the local node's Air Quality chart sees it.
     *
     * Before the fix this packet was stored immediately under its raw from_num and orphaned from the chart.
     */
    @Test
    fun `checkpoint 4 - local AQ received before myNodeNum resolves is buffered then charted`() =
        runTest(testDispatcher) {
            setup(myNodeNum = localNum) // query side: phone is connected to localNum

            val myNodeNumFlow = MutableStateFlow<Int?>(null) // not yet resolved
            val nodeManager = mock<NodeManager>(MockMode.autofill)
            val radioInterfaceService = mock<RadioInterfaceService>(MockMode.autofill)
            val session = RadioSessionContext(generation = 1L, address = "test:air-quality")
            every { nodeManager.isNodeDbReady } returns MutableStateFlow(true)
            every { nodeManager.myNodeNum } returns myNodeNumFlow
            every { radioInterfaceService.isSessionActive(session) } returns true
            everySuspend { radioInterfaceService.runWhileSessionActive(session, any()) } calls
                {
                    @Suppress("UNCHECKED_CAST")
                    val block = it.args[1] as (suspend () -> Unit)
                    block()
                    true
                }
            everySuspend { radioInterfaceService.runWithSessionLease(session, any()) } calls
                {
                    @Suppress("UNCHECKED_CAST")
                    val block = it.args[1] as (suspend (RadioSessionLease) -> Unit)
                    block(
                        object : RadioSessionLease {
                            override val session: RadioSessionContext = session

                            override fun isCurrent(): Boolean = true
                        },
                    )
                    true
                }

            val processor =
                MeshMessageProcessorImpl(
                    nodeManager = nodeManager,
                    serviceStateWriter = mock<ServiceStateWriter>(MockMode.autofill),
                    meshLogRepository = lazy { repository },
                    dataHandler = lazy { mock<MeshDataHandler>(MockMode.autofill) },
                    fromRadioDispatcher = mock<FromRadioPacketHandler>(MockMode.autofill),
                    radioInterfaceService = radioInterfaceService,
                    scope = backgroundScope,
                )

            // Arrives before MyNodeInfo resolves -> buffered, NOT written to the log table (so not orphaned in the DB).
            processor.handleReceivedMeshPacket(airQualityPacket(), myNodeNum = null, session = session)
            advanceUntilIdle()
            assertEquals(0, repository.getAllLogsUnbounded().first().size, "packet should be buffered, not yet stored")

            // MyNodeInfo resolves -> the buffer flushes and the packet is stored under NODE_NUM_LOCAL.
            myNodeNumFlow.value = localNum
            advanceUntilIdle()

            val charted = repository.getTelemetryFrom(localNum).first()
            assertEquals(1, charted.size, "after myNodeNum resolves, the local AQ packet must reach the chart")
            assertEquals(
                2,
                charted[0].air_quality_metrics?.pm25_standard,
                "the real reading (pm25_standard=2) survives",
            )
        }
}
