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
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.data.datasource.NodeInfoReadDataSource
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.MeshLog
import org.meshtastic.core.model.util.TELEMETRY_CHANNEL_COUNT
import org.meshtastic.core.model.util.adcVoltage
import org.meshtastic.core.model.util.oneWireTemperature
import org.meshtastic.core.repository.MeshLogRetention
import org.meshtastic.core.testing.FakeDatabaseProvider
import org.meshtastic.core.testing.FakeMeshLogPrefs
import org.meshtastic.proto.Data
import org.meshtastic.proto.DeviceMetrics
import org.meshtastic.proto.EnvironmentMetrics
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.LocalStats
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Telemetry
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import org.meshtastic.core.common.util.nowMillis as realNowMillis

abstract class CommonMeshLogRepositoryTest {

    protected lateinit var dbProvider: FakeDatabaseProvider
    protected lateinit var meshLogPrefs: FakeMeshLogPrefs
    protected lateinit var nodeInfoReadDataSource: NodeInfoReadDataSource
    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = CoroutineDispatchers(main = testDispatcher, io = testDispatcher, default = testDispatcher)

    protected lateinit var repository: MeshLogRepositoryImpl

    private val nowMillis = 1000000000L

    @BeforeTest
    fun setupRepo() {
        dbProvider = FakeDatabaseProvider()
        meshLogPrefs = FakeMeshLogPrefs()
        meshLogPrefs.setLoggingEnabled(true)
        nodeInfoReadDataSource = mock(MockMode.autofill)

        every { nodeInfoReadDataSource.myNodeInfoFlow() } returns MutableStateFlow(null)

        repository = MeshLogRepositoryImpl(dbProvider, dispatchers, meshLogPrefs, nodeInfoReadDataSource)
    }

    @AfterTest
    fun tearDown() {
        dbProvider.close()
    }

    @Test
    fun `parseTelemetryLog preserves zero temperature`() = runTest(testDispatcher) {
        val zeroTemp = 0.0f
        val telemetry = Telemetry(environment_metrics = EnvironmentMetrics(temperature = zeroTemp))

        val meshPacket =
            MeshPacket(decoded = Data(payload = telemetry.encode().toByteString(), portnum = PortNum.TELEMETRY_APP))

        val meshLog =
            MeshLog(
                uuid = "123",
                message_type = "telemetry",
                received_date = nowMillis,
                raw_message = "",
                fromNum = 0,
                portNum = PortNum.TELEMETRY_APP.value,
                fromRadio = FromRadio(packet = meshPacket),
            )

        repository.insert(meshLog)

        val result = repository.getTelemetryFrom(0).first()

        assertNotNull(result)
        assertEquals(1, result.size)
        val resultMetrics = result[0].environment_metrics
        assertNotNull(resultMetrics)
        assertEquals(zeroTemp, resultMetrics.temperature ?: 0f, 0.01f)
    }

