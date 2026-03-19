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
 * along with this program.  See
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.meshtastic.core.ui.viewmodel

import app.cash.turbine.test
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.proto.LocalConfig
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: ConnectionsViewModel
    private val radioConfigRepository: RadioConfigRepository = mock(MockMode.autofill)
    private val serviceRepository: ServiceRepository = mock(MockMode.autofill)
    private val nodeRepository: NodeRepository = mock(MockMode.autofill)
    private val uiPrefs: UiPrefs = mock(MockMode.autofill)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        
        every { radioConfigRepository.localConfigFlow } returns MutableStateFlow(LocalConfig())
        every { serviceRepository.connectionState } returns MutableStateFlow(org.meshtastic.core.model.ConnectionState.Disconnected)
        every { nodeRepository.myNodeInfo } returns MutableStateFlow(null)
        every { nodeRepository.ourNodeInfo } returns MutableStateFlow(null)
        every { uiPrefs.hasShownNotPairedWarning } returns MutableStateFlow(false)

        viewModel = ConnectionsViewModel(
            radioConfigRepository = radioConfigRepository,
            serviceRepository = serviceRepository,
            nodeRepository = nodeRepository,
            uiPrefs = uiPrefs,
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
    fun `suppressNoPairedWarning updates state and prefs`() {
        every { uiPrefs.setHasShownNotPairedWarning(any()) } returns Unit
        
        viewModel.suppressNoPairedWarning()
        
        assertEquals(true, viewModel.hasShownNotPairedWarning.value)
        verify { uiPrefs.setHasShownNotPairedWarning(true) }
    }
}
