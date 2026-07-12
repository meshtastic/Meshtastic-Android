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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.database.DatabaseProvider
import org.meshtastic.core.database.MeshtasticDatabase
import org.meshtastic.core.database.entity.DiscoverySessionEntity
import org.meshtastic.core.database.getInMemoryDatabaseBuilder
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

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

    private fun session(timestamp: Long) =
        DiscoverySessionEntity(timestamp = timestamp, presetsScanned = "LongFast", homePreset = "LongFast")

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
    fun flowsRelatchOntoTheCurrentDb() = runTest {
        dao.insertSession(session(timestamp = 1))
        assertEquals(1, dao.getAllSessions().first().size, "flow observes the active DB (A)")

        provider.switchTo(dbB)

        assertEquals(0, dao.getAllSessions().first().size, "flow re-latches onto the new (empty) DB after switch")
    }

    /** Minimal [DatabaseProvider] whose active DB the test can swap, mirroring a device/DB switch. */
    private class TestProvider(db: MeshtasticDatabase) : DatabaseProvider {
        private val _currentDb = MutableStateFlow(db)
        override val currentDb: StateFlow<MeshtasticDatabase> = _currentDb

        override suspend fun <T> withDb(block: suspend (MeshtasticDatabase) -> T): T? = block(_currentDb.value)

        fun switchTo(db: MeshtasticDatabase) {
            _currentDb.value = db
        }
    }
}
