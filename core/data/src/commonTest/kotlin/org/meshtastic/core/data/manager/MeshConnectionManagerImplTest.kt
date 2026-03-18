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


import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.AppWidgetUpdater
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.HistoryManager
import org.meshtastic.core.repository.MeshLocationManager
import org.meshtastic.core.repository.MeshServiceNotifications
import org.meshtastic.core.repository.MeshWorkerManager
import org.meshtastic.core.repository.MqttManager
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketHandler
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.ServiceBroadcasts
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.core.resources.getString
import org.meshtastic.proto.Config
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.LocalStats
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.ToRadio

class MeshConnectionManagerImplTest {
/*



    private val radioConnectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    private val connectionStateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    private val localConfigFlow = MutableStateFlow(LocalConfig())
    private val moduleConfigFlow = MutableStateFlow(LocalModuleConfig())

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var manager: MeshConnectionManagerImpl

    @Before
    fun setUp() {
        mockkStatic("org.meshtastic.core.resources.GetStringKt")

        every { radioInterfaceService.connectionState } returns radioConnectionState
        every { radioConfigRepository.localConfigFlow } returns localConfigFlow
        every { radioConfigRepository.moduleConfigFlow } returns moduleConfigFlow
        every { nodeRepository.myNodeInfo } returns MutableStateFlow<MyNodeInfo?>(null)
        every { nodeRepository.ourNodeInfo } returns MutableStateFlow<Node?>(null)
        every { nodeRepository.localStats } returns MutableStateFlow(LocalStats())
        every { serviceRepository.connectionState } returns connectionStateFlow

        manager =
            MeshConnectionManagerImpl(
                radioInterfaceService,
                serviceRepository,
                serviceBroadcasts,
                serviceNotifications,
                uiPrefs,
                packetHandler,
                nodeRepository,
                locationManager,
                mqttManager,
                historyManager,
                radioConfigRepository,
                commandSender,
                nodeManager,
                analytics,
                packetRepository,
                workerManager,
                appWidgetUpdater,
            )
    }

    @After
    fun tearDown() {
        unmockkStatic("org.meshtastic.core.resources.GetStringKt")
    }

    @Test
    fun `Connected state triggers broadcast and config start`() = runTest(testDispatcher) {
        manager.start(backgroundScope)
        radioConnectionState.value = ConnectionState.Connected
        advanceUntilIdle()

        assertEquals(
            "State should be Connecting after radio Connected",
            ConnectionState.Connecting,
            serviceRepository.connectionState.value,
        )
        verify { serviceBroadcasts.broadcastConnection() }
    }

    @Test
    fun `Disconnected state stops services`() = runTest(testDispatcher) {
        manager.start(backgroundScope)
        // Transition to Connected first so that Disconnected actually does something
        radioConnectionState.value = ConnectionState.Connected
        advanceUntilIdle()

        radioConnectionState.value = ConnectionState.Disconnected
        advanceUntilIdle()

        assertEquals(
            "State should be Disconnected after radio Disconnected",
            ConnectionState.Disconnected,
            serviceRepository.connectionState.value,
        )
        verify { packetHandler.stopPacketQueue() }
        verify { locationManager.stop() }
        verify { mqttManager.stop() }
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

        manager.start(backgroundScope)
        advanceUntilIdle()

        radioConnectionState.value = ConnectionState.DeviceSleep
        advanceUntilIdle()

        assertEquals(
            "State should be Disconnected when power saving is off",
            ConnectionState.Disconnected,
            serviceRepository.connectionState.value,
        )
    }

    @Test
    fun `DeviceSleep behavior when power saving is on stays in DeviceSleep`() = runTest(testDispatcher) {
        // Power saving enabled
        val config = LocalConfig(power = Config.PowerConfig(is_power_saving = true))
        every { radioConfigRepository.localConfigFlow } returns flowOf(config)

        manager.start(backgroundScope)
        advanceUntilIdle()

        radioConnectionState.value = ConnectionState.DeviceSleep
        advanceUntilIdle()

        assertEquals(
            "State should stay in DeviceSleep when power saving is on",
            ConnectionState.DeviceSleep,
            serviceRepository.connectionState.value,
        )
    }

    @Test
    fun `onRadioConfigLoaded enqueues queued packets and sets time`() = runTest(testDispatcher) {
        manager.start(backgroundScope)
        val packetId = 456
        every { dataPacket.id } returns packetId
        everySuspend { packetRepository.getQueuedPackets() } returns listOf(dataPacket)

        manager.onRadioConfigLoaded()
        advanceUntilIdle()

        verify { workerManager.enqueueSendMessage(packetId) }
    }

    @Test
    fun `onNodeDbReady starts MQTT and requests history`() = runTest(testDispatcher) {
        every { moduleConfig.mqtt } returns ModuleConfig.MQTTConfig(enabled = true)
        every { moduleConfig.store_forward } returns ModuleConfig.StoreForwardConfig(enabled = true)
        moduleConfigFlow.value = moduleConfig

        manager.start(backgroundScope)
        manager.onNodeDbReady()
        advanceUntilIdle()

    }

*/
}
