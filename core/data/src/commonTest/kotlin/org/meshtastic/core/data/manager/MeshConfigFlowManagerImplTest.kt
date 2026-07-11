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
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.encodeUtf8
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.HandshakeConstants
import org.meshtastic.core.repository.MeshConnectionManager
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.NotificationPrefs
import org.meshtastic.core.repository.PacketHandler
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.FileInfo
import org.meshtastic.proto.FirmwareEdition
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.NodeInfo
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.meshtastic.proto.MyNodeInfo as ProtoMyNodeInfo

@OptIn(ExperimentalCoroutinesApi::class)
class MeshConfigFlowManagerImplTest {

    private companion object {
        // Production issues two sequential delay(wantConfigDelay) calls (100ms each = 200ms)
        // plus scheduler slack for the inter-stage heartbeat and startNodeInfoOnly(); bump in
        // lockstep with wantConfigDelay if it changes.
        const val STAGE_TRANSITION_ADVANCE_MS = 250L
    }

    private val nodeManager = mock<NodeManager>(MockMode.autofill)
    private val connectionManager = mock<MeshConnectionManager>(MockMode.autofill)
    private val nodeRepository = mock<NodeRepository>(MockMode.autofill)
    private val radioConfigRepository = mock<RadioConfigRepository>(MockMode.autofill)
    private val serviceRepository = mock<ServiceRepository>(MockMode.autofill)
    private val analytics = mock<PlatformAnalytics>(MockMode.autofill)
    private val commandSender = mock<CommandSender>(MockMode.autofill)
    private val packetHandler = mock<PacketHandler>(MockMode.autofill)
    private val notificationPrefs = mock<NotificationPrefs>(MockMode.autofill)

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var manager: MeshConfigFlowManagerImpl

    private val myNodeNum = 12345

    private val protoMyNodeInfo =
        ProtoMyNodeInfo(
            my_node_num = myNodeNum,
            min_app_version = 30000,
            device_id = "test-device".encodeUtf8(),
            pio_env = "",
        )

    private val metadata =
        DeviceMetadata(firmware_version = "2.6.0", hw_model = HardwareModel.HELTEC_V3, hasWifi = false)

    @BeforeTest
    fun setUp() {
        every { commandSender.getCurrentPacketId() } returns 100
        every { packetHandler.sendToRadio(any<org.meshtastic.proto.ToRadio>()) } returns Unit
        every { nodeManager.nodeDBbyNodeNum } returns emptyMap()
        every { nodeManager.myNodeNum } returns MutableStateFlow(null)
        every { notificationPrefs.nodeEventsAutoDisabledForEvent } returns MutableStateFlow(false)
        every { notificationPrefs.nodeEventsEnabled } returns MutableStateFlow(true)
        // autofill returns null for List<Int>, which would NPE the non-null return; individual
        // tests override this stub where they exercise install failures or migration results.
        everySuspend { nodeRepository.installConfig(any(), any()) } returns emptyList()

        manager =
            MeshConfigFlowManagerImpl(
                nodeManager = nodeManager,
                connectionManager = lazy { connectionManager },
                nodeRepository = nodeRepository,
                radioConfigRepository = radioConfigRepository,
                serviceStateWriter = serviceRepository,
                analytics = analytics,
                commandSender = commandSender,
                heartbeatSender = DataLayerHeartbeatSender(packetHandler),
                notificationPrefs = notificationPrefs,
                scope = testScope,
            )
    }

    // ---------- handleMyInfo ----------

    @Test
    fun `handleMyInfo transitions to ReceivingConfig and sets myNodeNum`() = testScope.runTest {
        manager.handleMyInfo(protoMyNodeInfo)
        advanceUntilIdle()

        verify { nodeManager.setMyNodeNum(myNodeNum) }
    }

