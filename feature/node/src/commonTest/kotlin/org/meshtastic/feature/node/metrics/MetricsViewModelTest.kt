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
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.FileService
import org.meshtastic.core.repository.MeshLogRepository
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.TracerouteResponseProvider
import org.meshtastic.core.repository.TracerouteSnapshotRepository
import org.meshtastic.feature.node.detail.NodeDetailUiState
import org.meshtastic.feature.node.detail.NodeRequestActions
import org.meshtastic.feature.node.domain.usecase.GetNodeDetailsUseCase
import org.meshtastic.feature.node.model.MetricsState
import org.meshtastic.feature.node.model.TimeFrame
import org.meshtastic.proto.DeviceMetrics
import org.meshtastic.proto.EnvironmentMetrics
import org.meshtastic.proto.LocalStats
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.Position
import org.meshtastic.proto.PowerMetrics
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
    private val tracerouteResponseProvider: TracerouteResponseProvider = mock()
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
        every { tracerouteResponseProvider.tracerouteResponse } returns MutableStateFlow(null)
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
        tracerouteResponseProvider = tracerouteResponseProvider,
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

            val uri = CommonUri.parse("content://test")
            vm.savePositionCSV(uri, listOf(testPosition))
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

    @Test
    fun `saveDeviceMetricsCSV writes correct data`() = runTest(testDispatcher) {
        val testTelemetry =
            Telemetry(
                time = 1700000000,
                device_metrics =
                DeviceMetrics(
                    battery_level = 80,
                    voltage = 4.1f,
                    channel_utilization = 12.5f,
                    air_util_tx = 3.25f,
                    uptime_seconds = 3600,
                ),
            )

        val nodeDetailFlow =
            MutableStateFlow(NodeDetailUiState(metricsState = MetricsState(deviceMetrics = listOf(testTelemetry))))
        every { getNodeDetailsUseCase(1234) } returns nodeDetailFlow.asStateFlow()

        val buffer = Buffer()
        everySuspend { fileService.write(any(), any()) } calls
            { args ->
                val block = args.arg<suspend (BufferedSink) -> Unit>(1)
                block(buffer)
                true
            }

        val vm = createViewModel()
        vm.state.test {
            awaitItem()
            awaitItem()

            val uri = CommonUri.parse("content://test")
            vm.saveDeviceMetricsCSV(uri, listOf(testTelemetry))
            runCurrent()

            verifySuspend { fileService.write(uri, any()) }

            val csvOutput = buffer.readUtf8()
            assertTrue(
                csvOutput.startsWith(
                    "\"date\",\"time\",\"batteryLevel\",\"voltage\",\"channelUtilization\",\"airUtilTx\",\"uptimeSeconds\"",
                ),
            )
            assertTrue(csvOutput.contains("\"80\",\"4.1\",\"12.5\",\"3.25\",\"3600\""))

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saveEnvironmentMetricsCSV writes correct data`() = runTest(testDispatcher) {
        val testTelemetry =
            Telemetry(
                time = 1700000000,
                environment_metrics =
                EnvironmentMetrics(
                    temperature = 21.5f,
                    relative_humidity = 55.5f,
                    barometric_pressure = 1013.25f,
                    gas_resistance = 12.3f,
                    iaq = 42,
                    wind_speed = 5.5f,
                    wind_direction = 180,
                    soil_temperature = 18.75f,
                    soil_moisture = 65,
                    one_wire_temperature = listOf(1f, 2f, 3f),
                ),
            )

        val nodeDetailFlow =
            MutableStateFlow(
                NodeDetailUiState(
                    metricsState = MetricsState(deviceMetrics = emptyList()),
                    environmentState = EnvironmentMetricsState(environmentMetrics = listOf(testTelemetry)),
                ),
            )
        every { getNodeDetailsUseCase(1234) } returns nodeDetailFlow.asStateFlow()

        val buffer = Buffer()
        everySuspend { fileService.write(any(), any()) } calls
            { args ->
                val block = args.arg<suspend (BufferedSink) -> Unit>(1)
                block(buffer)
                true
            }

        val vm = createViewModel()
        vm.state.test {
            awaitItem()

            val uri = CommonUri.parse("content://test")
            vm.saveEnvironmentMetricsCSV(uri, listOf(testTelemetry))
            runCurrent()

            verifySuspend { fileService.write(uri, any()) }

            val csvOutput = buffer.readUtf8()
            assertTrue(
                csvOutput.startsWith(
                    "\"date\",\"time\",\"temperature\",\"relativeHumidity\",\"barometricPressure\",\"gasResistance\",\"iaq\",\"windSpeed\",\"windDirection\",\"soilTemperature\",\"soilMoisture\",\"oneWireTemp1\",\"oneWireTemp2\",\"oneWireTemp3\",\"oneWireTemp4\",\"oneWireTemp5\",\"oneWireTemp6\",\"oneWireTemp7\",\"oneWireTemp8\"",
                ),
            )
            assertTrue(
                csvOutput.contains(
                    "\"21.5\",\"55.5\",\"1013.25\",\"12.3\",\"42\",\"5.5\",\"180\",\"18.75\",\"65\",\"1.0\",\"2.0\",\"3.0\",\"\",\"\",\"\",\"\",\"\"",
                ),
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saveSignalMetricsCSV writes correct data`() = runTest(testDispatcher) {
        val testPacket = MeshPacket(rx_time = 1700000000, rx_rssi = -105, rx_snr = 7.5f)

        val nodeDetailFlow =
            MutableStateFlow(NodeDetailUiState(metricsState = MetricsState(signalMetrics = listOf(testPacket))))
        every { getNodeDetailsUseCase(1234) } returns nodeDetailFlow.asStateFlow()

        val buffer = Buffer()
        everySuspend { fileService.write(any(), any()) } calls
            { args ->
                val block = args.arg<suspend (BufferedSink) -> Unit>(1)
                block(buffer)
                true
            }

        val vm = createViewModel()
        vm.state.test {
            awaitItem()
            awaitItem()

            val uri = CommonUri.parse("content://test")
            vm.saveSignalMetricsCSV(uri, listOf(testPacket))
            runCurrent()

            verifySuspend { fileService.write(uri, any()) }

            val csvOutput = buffer.readUtf8()
            assertTrue(csvOutput.startsWith("\"date\",\"time\",\"rssi\",\"snr\""))
            assertTrue(csvOutput.contains("\"-105\",\"7.5\""))

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saveLocalStatsCSV writes only provided visible data`() = runTest(testDispatcher) {
        val visibleTelemetry =
            Telemetry(
                time = 1700000000,
                local_stats =
                LocalStats(
                    noise_floor = -112,
                    uptime_seconds = 3600,
                    channel_utilization = 12.5f,
                    air_util_tx = 3.25f,
                    num_packets_tx = 2,
                    num_packets_rx = 3,
                    num_packets_rx_bad = 1,
                    num_rx_dupe = 4,
                    num_tx_relay = 5,
                    num_tx_relay_canceled = 6,
                    num_online_nodes = 7,
                    num_total_nodes = 8,
                ),
            )
        val hiddenTelemetry =
            Telemetry(time = 1600000000, local_stats = LocalStats(noise_floor = -99, uptime_seconds = 10))

        val nodeDetailFlow =
            MutableStateFlow(
                NodeDetailUiState(
                    metricsState = MetricsState(localStats = listOf(visibleTelemetry, hiddenTelemetry)),
                ),
            )
        every { getNodeDetailsUseCase(1234) } returns nodeDetailFlow.asStateFlow()

        val buffer = Buffer()
        everySuspend { fileService.write(any(), any()) } calls
            { args ->
                val block = args.arg<suspend (BufferedSink) -> Unit>(1)
                block(buffer)
                true
            }

        val vm = createViewModel()
        vm.state.test {
            awaitItem()
            awaitItem()

            val uri = CommonUri.parse("content://test")
            vm.saveLocalStatsCSV(uri, listOf(visibleTelemetry))
            runCurrent()

            verifySuspend { fileService.write(uri, any()) }

            val csvOutput = buffer.readUtf8()
            assertTrue(csvOutput.startsWith("\"date\",\"time\",\"noise_floor_dbm\",\"uptime_seconds\""))
            assertTrue(csvOutput.contains("\"-112\",\"3600\",\"12.5\",\"3.25\""))
            assertTrue(csvOutput.contains("\"2\",\"3\",\"1\",\"4\",\"5\",\"6\",\"7\",\"8\""))
            assertTrue(!csvOutput.contains("-99"), "Should only export the rows supplied by the screen")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearLocalStats deletes local stats logs for route node`() = runTest(testDispatcher) {
        everySuspend { meshLogRepository.deleteLocalStatsLogs(1234) } returns Unit

        val vm = createViewModel()
        vm.clearLocalStats()
        runCurrent()

        verifySuspend { meshLogRepository.deleteLocalStatsLogs(1234) }
    }

    @Test
    fun `savePowerMetricsCSV writes correct data`() = runTest(testDispatcher) {
        val testTelemetry =
            Telemetry(
                time = 1700000000,
                power_metrics =
                PowerMetrics(
                    ch1_voltage = 3.3f,
                    ch1_current = 0.1f,
                    ch2_voltage = 5.0f,
                    ch2_current = 0.2f,
                    ch3_voltage = 12.0f,
                    ch3_current = 0.3f,
                ),
            )

        val nodeDetailFlow =
            MutableStateFlow(NodeDetailUiState(metricsState = MetricsState(powerMetrics = listOf(testTelemetry))))
        every { getNodeDetailsUseCase(1234) } returns nodeDetailFlow.asStateFlow()

        val buffer = Buffer()
        everySuspend { fileService.write(any(), any()) } calls
            { args ->
                val block = args.arg<suspend (BufferedSink) -> Unit>(1)
                block(buffer)
                true
            }

        val vm = createViewModel()
        vm.state.test {
            awaitItem()
            awaitItem()

            val uri = CommonUri.parse("content://test")
            vm.savePowerMetricsCSV(uri, listOf(testTelemetry))
            runCurrent()

            verifySuspend { fileService.write(uri, any()) }

            val csvOutput = buffer.readUtf8()
            assertTrue(
                csvOutput.startsWith(
                    "\"date\",\"time\",\"ch1Voltage\",\"ch1Current\",\"ch2Voltage\",\"ch2Current\",\"ch3Voltage\",\"ch3Current\"",
                ),
            )
            assertTrue(csvOutput.contains("\"3.3\",\"0.1\",\"5.0\",\"0.2\",\"12.0\",\"0.3\""))

            cancelAndIgnoreRemainingEvents()
        }
    }
}
