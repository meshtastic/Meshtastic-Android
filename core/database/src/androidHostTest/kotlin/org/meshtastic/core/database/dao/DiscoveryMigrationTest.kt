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
package org.meshtastic.core.database.dao

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.database.MeshtasticDatabase
import org.meshtastic.core.database.MeshtasticDatabaseConstructor
import org.meshtastic.core.database.entity.DiscoveredNodeEntity
import org.meshtastic.core.database.entity.DiscoveryPresetResultEntity
import org.meshtastic.core.database.entity.DiscoverySessionEntity
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Migration coverage for discovery tables (D011).
 *
 * Verifies that the discovery schema (version 39→40 auto-migration) creates the expected tables, supports CRUD
 * operations, enforces foreign key cascade behavior, and respects column defaults.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@Suppress("MagicNumber")
class DiscoveryMigrationTest {
    private lateinit var database: MeshtasticDatabase
    private lateinit var discoveryDao: DiscoveryDao

    @Before
    fun createDb() {
        database =
            Room.inMemoryDatabaseBuilder<MeshtasticDatabase>(factory = { MeshtasticDatabaseConstructor.initialize() })
                .setDriver(BundledSQLiteDriver())
                .build()
        discoveryDao = database.discoveryDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    // region Table creation and basic CRUD

    @Test
    fun discoverySessionTable_insertAndRetrieve() = runTest {
        val session =
            DiscoverySessionEntity(
                timestamp = 1_000_000L,
                presetsScanned = "LONG_FAST,SHORT_FAST",
                homePreset = "LONG_FAST",
                completionStatus = "complete",
            )
        val id = discoveryDao.insertSession(session)
        assertTrue(id > 0, "Insert should return positive auto-generated ID")
        val loaded = discoveryDao.getSession(id)
        assertNotNull(loaded)
        assertEquals("LONG_FAST,SHORT_FAST", loaded.presetsScanned)
        assertEquals("complete", loaded.completionStatus)
    }

    @Test
    fun discoveryPresetResultTable_insertAndRetrieve() = runTest {
        val sessionId = discoveryDao.insertSession(testSession())
        val result =
            DiscoveryPresetResultEntity(
                sessionId = sessionId,
                presetName = "LONG_FAST",
                dwellDurationSeconds = 30,
                uniqueNodes = 5,
                directNeighborCount = 3,
                meshNeighborCount = 2,
            )
        val resultId = discoveryDao.insertPresetResult(result)
        assertTrue(resultId > 0)
        val results = discoveryDao.getPresetResults(sessionId)
        assertEquals(1, results.size)
        assertEquals("LONG_FAST", results[0].presetName)
        assertEquals(5, results[0].uniqueNodes)
    }

    @Test
    fun discoveredNodeTable_insertAndRetrieve() = runTest {
        val sessionId = discoveryDao.insertSession(testSession())
        val presetId = discoveryDao.insertPresetResult(testPresetResult(sessionId))
        val node =
            DiscoveredNodeEntity(
                presetResultId = presetId,
                nodeNum = 12345,
                shortName = "TST",
                longName = "Test Node",
                neighborType = "direct",
                latitude = 37.7749,
                longitude = -122.4194,
                snr = 8.5f,
                rssi = -65,
            )
        val nodeId = discoveryDao.insertDiscoveredNode(node)
        assertTrue(nodeId > 0)
        val nodes = discoveryDao.getDiscoveredNodes(presetId)
        assertEquals(1, nodes.size)
        assertEquals(12345L, nodes[0].nodeNum)
        assertEquals("direct", nodes[0].neighborType)
    }

    // endregion

    // region Column defaults

    @Test
    fun sessionEntity_defaultValues() = runTest {
        // Insert with only required fields — verify defaults
        val session = DiscoverySessionEntity(timestamp = 1L, presetsScanned = "A", homePreset = "A")
        val id = discoveryDao.insertSession(session)
        val loaded = discoveryDao.getSession(id)!!
        assertEquals(0, loaded.totalUniqueNodes)
        assertEquals(0.0, loaded.avgChannelUtilization)
        assertEquals(0, loaded.totalMessages)
        assertEquals(0, loaded.totalSensorPackets)
        assertEquals(0.0, loaded.furthestNodeDistance)
        assertEquals("complete", loaded.completionStatus)
        assertNull(loaded.aiSummary)
        assertEquals(0.0, loaded.userLatitude)
        assertEquals(0.0, loaded.userLongitude)
        assertEquals(0L, loaded.totalDwellSeconds)
    }

    @Test
    fun presetResultEntity_defaultValues() = runTest {
        val sessionId = discoveryDao.insertSession(testSession())
        val result = DiscoveryPresetResultEntity(sessionId = sessionId, presetName = "TEST")
        val id = discoveryDao.insertPresetResult(result)
        val loaded = discoveryDao.getPresetResults(sessionId).first { it.id == id }
        assertEquals(0L, loaded.dwellDurationSeconds)
        assertEquals(0, loaded.uniqueNodes)
        assertEquals(0, loaded.directNeighborCount)
        assertEquals(0, loaded.meshNeighborCount)
        assertEquals(0, loaded.messageCount)
        assertEquals(0, loaded.sensorPacketCount)
        assertEquals(0.0, loaded.avgChannelUtilization)
        assertEquals(0.0, loaded.avgAirtimeRate)
        assertEquals(0.0, loaded.packetSuccessRate)
        assertEquals(0.0, loaded.packetFailureRate)
        assertEquals(0, loaded.numPacketsTx)
        assertEquals(0, loaded.numPacketsRx)
        assertEquals(0, loaded.numPacketsRxBad)
        assertEquals(0, loaded.numRxDupe)
        assertEquals(0, loaded.numTxRelay)
        assertEquals(0, loaded.numTxRelayCanceled)
        assertEquals(0, loaded.numOnlineNodes)
        assertEquals(0, loaded.numTotalNodes)
        assertEquals(0, loaded.uptimeSeconds)
        assertNull(loaded.aiSummary)
    }

    @Test
    fun discoveredNodeEntity_defaultValues() = runTest {
        val sessionId = discoveryDao.insertSession(testSession())
        val presetId = discoveryDao.insertPresetResult(testPresetResult(sessionId))
        val node = DiscoveredNodeEntity(presetResultId = presetId, nodeNum = 1)
        val nodeId = discoveryDao.insertDiscoveredNode(node)
        val loaded = discoveryDao.getDiscoveredNodes(presetId).first { it.id == nodeId }
        assertNull(loaded.shortName)
        assertNull(loaded.longName)
        assertEquals("direct", loaded.neighborType)
        assertNull(loaded.latitude)
        assertNull(loaded.longitude)
        assertNull(loaded.distanceFromUser)
        assertEquals(0, loaded.hopCount)
        assertEquals(0f, loaded.snr)
        assertEquals(0, loaded.rssi)
        assertEquals(0, loaded.messageCount)
        assertEquals(0, loaded.sensorPacketCount)
    }

    // endregion

    // region Foreign key cascade

    @Test
    fun deleteSession_cascadesPresetResultsAndNodes() = runTest {
        val sessionId = discoveryDao.insertSession(testSession())
        val presetId = discoveryDao.insertPresetResult(testPresetResult(sessionId))
        discoveryDao.insertDiscoveredNode(DiscoveredNodeEntity(presetResultId = presetId, nodeNum = 1))
        discoveryDao.insertDiscoveredNode(DiscoveredNodeEntity(presetResultId = presetId, nodeNum = 2))

        discoveryDao.deleteSession(sessionId)

        assertNull(discoveryDao.getSession(sessionId))
        assertTrue(discoveryDao.getPresetResults(sessionId).isEmpty())
        assertTrue(discoveryDao.getDiscoveredNodes(presetId).isEmpty())
    }

    // endregion

    // region Aggregate queries across migration-created schema

    @Test
    fun uniqueNodeCount_deduplicatesAcrossPresets() = runTest {
        val sessionId = discoveryDao.insertSession(testSession())
        val pre1 = discoveryDao.insertPresetResult(testPresetResult(sessionId, "LONG_FAST"))
        val pre2 = discoveryDao.insertPresetResult(testPresetResult(sessionId, "SHORT_FAST"))
        // Node 100 appears in both presets
        discoveryDao.insertDiscoveredNode(DiscoveredNodeEntity(presetResultId = pre1, nodeNum = 100))
        discoveryDao.insertDiscoveredNode(DiscoveredNodeEntity(presetResultId = pre1, nodeNum = 200))
        discoveryDao.insertDiscoveredNode(DiscoveredNodeEntity(presetResultId = pre2, nodeNum = 100))
        discoveryDao.insertDiscoveredNode(DiscoveredNodeEntity(presetResultId = pre2, nodeNum = 300))

        assertEquals(3, discoveryDao.getUniqueNodeCount(sessionId))
    }

    @Test
    fun getAllSessions_sortedNewestFirst() = runTest {
        discoveryDao.insertSession(testSession(timestamp = 100))
        discoveryDao.insertSession(testSession(timestamp = 300))
        discoveryDao.insertSession(testSession(timestamp = 200))

        val sessions = discoveryDao.getAllSessions().first()
        assertEquals(listOf(300L, 200L, 100L), sessions.map { it.timestamp })
    }

    // endregion

    // region Helpers

    private fun testSession(timestamp: Long = 1_000_000L) = DiscoverySessionEntity(
        timestamp = timestamp,
        presetsScanned = "LONG_FAST",
        homePreset = "LONG_FAST",
        completionStatus = "in_progress",
    )

    private fun testPresetResult(sessionId: Long, presetName: String = "LONG_FAST") =
        DiscoveryPresetResultEntity(sessionId = sessionId, presetName = presetName)

    // endregion
}
