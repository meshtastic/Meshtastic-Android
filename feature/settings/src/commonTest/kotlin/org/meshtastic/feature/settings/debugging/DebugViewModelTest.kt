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

import dev.mokkery.MockMode
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.testing.FakeMeshLogPrefs
import org.meshtastic.core.testing.FakeMeshLogRepository
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.ui.util.AlertManager
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DebugViewModelTest {

    private val meshLogRepository = FakeMeshLogRepository()
    private val nodeRepository = FakeNodeRepository()
    private val meshLogPrefs = FakeMeshLogPrefs()
    private val alertManager: AlertManager = mock(MockMode.autofill)

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = CoroutineDispatchers(testDispatcher, testDispatcher, testDispatcher)

    private lateinit var viewModel: DebugViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        meshLogPrefs.setRetentionDays(7)
        meshLogPrefs.setLoggingEnabled(true)

        viewModel =
            DebugViewModel(
                meshLogRepository = meshLogRepository,
                nodeRepository = nodeRepository,
                meshLogPrefs = meshLogPrefs,
                alertManager = alertManager,
                dispatchers = dispatchers,
            )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `setRetentionDays updates prefs and deletes old logs`() = runTest {
        viewModel.setRetentionDays(14)

        meshLogPrefs.retentionDays.value shouldBe 14
        meshLogRepository.deleteLogsOlderThanCalledDays shouldBe 14
        viewModel.retentionDays.value shouldBe 14
    }

    @Test
    fun `setLoggingEnabled false deletes all logs`() = runTest {
        meshLogRepository.insert(org.meshtastic.core.model.MeshLog("123", "type", 1L, "raw"))
        viewModel.setLoggingEnabled(false)

        meshLogPrefs.loggingEnabled.value shouldBe false
        meshLogRepository.currentLogs shouldBe emptyList()
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
}