    @Test
    fun `handleMyInfo clears persisted radio config`() = testScope.runTest {
        manager.handleMyInfo(protoMyNodeInfo)
        advanceUntilIdle()

        verifySuspend { radioConfigRepository.clearChannelSet() }
        verifySuspend { radioConfigRepository.clearLocalConfig() }
        verifySuspend { radioConfigRepository.clearLocalModuleConfig() }
        verifySuspend { radioConfigRepository.clearDeviceUIConfig() }
        verifySuspend { radioConfigRepository.clearFileManifest() }
        verifySuspend { radioConfigRepository.clearLoraRegionPresetMap() }
    }

    // ---------- handleLocalMetadata ----------

    @Test
    fun `handleLocalMetadata persists metadata when in ReceivingConfig state`() = testScope.runTest {
        manager.handleMyInfo(protoMyNodeInfo)
        advanceUntilIdle()

        manager.handleLocalMetadata(metadata)
        advanceUntilIdle()

        verifySuspend { nodeRepository.insertMetadata(myNodeNum, metadata) }
    }

    @Test
    fun `handleLocalMetadata skips empty metadata`() = testScope.runTest {
        manager.handleMyInfo(protoMyNodeInfo)
        advanceUntilIdle()

        // Default/empty DeviceMetadata should not trigger insertMetadata
        manager.handleLocalMetadata(DeviceMetadata())
        advanceUntilIdle()

        // insertMetadata should only have been called zero times for default metadata
        // (we just verify no crash occurs)
    }

    @Test
    fun `handleLocalMetadata ignored outside ReceivingConfig state`() = testScope.runTest {
        // State is Idle — handleLocalMetadata should be a no-op
        manager.handleLocalMetadata(metadata)
        advanceUntilIdle()
        // No crash, no insertMetadata call
    }

    // ---------- handleConfigComplete Stage 1 ----------

    @Test
    fun `Stage 1 complete builds MyNodeInfo and transitions to ReceivingNodeInfo`() = testScope.runTest {
        manager.handleMyInfo(protoMyNodeInfo)
        advanceUntilIdle()
        manager.handleLocalMetadata(metadata)
        advanceUntilIdle()

        manager.handleConfigComplete(HandshakeConstants.CONFIG_NONCE)
        advanceTimeBy(STAGE_TRANSITION_ADVANCE_MS)
        runCurrent()

        verify { connectionManager.onRadioConfigLoaded() }
        verify { connectionManager.startNodeInfoOnly() }
    }

    @Test
    fun `Stage 1 complete sends heartbeat with non-zero nonce between stages`() = testScope.runTest {
        val sentPackets = mutableListOf<org.meshtastic.proto.ToRadio>()
        every { packetHandler.sendToRadio(any<org.meshtastic.proto.ToRadio>()) } calls
            { call ->
                sentPackets.add(call.arg(0))
            }

        manager.handleMyInfo(protoMyNodeInfo)
        advanceUntilIdle()
        manager.handleLocalMetadata(metadata)
        advanceUntilIdle()

        sentPackets.clear() // Clear any packets from prior phases
        manager.handleConfigComplete(HandshakeConstants.CONFIG_NONCE)
        advanceTimeBy(STAGE_TRANSITION_ADVANCE_MS)
        runCurrent()

        val heartbeats = sentPackets.filter { it.heartbeat != null }
        assertEquals(1, heartbeats.size, "Expected exactly one inter-stage heartbeat")
        assertEquals(
            true,
            heartbeats[0].heartbeat!!.nonce != 0,
            "Inter-stage heartbeat should have a non-zero nonce",
        )
    }

    @Test
    fun `Stage 1 complete with old firmware logs warning but continues handshake`() = testScope.runTest {
        val oldMetadata =
            DeviceMetadata(firmware_version = "2.3.0", hw_model = HardwareModel.HELTEC_V3, hasWifi = false)
        manager.handleMyInfo(protoMyNodeInfo)
        advanceUntilIdle()
        manager.handleLocalMetadata(oldMetadata)
        advanceUntilIdle()

        manager.handleConfigComplete(HandshakeConstants.CONFIG_NONCE)
        advanceTimeBy(STAGE_TRANSITION_ADVANCE_MS)
        runCurrent()

        // Handshake should still progress despite old firmware
        verify { connectionManager.onRadioConfigLoaded() }
        verify { connectionManager.startNodeInfoOnly() }
    }

