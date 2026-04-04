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
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verifySuspend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.proto.Channel
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceUIConfig
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.ModuleConfig
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MeshConfigHandlerImplTest {

    private val radioConfigRepository = mock<RadioConfigRepository>(MockMode.autofill)
    private val serviceRepository = mock<ServiceRepository>(MockMode.autofill)
    private val nodeManager = mock<NodeManager>(MockMode.autofill)

    private val localConfigFlow = MutableStateFlow(LocalConfig())
    private val moduleConfigFlow = MutableStateFlow(LocalModuleConfig())

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var handler: MeshConfigHandlerImpl

    @BeforeTest
    fun setUp() {
        every { radioConfigRepository.localConfigFlow } returns localConfigFlow
        every { radioConfigRepository.moduleConfigFlow } returns moduleConfigFlow

        handler =
            MeshConfigHandlerImpl(
                radioConfigRepository = radioConfigRepository,
                serviceRepository = serviceRepository,
                nodeManager = nodeManager,
            )
    }

    // ---------- start and flow wiring ----------

    @Test
    fun `start wires localConfig flow from repository`() = runTest(testDispatcher) {
        handler.start(backgroundScope)
        val config = LocalConfig(device = Config.DeviceConfig(role = Config.DeviceConfig.Role.ROUTER))
        localConfigFlow.value = config
        advanceUntilIdle()

        assertEquals(config, handler.localConfig.value)
    }

    @Test
    fun `start wires moduleConfig flow from repository`() = runTest(testDispatcher) {
        handler.start(backgroundScope)
        val config = LocalModuleConfig(mqtt = ModuleConfig.MQTTConfig(enabled = true))
        moduleConfigFlow.value = config
        advanceUntilIdle()

        assertEquals(config, handler.moduleConfig.value)
    }

    // ---------- handleDeviceConfig ----------

    @Test
    fun `handleDeviceConfig persists config and updates progress`() = runTest(testDispatcher) {
        handler.start(backgroundScope)
        val config = Config(device = Config.DeviceConfig(role = Config.DeviceConfig.Role.CLIENT))
        handler.handleDeviceConfig(config)
        advanceUntilIdle()

        verifySuspend { radioConfigRepository.setLocalConfig(config) }
        verify { serviceRepository.setConnectionProgress("Device config received") }
    }

    @Test
    fun `handleDeviceConfig handles all config variants`() = runTest(testDispatcher) {
        handler.start(backgroundScope)
        val configs =
            listOf(
                Config(position = Config.PositionConfig()),
                Config(power = Config.PowerConfig()),
                Config(network = Config.NetworkConfig()),
                Config(display = Config.DisplayConfig()),
                Config(lora = Config.LoRaConfig()),
                Config(bluetooth = Config.BluetoothConfig()),
                Config(security = Config.SecurityConfig()),
            )

        for (config in configs) {
            handler.handleDeviceConfig(config)
            advanceUntilIdle()
        }

        // All should have been persisted (7 configs)
        verifySuspend { radioConfigRepository.setLocalConfig(any()) }
    }

    // ---------- handleModuleConfig ----------

    @Test
    fun `handleModuleConfig persists config and updates progress`() = runTest(testDispatcher) {
        handler.start(backgroundScope)
        val config = ModuleConfig(mqtt = ModuleConfig.MQTTConfig(enabled = true))
        handler.handleModuleConfig(config)
        advanceUntilIdle()

        verifySuspend { radioConfigRepository.setLocalModuleConfig(config) }
        verify { serviceRepository.setConnectionProgress("Module config received") }
    }

    @Test
    fun `handleModuleConfig with statusmessage updates node status`() = runTest(testDispatcher) {
        handler.start(backgroundScope)
        val myNum = 123
        every { nodeManager.myNodeNum } returns MutableStateFlow<Int?>(myNum)

        val config = ModuleConfig(statusmessage = ModuleConfig.StatusMessageConfig(node_status = "Active"))
        handler.handleModuleConfig(config)
        advanceUntilIdle()

        verify { nodeManager.updateNodeStatus(myNum, "Active") }
    }

    @Test
    fun `handleModuleConfig with statusmessage skipped when myNodeNum is null`() = runTest(testDispatcher) {
        handler.start(backgroundScope)
        every { nodeManager.myNodeNum } returns MutableStateFlow<Int?>(null)

        val config = ModuleConfig(statusmessage = ModuleConfig.StatusMessageConfig(node_status = "Active"))
        handler.handleModuleConfig(config)
        advanceUntilIdle()
        // No crash — updateNodeStatus should not be called
    }

    // ---------- handleChannel ----------

    @Test
    fun `handleChannel persists channel settings`() = runTest(testDispatcher) {
        handler.start(backgroundScope)
        val channel = Channel(index = 0)
        handler.handleChannel(channel)
        advanceUntilIdle()

        verifySuspend { radioConfigRepository.updateChannelSettings(channel) }
    }

    @Test
    fun `handleChannel shows progress with max channels when myNodeInfo available`() = runTest(testDispatcher) {
        handler.start(backgroundScope)
        every { nodeManager.getMyNodeInfo() } returns
            MyNodeInfo(
                myNodeNum = 123,
                hasGPS = false,
                model = null,
                firmwareVersion = null,
                couldUpdate = false,
                shouldUpdate = false,
                currentPacketId = 0L,
                messageTimeoutMsec = 0,
                minAppVersion = 0,
                maxChannels = 8,
                hasWifi = false,
                channelUtilization = 0f,
                airUtilTx = 0f,
                deviceId = null,
            )

        val channel = Channel(index = 2)
        handler.handleChannel(channel)
        advanceUntilIdle()

        verify { serviceRepository.setConnectionProgress("Channels (3 / 8)") }
    }

    @Test
    fun `handleChannel shows progress without max channels when myNodeInfo unavailable`() = runTest(testDispatcher) {
        handler.start(backgroundScope)
        every { nodeManager.getMyNodeInfo() } returns null

        val channel = Channel(index = 0)
        handler.handleChannel(channel)
        advanceUntilIdle()

        verify { serviceRepository.setConnectionProgress("Channels (1)") }
    }

    // ---------- handleDeviceUIConfig ----------

    @Test
    fun `handleDeviceUIConfig persists config`() = runTest(testDispatcher) {
        handler.start(backgroundScope)
        val config = DeviceUIConfig()
        handler.handleDeviceUIConfig(config)
        advanceUntilIdle()

        verifySuspend { radioConfigRepository.setDeviceUIConfig(config) }
    }
}
