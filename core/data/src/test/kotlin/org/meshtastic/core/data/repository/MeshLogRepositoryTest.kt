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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.meshtastic.core.database.DatabaseManager
import org.meshtastic.core.database.MeshtasticDatabase
import org.meshtastic.core.database.dao.MeshLogDao
import org.meshtastic.core.database.entity.MeshLog
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.prefs.meshlog.MeshLogPrefs
import org.meshtastic.proto.MeshProtos.Data
import org.meshtastic.proto.MeshProtos.FromRadio
import org.meshtastic.proto.MeshProtos.MeshPacket
import org.meshtastic.proto.Portnums.PortNum
import org.meshtastic.proto.TelemetryProtos.EnvironmentMetrics
import org.meshtastic.proto.TelemetryProtos.Telemetry
import java.util.UUID

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
        val envMetrics = EnvironmentMetrics.newBuilder().setTemperature(zeroTemp).build()
        val telemetry = Telemetry.newBuilder().setEnvironmentMetrics(envMetrics).build()

        val meshPacket =
            MeshPacket.newBuilder()
                .setDecoded(
                    Data.newBuilder().setPayload(telemetry.toByteString()).setPortnum(PortNum.TELEMETRY_APP),
                )
                .build()

        val meshLog =
            MeshLog(
                uuid = UUID.randomUUID().toString(),
                message_type = "telemetry",
                received_date = System.currentTimeMillis(),
                raw_message = "",
                fromRadio = FromRadio.newBuilder().setPacket(meshPacket).build(),
            )

        // Using reflection to test private method parseTelemetryLog
        val method = MeshLogRepository::class.java.getDeclaredMethod("parseTelemetryLog", MeshLog::class.java)
        method.isAccessible = true
        val result = method.invoke(repository, meshLog) as Telemetry?

        assertNotNull(result)
        val resultMetrics = result?.environmentMetrics
        assertNotNull(resultMetrics)
        assertEquals(zeroTemp, resultMetrics?.temperature!!, 0.01f)
    }

    @Test
    fun `parseTelemetryLog maps missing temperature to NaN`() = runTest(testDispatcher) {
        val envMetrics = EnvironmentMetrics.newBuilder().build() // Temperature not set
        val telemetry = Telemetry.newBuilder().setEnvironmentMetrics(envMetrics).build()

        val meshPacket =
            MeshPacket.newBuilder()
                .setDecoded(
                    Data.newBuilder().setPayload(telemetry.toByteString()).setPortnum(PortNum.TELEMETRY_APP),
                )
                .build()

        val meshLog =
            MeshLog(
                uuid = UUID.randomUUID().toString(),
                message_type = "telemetry",
                received_date = System.currentTimeMillis(),
                raw_message = "",
                fromRadio = FromRadio.newBuilder().setPacket(meshPacket).build(),
            )

        val method = MeshLogRepository::class.java.getDeclaredMethod("parseTelemetryLog", MeshLog::class.java)
        method.isAccessible = true
        val result = method.invoke(repository, meshLog) as Telemetry?

        assertNotNull(result)
        val resultMetrics = result?.environmentMetrics

        // Should be NaN as per repository logic for missing fields
        assertEquals(Float.NaN, resultMetrics?.temperature!!, 0.01f)
    }
}