    @Test
    fun `Stage 1 complete without metadata still succeeds with null firmware version`() = testScope.runTest {
        manager.handleMyInfo(protoMyNodeInfo)
        advanceUntilIdle()

        // No metadata provided
        manager.handleConfigComplete(HandshakeConstants.CONFIG_NONCE)
        advanceUntilIdle()

        verify { connectionManager.onRadioConfigLoaded() }
    }

    @Test
    fun `Stage 1 complete updates progress for node list loading`() = testScope.runTest {
        manager.handleMyInfo(protoMyNodeInfo)
        advanceUntilIdle()
        manager.handleLocalMetadata(metadata)
        advanceUntilIdle()

        manager.handleConfigComplete(HandshakeConstants.CONFIG_NONCE)
        advanceTimeBy(STAGE_TRANSITION_ADVANCE_MS)
        runCurrent()

        verify { serviceRepository.setConnectionProgress("Loading node list") }
    }

    @Test
    fun `Stage 1 complete id ignored when not in ReceivingConfig state`() = testScope.runTest {
        // State is Idle — should be a no-op
        manager.handleConfigComplete(HandshakeConstants.CONFIG_NONCE)
        advanceUntilIdle()
        // No crash, no onRadioConfigLoaded
    }

    @Test
    fun `Duplicate Stage 1 config_complete does not re-trigger`() = testScope.runTest {
        manager.handleMyInfo(protoMyNodeInfo)
        advanceUntilIdle()
        manager.handleLocalMetadata(metadata)
        advanceUntilIdle()

        manager.handleConfigComplete(HandshakeConstants.CONFIG_NONCE)
        advanceTimeBy(STAGE_TRANSITION_ADVANCE_MS)
        runCurrent()

        // Now in ReceivingNodeInfo — a second Stage 1 complete should be ignored
        manager.handleConfigComplete(HandshakeConstants.CONFIG_NONCE)
        advanceTimeBy(STAGE_TRANSITION_ADVANCE_MS)
        runCurrent()
    }

    // ---------- handleNodeInfo ----------

    @Test
    fun `handleNodeInfo accumulates nodes during Stage 2`() = testScope.runTest {
        // Transition to Stage 2
        manager.handleMyInfo(protoMyNodeInfo)
        advanceUntilIdle()
        manager.handleLocalMetadata(metadata)
        advanceUntilIdle()
        manager.handleConfigComplete(HandshakeConstants.CONFIG_NONCE)
        advanceTimeBy(STAGE_TRANSITION_ADVANCE_MS)
        runCurrent()

        // Now in ReceivingNodeInfo
        manager.handleNodeInfo(NodeInfo(num = 100))
        manager.handleNodeInfo(NodeInfo(num = 200))

        assertEquals(2, manager.newNodeCount)
    }

    @Test
    fun `handleNodeInfo buffers nodes received during Stage 1`() = testScope.runTest {
        val testNode = org.meshtastic.core.testing.TestDataFactory.createTestNode(num = 100)
        every { nodeManager.nodeDBbyNodeNum } returns mapOf(100 to testNode)

        manager.handleMyInfo(protoMyNodeInfo)
        advanceUntilIdle()

        manager.handleNodeInfo(NodeInfo(num = 100))
        assertEquals(1, manager.newNodeCount)

        manager.handleLocalMetadata(metadata)
        advanceUntilIdle()
        manager.handleConfigComplete(HandshakeConstants.CONFIG_NONCE)
        advanceTimeBy(STAGE_TRANSITION_ADVANCE_MS)
        runCurrent()
        manager.handleConfigComplete(HandshakeConstants.NODE_INFO_NONCE)
        advanceUntilIdle()

        verify { nodeManager.installNodeInfo(NodeInfo(num = 100)) }
        verifySuspend { connectionManager.onNodeDbReady() }
    }

    @Test
    fun `handleNodeInfo ignored outside Stage 2`() = testScope.runTest {
        // State is Idle
        manager.handleNodeInfo(NodeInfo(num = 999))

        assertEquals(0, manager.newNodeCount)
    }

