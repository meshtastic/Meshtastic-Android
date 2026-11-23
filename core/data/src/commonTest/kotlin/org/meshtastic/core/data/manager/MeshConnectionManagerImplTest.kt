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
package org.meshtastic.core.data.manager

import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.common.di.asServiceScope
import org.meshtastic.core.model.TransportFailure
import org.meshtastic.core.model.TransportFailureKind
import org.meshtastic.core.model.TransportPhase
import org.meshtastic.core.model.TransportStatus
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.AppWidgetUpdater
import org.meshtastic.core.repository.ConnectionStateHolder
import org.meshtastic.core.repository.HistoryManager
import org.meshtastic.core.repository.MeshLocationManager
import org.meshtastic.core.repository.MeshNotificationManager
import org.meshtastic.core.repository.MeshWorkerManager
import org.meshtastic.core.repository.MqttManager
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NodeRestartTracker
import org.meshtastic.core.repository.PacketHandler
import org.meshtastic.core.repository.PacketQueueRejectedException
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.PersistedPacket
import org.meshtastic.core.repository.PersistedPacketId
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.core.testing.CapturingLogWriter
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.proto.Config
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.LocalStats
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Position
import org.meshtastic.proto.Telemetry
import org.meshtastic.sdk.RadioClient
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MeshConnectionManagerImplTest {
    private lateinit var radioInterfaceService: RadioInterfaceService
    private lateinit var serviceRepository: ServiceRepository

    private lateinit var serviceNotifications: MeshNotificationManager
    private lateinit var uiPrefs: UiPrefs
    private lateinit var packetHandler: PacketHandler
    private lateinit var nodeRepository: FakeNodeRepository
    private lateinit var locationManager: MeshLocationManager
    private lateinit var mqttManager: MqttManager
    private lateinit var historyManager: HistoryManager
    private lateinit var radioConfigRepository: RadioConfigRepository

    /** Each call to the radio's session client, in order; [sessionClient] answers it. */
    private var clientRequests = 0
    private var sessionClient: (attempt: Int) -> RadioClient? = { null }
    private lateinit var nodeManager: NodeManager
    private lateinit var analytics: PlatformAnalytics
    private lateinit var packetRepository: PacketRepository
    private lateinit var workerManager: MeshWorkerManager
    private lateinit var appWidgetUpdater: AppWidgetUpdater

    private val dataPacket = DataPacket(id = 456, time = 0L, to = "0", from = "0", bytes = null, dataType = 0)

    private val radioConnectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    private val transportStatus = MutableStateFlow<TransportStatus?>(null)
    private lateinit var connectionStateHolder: ConnectionStateHolder
    private val localConfigFlow = MutableStateFlow(LocalConfig.Builder().build())
    private val moduleConfigFlow = MutableStateFlow(LocalModuleConfig.Builder().build())

    private lateinit var testDispatcher: TestDispatcher

    private lateinit var manager: MeshConnectionManagerImpl

    @BeforeTest
    fun setUp() {
        radioInterfaceService = mock(MockMode.autofill)
        serviceRepository = mock(MockMode.autofill)
        serviceNotifications = mock(MockMode.autofill)
        uiPrefs = mock(MockMode.autofill)
        packetHandler = mock(MockMode.autofill)
        nodeRepository = FakeNodeRepository()
        locationManager = mock(MockMode.autofill)
        mqttManager = mock(MockMode.autofill)
        historyManager = mock(MockMode.autofill)
        radioConfigRepository = mock(MockMode.autofill)
        clientRequests = 0
        sessionClient = { null }
        nodeManager = mock(MockMode.autofill)
        analytics = mock(MockMode.autofill)
        packetRepository = mock(MockMode.autofill)
        workerManager = mock(MockMode.autofill)
        appWidgetUpdater = mock(MockMode.autofill)

        testDispatcher = UnconfinedTestDispatcher()
        radioConnectionState.value = ConnectionState.Disconnected
        transportStatus.value = null
        connectionStateHolder = ConnectionStateHolder()
        localConfigFlow.value = LocalConfig.Builder().build()
        moduleConfigFlow.value = LocalModuleConfig.Builder().build()

        every { radioInterfaceService.connectionState } returns radioConnectionState
        every { radioInterfaceService.transportStatus } returns transportStatus
        every { radioConfigRepository.localConfigFlow } returns localConfigFlow
        every { radioConfigRepository.moduleConfigFlow } returns moduleConfigFlow
        every { serviceRepository.connectionState } returns connectionStateHolder.connectionState
        every { serviceRepository.connectionLifecycle } returns connectionStateHolder.connectionLifecycle
        every { serviceRepository.setConnectionState(any()) } calls
            { call ->
                applyConnectionState(call.arg<ConnectionState>(0))
            }
        every { serviceNotifications.updateServiceStateNotification(any(), any()) } returns Unit
        every { radioInterfaceService.activeSession } returns MutableStateFlow(RadioSessionContext(1, "tnode"))
        every { radioInterfaceService.clientForSession(any()) } calls { sessionClient(++clientRequests) }
        every { locationManager.stop() } returns Unit
        every { mqttManager.stop() } returns Unit
        every { nodeManager.nodeDBbyNodeNum } returns emptyMap<Int, Node>()
    }

    private fun createManager(scope: CoroutineScope): MeshConnectionManagerImpl = MeshConnectionManagerImpl(
        radioInterfaceService,
        serviceRepository,
        serviceNotifications,
        uiPrefs,
        packetHandler,
        nodeRepository,
        locationManager,
        mqttManager,
        historyManager,
        radioConfigRepository,
        nodeManager,
        analytics,
        packetRepository,
        workerManager,
        appWidgetUpdater,
        scope.asServiceScope(),
        NodeRestartTracker(scope),
    )

    private fun applyConnectionState(state: ConnectionState) {
        connectionStateHolder.setConnectionState(state)
    }

    @AfterTest
    fun tearDown() {
        CapturingLogWriter.uninstall()
    }

    @Test
    fun `link connection waits for SDK snapshot readiness`() = runTest(testDispatcher) {
        manager = createManager(backgroundScope)
        radioConnectionState.value = ConnectionState.Connected
        runCurrent()
        assertEquals(ConnectionState.Disconnected, serviceRepository.connectionState.value)
    }

    @Test
    fun `the SDK handshake shows as connecting until the snapshot is installed`() = runTest(testDispatcher) {
        manager = createManager(backgroundScope)

        transportStatus.value = TransportStatus(TransportPhase.HandshakeConfig)
        runCurrent()
        // Main kept the app state Connecting from link-up until the config load finished.
        assertEquals(ConnectionState.Connecting, serviceRepository.connectionState.value)

        // MeshConfigFlowManagerImpl publishes Connected once the snapshot is installed.
        serviceRepository.setConnectionState(ConnectionState.Connected)
        radioConnectionState.value = ConnectionState.Connected
        transportStatus.value = TransportStatus(TransportPhase.HandshakeNodes)
        transportStatus.value = TransportStatus(TransportPhase.Connected)
        runCurrent()
        assertEquals(ConnectionState.Connected, serviceRepository.connectionState.value)
    }

    @Test
    fun `a handshake phase collected after the snapshot install keeps the app connected`() = runTest(testDispatcher) {
        manager = createManager(backgroundScope)
        // The install publishes Connected from the SDK session while the status collector can still lag behind.
        serviceRepository.setConnectionState(ConnectionState.Connected)

        transportStatus.value = TransportStatus(TransportPhase.HandshakeNodes)
        runCurrent()

        assertEquals(ConnectionState.Connected, serviceRepository.connectionState.value)
    }

    @Test
    fun `a handshake that fails short of Ready falls back to the link state`() = runTest(testDispatcher) {
        radioConnectionState.value = ConnectionState.DeviceSleep
        manager = createManager(backgroundScope)
        runCurrent()
        val linkState = serviceRepository.connectionState.value

        transportStatus.value = TransportStatus(TransportPhase.HandshakeConfig)
        runCurrent()
        assertEquals(ConnectionState.Connecting, serviceRepository.connectionState.value)

        // The link stays DeviceSleep through the retry, so no link state change reports the failure.
        transportStatus.value =
            TransportStatus(TransportPhase.WaitingToRetry, TransportFailure(TransportFailureKind.LinkLost))
        runCurrent()
        assertEquals(linkState, serviceRepository.connectionState.value)
    }

    @Test
    fun `identity refresh restarts the current SDK session`() = runTest(testDispatcher) {
        val session = RadioSessionContext(1, "tnode")
        every { radioInterfaceService.activeSession } returns MutableStateFlow(session)
        manager = createManager(backgroundScope)
        manager.refreshRadioIdentity()
        verifySuspend { radioInterfaceService.restartTransport(session) }
    }

    @Test
    fun `Disconnected state stops services`() = runTest(testDispatcher) {
        every { nodeManager.nodeDBbyNodeNum } returns emptyMap()
        manager = createManager(backgroundScope)
        // The SDK snapshot has already published the app connection.
        radioConnectionState.value = ConnectionState.Connected
        applyConnectionState(ConnectionState.Connected)
        advanceUntilIdle()

        radioConnectionState.value = ConnectionState.Disconnected
        advanceUntilIdle()

        assertEquals(
            ConnectionState.Disconnected,
            serviceRepository.connectionState.value,
            "State should be Disconnected after radio Disconnected",
        )
        verify { locationManager.stop() }
        verify { mqttManager.stop() }
    }

    @Test
    fun `Disconnected state clears the stale noise floor`() = runTest(testDispatcher) {
        // localStats only overwrites on a fresh local_stats telemetry packet, so a stale reading from the previous
        // radio would otherwise silently blend into the next radio's signal-quality rating until it reports its
        // own.
        every { nodeManager.nodeDBbyNodeNum } returns emptyMap()
        manager = createManager(backgroundScope)
        radioConnectionState.value = ConnectionState.Connected
        applyConnectionState(ConnectionState.Connected)
        advanceUntilIdle()
        nodeRepository.updateLocalStats(LocalStats.Builder().also { wb -> wb.noise_floor = -70 }.build())

        radioConnectionState.value = ConnectionState.Disconnected
        advanceUntilIdle()

        assertEquals(
            LocalStats.Builder().build(),
            nodeRepository.localStats.value,
            "Disconnect should reset to \"no reading yet\"",
        )
    }

    @Test
    fun `DeviceSleep behavior when power saving is off maps to Disconnected`() = runTest(testDispatcher) {
        // Power saving disabled + Role CLIENT
        val config =
            LocalConfig.Builder()
                .also { wb ->
                    wb.power = Config.PowerConfig.Builder().also { wb -> wb.is_power_saving = false }.build()
                    wb.device =
                        Config.DeviceConfig.Builder()
                            .also { wb -> wb.role = Config.DeviceConfig.Role.CLIENT }
                            .build()
                }
                .build()
        every { radioConfigRepository.localConfigFlow } returns flowOf(config)
        every { nodeManager.nodeDBbyNodeNum } returns emptyMap()

        manager = createManager(backgroundScope)
        advanceUntilIdle()

        radioConnectionState.value = ConnectionState.DeviceSleep
        advanceUntilIdle()

        assertEquals(
            ConnectionState.Disconnected,
            serviceRepository.connectionState.value,
            "State should be Disconnected when power saving is off",
        )
    }

    @Test
    fun `DeviceSleep behavior when power saving is on stays in DeviceSleep`() = runTest(testDispatcher) {
        // Power saving enabled
        val config =
            LocalConfig.Builder()
                .also { wb ->
                    wb.power = Config.PowerConfig.Builder().also { wb -> wb.is_power_saving = true }.build()
                }
                .build()
        every { radioConfigRepository.localConfigFlow } returns flowOf(config)

        manager = createManager(backgroundScope)
        advanceUntilIdle()

        radioConnectionState.value = ConnectionState.DeviceSleep
        advanceUntilIdle()

        assertEquals(
            ConnectionState.DeviceSleep,
            serviceRepository.connectionState.value,
            "State should stay in DeviceSleep when power saving is on",
        )
    }

    @Test
    fun `onRadioConfigLoaded enqueues queued packets`() = runTest(testDispatcher) {
        manager = createManager(backgroundScope)
        val packetId = PersistedPacketId(myNodeNum = 123, uuid = 456L)
        everySuspend { packetRepository.getQueuedPackets() } returns listOf(PersistedPacket(packetId, dataPacket))
        every { workerManager.enqueueSendMessage(any()) } returns Unit

        manager.onRadioConfigLoaded()
        advanceUntilIdle()

        verify { workerManager.enqueueSendMessage(packetId) }
    }

    @Test
    fun `onNodeDbReady starts MQTT and requests history`() = runTest(testDispatcher) {
        val moduleConfig =
            LocalModuleConfig.Builder()
                .also { wb ->
                    wb.mqtt =
                        ModuleConfig.MQTTConfig.Builder()
                            .also { wb ->
                                wb.enabled = true
                                wb.proxy_to_client_enabled = true
                            }
                            .build()
                    wb.store_forward =
                        ModuleConfig.StoreForwardConfig.Builder().also { wb -> wb.enabled = true }.build()
                }
                .build()
        moduleConfigFlow.value = moduleConfig
        every { nodeManager.myNodeNum } returns MutableStateFlow(123)
        every { mqttManager.startProxy(any(), any()) } returns Unit
        everySuspend { historyManager.requestHistoryReplay(any(), any(), any(), any(), any()) } returns Unit
        every { nodeManager.getMyNodeInfo() } returns null

        manager = createManager(backgroundScope)
        serviceRepository.setConnectionState(ConnectionState.Connected)
        manager.onNodeDbReady()
        advanceUntilIdle()

        verify { mqttManager.startProxy(true, true) }
        verifySuspend { historyManager.requestHistoryReplay(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `onNodeDbReady skips history when its lifecycle departs while config loads`() = runTest(testDispatcher) {
        val delayedModuleConfig = MutableSharedFlow<LocalModuleConfig>()
        every { radioConfigRepository.moduleConfigFlow } returns delayedModuleConfig
        every { nodeManager.myNodeNum } returns MutableStateFlow(123)
        every { mqttManager.startProxy(any(), any()) } returns Unit
        everySuspend { historyManager.requestHistoryReplay(any(), any(), any(), any(), any()) } returns Unit

        manager = createManager(backgroundScope)
        serviceRepository.setConnectionState(ConnectionState.Connected)
        manager.onNodeDbReady()
        runCurrent()
        assertTrue(
            delayedModuleConfig.subscriptionCount.value >= 2,
            "MQTT and history collectors must be active; additional collectors are allowed",
        )
        serviceRepository.setConnectionState(ConnectionState.Disconnected)
        delayedModuleConfig.emit(
            LocalModuleConfig.Builder()
                .also { wb ->
                    wb.store_forward =
                        ModuleConfig.StoreForwardConfig.Builder().also { wb -> wb.enabled = true }.build()
                }
                .build(),
        )
        runCurrent()

        verifySuspend(exactly(0)) { historyManager.requestHistoryReplay(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `onNodeDbReady retains owned requests until queue admission opens`() = runTest(testDispatcher) {
        var historyAttempts = 0
        val admissionVersions = mutableListOf<Long>()
        moduleConfigFlow.value =
            LocalModuleConfig.Builder()
                .also { wb ->
                    wb.store_forward =
                        ModuleConfig.StoreForwardConfig.Builder().also { wb -> wb.enabled = true }.build()
                }
                .build()
        // Both telemetry requests find no session client on their first attempt and one on their retry.
        var sdk: SdkClientFixture? = null
        sessionClient = { attempt -> if (attempt <= 2) null else sdk?.client }
        everySuspend { historyManager.requestHistoryReplay(any(), any(), any(), any(), any()) } calls
            { call ->
                admissionVersions += call.arg<Long>(4)
                historyAttempts++
                if (historyAttempts == 1) throw PacketQueueRejectedException("test history replay")
            }
        every { nodeManager.myNodeNum } returns MutableStateFlow(123)
        every { mqttManager.startProxy(any(), any()) } returns Unit
        every { nodeManager.getMyNodeInfo() } returns null

        manager = createManager(backgroundScope)
        serviceRepository.setConnectionState(ConnectionState.Connected)
        manager.onNodeDbReady()
        runCurrent()
        sdk = sdkClientFixture(123)
        advanceTimeBy(
            (
                MeshConnectionManagerImpl.postHandshakeAdmissionRetryDelay(1) +
                    MeshConnectionManagerImpl.postHandshakeAdmissionRetryDelay(2)
                )
                .inWholeMilliseconds,
        )
        runCurrent()

        assertEquals(2, historyAttempts)
        assertEquals(4, clientRequests)
        assertEquals(setOf(connectionStateHolder.connectionLifecycle.value.version), admissionVersions.toSet())
        checkNotNull(sdk).client.disconnect()
    }

    @Test
    fun `our phone fix goes to our own node at background priority with its time and precision`() = runTest {
        var sendPosition: (suspend (Position) -> Boolean)? = null
        every { uiPrefs.shouldProvideNodeLocation(any()) } returns MutableStateFlow(true)
        every { locationManager.start(any(), any()) } calls { sendPosition = it.arg(1) }
        every { nodeManager.myNodeNum } returns MutableStateFlow(123)
        manager = createManager(backgroundScope)
        nodeRepository.setMyNodeInfo(MyNodeInfo(123, true, null, null, false, false, 0L, 0, 0, 8, false, 0f, 0f, null))
        runCurrent()
        val sdk = sdkClientFixture(123)
        sessionClient = { sdk.client }
        val fix =
            Position.Builder()
                .also { wb ->
                    wb.latitude_i = 515_000_000
                    wb.longitude_i = -1_250_000
                    wb.time = 1_700_000_000
                    wb.precision_bits = 13
                }
                .build()

        assertTrue(checkNotNull(sendPosition)(fix))
        runCurrent()

        val sent = sdk.transport.outboundPackets().single { it.decoded?.portnum == PortNum.POSITION_APP }
        assertEquals(123, sent.to)
        assertEquals(MeshPacket.Priority.BACKGROUND, sent.priority)
        assertEquals(fix, Position.ADAPTER.decode(checkNotNull(sent.decoded).payload))
        verify { nodeManager.handleReceivedPosition(123, 123, fix, any()) }
        verify {
            packetHandler.logSent(
                sent.id,
                123,
                PortNum.POSITION_APP,
                fix.encode().toByteString(),
                false,
                0,
                MeshPacket.Priority.BACKGROUND,
            )
        }
        sdk.client.disconnect()
    }

    @Test
    fun `an unanswered post-handshake telemetry request is sent again`() = runTest {
        every { nodeManager.myNodeNum } returns MutableStateFlow(123)
        every { mqttManager.startProxy(any(), any()) } returns Unit
        every { nodeManager.getMyNodeInfo() } returns null
        val sdk = sdkClientFixture(123)
        sessionClient = { sdk.client }
        manager = createManager(backgroundScope)
        // Let the manager's transport-state bridge publish its initial Disconnected first.
        runCurrent()
        serviceRepository.setConnectionState(ConnectionState.Connected)

        manager.onNodeDbReady()
        runCurrent()
        // Past the SDK's 30 s RPC timeout and the first admission retry delay.
        advanceTimeBy(31_000 + MeshConnectionManagerImpl.postHandshakeAdmissionRetryDelay(1).inWholeMilliseconds)
        runCurrent()

        val localStatsRequests =
            sdk.transport.outboundPackets().count { packet ->
                packet.decoded?.portnum == PortNum.TELEMETRY_APP &&
                    Telemetry.ADAPTER.decode(checkNotNull(packet.decoded).payload).local_stats != null
            }
        assertEquals(2, localStatsRequests)
        sdk.client.disconnect()
    }

    @Test
    fun `post-handshake admission retries stop when the connected lifecycle changes`() = runTest(testDispatcher) {
        // No session client: both telemetry requests are refused on every attempt.
        every { nodeManager.myNodeNum } returns MutableStateFlow(123)
        every { mqttManager.startProxy(any(), any()) } returns Unit
        every { nodeManager.getMyNodeInfo() } returns null

        manager = createManager(backgroundScope)
        serviceRepository.setConnectionState(ConnectionState.Connected)
        manager.onNodeDbReady()
        runCurrent()
        assertEquals(2, clientRequests)

        serviceRepository.setConnectionState(ConnectionState.Disconnected)
        advanceTimeBy(
            (1 until MeshConnectionManagerImpl.MAX_POST_HANDSHAKE_ADMISSION_ATTEMPTS).sumOf { rejectionCount ->
                MeshConnectionManagerImpl.postHandshakeAdmissionRetryDelay(rejectionCount).inWholeMilliseconds
            },
        )
        runCurrent()

        assertEquals(2, clientRequests, "a stale connection generation must not keep retrying admission")
    }

    @Test
    fun `post-handshake admission retries stop at the attempt cap`() = runTest(testDispatcher) {
        // No session client: both telemetry requests are refused on every attempt.
        every { nodeManager.myNodeNum } returns MutableStateFlow(123)
        every { mqttManager.startProxy(any(), any()) } returns Unit
        every { nodeManager.getMyNodeInfo() } returns null

        manager = createManager(backgroundScope)
        serviceRepository.setConnectionState(ConnectionState.Connected)
        manager.onNodeDbReady()
        runCurrent()

        val retryWindowMillis =
            (1 until MeshConnectionManagerImpl.MAX_POST_HANDSHAKE_ADMISSION_ATTEMPTS).sumOf { rejectionCount ->
                MeshConnectionManagerImpl.postHandshakeAdmissionRetryDelay(rejectionCount).inWholeMilliseconds
            }
        advanceTimeBy(retryWindowMillis)
        runCurrent()

        assertEquals(2 * MeshConnectionManagerImpl.MAX_POST_HANDSHAKE_ADMISSION_ATTEMPTS, clientRequests)
    }

    @Test
    fun `DeviceSleep timeout is capped at MAX_SLEEP_TIMEOUT_SECONDS for high ls_secs`() = runTest(testDispatcher) {
        // Router with ls_secs=3600 — previously this created a 3630s timeout.
        // With the cap, it should be clamped to 300s.
        val config =
            LocalConfig.Builder()
                .also { wb ->
                    wb.power =
                        Config.PowerConfig.Builder()
                            .also { wb ->
                                wb.is_power_saving = true
                                wb.ls_secs = 3600
                            }
                            .build()
                    wb.device =
                        Config.DeviceConfig.Builder()
                            .also { wb -> wb.role = Config.DeviceConfig.Role.ROUTER }
                            .build()
                }
                .build()
        every { radioConfigRepository.localConfigFlow } returns flowOf(config)
        every { nodeManager.nodeDBbyNodeNum } returns emptyMap()

        manager = createManager(backgroundScope)
        advanceUntilIdle()

        // Transition to Connected then DeviceSleep
        radioConnectionState.value = ConnectionState.Connected
        advanceUntilIdle()
        radioConnectionState.value = ConnectionState.DeviceSleep
        advanceUntilIdle()

        assertEquals(
            ConnectionState.DeviceSleep,
            serviceRepository.connectionState.value,
            "Should be in DeviceSleep initially",
        )

        // Advance 300 seconds (the cap) + 1 second to trigger the timeout.
        advanceTimeBy(301_000L)

        assertEquals(
            ConnectionState.Disconnected,
            serviceRepository.connectionState.value,
            "Should transition to Disconnected after capped timeout (300s), not the raw 3630s",
        )
    }

    @Test
    fun `rapid state transitions are serialized by connectionMutex`() = runTest(testDispatcher) {
        // Power saving enabled so DeviceSleep is preserved (not mapped to Disconnected)
        val config =
            LocalConfig.Builder()
                .also { wb ->
                    wb.power = Config.PowerConfig.Builder().also { wb -> wb.is_power_saving = true }.build()
                }
                .build()
        every { radioConfigRepository.localConfigFlow } returns flowOf(config)
        every { nodeManager.nodeDBbyNodeNum } returns emptyMap()

        // Record every state transition so we can verify ordering
        val observed = mutableListOf<ConnectionState>()
        every { serviceRepository.setConnectionState(any()) } calls
            { call ->
                val state = call.arg<ConnectionState>(0)
                observed.add(state)
                applyConnectionState(state)
            }

        manager = createManager(backgroundScope)
        advanceUntilIdle()

        // Rapid-fire: Connected -> DeviceSleep -> Disconnected without yielding between them.
        // Without the Mutex, the intermediate DeviceSleep could be missed or applied out of order.
        radioConnectionState.value = ConnectionState.Connecting
        radioConnectionState.value = ConnectionState.DeviceSleep
        radioConnectionState.value = ConnectionState.Disconnected
        advanceUntilIdle()

        // Verify final state
        assertEquals(
            ConnectionState.Disconnected,
            serviceRepository.connectionState.value,
            "Final state should be Disconnected after rapid transitions",
        )

        // Verify that all intermediate states were observed in correct order.
        // Confirm the bridge preserved the transport's two intermediate states.
        assertEquals(
            listOf(ConnectionState.Connecting, ConnectionState.DeviceSleep, ConnectionState.Disconnected),
            observed,
            "State transitions should be serialized in order: Connecting -> DeviceSleep -> Disconnected",
        )
    }

    @Test
    fun `concurrent sleep-timeout and radio state change are serialized`() {
        val standardDispatcher = StandardTestDispatcher()
        runTest(standardDispatcher) {
            // Power saving enabled with ls_secs=0 so the sleep timeout boundary is just before the
            // Stage 1 handshake watchdog. That keeps this test isolated to sleep-timeout behavior.
            val config =
                LocalConfig.Builder()
                    .also { wb ->
                        wb.power =
                            Config.PowerConfig.Builder()
                                .also { wb ->
                                    wb.is_power_saving = true
                                    wb.ls_secs = 0
                                }
                                .build()
                    }
                    .build()
            every { radioConfigRepository.localConfigFlow } returns flowOf(config)
            every { nodeManager.nodeDBbyNodeNum } returns emptyMap()

            val observed = mutableListOf<ConnectionState>()
            every { serviceRepository.setConnectionState(any()) } calls
                { call ->
                    val state = call.arg<ConnectionState>(0)
                    observed.add(state)
                    applyConnectionState(state)
                }

            manager = createManager(backgroundScope)
            runCurrent()

            // Transition to Connected -> DeviceSleep to start the sleep timer
            radioConnectionState.value = ConnectionState.Connected
            runCurrent()
            radioConnectionState.value = ConnectionState.DeviceSleep
            runCurrent()

            observed.clear()

            // Before the sleep timeout fires, emit Connected from the radio (simulating device
            // waking up). Then let the timeout fire. The mutex ensures they don't race.
            radioConnectionState.value = ConnectionState.Connected
            runCurrent()
            // Advance past the sleep timeout (ls_secs=0 + 30s base), but stop before the Stage 1
            // handshake watchdog fires at roughly 30.1s after the wake-up Connected signal.
            advanceTimeBy(30_050L)
            runCurrent()

            // The Connected transition should have cancelled the sleep timeout, so we should
            // end up in Connecting (from handleConnected), NOT Disconnected (from timeout).
            assertEquals(
                ConnectionState.DeviceSleep,
                serviceRepository.connectionState.value,
                "The link wake cancels the sleep timeout while snapshot installation is pending",
            )
        }
    }
}
