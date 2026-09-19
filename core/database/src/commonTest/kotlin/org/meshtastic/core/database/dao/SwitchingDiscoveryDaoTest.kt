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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.database.DatabaseProvider
import org.meshtastic.core.database.MeshtasticDatabase
import org.meshtastic.core.database.entity.DiscoveredNodeEntity
import org.meshtastic.core.database.entity.DiscoveryPresetResultEntity
import org.meshtastic.core.database.entity.DiscoverySessionEntity
import org.meshtastic.core.database.entity.DiscoverySessionStatus
import org.meshtastic.core.database.getInMemoryDatabaseBuilder
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Regression for #6235: the DAO Koin hands to `feature:discovery` used to be pinned to the injection-time database, so
 * a device/DB switch left long-lived consumers reading (and writing!) the old DB. [SwitchingDiscoveryDao] must resolve
 * the active database per call/collection instead.
 */
class SwitchingDiscoveryDaoTest {

    private val dbA: MeshtasticDatabase = getInMemoryDatabaseBuilder().build()
    private val dbB: MeshtasticDatabase = getInMemoryDatabaseBuilder().build()
    private val provider = TestProvider(dbA)
    private val dao = SwitchingDiscoveryDao(provider)

    @AfterTest
    fun tearDown() {
        dbA.close()
        dbB.close()
    }

    private fun session(timestamp: Long, deviceAddress: String = DEVICE_A) = DiscoverySessionEntity(
        timestamp = timestamp,
        presetsScanned = "LongFast",
        homePreset = "LongFast",
        deviceAddress = deviceAddress,
    )

    @Test
    fun suspendCallsResolveTheCurrentDbPerCall() = runTest {
        dao.insertSession(session(timestamp = 1))
        assertEquals(1, dbA.discoveryDao().getAllSessionsSnapshot().size, "first insert lands in the active DB (A)")

        provider.switchTo(dbB)

        dao.insertSession(session(timestamp = 2))
        assertEquals(1, dbA.discoveryDao().getAllSessionsSnapshot().size, "old DB untouched after switch")
        assertEquals(1, dbB.discoveryDao().getAllSessionsSnapshot().size, "post-switch insert lands in the new DB (B)")
        assertEquals(1, dao.getAllSessionsSnapshot().size, "reads resolve the new DB too")
    }

    @Test
    fun statusUpdateReportsUnavailableDatabase() = runTest {
        val sessionId =
            dao.insertSession(session(timestamp = 1).copy(completionStatus = DiscoverySessionStatus.IN_PROGRESS))
        provider.setWritesAvailable(false)

        assertEquals(0, dao.updateSessionCompletionStatus(sessionId, DiscoverySessionStatus.FAILED))
        assertEquals(
            DiscoverySessionStatus.IN_PROGRESS,
            dbA.discoveryDao().getSession(sessionId)?.completionStatus,
            "an unavailable database must not be reported as a successful status write",
        )
    }

    @Test
    fun recoverableStatusUpdateReportsUnavailableDatabase() = runTest {
        val sessionId =
            dao.insertSession(session(timestamp = 1).copy(completionStatus = DiscoverySessionStatus.IN_PROGRESS))
        provider.setWritesAvailable(false)

        assertEquals(0, dao.updateRecoverableSessionCompletionStatus(sessionId, DiscoverySessionStatus.FAILED))
        assertEquals(
            DiscoverySessionStatus.IN_PROGRESS,
            dbA.discoveryDao().getSession(sessionId)?.completionStatus,
            "an unavailable database must not be reported as a successful recoverable status write",
        )
    }

    /**
     * Regression for the FK-787 crash (Crashlytics `d79ee407`). A caller cannot check the parent session and then
     * insert its child: the two calls resolve the active database independently, so a switch landing between them
     * writes a `discovery_preset_result` whose `session_id` has no row. `insertDwellIfSessionExists` must do both in
     * one resolution and report the missing parent instead of raising a constraint failure.
     */
    @Test
    fun dwellWriteAfterASwitchReportsTheMissingParentInsteadOfViolatingTheForeignKey() = runTest {
        val sessionId = dao.insertSession(session(timestamp = 1))
        assertEquals(1, dbA.discoveryDao().getAllSessionsSnapshot().size, "parent session lives in DB A")

        provider.switchTo(dbB)

        val presetResultId =
            dao.insertDwellIfSessionExists(
                result = DiscoveryPresetResultEntity(sessionId = sessionId, presetName = "LongFast"),
                nodes = emptyList(),
                deviceAddress = DEVICE_A,
            )

        assertNull(presetResultId, "a dwell whose session is not in the active DB must report, not insert")
        assertEquals(0, dbB.discoveryDao().getPresetResults(sessionId).size, "no orphan row in the new DB")
        assertEquals(0, dbA.discoveryDao().getPresetResults(sessionId).size, "and none written back to the old DB")
    }

