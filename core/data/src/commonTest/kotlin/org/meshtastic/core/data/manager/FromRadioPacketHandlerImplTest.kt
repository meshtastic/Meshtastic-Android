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
import org.meshtastic.core.model.util.isOtaStatusNotification
import org.meshtastic.core.repository.FirmwareUpdateStatusRepository
import org.meshtastic.core.repository.MeshConfigFlowManager
import org.meshtastic.core.repository.MeshConfigHandler
import org.meshtastic.core.repository.MqttManager
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.repository.PacketHandler
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.repository.RadioSessionLease
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.XModemManager
import org.meshtastic.core.testing.FakeLockdownCoordinator
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.LoRaRegionPresetMap
import org.meshtastic.proto.LockdownStatus
import org.meshtastic.proto.LogRecord
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.MqttClientProxyMessage
import org.meshtastic.proto.MyNodeInfo
import org.meshtastic.proto.QueueStatus
import org.meshtastic.proto.XModem
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.meshtastic.proto.NodeInfo as ProtoNodeInfo

class FromRadioPacketHandlerImplTest {

    private val serviceRepository: ServiceRepository = mock(MockMode.autofill)
    private val mqttManager: MqttManager = mock(MockMode.autofill)
    private val packetHandler: PacketHandler = mock(MockMode.autofill)
    private val notificationManager: NotificationManager = mock(MockMode.autofill)
    private val configFlowManager: MeshConfigFlowManager = mock(MockMode.autofill)
    private val configHandler: MeshConfigHandler = mock(MockMode.autofill)
    private val xmodemManager: XModemManager = mock(MockMode.autofill)
    private val lockdownCoordinator = FakeLockdownCoordinator()
    private val firmwareUpdateStatusRepository = FirmwareUpdateStatusRepository()
    private val radioInterfaceService: RadioInterfaceService = mock(MockMode.autofill)
    private val session = RadioSessionContext(generation = 7L, address = "tcp:test")
    private val sessionLease =
        object : RadioSessionLease {
            override val session: RadioSessionContext = this@FromRadioPacketHandlerImplTest.session

            override fun isCurrent(): Boolean = true
        }

    private lateinit var handler: FromRadioPacketHandlerImpl

    @BeforeTest
    fun setup() {
        every { radioInterfaceService.runIfSessionActive(session, any()) } calls
            {
                @Suppress("UNCHECKED_CAST")
                val block = it.args[1] as () -> Unit
                block()
                true
            }
        everySuspend { radioInterfaceService.runWithSessionLease(session, any()) } calls
            {
                @Suppress("UNCHECKED_CAST")
                val block = it.args[1] as suspend (RadioSessionLease) -> Unit
                block(sessionLease)
                true
            }
        every { configFlowManager.handleNodeInfo(any(), any()) } returns true
        every { configFlowManager.handleConfigComplete(any(), any()) } returns true
        handler =
            FromRadioPacketHandlerImpl(
                serviceRepository,
                lazy { configFlowManager },
                lazy { configHandler },
                lazy { xmodemManager },
                mqttManager,
                packetHandler,
                notificationManager,
                lockdownCoordinator,
                firmwareUpdateStatusRepository,
                radioInterfaceService,
            )
    }

    private fun handle(proto: FromRadio) = handler.handleFromRadio(proto, session)

    @Test
    fun `handleFromRadio routes MY_INFO to configFlowManager`() {
        val myInfo = MyNodeInfo.Builder().also { wb -> wb.my_node_num = 1234 }.build()
        val proto = FromRadio.Builder().also { wb -> wb.my_info = myInfo }.build()

        handle(proto)

        verify { configFlowManager.handleMyInfo(myInfo, session) }
    }

    @Test
    fun `handleFromRadio routes METADATA to configFlowManager`() {
        val metadata = DeviceMetadata.Builder().also { wb -> wb.firmware_version = "v1.0" }.build()
        val proto = FromRadio.Builder().also { wb -> wb.metadata = metadata }.build()

        handle(proto)

        verify { configFlowManager.handleLocalMetadata(metadata, session) }
    }

