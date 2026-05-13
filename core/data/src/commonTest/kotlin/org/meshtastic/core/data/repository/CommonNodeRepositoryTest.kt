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

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.data.datasource.NodeInfoReadDataSource
import org.meshtastic.core.data.datasource.NodeInfoWriteDataSource
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.database.entity.NodeWithRelations
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.MeshLog
import org.meshtastic.core.testing.FakeLocalStatsDataSource
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

abstract class CommonNodeRepositoryTest {

    protected lateinit var lifecycleOwner: LifecycleOwner
    protected lateinit var readDataSource: NodeInfoReadDataSource
    protected lateinit var writeDataSource: NodeInfoWriteDataSource
    protected lateinit var localStatsDataSource: FakeLocalStatsDataSource
    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = CoroutineDispatchers(main = testDispatcher, io = testDispatcher, default = testDispatcher)

    private val myNodeInfoFlow = MutableStateFlow<MyNodeEntity?>(null)

    protected lateinit var repository: NodeRepositoryImpl

    fun setupRepo() {
        Dispatchers.setMain(testDispatcher)
        lifecycleOwner =
            object : LifecycleOwner {
                override val lifecycle = LifecycleRegistry(this)
            }
        (lifecycleOwner.lifecycle as LifecycleRegistry).handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        readDataSource = mock(MockMode.autofill)
        writeDataSource = mock(MockMode.autofill)
        localStatsDataSource = FakeLocalStatsDataSource()

        every { readDataSource.myNodeInfoFlow() } returns myNodeInfoFlow
        every { readDataSource.nodeDBbyNumFlow() } returns MutableStateFlow<Map<Int, NodeWithRelations>>(emptyMap())

        repository =
            NodeRepositoryImpl(
                lifecycleOwner.lifecycle,
                readDataSource,
                writeDataSource,
                dispatchers,
                localStatsDataSource,
            )
    }

    @AfterTest
    fun tearDown() {
        // Essential to stop background jobs in NodeRepositoryImpl
        (lifecycleOwner.lifecycle as LifecycleRegistry).handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        Dispatchers.resetMain()
    }

    private fun createMyNodeEntity(nodeNum: Int) = MyNodeEntity(
        myNodeNum = nodeNum,
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

    @Test
    fun `effectiveLogNodeId maps local node number to NODE_NUM_LOCAL`() = runTest(testDispatcher) {
        val myNodeNum = 12345
        myNodeInfoFlow.value = createMyNodeEntity(myNodeNum)

        val result = repository.effectiveLogNodeId(myNodeNum).filter { it == MeshLog.NODE_NUM_LOCAL }.first()

        assertEquals(MeshLog.NODE_NUM_LOCAL, result)
    }

    @Test
    fun `effectiveLogNodeId preserves remote node numbers`() = runTest(testDispatcher) {
        val myNodeNum = 12345
        val remoteNodeNum = 67890
        myNodeInfoFlow.value = createMyNodeEntity(myNodeNum)

        val result = repository.effectiveLogNodeId(remoteNodeNum).first()

        assertEquals(remoteNodeNum, result)
    }
}
