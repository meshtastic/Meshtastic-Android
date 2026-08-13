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
package org.meshtastic.core.data.datasource

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.data.repository.NodeRepositoryImpl
import org.meshtastic.core.database.DatabaseProvider
import org.meshtastic.core.database.MeshtasticDatabase
import org.meshtastic.core.database.entity.MetadataEntity
import org.meshtastic.core.database.entity.NodeEntity
import org.meshtastic.core.database.getInMemoryDatabaseBuilder
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.testing.FakeLocalStatsDataSource
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.User
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NodeDeletionDatabaseSwitchTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = CoroutineDispatchers(main = testDispatcher, io = testDispatcher, default = testDispatcher)
    private lateinit var dbA: MeshtasticDatabase
    private lateinit var dbB: MeshtasticDatabase
    private lateinit var provider: SwitchAfterFirstWriteProvider
    private lateinit var lifecycleOwner: TestLifecycleOwner
    private lateinit var repository: NodeRepositoryImpl

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        dbA = getInMemoryDatabaseBuilder().build()
        dbB = getInMemoryDatabaseBuilder().build()
        provider = SwitchAfterFirstWriteProvider(dbA, dbB)
        lifecycleOwner = TestLifecycleOwner()
        lifecycleOwner.moveTo(Lifecycle.Event.ON_CREATE)
        val switchingWriteDataSource = SwitchingNodeInfoWriteDataSource(provider, dispatchers)
        val writeDataSourceWithoutStartupBackfill =
            object : NodeInfoWriteDataSource by switchingWriteDataSource {
                override suspend fun backfillDenormalizedNames() = Unit
            }
        repository =
            NodeRepositoryImpl(
                processLifecycle = lifecycleOwner.lifecycle,
                nodeInfoReadDataSource = SwitchingNodeInfoReadDataSource(provider),
                nodeInfoWriteDataSource = writeDataSourceWithoutStartupBackfill,
                dispatchers = dispatchers,
                localStatsDataSource = FakeLocalStatsDataSource(),
            )
        provider.arm()
    }

    @AfterTest
    fun tearDown() {
        lifecycleOwner.moveTo(Lifecycle.Event.ON_DESTROY)
        dbA.close()
        dbB.close()
        Dispatchers.resetMain()
    }

    @Test
    fun deleteNodeUsesOneDatabaseCaptureForNodeAndMetadata() = runTest(testDispatcher) {
        seedNodeAndMetadata(dbA, "A")
        seedNodeAndMetadata(dbB, "B")

        repository.deleteNode(NODE_NUM)

        assertEquals(1, provider.withDbCalls, "one logical deletion must capture the active database exactly once")
        assertNull(dbA.nodeInfoDao().getNodeByNum(NODE_NUM), "database A node must be deleted")
        assertFalse(dbA.hasMetadata(NODE_NUM), "database A metadata must not be orphaned")
        assertNotNull(dbB.nodeInfoDao().getNodeByNum(NODE_NUM), "database B node must be untouched")
        assertTrue(dbB.hasMetadata(NODE_NUM), "database B metadata must be untouched")
    }

    private suspend fun seedNodeAndMetadata(database: MeshtasticDatabase, label: String) {
        database.nodeInfoDao().upsert(NodeEntity(num = NODE_NUM, user = User(id = "!node-$label")))
        database.nodeInfoDao().upsert(MetadataEntity(num = NODE_NUM, proto = DeviceMetadata(firmware_version = label)))
    }

    private suspend fun MeshtasticDatabase.hasMetadata(num: Int): Boolean =
        nodeInfoDao().getAllMetadataSnapshot().any { it.num == num }

    private class TestLifecycleOwner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry.createUnsafe(this)

        fun moveTo(event: Lifecycle.Event) {
            lifecycle.handleLifecycleEvent(event)
        }
    }

    /** Switches A -> B after the first armed callback, reproducing a device change between two logical writes. */
    private class SwitchAfterFirstWriteProvider(dbA: MeshtasticDatabase, private val dbB: MeshtasticDatabase) :
        DatabaseProvider {
        private val mutableCurrentDb = MutableStateFlow(dbA)
        override val currentDb: StateFlow<MeshtasticDatabase> = mutableCurrentDb
        private var armed = false
        var withDbCalls: Int = 0
            private set

        override fun <T> observeCurrentDb(query: (MeshtasticDatabase) -> Flow<T>): Flow<T> =
            currentDb.flatMapLatest(query)

        override suspend fun <T> withReadDb(block: suspend (MeshtasticDatabase) -> T): T = block(currentDb.value)

        override suspend fun <T> withDb(block: suspend (MeshtasticDatabase) -> T): T? {
            val capturedDb = currentDb.value
            val result = block(capturedDb)
            if (armed) {
                withDbCalls += 1
                if (withDbCalls == 1) mutableCurrentDb.value = dbB
            }
            return result
        }

        fun arm() {
            armed = true
            withDbCalls = 0
        }
    }

    private companion object {
        const val NODE_NUM = 0x1234
    }
}