    @Test
    fun `handleFromRadio routes NODE_INFO to configFlowManager and updates status`() {
        val nodeInfo = ProtoNodeInfo.Builder().also { wb -> wb.num = 1234 }.build()
        val proto = FromRadio.Builder().also { wb -> wb.node_info = nodeInfo }.build()

        every { configFlowManager.newNodeCount } returns 1

        handle(proto)

        verify { configFlowManager.handleNodeInfo(nodeInfo, session) }
        verify { serviceRepository.setConnectionProgress("Nodes (1)") }
    }

    @Test
    fun `handleFromRadio routes CONFIG_COMPLETE_ID to configFlowManager`() {
        val nonce = 69420
        val proto = FromRadio.Builder().also { wb -> wb.config_complete_id = nonce }.build()

        handle(proto)

        verify { configFlowManager.handleConfigComplete(nonce, session) }
        assertTrue(lockdownCoordinator.configCompleteCalled)
    }

    @Test
    fun `stale NODE_INFO does not update connection progress`() {
        val nodeInfo = ProtoNodeInfo.Builder().also { wb -> wb.num = 1234 }.build()
        every { configFlowManager.handleNodeInfo(nodeInfo, session) } returns false

        handle(FromRadio.Builder().also { wb -> wb.node_info = nodeInfo }.build())

        verify { configFlowManager.handleNodeInfo(nodeInfo, session) }
        verify(mode = VerifyMode.exactly(0)) { serviceRepository.setConnectionProgress(any()) }
    }

    @Test
    fun `stale CONFIG_COMPLETE does not notify lockdown coordinator`() {
        val nonce = 69420
        every { configFlowManager.handleConfigComplete(nonce, session) } returns false

        handle(FromRadio.Builder().also { wb -> wb.config_complete_id = nonce }.build())

        verify { configFlowManager.handleConfigComplete(nonce, session) }
        assertFalse(lockdownCoordinator.configCompleteCalled)
    }

    @Test
    fun `revoked session skips node progress after handler returns`() {
        val nodeInfo = ProtoNodeInfo.Builder().also { wb -> wb.num = 1234 }.build()
        var active = true
        every { configFlowManager.handleNodeInfo(nodeInfo, session) } calls
            {
                active = false
                true
            }
        every { radioInterfaceService.runIfSessionActive(session, any()) } calls
            {
                if (!active) {
                    false
                } else {
                    @Suppress("UNCHECKED_CAST")
                    val block = it.args[1] as () -> Unit
                    block()
                    true
                }
            }

        handle(FromRadio.Builder().also { wb -> wb.node_info = nodeInfo }.build())

        verify(mode = VerifyMode.exactly(0)) { serviceRepository.setConnectionProgress(any()) }
    }

    @Test
    fun `revoked session skips direct packet-dispatch branches`() {
        every { radioInterfaceService.runIfSessionActive(session, any()) } returns false
        val proxyMessage = MqttClientProxyMessage.Builder().also { wb -> wb.topic = "test/topic" }.build()
        val queueStatus = QueueStatus.Builder().also { wb -> wb.free = 10 }.build()
        val xmodemPacket = XModem.Builder().build()
        val lockdownStatus = LockdownStatus.Builder().also { wb -> wb.state = LockdownStatus.State.LOCKED }.build()

        handle(FromRadio.Builder().also { wb -> wb.mqttClientProxyMessage = proxyMessage }.build())
        handle(FromRadio.Builder().also { wb -> wb.queueStatus = queueStatus }.build())
        handle(FromRadio.Builder().also { wb -> wb.xmodemPacket = xmodemPacket }.build())
        handle(FromRadio.Builder().also { wb -> wb.lockdown_status = lockdownStatus }.build())

        verify(mode = VerifyMode.exactly(0)) { mqttManager.handleMqttProxyMessage(any()) }
        verify(mode = VerifyMode.exactly(0)) { packetHandler.handleQueueStatus(any()) }
        verify(mode = VerifyMode.exactly(0)) { xmodemManager.handleIncomingXModem(any()) }
        assertEquals(null, lockdownCoordinator.lastStatus)
    }