    // ---------- handleConfigComplete Stage 2 ----------

    @Test
    fun `Stage 2 complete processes nodes and sets Connected state`() = testScope.runTest {
        val testNode = org.meshtastic.core.testing.TestDataFactory.createTestNode(num = 100)
        every { nodeManager.nodeDBbyNodeNum } returns mapOf(100 to testNode)

        // Full handshake: MyInfo -> metadata -> Stage 1 complete -> nodes -> Stage 2 complete
        manager.handleMyInfo(protoMyNodeInfo)
        advanceUntilIdle()
        manager.handleLocalMetadata(metadata)
        advanceUntilIdle()
        manager.handleConfigComplete(HandshakeConstants.CONFIG_NONCE)
        advanceTimeBy(STAGE_TRANSITION_ADVANCE_MS)
        runCurrent()

        manager.handleNodeInfo(NodeInfo(num = 100))
        manager.handleConfigComplete(HandshakeConstants.NODE_INFO_NONCE)
        advanceUntilIdle()

        verify { nodeManager.installNodeInfo(any()) }
        verify { nodeManager.setNodeDbReady(true) }
        verify { nodeManager.setAllowNodeDbWrites(true) }
        verifySuspend { connectionManager.onNodeDbReady() }
    }

    @Test
    fun `Stage 2 complete id ignored when not in ReceivingNodeInfo state`() = testScope.runTest {
        manager.handleConfigComplete(HandshakeConstants.NODE_INFO_NONCE)
        advanceUntilIdle()
        // No crash
    }

    @Test
    fun `Stage 2 complete with no nodes still transitions to Connected`() = testScope.runTest {
        manager.handleMyInfo(protoMyNodeInfo)
        advanceUntilIdle()
        manager.handleLocalMetadata(metadata)
        advanceUntilIdle()
        manager.handleConfigComplete(HandshakeConstants.CONFIG_NONCE)
        advanceTimeBy(STAGE_TRANSITION_ADVANCE_MS)
        runCurrent()

        // No handleNodeInfo calls — empty node list
        manager.handleConfigComplete(HandshakeConstants.NODE_INFO_NONCE)
        advanceUntilIdle()

        verify { nodeManager.setNodeDbReady(true) }
        verifySuspend { connectionManager.onNodeDbReady() }
    }

    @Test
    fun `Stage 2 complete keeps connected when post NodeDB side effects fail`() = testScope.runTest {
        every { analytics.setDeviceAttributes(any(), any()) } calls { throw IllegalStateException("analytics") }
        everySuspend { connectionManager.onNodeDbReady() } calls { throw IllegalStateException("side effects") }

        manager.handleMyInfo(protoMyNodeInfo)
        advanceUntilIdle()
        manager.handleLocalMetadata(metadata)
        advanceUntilIdle()
        manager.handleConfigComplete(HandshakeConstants.CONFIG_NONCE)
        advanceTimeBy(STAGE_TRANSITION_ADVANCE_MS)
        runCurrent()

        manager.handleConfigComplete(HandshakeConstants.NODE_INFO_NONCE)
        advanceUntilIdle()

        verify { nodeManager.setNodeDbReady(true) }
        verify { nodeManager.setAllowNodeDbWrites(true) }
        verify { serviceRepository.setConnectionState(ConnectionState.Connected) }
        verifySuspend { connectionManager.onNodeDbReady() }
        verify(mode = VerifyMode.not) { nodeManager.setNodeDbReady(false) }
        verify(mode = VerifyMode.not) { nodeManager.setAllowNodeDbWrites(false) }
        verify(mode = VerifyMode.not) { connectionManager.recoverPostHandshakeFailure() }
    }

