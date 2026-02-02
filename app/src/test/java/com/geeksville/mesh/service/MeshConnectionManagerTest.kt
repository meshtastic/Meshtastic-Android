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
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.meshtastic.core.analytics.platform.PlatformAnalytics
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.data.repository.RadioConfigRepository
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.prefs.ui.UiPrefs
import org.meshtastic.core.service.ConnectionState
import org.meshtastic.core.service.MeshServiceNotifications
import org.meshtastic.proto.Config
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.ToRadio

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
    private val localConfigFlow = MutableStateFlow(LocalConfig())

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var manager: MeshConnectionManager

    @Before
    fun setUp() {
        mockkStatic("org.jetbrains.compose.resources.StringResourcesKt")
        coEvery { org.jetbrains.compose.resources.getString(any()) } returns "Mocked String"
        coEvery { org.jetbrains.compose.resources.getString(any(), *anyVararg()) } returns "Mocked String"

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
    }

    @After
    fun tearDown() {
        unmockkStatic("org.jetbrains.compose.resources.StringResourcesKt")
    }

    @Test
    fun `Connected state triggers broadcast and config start`() = runTest(testDispatcher) {
        manager.start(backgroundScope)
        radioConnectionState.value = ConnectionState.Connected
        advanceUntilIdle()

        assertEquals(
            "State should be Connecting after radio Connected",
            ConnectionState.Connecting,
            connectionStateHolder.connectionState.value,
        )
        verify { serviceBroadcasts.broadcastConnection() }
        verify { packetHandler.sendToRadio(any<ToRadio>()) }
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
            connectionStateHolder.connectionState.value,
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
            connectionStateHolder.connectionState.value,
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
            connectionStateHolder.connectionState.value,
        )
    }
}
