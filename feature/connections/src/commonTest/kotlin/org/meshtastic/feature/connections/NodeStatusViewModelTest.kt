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
package org.meshtastic.feature.connections

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.network.radio.NodeStatusRepository
import org.meshtastic.node.host.Bearer
import org.meshtastic.node.host.NodePhase
import org.meshtastic.node.host.NodeStatus
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class NodeStatusViewModelTest {

    private class FakeRepository : NodeStatusRepository {
        override val status = MutableStateFlow<NodeStatus?>(null)
        val hostBearerWrites = mutableListOf<Pair<Bearer, Boolean>>()
        var refreshes = 0
        var usbRequests = 0

        override suspend fun setHostBearer(bearer: Bearer, on: Boolean) {
            hostBearerWrites += bearer to on
        }

        override fun refreshRequirements() {
            refreshes++
        }

        override fun requestUsbPermission() {
            usbRequests++
        }
    }

    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = FakeRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `status mirrors the repository and is null without a node`() = runTest(dispatcher) {
        val viewModel = NodeStatusViewModel(repository)
        viewModel.status.test {
            assertNull(awaitItem())
            repository.status.value = NodeStatus(NodePhase.Running, 7L, "Phone", "PHON", emptyMap(), 2)
            val ui = awaitItem()
            assertEquals(NodePhase.Running, ui?.phase)
            assertEquals("!00000007", ui?.nodeId)
            repository.status.value = null
            assertNull(awaitItem())
        }
    }

    @Test
    fun `actions reach the repository`() = runTest(dispatcher) {
        val viewModel = NodeStatusViewModel(repository)
        viewModel.setHostBearer(Bearer.AUDIO, true)
        viewModel.refreshRequirements()
        viewModel.requestUsbPermission()
        assertEquals(listOf(Bearer.AUDIO to true), repository.hostBearerWrites)
        assertEquals(1, repository.refreshes)
        assertEquals(1, repository.usbRequests)
    }
}
