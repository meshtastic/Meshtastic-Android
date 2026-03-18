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

import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class DebugViewModelTest {
    /*


    private val testDispatcher = UnconfinedTestDispatcher()


    private lateinit var viewModel: DebugViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { meshLogRepository.getAllLogs() } returns flowOf(emptyList())
        every { nodeRepository.myNodeInfo } returns MutableStateFlow(null)
        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(emptyMap())
        every { meshLogPrefs.retentionDays.value } returns 7
        every { meshLogPrefs.loggingEnabled.value } returns true

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

        verify { meshLogPrefs.setRetentionDays(14) }
        verifySuspend { meshLogRepository.deleteLogsOlderThan(14) }
        viewModel.retentionDays.value shouldBe 14
    }

    @Test
    fun `setLoggingEnabled false deletes all logs`() = runTest {
        viewModel.setLoggingEnabled(false)

        verify { meshLogPrefs.setLoggingEnabled(false) }
        verifySuspend { meshLogRepository.deleteAll() }
        viewModel.loggingEnabled.value shouldBe false
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
        state.hasMatches shouldBe true
        state.allMatches.size shouldBe 1
        state.allMatches[0].logIndex shouldBe 0
    }

    @Test
    fun `requestDeleteAllLogs shows alert`() {
        viewModel.requestDeleteAllLogs()
        verify { alertManager.showAlert(titleRes = any(), messageRes = any(), onConfirm = any()) }
    }

     */
}
