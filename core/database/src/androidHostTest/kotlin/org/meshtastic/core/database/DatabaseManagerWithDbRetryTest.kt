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
package org.meshtastic.core.database

import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.meshtastic.core.common.ContextServices
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.di.CoroutineDispatchers
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DatabaseManagerWithDbRetryTest {
    private val oldAddress = "AA:BB:CC:DD:EE:01"
    private val newAddress = "AA:BB:CC:DD:EE:02"

    private lateinit var manager: DatabaseManager
    private lateinit var datastoreName: String

    @BeforeTest
    fun setUp() {
        ContextServices.app = ApplicationProvider.getApplicationContext()
        datastoreName = "db-manager-retry-${System.nanoTime()}"
        manager =
            DatabaseManager(
                datastore = createDatabaseDataStore(datastoreName),
                dispatchers =
                CoroutineDispatchers(io = Dispatchers.IO, main = Dispatchers.IO, default = Dispatchers.Default),
            )
    }

    @AfterTest
    fun tearDown() {
        manager.close()
        deleteDatabase(DatabaseConstants.DEFAULT_DB_NAME)
        deleteDatabase(buildDbName(oldAddress))
        deleteDatabase(buildDbName(newAddress))
        ContextServices.app.preferencesDataStoreFile(datastoreName).delete()
    }

    @Test
    fun `withDb retries against current database when previous pool closes during switch`() = runTest {
        manager.switchActiveDatabase(oldAddress)
        val oldDb = manager.currentDb.value
        val started = CompletableDeferred<Unit>()
        val continueFirstAttempt = CompletableDeferred<Unit>()
        val visitedDbs = mutableListOf<MeshtasticDatabase>()
        var attempts = 0

        val result = async {
            manager.withDb { db ->
                visitedDbs += db
                when (++attempts) {
                    1 -> {
                        started.complete(Unit)
                        continueFirstAttempt.await()
                    }
                }
                db.nodeInfoDao().getMyNodeInfo().first()?.myNodeNum
            }
        }

        started.await()

        manager.switchActiveDatabase(newAddress)
        val newDb = manager.currentDb.value
        newDb.nodeInfoDao().setMyNodeInfo(newMyNodeInfo)

        oldDb.close()
        continueFirstAttempt.complete(Unit)

        assertEquals(newMyNodeInfo.myNodeNum, result.await())
        assertEquals(2, attempts)
        assertSame(oldDb, visitedDbs.first())
        assertSame(newDb, visitedDbs.last())
    }

    private companion object {
        val newMyNodeInfo =
            MyNodeEntity(
                myNodeNum = 42424242,
                model = "TBEAM",
                firmwareVersion = "2.5.0",
                couldUpdate = false,
                shouldUpdate = false,
                currentPacketId = 1L,
                messageTimeoutMsec = 300000,
                minAppVersion = 1,
                maxChannels = 8,
                hasWifi = false,
            )
    }
}