    /** The same call must still write normally when the parent session is present. */
    @Test
    fun dwellWriteSucceedsWhenTheSessionIsInTheActiveDb() = runTest {
        val sessionId = dao.insertSession(session(timestamp = 1))

        val presetResultId =
            dao.insertDwellIfSessionExists(
                result = DiscoveryPresetResultEntity(sessionId = sessionId, presetName = "LongFast"),
                nodes = listOf(DiscoveredNodeEntity(presetResultId = 0L, nodeNum = 42L)),
                deviceAddress = DEVICE_A,
            )

        assertNotNull(presetResultId, "a dwell with a live parent session must be written")
        assertEquals(1, dbA.discoveryDao().getPresetResults(sessionId).size)
        val nodes = dbA.discoveryDao().getDiscoveredNodes(presetResultId)
        assertEquals(1, nodes.size, "child nodes are stamped with the new preset-result id inside the transaction")
        assertEquals(42L, nodes.first().nodeNum)
    }

    /**
     * Every per-device database autogenerates session ids from 1, so an id alone is not an identity: DB A's session 1
     * and DB B's session 1 are unrelated scans. A parent check on the id alone passes after a switch and silently
     * attaches the dwell to the other radio's session, so the check matches the device address too.
     */
    @Test
    fun dwellWriteAfterASwitchRejectsAnUnrelatedSessionSharingTheId() = runTest {
        val sessionId = dao.insertSession(session(timestamp = 1))
        val otherId = dbB.discoveryDao().insertSession(session(timestamp = 2, deviceAddress = DEVICE_B))
        assertEquals(sessionId, otherId, "both databases autogenerate the same first id - that is the trap")

        provider.switchTo(dbB)

        val presetResultId =
            dao.insertDwellIfSessionExists(
                result = DiscoveryPresetResultEntity(sessionId = sessionId, presetName = "LongFast"),
                nodes = listOf(DiscoveredNodeEntity(presetResultId = 0L, nodeNum = 42L)),
                deviceAddress = DEVICE_A,
            )

        assertNull(presetResultId, "a session id matching another radio's session must not be treated as the parent")
        assertEquals(0, dbB.discoveryDao().getPresetResults(otherId).size, "the other radio's session gains no dwell")
        assertEquals(0, dbA.discoveryDao().getPresetResults(sessionId).size, "and none is written back to the old DB")
    }

    @Test
    fun flowsRelatchOntoTheCurrentDb() = runTest {
        dao.insertSession(session(timestamp = 1))

        // One live collection end-to-end: the first emission (DB A's session) triggers the switch, and the SAME
        // collector must then receive DB B's empty state. A delegate that resolved currentDb only at flow creation
        // would never emit the empty list, and the test would fail on runTest's timeout.
        var sawDbA = false
        val relatched =
            dao.getAllSessions()
                .onEach { sessions ->
                    if (sessions.isNotEmpty()) {
                        sawDbA = true
                        provider.switchTo(dbB)
                    }
                }
                .first { it.isEmpty() }

        assertTrue(sawDbA, "collector observed DB A's session before the switch")
        assertEquals(0, relatched.size, "the same collector re-latched onto the new (empty) DB")
    }

    private companion object {
        const val DEVICE_A = "x:AA:BB:CC:DD:EE:01"
        const val DEVICE_B = "x:AA:BB:CC:DD:EE:02"
    }

    /** Minimal [DatabaseProvider] whose active DB the test can swap, mirroring a device/DB switch. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private class TestProvider(db: MeshtasticDatabase) : DatabaseProvider {
        private val _currentDb = MutableStateFlow(db)
        private var writesAvailable = true
        override val currentDb: StateFlow<MeshtasticDatabase> = _currentDb

        override fun <T> observeCurrentDb(query: (MeshtasticDatabase) -> Flow<T>): Flow<T> =
            currentDb.flatMapLatest(query)

        override suspend fun <T> withReadDb(block: suspend (MeshtasticDatabase) -> T): T = block(_currentDb.value)

        override suspend fun <T> withDb(block: suspend (MeshtasticDatabase) -> T): T? =
            if (writesAvailable) block(_currentDb.value) else null

        fun setWritesAvailable(available: Boolean) {
            writesAvailable = available
        }

        fun switchTo(db: MeshtasticDatabase) {
            _currentDb.value = db
        }
    }
}
