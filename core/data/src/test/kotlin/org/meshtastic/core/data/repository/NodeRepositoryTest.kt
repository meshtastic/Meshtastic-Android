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

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.coroutineScope
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.meshtastic.core.data.datasource.NodeInfoReadDataSource
import org.meshtastic.core.data.datasource.NodeInfoWriteDataSource
import org.meshtastic.core.database.entity.MeshLog
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.datastore.LocalStatsDataSource
import org.meshtastic.core.di.CoroutineDispatchers

@OptIn(ExperimentalCoroutinesApi::class)
class NodeRepositoryTest {

    private val readDataSource: NodeInfoReadDataSource = mockk(relaxed = true)
    private val writeDataSource: NodeInfoWriteDataSource = mockk(relaxed = true)
    private val lifecycle: Lifecycle = mockk(relaxed = true)
    private val lifecycleScope: LifecycleCoroutineScope = mockk()
    private val localStatsDataSource: LocalStatsDataSource = mockk(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()
    private val dispatchers = CoroutineDispatchers(main = testDispatcher, io = testDispatcher, default = testDispatcher)

    private val myNodeInfoFlow = MutableStateFlow<MyNodeEntity?>(null)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic("androidx.lifecycle.LifecycleKt")
        every { lifecycleScope.coroutineContext } returns testDispatcher + Job()
        every { lifecycle.coroutineScope } returns lifecycleScope
        every { readDataSource.myNodeInfoFlow() } returns myNodeInfoFlow
        every { readDataSource.nodeDBbyNumFlow() } returns MutableStateFlow(emptyMap())
    }

    @After
    fun tearDown() {
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

        val repository =
            NodeRepository(lifecycle, readDataSource, writeDataSource, dispatchers, localStatsDataSource)
        testScheduler.runCurrent()

        val result = repository.effectiveLogNodeId(myNodeNum).filter { it == MeshLog.NODE_NUM_LOCAL }.first()

        assertEquals(MeshLog.NODE_NUM_LOCAL, result)
    }

    @Test
    fun `effectiveLogNodeId preserves remote node numbers`() = runTest(testDispatcher) {
        val myNodeNum = 12345
        val remoteNodeNum = 67890
        myNodeInfoFlow.value = createMyNodeEntity(myNodeNum)

        val repository =
            NodeRepository(lifecycle, readDataSource, writeDataSource, dispatchers, localStatsDataSource)
        testScheduler.runCurrent()

        val result = repository.effectiveLogNodeId(remoteNodeNum).first()

        assertEquals(remoteNodeNum, result)
    }

    @Test
    fun `effectiveLogNodeId updates when local node number changes`() = runTest(testDispatcher) {
        val firstNodeNum = 111
        val secondNodeNum = 222
        val targetNodeNum = 111

        myNodeInfoFlow.value = createMyNodeEntity(firstNodeNum)
        val repository =
            NodeRepository(lifecycle, readDataSource, writeDataSource, dispatchers, localStatsDataSource)
        testScheduler.runCurrent()

        // Initially should be mapped to LOCAL because it matches
        assertEquals(
            MeshLog.NODE_NUM_LOCAL,
            repository.effectiveLogNodeId(targetNodeNum).filter { it == MeshLog.NODE_NUM_LOCAL }.first(),
        )

        // Change local node num
        myNodeInfoFlow.value = createMyNodeEntity(secondNodeNum)
        testScheduler.runCurrent()

        // Now it shouldn't match, so should return the original num
        assertEquals(
            targetNodeNum,
            repository.effectiveLogNodeId(targetNodeNum).filter { it == targetNodeNum }.first(),
        )
    }
}