    @Test
    fun `handleFromRadio routes LOCKDOWN_STATUS to lockdownCoordinator`() {
        val lockdownStatus =
            LockdownStatus.Builder()
                .also { wb ->
                    wb.state = LockdownStatus.State.LOCKED
                    wb.lock_reason = "token_missing"
                }
                .build()
        val proto = FromRadio.Builder().also { wb -> wb.lockdown_status = lockdownStatus }.build()

        handle(proto)

        assertEquals(lockdownStatus, lockdownCoordinator.lastStatus)
    }

    @Test
    fun `handleFromRadio routes QUEUESTATUS to packetHandler`() {
        val queueStatus = QueueStatus.Builder().also { wb -> wb.free = 10 }.build()
        val proto = FromRadio.Builder().also { wb -> wb.queueStatus = queueStatus }.build()

        handle(proto)

        verify { packetHandler.handleQueueStatus(queueStatus) }
    }

    @Test
    fun `handleFromRadio routes CONFIG to configHandler`() {
        val config =
            Config.Builder()
                .also { wb -> wb.lora = Config.LoRaConfig.Builder().also { wb -> wb.use_preset = true }.build() }
                .build()
        val proto = FromRadio.Builder().also { wb -> wb.config = config }.build()

        handle(proto)

        verify { configHandler.handleDeviceConfig(config, session) }
    }

    @Test
    fun `handleFromRadio routes MODULE_CONFIG to configHandler`() {
        val moduleConfig =
            ModuleConfig.Builder()
                .also { wb -> wb.mqtt = ModuleConfig.MQTTConfig.Builder().also { wb -> wb.enabled = true }.build() }
                .build()
        val proto = FromRadio.Builder().also { wb -> wb.moduleConfig = moduleConfig }.build()

        handle(proto)

        verify { configHandler.handleModuleConfig(moduleConfig, session) }
    }

    @Test
    fun `handleFromRadio routes CHANNEL to configHandler`() {
        val channel = Channel.Builder().also { wb -> wb.index = 0 }.build()
        val proto = FromRadio.Builder().also { wb -> wb.channel = channel }.build()

        handle(proto)

        verify { configHandler.handleChannel(channel, session) }
    }

    @Test
    fun `handleFromRadio routes REGION_PRESETS to configHandler`() {
        val map = LoRaRegionPresetMap.Builder().build()
        val proto = FromRadio.Builder().also { wb -> wb.region_presets = map }.build()

        handle(proto)

        verify { configHandler.handleRegionPresets(map, session) }
    }

    @Test
    fun `handleFromRadio routes MQTT_CLIENT_PROXY_MESSAGE to mqttManager`() {
        val proxyMsg = MqttClientProxyMessage.Builder().also { wb -> wb.topic = "test/topic" }.build()
        val proto = FromRadio.Builder().also { wb -> wb.mqttClientProxyMessage = proxyMsg }.build()

        handle(proto)

        verify { mqttManager.handleMqttProxyMessage(proxyMsg) }
    }

    @Test
    fun `handleFromRadio routes CLIENTNOTIFICATION to serviceRepository`() {
        val notification = ClientNotification.Builder().also { wb -> wb.message = "test" }.build()
        val proto = FromRadio.Builder().also { wb -> wb.clientNotification = notification }.build()

        // Note: getString() from Compose Resources requires Skiko native lib which
        // is not available in headless JVM tests. We test the parts that don't trigger it.
        try {
            handle(proto)
        } catch (_: Throwable) {
            // Expected: Skiko can't load in headless JVM/native
        }

        verify { serviceRepository.setClientNotification(notification) }
    }

