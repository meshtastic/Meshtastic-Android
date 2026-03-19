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
package org.meshtastic.core.ui.share

import app.cash.turbine.test
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.service.ServiceAction
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.proto.SharedContact
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class SharedContactViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: SharedContactViewModel
    private val nodeRepository: NodeRepository = mock(MockMode.autofill)
    private val serviceRepository: ServiceRepository = mock(MockMode.autofill)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { nodeRepository.getNodes() } returns MutableStateFlow(emptyList())
        viewModel = SharedContactViewModel(nodeRepository, serviceRepository)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialization() {
        assertNotNull(viewModel)
    }

    @Test
    fun `unfilteredNodes reflects repository updates`() = runTest {
        val nodesFlow = MutableStateFlow<List<Node>>(emptyList())
        every { nodeRepository.getNodes() } returns nodesFlow

        viewModel = SharedContactViewModel(nodeRepository, serviceRepository)

        viewModel.unfilteredNodes.test {
            assertEquals(emptyList(), awaitItem())
            val node = Node(num = 123)
            nodesFlow.value = listOf(node)
            assertEquals(listOf(node), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addSharedContact delegates to serviceRepository`() = runTest {
        val contact = SharedContact(node_num = 123)
        everySuspend { serviceRepository.onServiceAction(any()) } returns Unit

        viewModel.addSharedContact(contact)
        testScheduler.runCurrent()

        verifySuspend { serviceRepository.onServiceAction(ServiceAction.ImportContact(contact)) }
    }
}