    @Test
    fun `deleteLogs redirects local node number to NODE_NUM_LOCAL`() = runTest(testDispatcher) {
        val localNodeNum = 999
        val port = PortNum.TEXT_MESSAGE_APP.value
        val myNodeEntity =
            MyNodeEntity(
                myNodeNum = localNodeNum,
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
        every { nodeInfoReadDataSource.myNodeInfoFlow() } returns MutableStateFlow(myNodeEntity)

        val log =
            MeshLog(
                uuid = "123",
                message_type = "TEXT",
                received_date = nowMillis,
                raw_message = "",
                fromNum =
                0, // asEntity will map it if we pass localNodeNum to asEntity, but here we set it manually
                portNum = port,
                fromRadio =
                FromRadio(
                    packet = MeshPacket(from = localNodeNum, decoded = Data(portnum = PortNum.TEXT_MESSAGE_APP)),
                ),
            )
        repository.insert(log)

        // Verify it's there
        assertEquals(1, repository.getAllLogsUnbounded().first().size)

        repository.deleteLogs(localNodeNum, port)

        val logs = repository.getAllLogsUnbounded().first()
        assertTrue(logs.isEmpty())
    }

    @Test
    fun `deleteLocalStatsLogs deletes only local stats telemetry`() = runTest(testDispatcher) {
        val nodeNum = 1234
        val localStatsLog =
            telemetryLog(
                uuid = "local-stats",
                nodeNum = nodeNum,
                telemetry = Telemetry(local_stats = LocalStats(noise_floor = -112)),
                receivedDate = nowMillis + 3,
            )
        val deviceLog =
            telemetryLog(
                uuid = "device",
                nodeNum = nodeNum,
                telemetry = Telemetry(device_metrics = DeviceMetrics(battery_level = 80)),
                receivedDate = nowMillis + 2,
            )
        val environmentLog =
            telemetryLog(
                uuid = "environment",
                nodeNum = nodeNum,
                telemetry = Telemetry(environment_metrics = EnvironmentMetrics(temperature = 21f)),
                receivedDate = nowMillis + 1,
            )
        val localStatsRequestLog =
            telemetryLog(
                uuid = "local-stats-request",
                nodeNum = nodeNum,
                telemetry = Telemetry(local_stats = LocalStats()),
                receivedDate = nowMillis,
                wantResponse = true,
            )

        listOf(localStatsLog, deviceLog, environmentLog, localStatsRequestLog).forEach { repository.insert(it) }

        repository.deleteLocalStatsLogs(nodeNum)

        val remainingIds = repository.getAllLogsUnbounded().first().map { it.uuid }.toSet()
        assertEquals(setOf("device", "environment", "local-stats-request"), remainingIds)
    }

    @Test
    fun `deleteLogsOlderThan one hour sentinel keeps the last hour instead of wiping the table`() =
        runTest(testDispatcher) {
            val now = realNowMillis
            repository.insert(retentionLog("recent", now - 30.minutes.inWholeMilliseconds))
            repository.insert(retentionLog("stale", now - 2.hours.inWholeMilliseconds))

            repository.deleteLogsOlderThan(MeshLogRetention.ONE_HOUR)

            assertEquals(setOf("recent"), repository.getAllLogsUnbounded().first().map { it.uuid }.toSet())
        }

    @Test
    fun `deleteLogsOlderThan keep forever sentinel deletes nothing`() = runTest(testDispatcher) {
        val now = realNowMillis
        repository.insert(retentionLog("ancient", now - 400.days.inWholeMilliseconds))
        repository.insert(retentionLog("recent", now))

        repository.deleteLogsOlderThan(MeshLogRetention.KEEP_FOREVER)

        assertEquals(setOf("ancient", "recent"), repository.getAllLogsUnbounded().first().map { it.uuid }.toSet())
    }

    @Test
    fun `deleteLogsOlderThan trims to the configured day count`() = runTest(testDispatcher) {
        val now = realNowMillis
        repository.insert(retentionLog("within", now - 6.days.inWholeMilliseconds))
        repository.insert(retentionLog("outside", now - 8.days.inWholeMilliseconds))

        repository.deleteLogsOlderThan(7)

        assertEquals(setOf("within"), repository.getAllLogsUnbounded().first().map { it.uuid }.toSet())
    }

    /** Retention is measured against the real clock, so these rows are stamped relative to it. */
    private fun retentionLog(uuid: String, receivedDate: Long) =
        MeshLog(uuid = uuid, message_type = "TEXT", received_date = receivedDate, raw_message = "")

    @Test
    fun `parseTelemetryLog lifts legacy one-wire list onto per-channel fields`() = runTest(testDispatcher) {
        // Firmware before 2.8 emitted the repeated field; stored logs must still chart after the repoint.
        @Suppress("DEPRECATION")
        val telemetry =
            Telemetry(environment_metrics = EnvironmentMetrics(one_wire_temperature = listOf(11f, 0f, 33f)))
        repository.insert(telemetryLog("legacy-one-wire", 0, telemetry, nowMillis))

        val metrics = repository.getTelemetryFrom(0).first().single().environment_metrics
        assertNotNull(metrics)

        assertEquals(11f, metrics.oneWireTemperature(0)!!, 0.01f)
        // A stored 0°C is a real reading, so it must survive the lift rather than reading as absent.
        assertEquals(0f, metrics.oneWireTemperature(1)!!, 0.01f)
        assertEquals(33f, metrics.oneWireTemperature(2)!!, 0.01f)
        // Channels the legacy list never carried normalize to the NaN the charts filter on.
        assertTrue(metrics.oneWireTemperature(3)!!.isNaN())
    }

    @Test
    fun `parseTelemetryLog normalizes absent per-channel readings to NaN`() = runTest(testDispatcher) {
        val telemetry = Telemetry(environment_metrics = EnvironmentMetrics(temperature = 21f))
        repository.insert(telemetryLog("absent-channels", 0, telemetry, nowMillis))

        val metrics = repository.getTelemetryFrom(0).first().single().environment_metrics
        assertNotNull(metrics)

        for (channel in 0 until TELEMETRY_CHANNEL_COUNT) {
            assertTrue(metrics.oneWireTemperature(channel)!!.isNaN(), "1-Wire ch$channel should be NaN")
            assertTrue(metrics.adcVoltage(channel)!!.isNaN(), "ADC ch$channel should be NaN")
        }
    }

    @Test
    fun `parseTelemetryLog preserves zero per-channel readings`() = runTest(testDispatcher) {
        // 0 V on an unloaded ADC input and 0°C on a probe are measurements, not "no sensor" sentinels.
        val telemetry =
            Telemetry(environment_metrics = EnvironmentMetrics(one_wire_temperature_ch0 = 0f, adc_voltage_ch0 = 0f))
        repository.insert(telemetryLog("zero-channels", 0, telemetry, nowMillis))

        val metrics = repository.getTelemetryFrom(0).first().single().environment_metrics
        assertNotNull(metrics)

        assertEquals(0f, metrics.oneWireTemperature(0)!!, 0.01f)
        assertEquals(0f, metrics.adcVoltage(0)!!, 0.01f)
    }

    private fun telemetryLog(
        uuid: String,
        nodeNum: Int,
        telemetry: Telemetry,
        receivedDate: Long,
        wantResponse: Boolean = false,
    ) = MeshLog(
        uuid = uuid,
        message_type = "telemetry",
        received_date = receivedDate,
        raw_message = "",
        fromNum = nodeNum,
        portNum = PortNum.TELEMETRY_APP.value,
        fromRadio =
        FromRadio(
            packet =
            MeshPacket(
                from = nodeNum,
                decoded =
                Data(
                    payload = telemetry.encode().toByteString(),
                    portnum = PortNum.TELEMETRY_APP,
                    want_response = wantResponse,
                ),
            ),
        ),
    )
}
