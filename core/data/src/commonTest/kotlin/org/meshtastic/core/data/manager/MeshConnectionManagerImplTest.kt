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

import co.touchlab.kermit.Severity
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.AppWidgetUpdater
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.HistoryManager
import org.meshtastic.core.repository.MeshLocationManager
import org.meshtastic.core.repository.MeshNotificationManager
import org.meshtastic.core.repository.MeshWorkerManager
import org.meshtastic.core.repository.MqttManager
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NodeRestartTracker
import org.meshtastic.core.repository.PacketHandler
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.SessionManager
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.core.testing.FakeLockdownCoordinator
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.proto.Config
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.ModuleConfig
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
    private lateinit var commandSender: CommandSender
    private lateinit var sessionManager: SessionManager
    private lateinit var nodeManager: NodeManager
    private lateinit var analytics: PlatformAnalytics
    private lateinit var packetRepository: PacketRepository
    private lateinit var workerManager: MeshWorkerManager
    private lateinit var appWidgetUpdater: AppWidgetUpdater
    private lateinit var lockdownCoordinator: FakeLockdownCoordinator

    private val dataPacket = DataPacket(id = 456, time = 0L, to = "0", from = "0", bytes = null, dataType = 0)

    private val radioConnectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    private val connectionStateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    private val localConfigFlow = MutableStateFlow(LocalConfig())
    private val moduleConfigFlow = MutableStateFlow(LocalModuleConfig())

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
        commandSender = mock(MockMode.autofill)
        sessionManager = mock(MockMode.autofill)
        nodeManager = mock(MockMode.autofill)
        analytics = mock(MockMode.autofill)
        packetRepository = mock(MockMode.autofill)
        workerManager = mock(MockMode.autofill)
        appWidgetUpdater = mock(MockMode.autofill)
        lockdownCoordinator = FakeLockdownCoordinator()

        testDispatcher = UnconfinedTestDispatcher()
        radioConnectionState.value = ConnectionState.Disconnected
        connectionStateFlow.value = ConnectionState.Disconnected
        localConfigFlow.value = LocalConfig()
        moduleConfigFlow.value = LocalModuleConfig()

        every { radioInterfaceService.connectionState } returns radioConnectionState
        every { radioConfigRepository.localConfigFlow } returns localConfigFlow
        every { radioConfigRepository.moduleConfigFlow } returns moduleConfigFlow
        every { serviceRepository.connectionState } returns connectionStateFlow
        every { serviceRepository.setConnectionState(any()) } calls
            { call ->
                connectionStateFlow.value = call.arg<ConnectionState>(0)
            }
        every { serviceNotifications.updateServiceStateNotification(any(), any()) } returns Unit
        everySuspend { commandSender.sendAdmin(any(), any(), any(), any()) } returns Unit
        every { packetHandler.stopPacketQueue() } returns Unit
        every { locationManager.stop() } returns Unit
        every { mqttManager.stop() } returns Unit
        every { nodeManager.nodeDBbyNodeNum } returns emptyMap<Int, Node>()
        every { packetHandler.sendToRadio(any<org.meshtastic.proto.ToRadio>()) } returns Unit
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
        commandSender,
        sessionManager,
        nodeManager,
        analytics,
        packetRepository,
        workerManager,
        appWidgetUpdater,
        DataLayerHeartbeatSender(packetHandler),
        lockdownCoordinator,
        scope,
        NodeRestartTracker(scope),
    )

    private fun restartTransportCallCounter(): () -> Int {
        var restartCalls = 0
        everySuspend { radioInterfaceService.restartTransport() } calls { restartCalls += 1 }
        return { restartCalls }
    }

    @AfterTest fun tearDown() = Unit

    @Test
    fun `Connected state triggers broadcast and config start`() = runTest(testDispatcher) {
        manager = createManager(backgroundScope)
        radioConnectionState.value = ConnectionState.Connected
        advanceUntilIdle()

        assertEquals(
            ConnectionState.Connecting,
            serviceRepository.connectionState.value,
            "State should be Connecting after radio Connected",
        )
        assertEquals(true, lockdownCoordinator.connectCalled)
    }

    @Test
    fun `Connected state sends pre-handshake heartbeat before config request`() = runTest(testDispatcher) {
        val sentPackets = mutableListOf<org.meshtastic.proto.ToRadio>()
        every { packetHandler.sendToRadio(any<org.meshtastic.proto.ToRadio>()) } calls
            { call ->
                sentPackets.add(call.arg(0))
            }

        manager = createManager(backgroundScope)
        radioConnectionState.value = ConnectionState.Connected
        // Advance past PRE_HANDSHAKE_SETTLE_MS (100ms) but NOT the 30s stall guard timeout
        advanceTimeBy(200)

        // First ToRadio should be a heartbeat, second should be want_config_id
        assertEquals(2, sentPackets.size, "Expected heartbeat + want_config_id, got ${sentPackets.size} packets")
        val heartbeat = sentPackets[0]
        val wantConfig = sentPackets[1]

        assertEquals(true, heartbeat.heartbeat != null, "First packet should be a heartbeat")
        assertEquals(true, heartbeat.heartbeat!!.nonce != 0, "Heartbeat should have a non-zero nonce")
        assertEquals(
            org.meshtastic.core.repository.HandshakeConstants.CONFIG_NONCE,
            wantConfig.want_config_id,
            "Second packet should be want_config_id with CONFIG_NONCE",
        )
    }

    @Test
    fun `Disconnect during pre-handshake settle cancels config start`() = runTest(testDispatcher) {
        val sentPackets = mutableListOf<org.meshtastic.proto.ToRadio>()
        every { packetHandler.sendToRadio(any<org.meshtastic.proto.ToRadio>()) } calls
            { call ->
                sentPackets.add(call.arg(0))
            }
        every { nodeManager.nodeDBbyNodeNum } returns emptyMap()

        manager = createManager(backgroundScope)
        radioConnectionState.value = ConnectionState.Connected
        // Advance only 50ms — within the 100ms settle window
        advanceTimeBy(50)

        // Should have sent only the heartbeat so far, not want_config_id
        assertEquals(1, sentPackets.size, "Only heartbeat should be sent before settle completes")

        // Disconnect before the settle delay completes — should cancel the pending config start
        radioConnectionState.value = ConnectionState.Disconnected
        advanceTimeBy(200)

        // The want_config_id should NOT have been sent because the job was cancelled
        val configPackets = sentPackets.filter { it.want_config_id != null }
        assertEquals(0, configPackets.size, "want_config_id should not be sent after disconnect")
    }

    @Test
    fun `Disconnected state stops services`() = runTest(testDispatcher) {
        every { nodeManager.nodeDBbyNodeNum } returns emptyMap()
        manager = createManager(backgroundScope)
        // Transition to Connected first so that Disconnected actually does something
        radioConnectionState.value = ConnectionState.Connected
        advanceUntilIdle()

        radioConnectionState.value = ConnectionState.Disconnected
        advanceUntilIdle()

        assertEquals(
            ConnectionState.Disconnected,
            serviceRepository.connectionState.value,
            "State should be Disconnected after radio Disconnected",
        )
        verify { packetHandler.stopPacketQueue() }
        verify { locationManager.stop() }
        verify { mqttManager.stop() }
        assertEquals(true, lockdownCoordinator.disconnectCalled)
    }

    @Test
    fun `DeviceSleep behavior when power saving is off maps to Disconnected`() = runTest(testDispatcher) {
        // Power saving disabled + Role CLIENT
        val config =
            LocalConfig(
                power = Config.PowerConfig(is_power_saving = false),
                device = Config.DeviceConfig(role = Config.DeviceConfig.Role.CLIENT),
            )
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
        val config = LocalConfig(power = Config.PowerConfig(is_power_saving = true))
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
    fun `onRadioConfigLoaded enqueues queued packets and sets time`() = runTest(testDispatcher) {
        manager = createManager(backgroundScope)
        val packetId = 456
        everySuspend { packetRepository.getQueuedPackets() } returns listOf(dataPacket)
        every { workerManager.enqueueSendMessage(any()) } returns Unit

        manager.onRadioConfigLoaded()
        advanceUntilIdle()

        verify { workerManager.enqueueSendMessage(packetId) }
    }

    @Test
    fun `onNodeDbReady starts MQTT and requests history`() = runTest(testDispatcher) {
        val moduleConfig =
            LocalModuleConfig(
                mqtt = ModuleConfig.MQTTConfig(enabled = true, proxy_to_client_enabled = true),
                store_forward = ModuleConfig.StoreForwardConfig(enabled = true),
            )
        moduleConfigFlow.value = moduleConfig
        everySuspend { commandSender.requestTelemetry(any(), any(), any()) } returns Unit
        every { nodeManager.myNodeNum } returns MutableStateFlow(123)
        every { mqttManager.startProxy(any(), any()) } returns Unit
        everySuspend { historyManager.requestHistoryReplay(any(), any(), any(), any()) } returns Unit
        every { nodeManager.getMyNodeInfo() } returns null

        manager = createManager(backgroundScope)
        manager.onNodeDbReady()
        advanceUntilIdle()

        verify { mqttManager.startProxy(true, true) }
        verifySuspend { historyManager.requestHistoryReplay(any(), any(), any(), any()) }
    }

    @Test
    fun `DeviceSleep timeout is capped at MAX_SLEEP_TIMEOUT_SECONDS for high ls_secs`() = runTest(testDispatcher) {
        // Router with ls_secs=3600 — previously this created a 3630s timeout.
        // With the cap, it should be clamped to 300s.
        val config =
            LocalConfig(
                power = Config.PowerConfig(is_power_saving = true, ls_secs = 3600),
                device = Config.DeviceConfig(role = Config.DeviceConfig.Role.ROUTER),
            )
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
        val config = LocalConfig(power = Config.PowerConfig(is_power_saving = true))
        every { radioConfigRepository.localConfigFlow } returns flowOf(config)
        every { nodeManager.nodeDBbyNodeNum } returns emptyMap()

        // Record every state transition so we can verify ordering
        val observed = mutableListOf<ConnectionState>()
        every { serviceRepository.setConnectionState(any()) } calls
            { call ->
                val state = call.arg<ConnectionState>(0)
                observed.add(state)
                connectionStateFlow.value = state
            }

        manager = createManager(backgroundScope)
        advanceUntilIdle()

        // Rapid-fire: Connected -> DeviceSleep -> Disconnected without yielding between them.
        // Without the Mutex, the intermediate DeviceSleep could be missed or applied out of order.
        radioConnectionState.value = ConnectionState.Connected
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
        // Connected triggers handleConnected() which sets Connecting (handshake start),
        // then DeviceSleep, then Disconnected.
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
            val config = LocalConfig(power = Config.PowerConfig(is_power_saving = true, ls_secs = 0))
            every { radioConfigRepository.localConfigFlow } returns flowOf(config)
            every { nodeManager.nodeDBbyNodeNum } returns emptyMap()

            val observed = mutableListOf<ConnectionState>()
            every { serviceRepository.setConnectionState(any()) } calls
                { call ->
                    val state = call.arg<ConnectionState>(0)
                    observed.add(state)
                    connectionStateFlow.value = state
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
                ConnectionState.Connecting,
                serviceRepository.connectionState.value,
                "Connected should cancel the sleep timeout; final state should be Connecting",
            )
        }
    }

    @Test
    fun `Stage 1 config stall triggers transport restart and ends Disconnected`() = runTest(testDispatcher) {
        manager = createManager(backgroundScope)
        // Disconnected -> Connected: handleConnected() sets Connecting, sends pre-handshake
        // heartbeat, and (after PRE_HANDSHAKE_SETTLE_MS=100ms) calls startConfigOnly() which
        // arms the Stage 1 stall guard (HANDSHAKE_TIMEOUT_STAGE1 = 30s).
        radioConnectionState.value = ConnectionState.Connected
        advanceTimeBy(200)
        advanceUntilIdle()

        // Pre-condition: Stage 1 is in flight — manager is Connecting and a ToRadio has been sent
        // (heartbeat + want_config_id). Use at-least-one here so the test isn't brittle on the
        // exact packet count.
        assertEquals(
            ConnectionState.Connecting,
            serviceRepository.connectionState.value,
            "Manager should be Connecting after radio Connected",
        )
        verify { packetHandler.sendToRadio(any<org.meshtastic.proto.ToRadio>()) }

        // Advance past HANDSHAKE_TIMEOUT_STAGE1 (30s) WITHOUT any config arrival. The stall
        // fires the recovery sibling unconditionally on every transport — there is no longer a
        // want_config retry delay before restart. The production code runs BOTH transitions
        // inside one sibling recovery job — onConnectionChanged(Disconnected) FIRST, then
        // restartTransport() — so the fresh Connected emission from restartTransport arrives
        // with app-level state already Disconnected and is not ignored by the redundant-
        // Connecting guard in onConnectionChanged. The advanceTimeBy keeps extra slack so the
        // test stays robust against small timeout tweaks.
        advanceTimeBy(46_000L)
        advanceUntilIdle()

        verifySuspend(exactly(1)) { radioInterfaceService.restartTransport() }
        assertEquals(
            ConnectionState.Disconnected,
            serviceRepository.connectionState.value,
            "Stage 1 stall should end in Disconnected after restart is requested",
        )
    }

    @Test
    fun `Handshake stall recovery orders app disconnect before transport restart emissions`() =
        runTest(testDispatcher) {
            // This test locks in the ordering invariant of the stall recovery
            // sibling: onConnectionChanged(Disconnected) runs FIRST, then restartTransport().
            // We deliberately do NOT stub restartTransport() — the default mock no-op leaves it
            // as a pure boundary call so the sibling's two phases can be observed independently.
            //
            // After the stall fires and the sibling completes, we MANUALLY replay the
            // transport-level emissions that the real restartTransport() would produce:
            //   - DeviceSleep (onDisconnect(isPermanent=false) on the old transport)
            //   - Connected   (the new transport's onConnect callback)
            // Under the FIXED ordering, the fresh Connected arrives with app state already
            // Disconnected, bypasses the redundant-Connecting guard in onConnectionChanged,
            // and re-enters handleConnected → state returns to Connecting.
            // Under the BROKEN (old) ordering — restartTransport() BEFORE Disconnected — the
            // fresh Connected would arrive while app state was still Connecting, the redundant-
            // Connecting guard would drop it, and the state would never return to Connecting.
            //
            // Restructured to be deterministic on JVM CI: rather than relying on a stubbed
            // restartTransport() lambda whose StateFlow side-effect emissions race with the
            // flow collector under Mokkery, the test body itself drives the emissions in order.
            manager = createManager(backgroundScope)
            // Disconnected -> Connected: handleConnected() sets Connecting, sends pre-handshake
            // heartbeat, and (after PRE_HANDSHAKE_SETTLE_MS=100ms) calls startConfigOnly() which
            // arms the Stage 1 stall guard (HANDSHAKE_TIMEOUT_STAGE1 = 30s).
            radioConnectionState.value = ConnectionState.Connected
            advanceTimeBy(200)
            advanceUntilIdle()

            // Pre-condition: Stage 1 is in flight.
            assertEquals(
                ConnectionState.Connecting,
                serviceRepository.connectionState.value,
                "Manager should be Connecting after radio Connected",
            )

            // Advance past HANDSHAKE_TIMEOUT_STAGE1 (30s) WITHOUT any config arrival. The stall
            // fires the recovery sibling: onConnectionChanged(Disconnected) FIRST, then
            // restartTransport() (default mock no-op, so nothing re-arms a stall guard and
            // advanceUntilIdle is safe here).
            advanceTimeBy(46_000L)
            advanceUntilIdle()

            verifySuspend(exactly(1)) { radioInterfaceService.restartTransport() }
            assertEquals(
                ConnectionState.Disconnected,
                serviceRepository.connectionState.value,
                "Sibling must run onConnectionChanged(Disconnected) BEFORE restartTransport() — " +
                    "proves the app-level Disconnected transition landed",
            )

            // Manually replay the transport-level restart signals that the real restartTransport()
            // would emit. DeviceSleep corresponds to onDisconnect(isPermanent=false) on the old
            // transport; Connected corresponds to the new transport's onConnect callback. With
            // UnconfinedTestDispatcher each emission is collected synchronously inline, so no
            // advanceUntilIdle() is needed between them — and none is safe AFTER the Connected
            // emission, because handleConnected re-arms a fresh Stage 1 stall guard and
            // advanceUntilIdle would advance virtual time past it (and every subsequent re-arm),
            // looping the recovery and obscuring the single-shot ordering this test locks in.
            radioConnectionState.value = ConnectionState.DeviceSleep
            radioConnectionState.value = ConnectionState.Connected

            assertEquals(
                ConnectionState.Connecting,
                serviceRepository.connectionState.value,
                "Fresh Connected emission must re-enter handleConnected (NOT be ignored by the " +
                    "redundant-Connecting guard) because the app-level Disconnected transition " +
                    "already ran BEFORE restartTransport's transport cycle — this is the ordering " +
                    "invariant under test",
            )
        }

    @Test
    fun `Stage 2 node-info stall triggers transport restart`() = runTest(testDispatcher) {
        manager = createManager(backgroundScope)
        radioConnectionState.value = ConnectionState.Connected
        // Pre-handshake settle completes; Stage 1 stall guard armed.
        advanceTimeBy(200)
        advanceUntilIdle()

        // Drive the connection into Stage 2. In production this is done by the config-flow
        // manager once Stage 1 config arrives; here we invoke it directly. startNodeInfoOnly()
        // cancels the Stage 1 stall guard and arms Stage 2 (HANDSHAKE_TIMEOUT_STAGE2 = 60s).
        manager.startNodeInfoOnly()
        advanceUntilIdle()

        assertEquals(
            ConnectionState.Connecting,
            serviceRepository.connectionState.value,
            "Manager should still be Connecting entering Stage 2",
        )

        // Advance past HANDSHAKE_TIMEOUT_STAGE2 (60s) WITHOUT invoking onNodeDbReady(). The
        // stall must fire the recovery sibling unconditionally.
        advanceTimeBy(76_000L)
        advanceUntilIdle()

        verifySuspend(exactly(1)) { radioInterfaceService.restartTransport() }
        assertEquals(
            ConnectionState.Disconnected,
            serviceRepository.connectionState.value,
            "Stage 2 stall should end in Disconnected after restart is requested",
        )
    }

    @Test
    fun `Handshake completing before stall timeout does not trigger transport restart`() = runTest(testDispatcher) {
        // Stubs required by onNodeDbReady() (full handshake completion path).
        everySuspend { commandSender.requestTelemetry(any(), any(), any()) } returns Unit
        every { nodeManager.myNodeNum } returns MutableStateFlow(123)
        every { mqttManager.startProxy(any(), any()) } returns Unit
        everySuspend { historyManager.requestHistoryReplay(any(), any(), any(), any()) } returns Unit
        every { nodeManager.getMyNodeInfo() } returns null

        manager = createManager(backgroundScope)
        radioConnectionState.value = ConnectionState.Connected
        // Pre-handshake settle completes; Stage 1 stall guard armed.
        advanceTimeBy(200)
        advanceUntilIdle()

        // Simulate the full handshake completing (config arrives + NodeDB becomes ready).
        // onNodeDbReady() cancels handshakeTimeout, so the stall recovery sibling can
        // never run even if virtual time later crosses the stage windows.
        manager.onNodeDbReady()
        advanceUntilIdle()

        // Advance well past BOTH stage windows (Stage 1: 30s, Stage 2: 60s).
        advanceTimeBy(120_000L)
        advanceUntilIdle()

        verifySuspend(exactly(0)) { radioInterfaceService.restartTransport() }
    }

    @Test
    fun `TCP Stage 1 stall fires restartTransport at 12s without retry`() = runTest(testDispatcher) {
        // Address starting with 't' → DeviceType.TCP → fast recovery transport.
        every { radioInterfaceService.getDeviceAddress() } returns "t192.168.1.42"
        val sentPackets = mutableListOf<org.meshtastic.proto.ToRadio>()
        every { packetHandler.sendToRadio(any<org.meshtastic.proto.ToRadio>()) } calls
            { call ->
                sentPackets.add(call.arg(0))
            }

        manager = createManager(backgroundScope)
        radioConnectionState.value = ConnectionState.Connected
        // Pre-handshake settle (100ms): heartbeat + Stage 1 want_config_id sent, fast watchdog armed.
        advanceTimeBy(200)
        advanceUntilIdle()

        assertEquals(
            ConnectionState.Connecting,
            serviceRepository.connectionState.value,
            "Manager should be Connecting after radio Connected",
        )

        // Sanity: heartbeat + Stage 1 want_config_id have been sent.
        val packetsBeforeStall = sentPackets.size

        // Advance past FAST_HANDSHAKE_TIMEOUT (12s) WITHOUT any progress signal. The fast
        // branch must fire the recovery sibling directly — no same-session want_config re-send.
        advanceTimeBy(13_000L)
        advanceUntilIdle()

        verifySuspend(exactly(1)) { radioInterfaceService.restartTransport() }
        assertEquals(
            ConnectionState.Disconnected,
            serviceRepository.connectionState.value,
            "Fast stall should end in Disconnected after restart is requested",
        )
        // The recovery sibling must not re-send want_config_id on the same session: no additional
        // packet may be sent between the initial arming and the restartTransport call (a same-
        // session re-send re-enters firmware handleStartConfig() and crashes the device).
        assertEquals(
            packetsBeforeStall,
            sentPackets.size,
            "Fast transport stall must NOT trigger a retry send of want_config_id",
        )
    }

    @Test
    fun `TCP Stage 1 meaningful progress resets watchdog without false restart`() = runTest(testDispatcher) {
        every { radioInterfaceService.getDeviceAddress() } returns "t192.168.1.42"
        manager = createManager(backgroundScope)
        radioConnectionState.value = ConnectionState.Connected
        advanceTimeBy(200)
        advanceUntilIdle()

        // Trickle inbound progress signals — each lands well inside the 12s fast window, so
        // each one re-arms the watchdog and no restart may fire across multiple resets.
        repeat(5) {
            advanceTimeBy(8_000L)
            manager.onHandshakeProgress()
            advanceUntilIdle()
        }

        verifySuspend(exactly(0)) { radioInterfaceService.restartTransport() }
        assertEquals(
            ConnectionState.Connecting,
            serviceRepository.connectionState.value,
            "Steady trickle of progress must keep the manager Connecting",
        )
    }

    @Test
    fun `TCP Stage 1 watchdog fires after progress stops`() = runTest(testDispatcher) {
        every { radioInterfaceService.getDeviceAddress() } returns "t192.168.1.42"
        manager = createManager(backgroundScope)
        radioConnectionState.value = ConnectionState.Connected
        advanceTimeBy(200)
        advanceUntilIdle()

        // Two progress signals land inside the window.
        advanceTimeBy(8_000L)
        manager.onHandshakeProgress()
        advanceUntilIdle()
        advanceTimeBy(8_000L)
        manager.onHandshakeProgress()
        advanceUntilIdle()

        // Progress stops; advance past the fast timeout from the last re-arm.
        advanceTimeBy(13_000L)
        advanceUntilIdle()

        verifySuspend(exactly(1)) { radioInterfaceService.restartTransport() }
        assertEquals(
            ConnectionState.Disconnected,
            serviceRepository.connectionState.value,
            "Watchdog must fire once progress stops for longer than the fast timeout",
        )
    }

    @Test
    fun `TCP Stage 2 stall fires restartTransport at 12s`() = runTest(testDispatcher) {
        every { radioInterfaceService.getDeviceAddress() } returns "t192.168.1.42"
        manager = createManager(backgroundScope)
        radioConnectionState.value = ConnectionState.Connected
        advanceTimeBy(200)
        advanceUntilIdle()

        // Drive the connection into Stage 2. In production this is done by the config-flow
        // manager once Stage 1 config arrives; here we invoke it directly. startNodeInfoOnly()
        // cancels the Stage 1 watchdog and arms Stage 2 with the fast timeout (12s on TCP).
        manager.startNodeInfoOnly()
        advanceUntilIdle()

        assertEquals(
            ConnectionState.Connecting,
            serviceRepository.connectionState.value,
            "Manager should still be Connecting entering Stage 2",
        )

        // Advance past the fast timeout WITHOUT invoking onNodeDbReady().
        advanceTimeBy(13_000L)
        advanceUntilIdle()

        verifySuspend(exactly(1)) { radioInterfaceService.restartTransport() }
        assertEquals(
            ConnectionState.Disconnected,
            serviceRepository.connectionState.value,
            "Stage 2 fast stall should end in Disconnected after restart is requested",
        )
    }

    @Test
    fun `TCP Stage 2 NodeInfo progress resets watchdog`() = runTest(testDispatcher) {
        every { radioInterfaceService.getDeviceAddress() } returns "t192.168.1.42"
        manager = createManager(backgroundScope)
        radioConnectionState.value = ConnectionState.Connected
        advanceTimeBy(200)
        advanceUntilIdle()

        manager.startNodeInfoOnly()
        advanceUntilIdle()

        // Stage 2 node-info burst packets arrive as a trickle; each resets the fast watchdog.
        repeat(3) {
            advanceTimeBy(7_000L)
            manager.onHandshakeProgress()
            advanceUntilIdle()
        }

        verifySuspend(exactly(0)) { radioInterfaceService.restartTransport() }
        assertEquals(
            ConnectionState.Connecting,
            serviceRepository.connectionState.value,
            "Stage 2 progress must keep the manager Connecting",
        )
    }

    @Test
    fun `TCP fast recovery preserves Disconnected-before-restartTransport ordering`() = runTest(testDispatcher) {
        every { radioInterfaceService.getDeviceAddress() } returns "t192.168.1.42"

        val observed = mutableListOf<ConnectionState>()
        var progressBeforeRestart: String? = null
        var restartTransportCalls = 0
        every { serviceRepository.setConnectionState(any()) } calls
            { call ->
                val state = call.arg<ConnectionState>(0)
                observed.add(state)
                connectionStateFlow.value = state
            }
        every { serviceRepository.setConnectionProgress(any()) } calls
            { call ->
                // Capture the most recent progress string at the moment restartTransport runs.
                // This locks in that the "Reconnecting…" UX hook fires BEFORE the app-level
                // Disconnected transition.
                progressBeforeRestart = call.arg<String>(0)
            }
        everySuspend { radioInterfaceService.restartTransport() } calls
            {
                restartTransportCalls++
                // At the moment restartTransport runs, the app-level state MUST already be
                // Disconnected — otherwise the fresh transport Connected emission would be
                // dropped by the redundant-Connecting guard and we would re-introduce the
                // split-brain this recovery path exists to break.
                assertEquals(
                    ConnectionState.Disconnected,
                    connectionStateFlow.value,
                    "restartTransport must run AFTER app-level Disconnected transition",
                )
                assertEquals(
                    ServiceRepository.RECONNECTING_PROGRESS_TEXT,
                    progressBeforeRestart,
                    "setConnectionProgress(ServiceRepository.RECONNECTING_PROGRESS_TEXT) must run before restartTransport",
                )
            }

        manager = createManager(backgroundScope)
        radioConnectionState.value = ConnectionState.Connected
        advanceTimeBy(200)
        advanceUntilIdle()

        // Fire fast stall.
        advanceTimeBy(13_000L)
        advanceUntilIdle()

        assertEquals(1, restartTransportCalls)
        // Sanity: Connecting → Disconnected was the observed app-level transition path.
        assertEquals(ConnectionState.Disconnected, observed.last(), "Last app-level state must be Disconnected")
    }

    @Test
    fun `post-handshake failure recovery disconnects before restarting transport`() = runTest(testDispatcher) {
        every { radioInterfaceService.getDeviceAddress() } returns "t192.168.1.42"

        val observed = mutableListOf<ConnectionState>()
        var progressBeforeRestart: String? = null
        var restartTransportCalls = 0
        every { serviceRepository.setConnectionState(any()) } calls
            { call ->
                val state = call.arg<ConnectionState>(0)
                observed.add(state)
                connectionStateFlow.value = state
            }
        every { serviceRepository.setConnectionProgress(any()) } calls
            { call ->
                progressBeforeRestart = call.arg<String>(0)
            }
        everySuspend { radioInterfaceService.restartTransport() } calls
            {
                restartTransportCalls++
                assertEquals(
                    ConnectionState.Disconnected,
                    connectionStateFlow.value,
                    "post-handshake recovery must disconnect app state before restarting transport",
                )
                assertEquals(
                    ServiceRepository.RECONNECTING_PROGRESS_TEXT,
                    progressBeforeRestart,
                    "post-handshake recovery should surface reconnecting progress before restartTransport",
                )
            }

        manager = createManager(backgroundScope)
        radioConnectionState.value = ConnectionState.Connected
        advanceTimeBy(200)

        manager.onHandshakeComplete()
        manager.recoverPostHandshakeFailure()
        advanceUntilIdle()

        assertEquals(1, restartTransportCalls)
        assertEquals(ConnectionState.Disconnected, observed.last(), "Last app-level state must be Disconnected")
    }

    @Test
    fun `BLE transport unaffected by onHandshakeProgress regression`() = runTest(testDispatcher) {
        // Explicit BLE address (starts with 'x') — must NOT engage the fast path.
        every { radioInterfaceService.getDeviceAddress() } returns "xAA:BB:CC:DD:EE:FF"
        manager = createManager(backgroundScope)
        radioConnectionState.value = ConnectionState.Connected
        advanceTimeBy(200)
        advanceUntilIdle()

        // Spam progress signals — BLE must ignore them, so the 30s Stage 1 budget is unchanged.
        repeat(10) {
            manager.onHandshakeProgress()
            advanceUntilIdle()
        }

        // Advance to 13s — would fire a fast watchdog if BLE were incorrectly included.
        advanceTimeBy(13_000L)
        advanceUntilIdle()

        verifySuspend(exactly(0)) { radioInterfaceService.restartTransport() }
        assertEquals(
            ConnectionState.Connecting,
            serviceRepository.connectionState.value,
            "BLE must ignore onHandshakeProgress and stay Connecting through 13s",
        )

        // Now advance past the full BLE Stage 1 budget (30s). We already advanced 13s, so 33s
        // more crosses the 30s Stage 1 stall threshold the recovery sibling fires at. (Extra
        // slack is harmless — the BLE stage has no retry delay before recovery.)
        advanceTimeBy(33_000L)
        advanceUntilIdle()

        verifySuspend(exactly(1)) { radioInterfaceService.restartTransport() }
    }

    @Test
    fun `onHandshakeProgress is a no-op when state is not Connecting`() = runTest(testDispatcher) {
        every { radioInterfaceService.getDeviceAddress() } returns "t192.168.1.42"
        manager = createManager(backgroundScope)
        // Manager starts in Disconnected (initial state, no radio Connected signal yet).
        assertEquals(
            ConnectionState.Disconnected,
            serviceRepository.connectionState.value,
            "Precondition: manager must start Disconnected",
        )

        // Calling onHandshakeProgress while not Connecting must not arm any watchdog.
        manager.onHandshakeProgress()
        advanceUntilIdle()
        advanceTimeBy(60_000L)
        advanceUntilIdle()

        verifySuspend(exactly(0)) { radioInterfaceService.restartTransport() }
    }

    @Test
    fun `USB serial transport engages fast path like TCP`() = runTest(testDispatcher) {
        // Address starting with 's' → DeviceType.USB → fast recovery transport.
        every { radioInterfaceService.getDeviceAddress() } returns "s/dev/ttyUSB0"
        manager = createManager(backgroundScope)
        radioConnectionState.value = ConnectionState.Connected
        advanceTimeBy(200)
        advanceUntilIdle()

        // Fast timeout (12s) fires for USB just like TCP.
        advanceTimeBy(13_000L)
        advanceUntilIdle()

        verifySuspend(exactly(1)) { radioInterfaceService.restartTransport() }
        assertEquals(
            ConnectionState.Disconnected,
            serviceRepository.connectionState.value,
            "USB fast stall should end in Disconnected after restart is requested",
        )
    }

    @Test
    fun `onHandshakeComplete cancels armed Stage 2 fast watchdog`() = runTest(testDispatcher) {
        // TCP address → DeviceType.TCP → fast recovery transport, 12s fast watchdog.
        every { radioInterfaceService.getDeviceAddress() } returns "t192.168.1.42"
        manager = createManager(backgroundScope)
        radioConnectionState.value = ConnectionState.Connected
        // Pre-handshake settle (100ms) completes; Stage 1 fast watchdog armed.
        advanceTimeBy(200)
        advanceUntilIdle()

        assertEquals(
            ConnectionState.Connecting,
            serviceRepository.connectionState.value,
            "Manager should be Connecting after radio Connected",
        )

        // Drive into Stage 2. startNodeInfoOnly() cancels the Stage 1 watchdog and arms Stage 2
        // with the 12s fast timeout. In production this is invoked by MeshConfigFlowManagerImpl
        // after Stage 1 completes; here we drive it directly to isolate the watchdog path.
        manager.startNodeInfoOnly()
        advanceUntilIdle()

        // Synchronously cancel the watchdog, exactly as MeshConfigFlowManagerImpl now does the
        // instant NODE_INFO_NONCE arrives — BEFORE the async DB install work begins.
        manager.onHandshakeComplete()

        // Advance past the 12s fast timeout. With the watchdog cancelled, the recovery
        // sibling must NOT fire: no restartTransport, no state transition.
        advanceTimeBy(13_000L)
        advanceUntilIdle()

        verifySuspend(exactly(0)) { radioInterfaceService.restartTransport() }
        assertEquals(
            ConnectionState.Connecting,
            serviceRepository.connectionState.value,
            "Watchdog was cancelled by onHandshakeComplete — no restart, no state transition",
        )
    }

    @Test
    fun `Second recovery attempt applies exponential backoff delay before restart`() = runTest(testDispatcher) {
        // TCP address → fast path (12s stall window). Smallest reliable cycle for backoff testing.
        every { radioInterfaceService.getDeviceAddress() } returns "t192.168.1.42"
        val restartTransportCalls = restartTransportCallCounter()

        manager = createManager(backgroundScope)
        // Initial Connected → Connecting, pre-handshake armed.
        radioConnectionState.value = ConnectionState.Connected
        advanceTimeBy(200)
        advanceUntilIdle()

        // Stall #1 fires (priorFailures=0 → no backoff). restartTransport called exactly once.
        advanceTimeBy(13_000L)
        advanceUntilIdle()
        assertEquals(1, restartTransportCalls())

        // Replay the transport restart cycle so handleConnected re-arms the stall guard. DeviceSleep
        // is a no-op here (power-saving off); Connected re-enters handleConnected → new pre-handshake.
        radioConnectionState.value = ConnectionState.DeviceSleep
        radioConnectionState.value = ConnectionState.Connected
        advanceTimeBy(200)
        advanceUntilIdle()

        // Stall #2 fires (priorFailures=1 → 2 s backoff before restartTransport). The sibling has
        // launched but is suspended in delay(2.seconds). advanceUntilIdle cannot make progress
        // because the only pending task is the timed delay.
        advanceTimeBy(13_000L)
        advanceUntilIdle()
        // Prove the backoff is in effect: restartTransport must NOT have been called yet for the
        // second stall. If the backoff were skipped, this would already be exactly(2).
        assertEquals(1, restartTransportCalls())

        // Advancing past the 2 s backoff resumes the sibling and triggers the second restart.
        advanceTimeBy(2_000L)
        advanceUntilIdle()
        assertEquals(2, restartTransportCalls())
    }

    @Test
    fun `Recovery exhaustion after three consecutive failures surfaces sticky error and stops recovery`() =
        runTest(testDispatcher) {
            every { radioInterfaceService.getDeviceAddress() } returns "t192.168.1.42"

            // Capture every setErrorMessage invocation so we can verify the sticky error surfaced
            // with Severity.Error without depending on the exact localized text.
            val errorMessages = mutableListOf<Pair<String, Severity>>()
            every { serviceRepository.setErrorMessage(any(), any()) } calls
                { call ->
                    errorMessages += call.arg<String>(0) to call.arg<Severity>(1)
                }
            val restartTransportCalls = restartTransportCallCounter()

            manager = createManager(backgroundScope)
            radioConnectionState.value = ConnectionState.Connected
            advanceTimeBy(200)
            advanceUntilIdle()

            // Stall #1 (priorFailures=0): no delay → restart. Replay Connected.
            advanceTimeBy(13_000L)
            advanceUntilIdle()
            radioConnectionState.value = ConnectionState.DeviceSleep
            radioConnectionState.value = ConnectionState.Connected
            advanceTimeBy(200)
            advanceUntilIdle()

            // Stall #2 (priorFailures=1): 2 s backoff → restart. Replay Connected.
            advanceTimeBy(13_000L)
            advanceUntilIdle()
            advanceTimeBy(2_000L)
            advanceUntilIdle()
            radioConnectionState.value = ConnectionState.DeviceSleep
            radioConnectionState.value = ConnectionState.Connected
            advanceTimeBy(200)
            advanceUntilIdle()

            // Stall #3 (priorFailures=2): 4 s backoff → restart. Replay Connected.
            advanceTimeBy(13_000L)
            advanceUntilIdle()
            advanceTimeBy(4_000L)
            advanceUntilIdle()
            assertEquals(3, restartTransportCalls())
            radioConnectionState.value = ConnectionState.DeviceSleep
            radioConnectionState.value = ConnectionState.Connected
            advanceTimeBy(200)
            advanceUntilIdle()

            // Stall #4 (priorFailures=3 = MAX_CONSECUTIVE_RECOVERY_FAILURES): sticky error path.
            // The sibling must NOT call restartTransport; instead it resets the counter, clears the
            // progress text, transitions to Disconnected, and surfaces the sticky error.
            advanceTimeBy(13_000L)
            advanceUntilIdle()

            assertEquals(3, restartTransportCalls())
            assertEquals(
                ConnectionState.Disconnected,
                serviceRepository.connectionState.value,
                "Recovery exhaustion must leave the manager in Disconnected",
            )
            // The sticky error is surfaced via setConnectionProgress("") (cleared because the user
            // must manually retry) plus setErrorMessage(..., Severity.Error). We assert at least one
            // setErrorMessage call landed with Severity.Error — this is the user-visible signal that
            // recovery gave up. (Exact text comes from Res.string.error_recovery_exhausted and is
            // resolved by the production code via getStringSuspend.)
            assertTrue(
                errorMessages.any { (_, severity) -> severity == Severity.Error },
                "Recovery exhaustion must surface a sticky ERROR-severity message; got: $errorMessages",
            )
            // Counter is reset to 0 by the sticky-error branch so a manual user retry starts fresh.
            // We can't read the private atomic directly, but the reset is exercised by the next-stall
            // behavior; its correctness is locked in by `onHandshakeComplete resets counter` and by
            // the symmetric reset-on-exhaust code path in runSiblingHandshakeRecovery().
        }

    @Test
    fun `Successful handshake resets the consecutive recovery failure counter`() = runTest(testDispatcher) {
        every { radioInterfaceService.getDeviceAddress() } returns "t192.168.1.42"
        val restartTransportCalls = restartTransportCallCounter()

        manager = createManager(backgroundScope)
        radioConnectionState.value = ConnectionState.Connected
        advanceTimeBy(200)
        advanceUntilIdle()

        // Stall #1 (priorFailures=0): no delay → restart.
        advanceTimeBy(13_000L)
        advanceUntilIdle()
        assertEquals(1, restartTransportCalls())
        radioConnectionState.value = ConnectionState.DeviceSleep
        radioConnectionState.value = ConnectionState.Connected
        advanceTimeBy(200)
        advanceUntilIdle()

        // Stall #2 (priorFailures=1): 2 s backoff → restart. Counter now at 2.
        advanceTimeBy(13_000L)
        advanceUntilIdle()
        advanceTimeBy(2_000L)
        advanceUntilIdle()
        assertEquals(2, restartTransportCalls())
        radioConnectionState.value = ConnectionState.DeviceSleep
        radioConnectionState.value = ConnectionState.Connected
        advanceTimeBy(200)
        advanceUntilIdle()

        // The next stall WOULD see priorFailures=2 (4 s backoff) if the counter were not reset.
        // Call onHandshakeComplete() — this is the production signal that the handshake succeeded
        // (e.g. NODE_INFO_NONCE arrived). It cancels the armed stall guard and resets the counter.
        manager.onHandshakeComplete()
        advanceUntilIdle()

        // Re-arm a fresh Stage 1 stall guard directly. We use startConfigOnly() (the same entry
        // point handleConnected calls) instead of replaying a Connected emission because the
        // current state is still Connecting — a redundant Connected emission would be rejected by
        // the redundant-Connected-while-Connecting guard in onConnectionChanged, so handleConnected
        // would never re-run. The counter is now 0 thanks to the reset above.
        manager.startConfigOnly()
        advanceUntilIdle()

        // Stall #3 fires. Because the counter was reset, priorFailures=0 → NO backoff. The restart
        // must land immediately, without the 2 s or 4 s delay that an un-reset counter would impose.
        advanceTimeBy(13_000L)
        advanceUntilIdle()
        assertEquals(3, restartTransportCalls())
        // If the counter had NOT been reset, priorFailures would be 2 here and the sibling would be
        // suspended in delay(4.seconds); restartTransport would still be exactly(2). Reaching
        // exactly(3) immediately after the stall proves the counter was reset to 0.
    }
}
