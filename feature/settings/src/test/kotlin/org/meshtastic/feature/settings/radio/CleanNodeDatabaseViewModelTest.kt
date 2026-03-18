/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.meshtastic.core.domain.usecase.settings.CleanNodeDatabaseUseCase
import org.meshtastic.core.model.Node
import org.meshtastic.core.ui.util.AlertManager

@OptIn(ExperimentalCoroutinesApi::class)
class CleanNodeDatabaseViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var cleanNodeDatabaseUseCase: CleanNodeDatabaseUseCase
    private lateinit var alertManager: AlertManager
    private lateinit var viewModel: CleanNodeDatabaseViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        cleanNodeDatabaseUseCase = mock(MockMode.autofill)
        alertManager = mock(MockMode.autofill)
        viewModel = CleanNodeDatabaseViewModel(cleanNodeDatabaseUseCase, alertManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `getNodesToDelete updates state`() = runTest {
        val nodes = listOf(Node(num = 1), Node(num = 2))
        everySuspend { cleanNodeDatabaseUseCase.getNodesToClean(any(), any(), any()) } returns nodes

        viewModel.getNodesToDelete()
        advanceUntilIdle()

        assertEquals(nodes, viewModel.nodesToDelete.value)
    }

    @Test
    fun `cleanNodes calls useCase and clears state`() = runTest {
        val nodes = listOf(Node(num = 1))
        everySuspend { cleanNodeDatabaseUseCase.getNodesToClean(any(), any(), any()) } returns nodes
        viewModel.getNodesToDelete()
        advanceUntilIdle()

        viewModel.cleanNodes()
        advanceUntilIdle()

        verifySuspend { cleanNodeDatabaseUseCase.cleanNodes(listOf(1)) }
        assertEquals(0, viewModel.nodesToDelete.value.size)
    }
}
