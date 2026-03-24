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
package org.meshtastic.feature.node.metrics

import app.cash.turbine.test
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.Buffer
import okio.BufferedSink
import org.meshtastic.core.common.util.MeshtasticUri
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.FileService
import org.meshtastic.core.repository.MeshLogRepository
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.TracerouteSnapshotRepository
import org.meshtastic.feature.node.detail.NodeDetailUiState
import org.meshtastic.feature.node.detail.NodeRequestActions
import org.meshtastic.feature.node.domain.usecase.GetNodeDetailsUseCase
import org.meshtastic.feature.node.model.MetricsState
import org.meshtastic.feature.node.model.TimeFrame
import org.meshtastic.proto.Position
import org.meshtastic.proto.Telemetry
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MetricsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatchers = CoroutineDispatchers(main = testDispatcher, io = testDispatcher, default = testDispatcher)

    private val meshLogRepository: MeshLogRepository = mock()
    private val serviceRepository: ServiceRepository = mock()
    private val nodeRepository: NodeRepository = mock()
    private val tracerouteSnapshotRepository: TracerouteSnapshotRepository = mock()
    private val nodeRequestActions: NodeRequestActions = mock()
    private val alertManager: org.meshtastic.core.ui.util.AlertManager = mock()
    private val getNodeDetailsUseCase: GetNodeDetailsUseCase = mock()
    private val fileService: FileService = mock()

    private lateinit var viewModel: MetricsViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        // Default setup for flows
        every { serviceRepository.tracerouteResponse } returns MutableStateFlow(null)
        every { nodeRequestActions.lastTracerouteTime } returns MutableStateFlow(null)
        every { nodeRequestActions.lastRequestNeighborTimes } returns MutableStateFlow(emptyMap())
        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(emptyMap())

        // Mock the case where we get node details
        every { getNodeDetailsUseCase(any()) } returns flowOf(NodeDetailUiState())

        viewModel = createViewModel()
    }

    private fun createViewModel(destNum: Int = 1234) = MetricsViewModel(
        destNum = destNum,
        dispatchers = dispatchers,
        meshLogRepository = meshLogRepository,
        serviceRepository = serviceRepository,
        nodeRepository = nodeRepository,
        tracerouteSnapshotRepository = tracerouteSnapshotRepository,
        nodeRequestActions = nodeRequestActions,
        alertManager = alertManager,
        getNodeDetailsUseCase = getNodeDetailsUseCase,
        fileService = fileService,
    )

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialization() {
        assertNotNull(viewModel)
    }

    @Test
    fun `state reflects updates from getNodeDetailsUseCase`() = runTest(testDispatcher) {
        val nodeDetailFlow = MutableStateFlow(NodeDetailUiState())
        every { getNodeDetailsUseCase(1234) } returns nodeDetailFlow.asStateFlow()

        val vm = createViewModel()
        vm.state.test {
            assertEquals(MetricsState.Empty, awaitItem())

            val newState = MetricsState(isFahrenheit = true)
            nodeDetailFlow.value = NodeDetailUiState(metricsState = newState)

            assertEquals(newState, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `availableTimeFrames filters based on oldest data`() = runTest(testDispatcher) {
        val now = org.meshtastic.core.common.util.nowSeconds

        val nodeDetailFlow = MutableStateFlow(NodeDetailUiState())
        every { getNodeDetailsUseCase(1234) } returns nodeDetailFlow.asStateFlow()

        val vm = createViewModel()
        vm.availableTimeFrames.test {
            // Skip initial values
            var current = awaitItem()

            // Provide data from 2 hours ago (7200 seconds)
            // This should make ONE_HOUR available, but not TWENTY_FOUR_HOURS
            val twoHoursAgo = now - 7200
            nodeDetailFlow.value =
                NodeDetailUiState(
                    node =
                    org.meshtastic.core.model.Node(num = 1234, user = org.meshtastic.proto.User(id = "!1234")),
                    environmentState =
                    EnvironmentMetricsState(environmentMetrics = listOf(Telemetry(time = twoHoursAgo.toInt()))),
                )

            // We might get multiple emissions as flows propagate
            current = awaitItem()
            while (current.size == TimeFrame.entries.size) { // Skip the initial "all" if it's still there
                current = awaitItem()
            }

            assertTrue(current.contains(TimeFrame.ONE_HOUR))
            assertTrue(!current.contains(TimeFrame.TWENTY_FOUR_HOURS), "Should not contain 24h for 2h old data")

            // Provide data from 8 days ago
            val eightDaysAgo = now - (8 * 24 * 3600)
            nodeDetailFlow.value =
                NodeDetailUiState(
                    node =
                    org.meshtastic.core.model.Node(num = 1234, user = org.meshtastic.proto.User(id = "!1234")),
                    environmentState =
                    EnvironmentMetricsState(
                        environmentMetrics = listOf(Telemetry(time = eightDaysAgo.toInt())),
                    ),
                )

            current = awaitItem()
            assertTrue(current.contains(TimeFrame.SEVEN_DAYS))

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `savePositionCSV writes correct data`() = runTest(testDispatcher) {
        val testPosition =
            Position(
                latitude_i = 123456789,
                longitude_i = -987654321,
                altitude = 100,
                sats_in_view = 5,
                ground_speed = 10,
                ground_track = 123456,
                time = 1700000000,
            )

        val nodeDetailFlow =
            MutableStateFlow(NodeDetailUiState(metricsState = MetricsState(positionLogs = listOf(testPosition))))
        every { getNodeDetailsUseCase(1234) } returns nodeDetailFlow.asStateFlow()

        val buffer = Buffer()
        everySuspend { fileService.write(any(), any()) } calls
            { args ->
                val block = args.arg<suspend (BufferedSink) -> Unit>(1)
                block(buffer)
                true
            }

        val vm = createViewModel()
        // Wait for state to be collected so it's not Empty when savePositionCSV is called
        vm.state.test {
            awaitItem() // Empty
            awaitItem() // with position

            val uri = MeshtasticUri("content://test")
            vm.savePositionCSV(uri)
            runCurrent()

            verifySuspend { fileService.write(uri, any()) }

            val csvOutput = buffer.readUtf8()
            assertTrue(csvOutput.startsWith("\"date\",\"time\",\"latitude\",\"longitude\""))
            assertTrue(csvOutput.contains("12.3456789"))
            assertTrue(csvOutput.contains("-98.7654321"))
            assertTrue(csvOutput.contains("\"100\",\"5\",\"10\",\"1.23\""))

            cancelAndIgnoreRemainingEvents()
        }
    }
}