    @Test
    fun `Stage 2 complete disconnects when NodeDB install fails`() = testScope.runTest {
        everySuspend { nodeRepository.installConfig(any(), any()) } calls { throw IllegalStateException("room") }

        manager.handleMyInfo(protoMyNodeInfo)
        advanceUntilIdle()
        manager.handleLocalMetadata(metadata)
        advanceUntilIdle()
        manager.handleConfigComplete(HandshakeConstants.CONFIG_NONCE)
        advanceTimeBy(STAGE_TRANSITION_ADVANCE_MS)
        runCurrent()

        manager.handleConfigComplete(HandshakeConstants.NODE_INFO_NONCE)
        advanceUntilIdle()

        verify { connectionManager.onHandshakeComplete() }
        verify { nodeManager.setNodeDbReady(false) }
        verify { nodeManager.setAllowNodeDbWrites(false) }
        verify { connectionManager.recoverPostHandshakeFailure() }
        verify(mode = VerifyMode.not) { nodeManager.setNodeDbReady(true) }
        verifySuspend(mode = VerifyMode.not) { connectionManager.onNodeDbReady() }
    }

    // ---------- Unknown config_complete_id ----------

    @Test
    fun `Unknown config_complete_id is ignored`() = testScope.runTest {
        manager.handleConfigComplete(99999)
        advanceUntilIdle()
        // No crash
    }

    // ---------- newNodeCount ----------

    @Test
    fun `newNodeCount returns 0 when not in ReceivingNodeInfo state`() {
        assertEquals(0, manager.newNodeCount)
    }

    // ---------- handleFileInfo ----------

    @Test
    fun `handleFileInfo delegates to radioConfigRepository`() = testScope.runTest {
        val fileInfo = FileInfo(file_name = "firmware.bin", size_bytes = 1024)
        manager.handleFileInfo(fileInfo)
        advanceUntilIdle()

        verifySuspend { radioConfigRepository.addFileInfo(fileInfo) }
    }

    // ---------- triggerWantConfig ----------

    @Test
    fun `triggerWantConfig delegates to connectionManager startConfigOnly`() {
        manager.triggerWantConfig()
        verify { connectionManager.startConfigOnly() }
    }

    // ---------- Full handshake flow ----------

    @Test
    fun `Full handshake from Idle to Complete`() = testScope.runTest {
        val testNode = org.meshtastic.core.testing.TestDataFactory.createTestNode(num = 100)
        every { nodeManager.nodeDBbyNodeNum } returns mapOf(100 to testNode)

        // Stage 0: Idle -> handleMyInfo
        manager.handleMyInfo(protoMyNodeInfo)
        advanceUntilIdle()
        verify { nodeManager.setMyNodeNum(myNodeNum) }

        // Receive metadata during Stage 1
        manager.handleLocalMetadata(metadata)
        advanceUntilIdle()

        // Stage 1 complete
        manager.handleConfigComplete(HandshakeConstants.CONFIG_NONCE)
        advanceTimeBy(STAGE_TRANSITION_ADVANCE_MS)
        runCurrent()
        verify { connectionManager.onRadioConfigLoaded() }

        // Receive NodeInfo during Stage 2
        manager.handleNodeInfo(NodeInfo(num = 100))
        assertEquals(1, manager.newNodeCount)

        // Stage 2 complete
        manager.handleConfigComplete(HandshakeConstants.NODE_INFO_NONCE)
        advanceUntilIdle()

        verify { nodeManager.setNodeDbReady(true) }
        verifySuspend { connectionManager.onNodeDbReady() }

        // After complete, newNodeCount should be 0 (state is Complete)
        assertEquals(0, manager.newNodeCount)
    }

    // ---------- Interrupted handshake ----------

    @Test
    fun `handleMyInfo resets stale handshake state`() = testScope.runTest {
        // Start first handshake
        manager.handleMyInfo(protoMyNodeInfo)
        advanceUntilIdle()
        manager.handleLocalMetadata(metadata)
        advanceUntilIdle()

        // Before Stage 1 completes, a new handleMyInfo arrives (device rebooted)
        val newMyInfo = protoMyNodeInfo.copy(my_node_num = 99999)
        manager.handleMyInfo(newMyInfo)
        advanceUntilIdle()

        verify { nodeManager.setMyNodeNum(99999) }
    }

    // ---------- Event firmware notification defaults ----------