    @Test
    fun `platform-suppressed client notification skips modal state but preserves system delivery`() {
        val notification = protectedPositionAdvisory(replyId = 100, time = 1_000)
        every { notificationManager.suppressClientNotificationModal(notification) } returns true

        handle(FromRadio.Builder().also { wb -> wb.clientNotification = notification }.build())

        verify(mode = VerifyMode.exactly(0)) { serviceRepository.setClientNotification(any()) }
        verifySuspend(mode = VerifyMode.exactly(1)) { radioInterfaceService.runWithSessionLease(session, any()) }
    }

    @Test
    fun `default platform policy preserves modal and system delivery for exact advisory`() {
        val notification = protectedPositionAdvisory(replyId = 200, time = 2_000)
        every { notificationManager.suppressClientNotificationModal(notification) } returns false

        handle(FromRadio.Builder().also { wb -> wb.clientNotification = notification }.build())

        verify { serviceRepository.setClientNotification(notification) }
        verifySuspend { radioInterfaceService.runWithSessionLease(session, any()) }
    }

    @Test
    fun `stale client notification is discarded before publication`() {
        val notification = ClientNotification.Builder().also { wb -> wb.message = "stale" }.build()
        every { radioInterfaceService.runIfSessionActive(session, any()) } returns false

        handle(FromRadio.Builder().also { wb -> wb.clientNotification = notification }.build())

        verify(mode = VerifyMode.exactly(0)) { serviceRepository.setClientNotification(any()) }
        verifySuspend(mode = VerifyMode.exactly(0)) { notificationManager.dispatchClientNotification(any(), any()) }
    }

    @Test
    fun `client alert is skipped when the session retires after publication`() {
        val notification = ClientNotification.Builder().also { wb -> wb.message = "retired" }.build()
        everySuspend { radioInterfaceService.runWithSessionLease(session, any()) } returns false

        handle(FromRadio.Builder().also { wb -> wb.clientNotification = notification }.build())

        verify { serviceRepository.setClientNotification(notification) }
        verifySuspend(mode = VerifyMode.exactly(0)) { notificationManager.dispatchClientNotification(any(), any()) }
    }

    @Test
    fun `OTA status client notifications are identified`() {
        assertTrue(
            ClientNotification.Builder()
                .also { wb -> wb.message = "Rebooting to WiFi OTA" }
                .build()
                .isOtaStatusNotification(),
        )
        assertTrue(
            ClientNotification.Builder()
                .also { wb -> wb.message = "OTA Loader does not support WiFi" }
                .build()
                .isOtaStatusNotification(),
        )
        assertTrue(
            ClientNotification.Builder()
                .also { wb -> wb.message = "Cannot start OTA: OTA Loader partition not found." }
                .build()
                .isOtaStatusNotification(),
        )
        assertTrue(
            ClientNotification.Builder()
                .also { wb -> wb.message = "Unable to switch to the OTA partition." }
                .build()
                .isOtaStatusNotification(),
        )
    }

    @Test
    fun `non OTA client notifications are not identified as OTA status`() {
        assertFalse(ClientNotification.Builder().also { wb -> wb.message = "test" }.build().isOtaStatusNotification())
        assertFalse(
            ClientNotification.Builder().also { wb -> wb.message = "Low battery" }.build().isOtaStatusNotification(),
        )
        assertFalse(
            ClientNotification.Builder()
                .also { wb -> wb.message = "ROTATE credentials" }
                .build()
                .isOtaStatusNotification(),
        )
        assertFalse(
            ClientNotification.Builder().also { wb -> wb.message = "Quota exceeded" }.build().isOtaStatusNotification(),
        )
    }

    private fun protectedPositionAdvisory(replyId: Int, time: Int) = ClientNotification.Builder()
        .also { wb ->
            wb.message = "Location sharing is disabled on this channel"
            wb.reply_id = replyId
            wb.time = time
            wb.level = LogRecord.Level.WARNING
        }
        .build()
}
