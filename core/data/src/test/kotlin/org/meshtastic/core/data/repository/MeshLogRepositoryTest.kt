/*
 * Copyright (c) 2025 Meshtastic LLC
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

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.data.datasource.NodeInfoReadDataSource
import org.meshtastic.core.database.DatabaseManager
import org.meshtastic.core.database.MeshtasticDatabase
import org.meshtastic.core.database.dao.MeshLogDao
import org.meshtastic.core.database.entity.MeshLog
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.prefs.meshlog.MeshLogPrefs
import org.meshtastic.proto.Data
import org.meshtastic.proto.EnvironmentMetrics
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Telemetry
import kotlin.uuid.Uuid

class MeshLogRepositoryTest {

    private val dbManager: DatabaseManager = mockk()
    private val appDatabase: MeshtasticDatabase = mockk()
    private val meshLogDao: MeshLogDao = mockk()
    private val meshLogPrefs: MeshLogPrefs = mockk()
    private val nodeInfoReadDataSource: NodeInfoReadDataSource = mockk()
    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = CoroutineDispatchers(main = testDispatcher, io = testDispatcher, default = testDispatcher)

    private val repository = MeshLogRepository(dbManager, dispatchers, meshLogPrefs, nodeInfoReadDataSource)

    init {
        every { dbManager.currentDb } returns MutableStateFlow(appDatabase)
        every { appDatabase.meshLogDao() } returns meshLogDao
        every { nodeInfoReadDataSource.myNodeInfoFlow() } returns MutableStateFlow(null)
    }

    @Test
    fun `parseTelemetryLog preserves zero temperature`() = runTest(testDispatcher) {
        val zeroTemp = 0.0f
        val telemetry = Telemetry(environment_metrics = EnvironmentMetrics(temperature = zeroTemp))

        val meshPacket =
            MeshPacket(decoded = Data(payload = telemetry.encode().toByteString(), portnum = PortNum.TELEMETRY_APP))

        val meshLog =
            MeshLog(
                uuid = Uuid.random().toString(),
                message_type = "telemetry",
                received_date = nowMillis,
                raw_message = "",
                fromRadio = FromRadio(packet = meshPacket),
            )

        // Using reflection to test private method parseTelemetryLog
        val method = MeshLogRepository::class.java.getDeclaredMethod("parseTelemetryLog", MeshLog::class.java)
        method.isAccessible = true
        val result = method.invoke(repository, meshLog) as Telemetry?

        assertNotNull(result)
        val resultMetrics = result?.environment_metrics
        assertNotNull(resultMetrics)
        assertEquals(zeroTemp, resultMetrics?.temperature ?: 0f, 0.01f)
    }

    @Test
    fun `parseTelemetryLog maps missing temperature to NaN`() = runTest(testDispatcher) {
        val telemetry = Telemetry(environment_metrics = EnvironmentMetrics(temperature = null))

        val meshPacket =
            MeshPacket(decoded = Data(payload = telemetry.encode().toByteString(), portnum = PortNum.TELEMETRY_APP))

        val meshLog =
            MeshLog(
                uuid = Uuid.random().toString(),
                message_type = "telemetry",
                received_date = nowMillis,
                raw_message = "",
                fromRadio = FromRadio(packet = meshPacket),
            )

        val method = MeshLogRepository::class.java.getDeclaredMethod("parseTelemetryLog", MeshLog::class.java)
        method.isAccessible = true
        val result = method.invoke(repository, meshLog) as Telemetry?

        assertNotNull(result)
        val resultMetrics = result?.environment_metrics

        // Should be NaN as per repository logic for missing fields
        assertEquals(Float.NaN, resultMetrics?.temperature ?: 0f, 0.01f)
    }

    @Test
    fun `getRequestLogs filters correctly`() = runTest(testDispatcher) {
        val targetNode = 123
        val otherNode = 456
        val port = PortNum.TRACEROUTE_APP

        val logs =
            listOf(
                // Valid request
                MeshLog(
                    uuid = "1",
                    message_type = "Packet",
                    received_date = nowMillis,
                    raw_message = "",
                    fromNum = 0,
                    portNum = port.value,
                    fromRadio =
                    FromRadio(
                        packet =
                        MeshPacket(to = targetNode, decoded = Data(portnum = port, want_response = true)),
                    ),
                ),
                // Wrong target
                MeshLog(
                    uuid = "2",
                    message_type = "Packet",
                    received_date = nowMillis,
                    raw_message = "",
                    fromNum = 0,
                    portNum = port.value,
                    fromRadio =
                    FromRadio(
                        packet =
                        MeshPacket(to = otherNode, decoded = Data(portnum = port, want_response = true)),
                    ),
                ),
                // Not a request (want_response = false)
                MeshLog(
                    uuid = "3",
                    message_type = "Packet",
                    received_date = nowMillis,
                    raw_message = "",
                    fromNum = 0,
                    portNum = port.value,
                    fromRadio =
                    FromRadio(
                        packet =
                        MeshPacket(to = targetNode, decoded = Data(portnum = port, want_response = false)),
                    ),
                ),
                // Wrong fromNum
                MeshLog(
                    uuid = "4",
                    message_type = "Packet",
                    received_date = nowMillis,
                    raw_message = "",
                    fromNum = 789,
                    portNum = port.value,
                    fromRadio =
                    FromRadio(
                        packet =
                        MeshPacket(to = targetNode, decoded = Data(portnum = port, want_response = true)),
                    ),
                ),
            )

        every { meshLogDao.getLogsFrom(0, port.value, any()) } returns MutableStateFlow(logs)

        val result = repository.getRequestLogs(targetNode, port).first()

        assertEquals(1, result.size)
        assertEquals("1", result[0].uuid)
    }

    @Test
    fun `deleteLogs redirects local node number to NODE_NUM_LOCAL`() = runTest(testDispatcher) {
        val localNodeNum = 999
        val port = 100
        val myNodeEntity = mockk<MyNodeEntity>()
        every { myNodeEntity.myNodeNum } returns localNodeNum
        every { nodeInfoReadDataSource.myNodeInfoFlow() } returns MutableStateFlow(myNodeEntity)
        coEvery { meshLogDao.deleteLogs(any(), any()) } returns Unit

        repository.deleteLogs(localNodeNum, port)

        coVerify { meshLogDao.deleteLogs(MeshLog.NODE_NUM_LOCAL, port) }
    }

    @Test
    fun `deleteLogs preserves remote node numbers`() = runTest(testDispatcher) {
        val localNodeNum = 999
        val remoteNodeNum = 888
        val port = 100
        val myNodeEntity = mockk<MyNodeEntity>()
        every { myNodeEntity.myNodeNum } returns localNodeNum
        every { nodeInfoReadDataSource.myNodeInfoFlow() } returns MutableStateFlow(myNodeEntity)
        coEvery { meshLogDao.deleteLogs(any(), any()) } returns Unit

        repository.deleteLogs(remoteNodeNum, port)

        coVerify { meshLogDao.deleteLogs(remoteNodeNum, port) }
    }
}