    @Test
    fun `handleMyInfo disables node notifications for event firmware`() = testScope.runTest {
        every { notificationPrefs.nodeEventsAutoDisabledForEvent } returns MutableStateFlow(false)

        val eventMyInfo = protoMyNodeInfo.copy(firmware_edition = FirmwareEdition.DEFCON)
        manager.handleMyInfo(eventMyInfo)
        advanceUntilIdle()

        verify { notificationPrefs.setNodeEventsEnabled(false) }
        verify { notificationPrefs.setNodeEventsAutoDisabledForEvent(true) }
    }

    @Test
    fun `handleMyInfo does not re-disable if already auto-disabled`() = testScope.runTest {
        every { notificationPrefs.nodeEventsAutoDisabledForEvent } returns MutableStateFlow(true)

        val eventMyInfo = protoMyNodeInfo.copy(firmware_edition = FirmwareEdition.DEFCON)
        manager.handleMyInfo(eventMyInfo)
        advanceUntilIdle()

        verify(mode = VerifyMode.not) { notificationPrefs.setNodeEventsEnabled(any()) }
    }

    @Test
    fun `handleMyInfo re-enables node notifications when vanilla firmware reconnects`() = testScope.runTest {
        every { notificationPrefs.nodeEventsAutoDisabledForEvent } returns MutableStateFlow(true)

        manager.handleMyInfo(protoMyNodeInfo)
        advanceUntilIdle()

        verify { notificationPrefs.setNodeEventsEnabled(true) }
        verify { notificationPrefs.setNodeEventsAutoDisabledForEvent(false) }
    }

    @Test
    fun `handleMyInfo does not touch prefs for vanilla when not previously auto-disabled`() = testScope.runTest {
        every { notificationPrefs.nodeEventsAutoDisabledForEvent } returns MutableStateFlow(false)

        manager.handleMyInfo(protoMyNodeInfo)
        advanceUntilIdle()

        verify(mode = VerifyMode.not) { notificationPrefs.setNodeEventsEnabled(any()) }
        verify(mode = VerifyMode.not) { notificationPrefs.setNodeEventsAutoDisabledForEvent(any()) }
    }

    // ---------- onHandshakeProgress ----------

    @Test
    fun `handleMyInfo calls onHandshakeProgress`() = testScope.runTest {
        manager.handleMyInfo(protoMyNodeInfo)
        advanceUntilIdle()

        verify { connectionManager.onHandshakeProgress() }
    }

    @Test
    fun `handleLocalMetadata in ReceivingConfig calls onHandshakeProgress`() = testScope.runTest {
        manager.handleMyInfo(protoMyNodeInfo)
        advanceUntilIdle()
        manager.handleLocalMetadata(metadata)
        advanceUntilIdle()

        // handleMyInfo fires one call; handleLocalMetadata adds exactly one more in ReceivingConfig.
        verify(mode = VerifyMode.exactly(2)) { connectionManager.onHandshakeProgress() }
    }

    @Test
    fun `handleLocalMetadata outside ReceivingConfig does not call onHandshakeProgress`() = testScope.runTest {
        // State is Idle — metadata is ignored and must not reset the watchdog.
        manager.handleLocalMetadata(metadata)
        advanceUntilIdle()

        verify(mode = VerifyMode.not) { connectionManager.onHandshakeProgress() }
    }

    @Test
    fun `handleFileInfo calls onHandshakeProgress`() = testScope.runTest {
        val fileInfo = FileInfo(file_name = "firmware.bin", size_bytes = 1024)
        manager.handleFileInfo(fileInfo)
        advanceUntilIdle()

        verify { connectionManager.onHandshakeProgress() }
    }

    @Test
    fun `handleNodeInfo during Stage 1 calls onHandshakeProgress`() = testScope.runTest {
        manager.handleMyInfo(protoMyNodeInfo)
        advanceUntilIdle()
        manager.handleNodeInfo(NodeInfo(num = 100))

        // handleMyInfo fires one call; handleNodeInfo in ReceivingConfig adds exactly one more.
        verify(mode = VerifyMode.exactly(2)) { connectionManager.onHandshakeProgress() }
    }

