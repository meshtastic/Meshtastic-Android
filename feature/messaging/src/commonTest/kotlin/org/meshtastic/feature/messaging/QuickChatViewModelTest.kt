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
package org.meshtastic.feature.messaging

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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.database.entity.QuickChatAction
import org.meshtastic.core.repository.QuickChatActionRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class QuickChatViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: QuickChatViewModel
    private val quickChatActionRepository: QuickChatActionRepository = mock(MockMode.autofill)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { quickChatActionRepository.getAllActions() } returns MutableStateFlow(emptyList())
        viewModel = QuickChatViewModel(quickChatActionRepository)
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
    fun `quickChatActions reflects repository updates`() = runTest(testDispatcher) {
        val actionsFlow = MutableStateFlow<List<QuickChatAction>>(emptyList())
        every { quickChatActionRepository.getAllActions() } returns actionsFlow

        // Re-init
        viewModel = QuickChatViewModel(quickChatActionRepository)

        viewModel.quickChatActions.test {
            assertEquals(emptyList(), awaitItem())
            val action = QuickChatAction(uuid = 1L, name = "Test", message = "Hello", position = 0)
            actionsFlow.value = listOf(action)
            assertEquals(listOf(action), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addQuickChatAction delegates to repository`() = runTest(testDispatcher) {
        val action = QuickChatAction(uuid = 1L, name = "Test", message = "Hello", position = 0)
        everySuspend { quickChatActionRepository.upsert(any()) } returns Unit

        val job = viewModel.addQuickChatAction(action)
        job.join()

        verifySuspend { quickChatActionRepository.upsert(action) }
    }
}
