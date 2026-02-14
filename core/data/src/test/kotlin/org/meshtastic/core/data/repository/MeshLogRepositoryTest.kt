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

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.meshtastic.core.database.DatabaseManager
import org.meshtastic.core.database.MeshtasticDatabase
import org.meshtastic.core.database.dao.MeshLogDao
import org.meshtastic.core.database.entity.MeshLog
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
    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = CoroutineDispatchers(main = testDispatcher, io = testDispatcher, default = testDispatcher)

    private val repository = MeshLogRepository(dbManager, dispatchers, meshLogPrefs)

    init {
        every { dbManager.currentDb } returns MutableStateFlow(appDatabase)
        every { appDatabase.meshLogDao() } returns meshLogDao
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
                received_date = System.currentTimeMillis(),
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
                received_date = System.currentTimeMillis(),
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
}