    @Test
    fun `handleNodeInfo during Stage 2 calls onHandshakeProgress`() = testScope.runTest {
        manager.handleMyInfo(protoMyNodeInfo)
        advanceUntilIdle()
        manager.handleLocalMetadata(metadata)
        advanceUntilIdle()
        manager.handleConfigComplete(HandshakeConstants.CONFIG_NONCE)
        advanceTimeBy(STAGE_TRANSITION_ADVANCE_MS)
        runCurrent()

        // Now in ReceivingNodeInfo — a NodeInfo packet must reset the watchdog.
        manager.handleNodeInfo(NodeInfo(num = 100))

        // Prior calls: handleMyInfo(1) + handleLocalMetadata(1) + handleConfigOnlyComplete(1) = 3.
        // handleNodeInfo adds exactly one more.
        verify(mode = VerifyMode.exactly(4)) { connectionManager.onHandshakeProgress() }
    }

    @Test
    fun `Stage 1 complete calls onHandshakeProgress`() = testScope.runTest {
        manager.handleMyInfo(protoMyNodeInfo)
        advanceUntilIdle()
        manager.handleLocalMetadata(metadata)
        advanceUntilIdle()
        manager.handleConfigComplete(HandshakeConstants.CONFIG_NONCE)
        advanceTimeBy(STAGE_TRANSITION_ADVANCE_MS)
        runCurrent()

        // handleMyInfo(1) + handleLocalMetadata(1) + handleConfigOnlyComplete(1) = 3.
        verify(mode = VerifyMode.exactly(3)) { connectionManager.onHandshakeProgress() }
    }

    @Test
    fun `handleNodeInfo outside active handshake does not call onHandshakeProgress`() = testScope.runTest {
        // State is Idle — NodeInfo is ignored and must not reset the watchdog.
        manager.handleNodeInfo(NodeInfo(num = 999))

        verify(mode = VerifyMode.not) { connectionManager.onHandshakeProgress() }
    }

    /**
     * Regression guard: a full handshake must fire [MeshConnectionManager.onHandshakeProgress] exactly for meaningful
     * handshake events only. Packet types not meaningful to the handshake — queueStatus, mqttClientProxyMessage,
     * xmodemPacket, clientNotification, deviceuiConfig, and rebooted — are routed to sibling handlers
     * (PacketHandlerImpl, MqttManagerImpl, FromRadioPacketHandlerImpl, MeshConfigHandlerImpl) and must never reset the
     * watchdog from this manager. This test pins the exact count so any accidental wiring surfaces as a failure.
     */
    @Test
    fun `full handshake fires onHandshakeProgress exactly for meaningful events only`() = testScope.runTest {
        val testNode = org.meshtastic.core.testing.TestDataFactory.createTestNode(num = 100)
        every { nodeManager.nodeDBbyNodeNum } returns mapOf(100 to testNode)

        manager.handleMyInfo(protoMyNodeInfo)
        advanceUntilIdle()
        manager.handleLocalMetadata(metadata)
        advanceUntilIdle()
        manager.handleConfigComplete(HandshakeConstants.CONFIG_NONCE)
        advanceTimeBy(STAGE_TRANSITION_ADVANCE_MS)
        runCurrent()
        manager.handleNodeInfo(NodeInfo(num = 100))
        manager.handleConfigComplete(HandshakeConstants.NODE_INFO_NONCE)
        advanceUntilIdle()

        // handleMyInfo(1) + handleLocalMetadata(1) + handleConfigOnlyComplete(1)
        //   + handleNodeInfo(1) = 4. handleNodeInfoComplete intentionally does NOT call
        //   onHandshakeProgress: by that point the handshake is Complete and the synchronous
        //   onHandshakeComplete() call (verified in a separate test) cancels the watchdog.
        verify(mode = VerifyMode.exactly(4)) { connectionManager.onHandshakeProgress() }
    }

