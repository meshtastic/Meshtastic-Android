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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.repository.MeshConnectionManager
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.proto.Channel
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceUIConfig
import org.meshtastic.proto.LoRaRegionPresetMap
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.ModuleConfig
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MeshConfigHandlerImplTest {

    private val radioConfigRepository = mock<RadioConfigRepository>(MockMode.autofill)
    private val serviceRepository = mock<ServiceRepository>(MockMode.autofill)
    private val nodeManager = mock<NodeManager>(MockMode.autofill)
    private val connectionManager = mock<MeshConnectionManager>(MockMode.autofill)
    private val radioInterfaceService = mock<RadioInterfaceService>(MockMode.autofill)

    private val localConfigFlow = MutableStateFlow(LocalConfig())
    private val moduleConfigFlow = MutableStateFlow(LocalModuleConfig())

    private val testDispatcher = UnconfinedTestDispatcher()
    private val session = RadioSessionContext(generation = 1L, address = "tcp:test")

    private lateinit var handler: MeshConfigHandlerImpl

    @BeforeTest
    fun setUp() {
        every { radioConfigRepository.localConfigFlow } returns localConfigFlow
        every { radioConfigRepository.moduleConfigFlow } returns moduleConfigFlow
        every { radioInterfaceService.runIfSessionActive(session, any()) } calls
            {
                @Suppress("UNCHECKED_CAST")
                val block = it.args[1] as () -> Unit
                block()
                true
            }
        everySuspend { radioInterfaceService.runWhileSessionActive(session, any()) } calls
            {
                @Suppress("UNCHECKED_CAST")
                val block = it.args[1] as (suspend () -> Unit)
                block()
                true
            }
    }

    private fun createHandler(scope: CoroutineScope): MeshConfigHandlerImpl = MeshConfigHandlerImpl(
        radioConfigRepository = radioConfigRepository,
        serviceStateWriter = serviceRepository,
        nodeManager = nodeManager,
        connectionManager = lazy { this.connectionManager },
        radioInterfaceService = radioInterfaceService,
        scope = scope,
    )

    // ---------- start and flow wiring ----------

    @Test
    fun `start wires localConfig flow from repository`() = runTest(testDispatcher) {
        handler = createHandler(backgroundScope)
        val config = LocalConfig(device = Config.DeviceConfig(role = Config.DeviceConfig.Role.ROUTER))
        localConfigFlow.value = config
        advanceUntilIdle()

        assertEquals(config, handler.localConfig.value)
    }

    @Test
    fun `start wires moduleConfig flow from repository`() = runTest(testDispatcher) {
        handler = createHandler(backgroundScope)
        val config = LocalModuleConfig(mqtt = ModuleConfig.MQTTConfig(enabled = true))
        moduleConfigFlow.value = config
        advanceUntilIdle()

        assertEquals(config, handler.moduleConfig.value)
    }

    // ---------- handleDeviceConfig ----------

    @Test
    fun `handleDeviceConfig persists config and updates progress`() = runTest(testDispatcher) {
        handler = createHandler(backgroundScope)
        val config = Config(device = Config.DeviceConfig(role = Config.DeviceConfig.Role.CLIENT))
        handler.handleDeviceConfig(config, session)
        advanceUntilIdle()

        verifySuspend { radioConfigRepository.setLocalConfig(config) }
        verify { serviceRepository.setConnectionProgress("Device config received") }
    }

    @Test
    fun `stale session cannot persist config or advance handshake`() = runTest(testDispatcher) {
        handler = createHandler(backgroundScope)
        val config = Config(device = Config.DeviceConfig(role = Config.DeviceConfig.Role.CLIENT))
        every { radioInterfaceService.runIfSessionActive(session, any()) } returns false

        assertFalse(handler.handleDeviceConfig(config, session))
        advanceUntilIdle()

        verifySuspend(mode = VerifyMode.exactly(0)) { radioConfigRepository.setLocalConfig(any()) }
        verify(mode = VerifyMode.exactly(0)) { serviceRepository.setConnectionProgress(any()) }
        verify(mode = VerifyMode.exactly(0)) { connectionManager.onHandshakeProgress() }
    }

    @Test
    fun `active session persists device config and advances handshake`() = runTest(testDispatcher) {
        handler = createHandler(backgroundScope)
        val config = Config(device = Config.DeviceConfig(role = Config.DeviceConfig.Role.CLIENT))
        every { radioInterfaceService.runIfSessionActive(session, any()) } calls
            {
                @Suppress("UNCHECKED_CAST")
                val block = it.args[1] as () -> Unit
                block()
                true
            }
        everySuspend { radioInterfaceService.runWhileSessionActive(session, any()) } calls
            {
                @Suppress("UNCHECKED_CAST")
                val block = it.args[1] as (suspend () -> Unit)
                block()
                true
            }

        assertTrue(handler.handleDeviceConfig(config, session))
        advanceUntilIdle()

        verifySuspend { radioConfigRepository.setLocalConfig(config) }
        verify { serviceRepository.setConnectionProgress("Device config received") }
        verify { connectionManager.onHandshakeProgress() }
    }

    @Test
    fun `revoked session cannot complete queued config persistence`() = runTest(testDispatcher) {
        handler = createHandler(backgroundScope)
        val config = Config(device = Config.DeviceConfig(role = Config.DeviceConfig.Role.CLIENT))
        every { radioInterfaceService.runIfSessionActive(session, any()) } calls
            {
                @Suppress("UNCHECKED_CAST")
                val block = it.args[1] as () -> Unit
                block()
                true
            }
        everySuspend { radioInterfaceService.runWhileSessionActive(session, any()) } returns false

        handler.handleDeviceConfig(config, session)
        advanceUntilIdle()

        verifySuspend(mode = VerifyMode.exactly(0)) { radioConfigRepository.setLocalConfig(any()) }
        verify { serviceRepository.setConnectionProgress("Device config received") }
        verify { connectionManager.onHandshakeProgress() }
    }

    @Test
    fun `handleDeviceConfig handles all config variants`() = runTest(testDispatcher) {
        handler = createHandler(backgroundScope)
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
            handler.handleDeviceConfig(config, session)
            advanceUntilIdle()
        }

        // All should have been persisted (7 configs)
        verifySuspend { radioConfigRepository.setLocalConfig(any()) }
    }

    // ---------- handleModuleConfig ----------

    @Test
    fun `handleModuleConfig persists config and updates progress`() = runTest(testDispatcher) {
        handler = createHandler(backgroundScope)
        val config = ModuleConfig(mqtt = ModuleConfig.MQTTConfig(enabled = true))
        handler.handleModuleConfig(config, session)
        advanceUntilIdle()

        verifySuspend { radioConfigRepository.setLocalModuleConfig(config) }
        verify { serviceRepository.setConnectionProgress("Module config received") }
    }

    @Test
    fun `handleModuleConfig with statusmessage updates node status`() = runTest(testDispatcher) {
        handler = createHandler(backgroundScope)
        val myNum = 123
        every { nodeManager.myNodeNum } returns MutableStateFlow<Int?>(myNum)

        val config = ModuleConfig(statusmessage = ModuleConfig.StatusMessageConfig(node_status = "Active"))
        handler.handleModuleConfig(config, session)
        advanceUntilIdle()

        verifySuspend { nodeManager.updateNodeStatusAndPersist(myNum, "Active") }
    }

    @Test
    fun `handleModuleConfig with statusmessage skipped when myNodeNum is null`() = runTest(testDispatcher) {
        handler = createHandler(backgroundScope)
        every { nodeManager.myNodeNum } returns MutableStateFlow<Int?>(null)

        val config = ModuleConfig(statusmessage = ModuleConfig.StatusMessageConfig(node_status = "Active"))
        handler.handleModuleConfig(config, session)
        advanceUntilIdle()
        // No crash — updateNodeStatus should not be called
    }

    @Test
    fun `module config remains persisted when node status persistence fails`() = runTest(testDispatcher) {
        handler = createHandler(backgroundScope)
        val myNum = 123
        val config = ModuleConfig(statusmessage = ModuleConfig.StatusMessageConfig(node_status = "Active"))
        every { nodeManager.myNodeNum } returns MutableStateFlow<Int?>(myNum)
        everySuspend { nodeManager.updateNodeStatusAndPersist(myNum, "Active") } calls
            {
                throw IllegalStateException("forced node-status failure")
            }

        handler.handleModuleConfig(config, session)
        advanceUntilIdle()

        verifySuspend(mode = VerifyMode.exactly(1)) { radioConfigRepository.setLocalModuleConfig(config) }
        verifySuspend(mode = VerifyMode.exactly(1)) { nodeManager.updateNodeStatusAndPersist(myNum, "Active") }
    }

    @Test
    fun `module config cancellation cancels node status persistence job`() = runTest(testDispatcher) {
        val parentJob = Job()
        val handlerScope = CoroutineScope(parentJob + testDispatcher)
        handler = createHandler(handlerScope)
        val existingJobs = parentJob.children.toSet()
        val statusStarted = CompletableDeferred<Unit>()
        val releaseStatus = CompletableDeferred<Unit>()
        val myNum = 123
        val config = ModuleConfig(statusmessage = ModuleConfig.StatusMessageConfig(node_status = "Active"))
        every { nodeManager.myNodeNum } returns MutableStateFlow<Int?>(myNum)
        everySuspend { nodeManager.updateNodeStatusAndPersist(myNum, "Active") } calls
            {
                statusStarted.complete(Unit)
                releaseStatus.await()
                throw CancellationException("forced node-status cancellation")
            }

        handler.handleModuleConfig(config, session)
        statusStarted.await()
        val persistenceJob = parentJob.children.first { it !in existingJobs }
        releaseStatus.complete(Unit)
        advanceUntilIdle()

        assertTrue(persistenceJob.isCancelled, "node-status cancellation must remain cancellation")
        verifySuspend(mode = VerifyMode.exactly(1)) { radioConfigRepository.setLocalModuleConfig(config) }
        verifySuspend(mode = VerifyMode.exactly(1)) { nodeManager.updateNodeStatusAndPersist(myNum, "Active") }
        parentJob.cancel()
    }

    // ---------- handleChannel ----------

    @Test
    fun `handleChannel persists channel settings`() = runTest(testDispatcher) {
        handler = createHandler(backgroundScope)
        val channel = Channel(index = 0)
        handler.handleChannel(channel, session)
        advanceUntilIdle()

        verifySuspend { radioConfigRepository.updateChannelSettings(channel) }
    }

    @Test
    fun `handleChannel shows progress with max channels when myNodeInfo available`() = runTest(testDispatcher) {
        handler = createHandler(backgroundScope)
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
        handler.handleChannel(channel, session)
        advanceUntilIdle()

        verify { serviceRepository.setConnectionProgress("Channels (3 / 8)") }
    }

    @Test
    fun `handleChannel shows progress without max channels when myNodeInfo unavailable`() = runTest(testDispatcher) {
        handler = createHandler(backgroundScope)
        every { nodeManager.getMyNodeInfo() } returns null

        val channel = Channel(index = 0)
        handler.handleChannel(channel, session)
        advanceUntilIdle()

        verify { serviceRepository.setConnectionProgress("Channels (1)") }
    }

    // ---------- handleDeviceUIConfig ----------

    @Test
    fun `handleDeviceUIConfig persists config`() = runTest(testDispatcher) {
        handler = createHandler(backgroundScope)
        val config = DeviceUIConfig()
        handler.handleDeviceUIConfig(config, session)
        advanceUntilIdle()

        verifySuspend { radioConfigRepository.setDeviceUIConfig(config) }
    }

    // ---------- onHandshakeProgress ----------

    @Test
    fun `handleDeviceConfig calls onHandshakeProgress`() = runTest(testDispatcher) {
        handler = createHandler(backgroundScope)
        val config = Config(device = Config.DeviceConfig(role = Config.DeviceConfig.Role.CLIENT))
        handler.handleDeviceConfig(config, session)
        advanceUntilIdle()

        verify { connectionManager.onHandshakeProgress() }
    }

    @Test
    fun `handleModuleConfig calls onHandshakeProgress`() = runTest(testDispatcher) {
        handler = createHandler(backgroundScope)
        val config = ModuleConfig(mqtt = ModuleConfig.MQTTConfig(enabled = true))
        handler.handleModuleConfig(config, session)
        advanceUntilIdle()

        verify { connectionManager.onHandshakeProgress() }
    }

    @Test
    fun `handleChannel calls onHandshakeProgress`() = runTest(testDispatcher) {
        handler = createHandler(backgroundScope)
        every { nodeManager.getMyNodeInfo() } returns null

        val channel = Channel(index = 0)
        handler.handleChannel(channel, session)
        advanceUntilIdle()

        verify { connectionManager.onHandshakeProgress() }
    }

    @Test
    fun `handleDeviceUIConfig calls onHandshakeProgress`() = runTest(testDispatcher) {
        handler = createHandler(backgroundScope)
        handler.handleDeviceUIConfig(DeviceUIConfig(), session)
        advanceUntilIdle()

        verify { connectionManager.onHandshakeProgress() }
    }

    // ---------- handleRegionPresets ----------

    @Test
    fun `handleRegionPresets persists map`() = runTest(testDispatcher) {
        handler = createHandler(backgroundScope)
        val map = LoRaRegionPresetMap()
        handler.handleRegionPresets(map, session)
        advanceUntilIdle()

        verifySuspend { radioConfigRepository.setLoraRegionPresetMap(map) }
        verify { connectionManager.onHandshakeProgress() }
    }
}
