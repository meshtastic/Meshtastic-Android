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
package org.meshtastic.feature.settings.debugging

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.meshtastic.core.data.repository.MeshLogRepository
import org.meshtastic.core.prefs.meshlog.MeshLogPrefs
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.ui.util.AlertManager

@OptIn(ExperimentalCoroutinesApi::class)
class DebugViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val meshLogRepository: MeshLogRepository = mockk(relaxed = true)
    private val nodeRepository: NodeRepository = mockk(relaxed = true)
    private val meshLogPrefs: MeshLogPrefs = mockk(relaxed = true)
    private val alertManager: AlertManager = mockk(relaxed = true)

    private lateinit var viewModel: DebugViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { meshLogRepository.getAllLogs() } returns flowOf(emptyList())
        every { nodeRepository.myNodeInfo } returns MutableStateFlow(null)
        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(emptyMap())
        every { meshLogPrefs.retentionDays } returns 7
        every { meshLogPrefs.loggingEnabled } returns true

        viewModel =
            DebugViewModel(
                meshLogRepository = meshLogRepository,
                nodeRepository = nodeRepository,
                meshLogPrefs = meshLogPrefs,
                alertManager = alertManager,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `setRetentionDays updates prefs and deletes old logs`() = runTest {
        viewModel.setRetentionDays(14)

        verify { meshLogPrefs.retentionDays = 14 }
        coVerify { meshLogRepository.deleteLogsOlderThan(14) }
        assertEquals(14, viewModel.retentionDays.value)
    }

    @Test
    fun `setLoggingEnabled false deletes all logs`() = runTest {
        viewModel.setLoggingEnabled(false)

        verify { meshLogPrefs.loggingEnabled = false }
        coVerify { meshLogRepository.deleteAll() }
        assertEquals(false, viewModel.loggingEnabled.value)
    }

    @Test
    fun `search filters results correctly`() = runTest {
        val logs =
            listOf(
                DebugViewModel.UiMeshLog("1", "TypeA", "Date1", "Message Apple"),
                DebugViewModel.UiMeshLog("2", "TypeB", "Date2", "Message Banana"),
            )

        viewModel.searchManager.updateMatches("Apple", logs)

        val state = viewModel.searchState.value
        assertEquals(true, state.hasMatches)
        assertEquals(1, state.allMatches.size)
        assertEquals(0, state.allMatches[0].logIndex)
    }

    @Test
    fun `requestDeleteAllLogs shows alert`() {
        viewModel.requestDeleteAllLogs()
        verify { alertManager.showAlert(titleRes = any(), messageRes = any(), onConfirm = any()) }
    }
}
