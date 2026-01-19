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
package com.geeksville.mesh.service

import com.geeksville.mesh.repository.radio.RadioInterfaceService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.meshtastic.core.analytics.platform.PlatformAnalytics
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.data.repository.RadioConfigRepository
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.prefs.ui.UiPrefs
import org.meshtastic.core.service.ConnectionState
import org.meshtastic.core.service.MeshServiceNotifications
import org.meshtastic.proto.ConfigProtos
import org.meshtastic.proto.MeshProtos.ToRadio
import org.meshtastic.proto.localConfig

class MeshConnectionManagerTest {

    private val radioInterfaceService: RadioInterfaceService = mockk(relaxed = true)
    private val connectionStateHolder = ConnectionStateHandler()
    private val serviceBroadcasts: MeshServiceBroadcasts = mockk(relaxed = true)
    private val serviceNotifications: MeshServiceNotifications = mockk(relaxed = true)
    private val uiPrefs: UiPrefs = mockk(relaxed = true)
    private val packetHandler: PacketHandler = mockk(relaxed = true)
    private val nodeRepository: NodeRepository = mockk(relaxed = true)
    private val locationManager: MeshLocationManager = mockk(relaxed = true)
    private val mqttManager: MeshMqttManager = mockk(relaxed = true)
    private val historyManager: MeshHistoryManager = mockk(relaxed = true)
    private val radioConfigRepository: RadioConfigRepository = mockk(relaxed = true)
    private val commandSender: MeshCommandSender = mockk(relaxed = true)
    private val nodeManager: MeshNodeManager = mockk(relaxed = true)
    private val analytics: PlatformAnalytics = mockk(relaxed = true)
    private val radioConnectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    private val localConfigFlow = MutableStateFlow(localConfig {})

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher + SupervisorJob())

    private lateinit var manager: MeshConnectionManager

    @Before
    fun setUp() {
        every { radioInterfaceService.connectionState } returns radioConnectionState
        every { radioConfigRepository.localConfigFlow } returns localConfigFlow
        every { nodeRepository.myNodeInfo } returns MutableStateFlow<MyNodeEntity?>(null)

        manager =
            MeshConnectionManager(
                radioInterfaceService,
                connectionStateHolder,
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
            )
        manager.start(testScope)
    }

    @Test
    fun `Connected state triggers broadcast and config start`() = testScope.runTest {
        radioConnectionState.value = ConnectionState.Connected
        advanceUntilIdle()

        assert(connectionStateHolder.connectionState.value == ConnectionState.Connecting)
        verify { serviceBroadcasts.broadcastConnection() }
        verify { packetHandler.sendToRadio(any<ToRadio.Builder>()) }
    }

    @Test
    fun `Disconnected state stops services`() = testScope.runTest {
        radioConnectionState.value = ConnectionState.Disconnected
        advanceUntilIdle()

        assert(connectionStateHolder.connectionState.value == ConnectionState.Disconnected)
        verify { packetHandler.stopPacketQueue() }
        verify { locationManager.stop() }
        verify { mqttManager.stop() }
    }

    @Test
    fun `DeviceSleep behavior when power saving is off maps to Disconnected`() = testScope.runTest {
        // Power saving disabled + Role CLIENT
        localConfigFlow.value = localConfig {
            power = ConfigProtos.Config.PowerConfig.newBuilder().setIsPowerSaving(false).build()
            device =
                ConfigProtos.Config.DeviceConfig.newBuilder()
                    .setRole(ConfigProtos.Config.DeviceConfig.Role.CLIENT)
                    .build()
        }
        advanceUntilIdle()

        radioConnectionState.value = ConnectionState.DeviceSleep
        advanceUntilIdle()

        assert(connectionStateHolder.connectionState.value == ConnectionState.Disconnected)
    }

    @Test
    fun `DeviceSleep behavior when power saving is on stays in DeviceSleep`() = testScope.runTest {
        // Power saving enabled
        localConfigFlow.value = localConfig {
            power = ConfigProtos.Config.PowerConfig.newBuilder().setIsPowerSaving(true).build()
        }
        advanceUntilIdle()

        radioConnectionState.value = ConnectionState.DeviceSleep
        advanceUntilIdle()

        assert(connectionStateHolder.connectionState.value == ConnectionState.DeviceSleep)
    }
}
