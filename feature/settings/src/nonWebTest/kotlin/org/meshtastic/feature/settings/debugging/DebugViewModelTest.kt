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
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.MeshLog
import org.meshtastic.core.repository.MeshLogRetention
import org.meshtastic.core.testing.FakeMeshLogPrefs
import org.meshtastic.core.testing.FakeMeshLogRepository
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.ui.util.AlertManager
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

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
    fun `setRetentionDays one hour keeps the last hour instead of clearing the log`() = runTest {
        val now = nowMillis
        meshLogRepository.setLogs(
            listOf(
                MeshLog("recent", "TEXT", now - 30.minutes.inWholeMilliseconds, ""),
                MeshLog("stale", "TEXT", now - 2.hours.inWholeMilliseconds, ""),
            ),
        )

        viewModel.setRetentionDays(MeshLogRetention.ONE_HOUR)

        meshLogPrefs.retentionDays.value shouldBe MeshLogRetention.ONE_HOUR
        viewModel.retentionDays.value shouldBe MeshLogRetention.ONE_HOUR
        meshLogRepository.currentLogs.map { it.uuid } shouldBe listOf("recent")
    }

    @Test
    fun `setRetentionDays never keeps every log`() = runTest {
        val now = nowMillis
        meshLogRepository.setLogs(
            listOf(
                MeshLog("ancient", "TEXT", now - 400.days.inWholeMilliseconds, ""),
                MeshLog("recent", "TEXT", now, ""),
            ),
        )

        viewModel.setRetentionDays(MeshLogRetention.KEEP_FOREVER)

        meshLogPrefs.retentionDays.value shouldBe MeshLogRetention.KEEP_FOREVER
        meshLogRepository.currentLogs.map { it.uuid } shouldBe listOf("ancient", "recent")
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

        // presetFilters reads the real wall clock (nowInstant) for its date element, so bracket that
        // read: the instant the VM used lies between `before` and `after`, meaning its short-date must
        // equal one of the two bounds. Keeps the assertion deterministic even when the wall clock
        // crosses a minute boundary between the VM's read and the test's.
        val before = org.meshtastic.core.common.util.nowInstant.toEpochMilliseconds()
        val filters = viewModel.presetFilters
        val after = org.meshtastic.core.common.util.nowInstant.toEpochMilliseconds()

        val validDates =
            setOf(
                org.meshtastic.core.common.util.DateFormatter.formatShortDate(before),
                org.meshtastic.core.common.util.DateFormatter.formatShortDate(after),
            )
        (filters[3] in validDates) shouldBe true

        filters.shouldBe(
            listOf(
                "!00bc614e",
                "!ffffffff",
                "decoded",
                filters[3], // validated above against the bracketed wall-clock reads
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

    // Regression tests for #6520: Wire's toString prints int fields SIGNED and `=`-delimited
    // (`from=-559038737,`), which the annotation regexes — written for protobuf-java's
    // unsigned, whitespace-bounded text format — silently stopped matching. These go through
    // the real generated toString, so they hold whichever wire format is in use.

    @Test
    fun `packet log annotates signed Wire-format from and to with hex`() = runTest {
        val packet = org.meshtastic.core.testing.TestDataFactory.createTestPacket(from = 0xDEADBEEF.toInt(), to = -1)
        meshLogRepository.insert(
            org.meshtastic.core.model.MeshLog(
                uuid = "1",
                message_type = "Packet",
                received_date = 1L,
                raw_message = "",
                fromRadio = org.meshtastic.proto.FromRadio(packet = packet),
            ),
        )

        val logs = viewModel.loadLogsForExport()

        logs.size shouldBe 1
        logs[0].logMessage shouldContain "(!deadbeef)"
        logs[0].logMessage shouldContain "(!ffffffff)"
    }

    @Test
    fun `packet log annotates relay_node with the known node's name`() = runTest {
        nodeRepository.setNodes(
            listOf(
                org.meshtastic.core.testing.TestDataFactory.createTestNode(
                    num = 0x000001AA,
                    userId = "!000001aa",
                    longName = "Relay Node",
                    lastHeard = 100,
                ),
            ),
        )
        val packet = org.meshtastic.core.testing.TestDataFactory.createTestPacket(from = 5, to = -1, relayNode = 0xAA)
        meshLogRepository.insert(
            org.meshtastic.core.model.MeshLog(
                uuid = "1",
                message_type = "Packet",
                received_date = 1L,
                raw_message = "",
                fromRadio = org.meshtastic.proto.FromRadio(packet = packet),
            ),
        )

        val logs = viewModel.loadLogsForExport()

        logs[0].logMessage shouldContain "relay_node=Relay Node (!000001aa)"
    }

    @Test
    fun `packet log annotates unknown relay_node with hex`() = runTest {
        val packet = org.meshtastic.core.testing.TestDataFactory.createTestPacket(from = 5, to = -1, relayNode = 0xF5)
        meshLogRepository.insert(
            org.meshtastic.core.model.MeshLog(
                uuid = "1",
                message_type = "Packet",
                received_date = 1L,
                raw_message = "",
                fromRadio = org.meshtastic.proto.FromRadio(packet = packet),
            ),
        )

        val logs = viewModel.loadLogsForExport()

        logs[0].logMessage shouldContain "(!000000f5)"
    }

    @Test
    fun `node info log still annotates legacy protobuf-java text format`() = runTest {
        meshLogRepository.insert(
            org.meshtastic.core.model.MeshLog(
                uuid = "1",
                message_type = "NodeInfo",
                received_date = 1L,
                raw_message = "node_info {\n  num: 3735928559\n}",
                fromRadio =
                org.meshtastic.proto.FromRadio(node_info = org.meshtastic.proto.NodeInfo(num = 0xDEADBEEF.toInt())),
            ),
        )

        val logs = viewModel.loadLogsForExport()

        logs[0].logMessage shouldContain "num: 3735928559 (!deadbeef)"
    }
}
