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
import org.meshtastic.core.testing.FakeDatabaseProvider
import org.meshtastic.core.testing.FakeMeshLogPrefs
import org.meshtastic.proto.Data
import org.meshtastic.proto.EnvironmentMetrics
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Telemetry
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

abstract class CommonMeshLogRepositoryTest {

    protected lateinit var dbProvider: FakeDatabaseProvider
    protected lateinit var meshLogPrefs: FakeMeshLogPrefs
    protected lateinit var nodeInfoReadDataSource: NodeInfoReadDataSource
    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = CoroutineDispatchers(main = testDispatcher, io = testDispatcher, default = testDispatcher)

    protected lateinit var repository: MeshLogRepositoryImpl

    private val nowMillis = 1000000000L

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
}
