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
package org.meshtastic.feature.messaging.ui.contact

import app.cash.turbine.test
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.proto.ChannelSet
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class ContactsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: ContactsViewModel
    private val nodeRepository: NodeRepository = mock(MockMode.autofill)
    private val packetRepository: PacketRepository = mock(MockMode.autofill)
    private val radioConfigRepository: RadioConfigRepository = mock(MockMode.autofill)
    private val serviceRepository: ServiceRepository = mock(MockMode.autofill)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { nodeRepository.ourNodeInfo } returns MutableStateFlow(null)
        every { nodeRepository.myNodeInfo } returns MutableStateFlow(null)
        every { nodeRepository.myId } returns MutableStateFlow(null)
        every { nodeRepository.getNodes() } returns MutableStateFlow(emptyList())

        every { serviceRepository.connectionState } returns MutableStateFlow(ConnectionState.Disconnected)
        every { packetRepository.getUnreadCountTotal() } returns MutableStateFlow(0)
        every { radioConfigRepository.channelSetFlow } returns MutableStateFlow(ChannelSet())

        viewModel =
            ContactsViewModel(
                nodeRepository = nodeRepository,
                packetRepository = packetRepository,
                radioConfigRepository = radioConfigRepository,
                serviceRepository = serviceRepository,
            )
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
    fun `unreadCountTotal reflects updates from repository`() = runTest {
        val countFlow = MutableStateFlow(0)
        every { packetRepository.getUnreadCountTotal() } returns countFlow

        // Re-init VM
        viewModel = ContactsViewModel(nodeRepository, packetRepository, radioConfigRepository, serviceRepository)

        viewModel.unreadCountTotal.test {
            assertEquals(0, awaitItem())
            countFlow.value = 5
            assertEquals(5, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