    /**
     * Regression guard for the Stage 2 watchdog-cancellation race fixed by adding
     * [MeshConnectionManager.onHandshakeComplete]. Stage 2 completion must synchronously fire the terminal callback
     * exactly once so the transport-aware fast-recovery watchdog is cancelled before any async DB install work begins.
     */
    @Test
    fun `Stage 2 complete fires onHandshakeComplete exactly once`() = testScope.runTest {
        val testNode = org.meshtastic.core.testing.TestDataFactory.createTestNode(num = 100)
        every { nodeManager.nodeDBbyNodeNum } returns mapOf(100 to testNode)

        manager.handleMyInfo(protoMyNodeInfo)
        advanceUntilIdle()
        manager.handleLocalMetadata(metadata)
        advanceUntilIdle()
        manager.handleConfigComplete(HandshakeConstants.CONFIG_NONCE)
        advanceTimeBy(STAGE_TRANSITION_ADVANCE_MS)
        runCurrent()
        manager.handleNodeInfo(NodeInfo(num = 100))
        manager.handleConfigComplete(HandshakeConstants.NODE_INFO_NONCE)
        advanceUntilIdle()

        verify(mode = VerifyMode.exactly(1)) { connectionManager.onHandshakeComplete() }
    }

    /**
     * Regression guard for the actual Stage 2 watchdog-cancellation race: the synchronous
     * [MeshConnectionManager.onHandshakeComplete] call MUST land before the asynchronous NodeDB install work begins, so
     * that a slow Room commit on a large mesh cannot trip the 12 s fast-recovery timeout after the firmware handshake
     * has already succeeded.
     *
     * Under [StandardTestDispatcher] the async DB install coroutine does not run until the test dispatcher is advanced,
     * so we can assert the call ordering deterministically without any suspension trick on
     * [NodeRepository.installConfig].
     */
    @Test
    fun `Stage 2 complete cancels watchdog synchronously before async DB install work`() = testScope.runTest {
        val testNode = org.meshtastic.core.testing.TestDataFactory.createTestNode(num = 100)
        every { nodeManager.nodeDBbyNodeNum } returns mapOf(100 to testNode)

        // Record the order in which the synchronous watchdog-cancellation callback and the
        // async DB install entry point are observed. The order is the invariant under test.
        val callOrder = mutableListOf<String>()
        every { connectionManager.onHandshakeComplete() } calls { callOrder.add("handshakeComplete") }
        everySuspend { nodeRepository.installConfig(any(), any()) } calls
            {
                callOrder.add("installConfig")
                emptyList()
            }

        manager.handleMyInfo(protoMyNodeInfo)
        advanceUntilIdle()
        manager.handleLocalMetadata(metadata)
        advanceUntilIdle()
        manager.handleConfigComplete(HandshakeConstants.CONFIG_NONCE)
        advanceTimeBy(STAGE_TRANSITION_ADVANCE_MS)
        runCurrent()
        manager.handleNodeInfo(NodeInfo(num = 100))

        // Drive Stage 2 complete. handleNodeInfoComplete runs synchronously: state becomes
        // Complete, onHandshakeComplete() fires (cancelling the watchdog), then the async DB
        // install block is launched but not yet executed under StandardTestDispatcher.
        manager.handleConfigComplete(HandshakeConstants.NODE_INFO_NONCE)

        // Synchronous post-condition: the watchdog was cancelled BEFORE the async DB install
        // block was scheduled to run. If this ordering invariant ever regresses, a slow Room
        // commit on a large mesh would falsely trip the 12s fast-recovery timeout.
        assertEquals(
            listOf("handshakeComplete"),
            callOrder,
            "onHandshakeComplete() must fire synchronously at Stage 2 complete, BEFORE any " +
                "async DB install work begins — this is the race this test guards against",
        )

        // Now let the async DB install block run.
        advanceUntilIdle()
        assertEquals(
            listOf("handshakeComplete", "installConfig"),
            callOrder,
            "installConfig must run AFTER onHandshakeComplete, ensuring the watchdog is " +
                "already cancelled before any DB work could trip the fast-recovery timeout",
        )
    }
}
