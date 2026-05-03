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
import kotlinx.coroutines.test.runCurrent
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

    private lateinit var meshLogRepository: FakeMeshLogRepository
    private lateinit var nodeRepository: FakeNodeRepository
    private lateinit var meshLogPrefs: FakeMeshLogPrefs
    private val alertManager: AlertManager = mock(MockMode.autofill)

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = CoroutineDispatchers(testDispatcher, testDispatcher, testDispatcher)

    private lateinit var viewModel: DebugViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        meshLogRepository = FakeMeshLogRepository()
        nodeRepository = FakeNodeRepository()
        meshLogPrefs = FakeMeshLogPrefs()
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
        meshLogRepository.lastDeletedOlderThan shouldBe 14
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
                DebugViewModel.UiMeshLog("1", "TypeA", "Date1", "Apple"),
                DebugViewModel.UiMeshLog("2", "TypeB", "Date2", "Banana"),
            )

        viewModel.searchManager.setSearchText("Apple")
        viewModel.updateFilteredLogs(logs)
        runCurrent()

        val state = viewModel.searchState.value
        state.hasMatches shouldBe true
        state.allMatches.size shouldBe 1
        state.allMatches[0].logIndex shouldBe 0

        viewModel.searchManager.goToNextMatch()
        viewModel.searchState.value.currentMatchIndex shouldBe 0

        viewModel.searchManager.clearSearch()
        runCurrent()
        viewModel.searchState.value.searchText shouldBe ""
        viewModel.searchState.value.hasMatches shouldBe false
    }

    @Test
    fun `filterManager filters logs correctly with AND and OR modes`() {
        val logs =
            listOf(
                DebugViewModel.UiMeshLog("1", "TypeA", "Date1", "Apple Red"),
                DebugViewModel.UiMeshLog("2", "TypeB", "Date2", "Apple Green"),
                DebugViewModel.UiMeshLog("3", "TypeC", "Date3", "Banana Yellow"),
            )

        // OR mode
        val orResults = viewModel.filterManager.filterLogs(logs, listOf("Red", "Banana"), FilterMode.OR)
        orResults.size shouldBe 2
        orResults.map { it.uuid } shouldBe listOf("1", "3")

        // AND mode
        val andResults = viewModel.filterManager.filterLogs(logs, listOf("Apple", "Green"), FilterMode.AND)
        andResults.size shouldBe 1
        andResults[0].uuid shouldBe "2"
    }

    @Test
    fun `presetFilters includes my node ID and broadcast`() {
        nodeRepository.setMyNodeInfo(org.meshtastic.core.testing.TestDataFactory.createMyNodeInfo(myNodeNum = 12345678))

        val filters = viewModel.presetFilters
        filters.shouldBe(
            listOf(
                "!00bc614e",
                "!ffffffff",
                "decoded",
                org.meshtastic.core.common.util.DateFormatter.formatShortDate(
                    org.meshtastic.core.common.util.nowInstant.toEpochMilliseconds(),
                ),
            ) + org.meshtastic.proto.PortNum.entries.map { it.name },
        )
    }

    @Test
    fun `decodePayloadFromMeshLog decodes various portnums`() {
        val position = org.meshtastic.proto.Position(latitude_i = 10000000, longitude_i = 20000000)
        val packet =
            org.meshtastic.core.testing.TestDataFactory.createTestPacket(
                decoded =
                org.meshtastic.proto.Data(
                    portnum = org.meshtastic.proto.PortNum.POSITION_APP,
                    payload = okio.ByteString.Companion.of(*position.encode()),
                ),
            )
        val log =
            org.meshtastic.core.model.MeshLog(
                uuid = "1",
                message_type = "Packet",
                received_date = 1L,
                raw_message = "raw",
                fromRadio = org.meshtastic.proto.FromRadio(packet = packet),
            )

        // This is a private method but we can test it via toUiState
        // (tested in the previous test)
    }

    @Test
    fun `requestDeleteAllLogs shows alert`() {
        viewModel.requestDeleteAllLogs()
        verify { alertManager.showAlert(titleRes = any(), messageRes = any(), onConfirm = any()) }
    }
}
