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
package org.meshtastic.feature.settings.radio

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.domain.usecase.settings.CleanNodeDatabaseUseCase
import org.meshtastic.core.model.Node
import org.meshtastic.core.ui.util.AlertManager
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class CleanNodeDatabaseViewModelTest {

    private val cleanNodeDatabaseUseCase: CleanNodeDatabaseUseCase = mock(MockMode.autofill)
    private val alertManager: AlertManager = mock(MockMode.autofill)
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var viewModel: CleanNodeDatabaseViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = CleanNodeDatabaseViewModel(cleanNodeDatabaseUseCase, alertManager)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onOlderThanDaysChanged updates state`() {
        viewModel.onOlderThanDaysChanged(15f)
        assertEquals(15f, viewModel.olderThanDays.value)
    }

    @Test
    fun `onOnlyUnknownNodesChanged updates state and clamps olderThanDays`() {
        viewModel.onOlderThanDaysChanged(5f)
        viewModel.onOnlyUnknownNodesChanged(false)
        assertEquals(false, viewModel.onlyUnknownNodes.value)
        assertEquals(7f, viewModel.olderThanDays.value) // Clamped to MIN_DAYS_THRESHOLD
    }

    @Test
    fun `getNodesToDelete calls useCase and updates state`() = runTest {
        val nodes = listOf(Node(num = 1, user = org.meshtastic.proto.User(id = "!1")))
        everySuspend { cleanNodeDatabaseUseCase.getNodesToClean(any(), any(), any()) } returns nodes

        viewModel.getNodesToDelete()

        assertEquals(nodes, viewModel.nodesToDelete.value)
    }

    @Test
    fun `cleanNodes calls useCase and clears state`() = runTest {
        // First set some nodes to delete
        val nodes = listOf(Node(num = 1, user = org.meshtastic.proto.User(id = "!1")))
        everySuspend { cleanNodeDatabaseUseCase.getNodesToClean(any(), any(), any()) } returns nodes
        viewModel.getNodesToDelete()

        everySuspend { cleanNodeDatabaseUseCase.cleanNodes(any()) } returns Unit

        viewModel.cleanNodes()

        verifySuspend { cleanNodeDatabaseUseCase.cleanNodes(listOf(1)) }
        assertEquals(emptyList(), viewModel.nodesToDelete.value)
    }
}
