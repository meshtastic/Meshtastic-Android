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
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.data.datasource.NodeInfoReadDataSource
import org.meshtastic.core.data.manager.radioSessionProjection
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.MeshLog
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.testing.FakeDatabaseProvider
import org.meshtastic.core.testing.FakeMeshLogPrefs
import org.meshtastic.core.testing.FakeRadioInterfaceService
import org.meshtastic.proto.AirQualityMetrics
import org.meshtastic.proto.Data
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Telemetry
import org.meshtastic.sdk.RadioSessionEvent
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
 * `RadioSessionProjection` stores `fromNum = if (packet.from == myNodeNum) NODE_NUM_LOCAL else packet.from`, so a local
 * packet stored under its raw `from_num` would be orphaned from the chart while still visible in the Debug log.
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
    private fun brianAirQualityTelemetry() = Telemetry.Builder()
        .also { wb ->
            wb.air_quality_metrics =
                AirQualityMetrics.Builder()
                    .also { wb ->
                        wb.pm10_standard = 1
                        wb.pm25_standard = 2
                        wb.pm100_standard = 2
                        wb.pm10_environmental = 1
                        wb.pm25_environmental = 2
                        wb.pm100_environmental = 2
                    }
                    .build()
        }
        .build()

    private fun airQualityPacket() = MeshPacket.Builder()
        .also { wb ->
            wb.from = localNum
            wb.rx_time = 1_700_000_000
            wb.decoded =
                Data.Builder()
                    .also { wb ->
                        wb.payload = brianAirQualityTelemetry().encode().toByteString()
                        wb.portnum = PortNum.TELEMETRY_APP
                    }
                    .build()
        }
        .build()

    private fun airQualityLog(fromNum: Int) = MeshLog(
        uuid = "aq-$fromNum",
        message_type = "Packet",
        received_date = nowMillis,
        raw_message = "",
        fromNum = fromNum,
        portNum = PortNum.TELEMETRY_APP.value,
        fromRadio = FromRadio.Builder().also { wb -> wb.packet = airQualityPacket() }.build(),
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
     * Checkpoint 4 (end-to-end through the real insert path): the SDK installs the local identity at Ready before it
     * delivers any packet, so a local air-quality packet is stored under NODE_NUM_LOCAL and the chart sees it.
     */
    @Test
    fun `checkpoint 4 - local AQ projected after Ready is charted`() = runTest(testDispatcher) {
        setup(myNodeNum = localNum)

        val nodeManager = mock<NodeManager>(MockMode.autofill)
        every { nodeManager.myNodeNum } returns MutableStateFlow(localNum)
        val radio = FakeRadioInterfaceService(backgroundScope)
        radio.setDeviceAddress("tcp:air-quality")
        radio.connect()
        val session = checkNotNull(radio.activeSession.value)
        val projection = radioSessionProjection(nodeManager, radio, backgroundScope, meshLogRepository = repository)

        projection.project(
            RadioSessionEvent.Packet(sessionId = 1, sequence = 1, packet = airQualityPacket()),
            session,
        )
        advanceUntilIdle()

        val charted = repository.getTelemetryFrom(localNum).first()
        assertEquals(1, charted.size, "the local AQ packet must reach the chart")
        assertEquals(
            2,
            charted[0].air_quality_metrics?.pm25_standard,
            "the real reading (pm25_standard=2) survives",
        )
    }
}
