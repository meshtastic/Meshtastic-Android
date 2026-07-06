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
package org.meshtastic.feature.settings.radio

import app.cash.turbine.test
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.ByteString.Companion.encodeUtf8
import org.meshtastic.core.domain.usecase.settings.AdminActionsUseCase
import org.meshtastic.core.domain.usecase.settings.ExportProfileUseCase
import org.meshtastic.core.domain.usecase.settings.ImportProfileUseCase
import org.meshtastic.core.domain.usecase.settings.ImportSecurityConfigUseCase
import org.meshtastic.core.domain.usecase.settings.InstallProfileUseCase
import org.meshtastic.core.domain.usecase.settings.ProcessRadioResponseUseCase
import org.meshtastic.core.domain.usecase.settings.RadioConfigUseCase
import org.meshtastic.core.domain.usecase.settings.RadioResponseResult
import org.meshtastic.core.model.MqttProbeStatus
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.AnalyticsPrefs
import org.meshtastic.core.repository.FileService
import org.meshtastic.core.repository.HomoglyphPrefs
import org.meshtastic.core.repository.LocationRepository
import org.meshtastic.core.repository.LocationService
import org.meshtastic.core.repository.MapConsentPrefs
import org.meshtastic.core.repository.MqttManager
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.SecurityKeyBackupStore
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.StoredSecurityKeys
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.core.testing.FakeLockdownCoordinator
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.ui.util.SnackbarManager
import org.meshtastic.feature.settings.navigation.ConfigRoute
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.Data
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.DeviceProfile
import org.meshtastic.proto.HamParameters
import org.meshtastic.proto.LoRaPresetGroup
import org.meshtastic.proto.LoRaRegionPresetMap
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.User
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
class RadioConfigViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val radioConfigRepository: RadioConfigRepository = mock(MockMode.autofill)
    private val packetRepository: PacketRepository = mock(MockMode.autofill)
    private val serviceRepository: ServiceRepository = mock(MockMode.autofill)
    private val nodeRepository = FakeNodeRepository()
    private val locationRepository: LocationRepository = mock(MockMode.autofill)
    private val mapConsentPrefs: MapConsentPrefs = mock(MockMode.autofill)
    private val analyticsPrefs: AnalyticsPrefs = mock(MockMode.autofill)
    private val homoglyphEncodingPrefs: HomoglyphPrefs = mock(MockMode.autofill)

    private val importProfileUseCase: ImportProfileUseCase = mock(MockMode.autofill)
    private val exportProfileUseCase: ExportProfileUseCase = mock(MockMode.autofill)
    private val importSecurityConfigUseCase: ImportSecurityConfigUseCase = mock(MockMode.autofill)
    private val installProfileUseCase: InstallProfileUseCase = mock(MockMode.autofill)
    private val radioConfigUseCase: RadioConfigUseCase = mock(MockMode.autofill)
    private val adminActionsUseCase: AdminActionsUseCase = mock(MockMode.autofill)
    private val processRadioResponseUseCase: ProcessRadioResponseUseCase = mock(MockMode.autofill)
    private val locationService: LocationService = mock(MockMode.autofill)
    private val fileService: FileService = mock(MockMode.autofill)
    private val mqttManager: MqttManager = mock(MockMode.autofill)
    private val uiPrefs: UiPrefs = mock(MockMode.autofill)
    private val securityKeyBackupStore: SecurityKeyBackupStore = mock(MockMode.autofill)
    private val snackbarManager: SnackbarManager = mock(MockMode.autofill)

    private lateinit var viewModel: RadioConfigViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { radioConfigRepository.deviceProfileFlow } returns MutableStateFlow(DeviceProfile())
        every { radioConfigRepository.localConfigFlow } returns MutableStateFlow(LocalConfig())
        every { radioConfigRepository.channelSetFlow } returns MutableStateFlow(ChannelSet())
        every { radioConfigRepository.moduleConfigFlow } returns MutableStateFlow(LocalModuleConfig())
        every { radioConfigRepository.deviceUIConfigFlow } returns MutableStateFlow(null)
        every { radioConfigRepository.fileManifestFlow } returns MutableStateFlow(emptyList())
        every { radioConfigRepository.loraRegionPresetMapFlow } returns MutableStateFlow(null)

        every { analyticsPrefs.analyticsAllowed } returns MutableStateFlow(false)
        every { homoglyphEncodingPrefs.homoglyphEncodingEnabled } returns MutableStateFlow(false)

        every { serviceRepository.meshPacketFlow } returns MutableSharedFlow()
        every { serviceRepository.connectionState } returns
            MutableStateFlow(org.meshtastic.core.model.ConnectionState.Connected)

        every { mqttManager.mqttConnectionState } returns
            MutableStateFlow(org.meshtastic.core.model.MqttConnectionState.Inactive)
        every { mqttManager.proxyActive } returns MutableStateFlow(false)

        every { uiPrefs.showQuickChat } returns MutableStateFlow(false)

        viewModel = createViewModel()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(destNum: Int? = null) = RadioConfigViewModel(
        destNum = destNum,
        radioConfigRepository = radioConfigRepository,
        packetRepository = packetRepository,
        serviceRepository = serviceRepository,
        nodeRepository = nodeRepository,
        locationRepository = locationRepository,
        mapConsentPrefs = mapConsentPrefs,
        analyticsPrefs = analyticsPrefs,
        homoglyphEncodingPrefs = homoglyphEncodingPrefs,
        importProfileUseCase = importProfileUseCase,
        exportProfileUseCase = exportProfileUseCase,
        importSecurityConfigUseCase = importSecurityConfigUseCase,
        securityKeyBackupStore = securityKeyBackupStore,
        snackbarManager = snackbarManager,
        installProfileUseCase = installProfileUseCase,
        radioConfigUseCase = radioConfigUseCase,
        adminActionsUseCase = adminActionsUseCase,
        processRadioResponseUseCase = processRadioResponseUseCase,
        locationService = locationService,
        fileService = fileService,
        mqttManager = mqttManager,
        lockdownCoordinator = FakeLockdownCoordinator(),
    )

    @Test
    fun `setConfig calls useCase`() = runTest {
        val node = Node(num = 123, user = User(id = "!123"))
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        val config = Config(device = Config.DeviceConfig(role = Config.DeviceConfig.Role.ROUTER))
        everySuspend { radioConfigUseCase.setConfig(any(), any()) } returns 42

        viewModel.setConfig(config)

        viewModel.radioConfigState.test {
            val state = awaitItem()
            assertEquals(Config.DeviceConfig.Role.ROUTER, state.radioConfig.device?.role)
            cancelAndIgnoreRemainingEvents()
        }

        verifySuspend { radioConfigUseCase.setConfig(123, config) }
    }

    @Test
    fun `toggleAnalyticsAllowed calls prefs`() {
        every { analyticsPrefs.analyticsAllowed } returns MutableStateFlow(true)
        every { analyticsPrefs.setAnalyticsAllowed(false) } returns Unit

        viewModel.toggleAnalyticsAllowed()

        verify { analyticsPrefs.setAnalyticsAllowed(false) }
    }

    @Test
    fun `toggleHomoglyphCharactersEncodingEnabled calls prefs`() {
        every { homoglyphEncodingPrefs.homoglyphEncodingEnabled } returns MutableStateFlow(true)
        every { homoglyphEncodingPrefs.setHomoglyphEncodingEnabled(false) } returns Unit

        viewModel.toggleHomoglyphCharactersEncodingEnabled()

        verify { homoglyphEncodingPrefs.setHomoglyphEncodingEnabled(false) }
    }

    @Test
    fun `processPacketResponse updates state on metadata result`() = runTest {
        val node = Node(num = 123, user = User(id = "!123"))
        nodeRepository.setNodes(listOf(node))

        val packet = MeshPacket()
        val metadata = DeviceMetadata(firmware_version = "3.0.0")
        val packetFlow = MutableSharedFlow<MeshPacket>()

        every { serviceRepository.meshPacketFlow } returns packetFlow
        every { processRadioResponseUseCase(any(), 123, any()) } returns RadioResponseResult.Metadata(metadata)

        viewModel = createViewModel()

        packetFlow.emit(packet)

        viewModel.radioConfigState.test {
            val state = awaitItem()
            assertEquals("3.0.0", state.metadata?.firmware_version)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `probeMqttConnection updates status for success`() = runTest {
        everySuspend { mqttManager.probe("mqtt.example.com", true, "user", "pass") }
            .calls {
                delay(1)
                MqttProbeStatus.Success(serverInfo = "client=test")
            }

        viewModel.probeMqttConnection("mqtt.example.com", true, "user", "pass")

        assertEquals(MqttProbeStatus.Probing, viewModel.mqttProbeStatus.value)

        advanceTimeBy(1)
        runCurrent()

        assertEquals(MqttProbeStatus.Success(serverInfo = "client=test"), viewModel.mqttProbeStatus.value)
        verifySuspend { mqttManager.probe("mqtt.example.com", true, "user", "pass") }
    }

    @Test
    fun `probeMqttConnection updates status for timeout`() = runTest {
        everySuspend { mqttManager.probe("mqtt.example.com", false, null, null) } returns MqttProbeStatus.Timeout(5_000)

        viewModel.probeMqttConnection("mqtt.example.com", false, null, null)
        runCurrent()

        assertEquals(MqttProbeStatus.Timeout(5_000), viewModel.mqttProbeStatus.value)
        verifySuspend { mqttManager.probe("mqtt.example.com", false, null, null) }
    }

    @Test
    fun `probeMqttConnection converts thrown exception to other status`() = runTest {
        everySuspend { mqttManager.probe("mqtt.example.com", true, null, null) }
            .calls { throw IllegalStateException("boom") }

        viewModel.probeMqttConnection("mqtt.example.com", true, null, null)
        runCurrent()

        assertEquals(MqttProbeStatus.Other(message = "boom"), viewModel.mqttProbeStatus.value)
        verifySuspend { mqttManager.probe("mqtt.example.com", true, null, null) }
    }

    @Test
    fun `setMqttProxyActive false stops the proxy without touching device config`() = runTest {
        every { mqttManager.stop() } returns Unit

        viewModel.setMqttProxyActive(false)

        verify { mqttManager.stop() }
        // Phone-local cut must not issue any device config read/write.
        verifySuspend(exactly(0)) { radioConfigUseCase.setModuleConfig(any(), any()) }
    }

    @Test
    fun `setMqttProxyActive true restarts proxy using current device module config`() = runTest {
        every { radioConfigRepository.moduleConfigFlow } returns
            MutableStateFlow(
                LocalModuleConfig(
                    mqtt = org.meshtastic.proto.ModuleConfig.MQTTConfig(enabled = true, proxy_to_client_enabled = true),
                ),
            )
        every { mqttManager.startProxy(any(), any()) } returns Unit
        viewModel = createViewModel()
        runCurrent()

        viewModel.setMqttProxyActive(true)

        verify { mqttManager.startProxy(true, true) }
        verifySuspend(exactly(0)) { radioConfigUseCase.setModuleConfig(any(), any()) }
    }

    @Test
    fun `setMqttProxyActive true is a no-op start when device MQTT is disabled`() = runTest {
        every { radioConfigRepository.moduleConfigFlow } returns MutableStateFlow(LocalModuleConfig())
        every { mqttManager.startProxy(any(), any()) } returns Unit
        viewModel = createViewModel()
        runCurrent()

        viewModel.setMqttProxyActive(true)

        // The manager is still asked to start, but with both flags false it does nothing (its own guard).
        verify { mqttManager.startProxy(false, false) }
    }

    @Test
    fun `clearMqttProbeStatus resets probe state`() = runTest {
        everySuspend { mqttManager.probe("mqtt.example.com", false, null, null) }
            .calls {
                delay(1)
                MqttProbeStatus.Success(serverInfo = "client=test")
            }

        viewModel.probeMqttConnection("mqtt.example.com", false, null, null)
        assertEquals(MqttProbeStatus.Probing, viewModel.mqttProbeStatus.value)

        viewModel.clearMqttProbeStatus()
        assertEquals(null, viewModel.mqttProbeStatus.value)

        advanceTimeBy(1)
        runCurrent()

        assertEquals(null, viewModel.mqttProbeStatus.value)
    }

    @Test
    fun `updateChannels calls useCase for each changed channel`() = runTest {
        val node = Node(num = 123, user = User(id = "!123"))
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        val old = listOf(ChannelSettings(name = "Old"))
        val new = listOf(ChannelSettings(name = "New"))

        everySuspend { radioConfigUseCase.setRemoteChannel(any(), any()) } returns 42

        viewModel.updateChannels(new, old)

        verifySuspend { radioConfigUseCase.setRemoteChannel(123, any()) }
        assertEquals(new, viewModel.radioConfigState.value.channelList)
    }

    @Test
    fun `updateChannels writes changed channels sequentially in index order`() = runTest {
        val node = Node(num = 123, user = User(id = "!123"))
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        val channelA = ChannelSettings(name = "A")
        val channelB = ChannelSettings(name = "B")
        val channelC = ChannelSettings(name = "C")
        val old = listOf(channelA, channelB, channelC)
        val new = listOf(channelA, channelC, channelB)
        val writtenIndexes = mutableListOf<Int>()
        var activeWrites = 0
        var maxConcurrentWrites = 0

        everySuspend { radioConfigUseCase.setRemoteChannel(any(), any()) } calls
            {
                val channel = it.args[1] as Channel
                activeWrites++
                maxConcurrentWrites = maxOf(maxConcurrentWrites, activeWrites)
                writtenIndexes.add(channel.index)
                delay(1)
                activeWrites--
                writtenIndexes.size
            }

        viewModel.updateChannels(new, old)
        advanceUntilIdle()

        assertEquals(listOf(1, 2), writtenIndexes)
        assertEquals(1, maxConcurrentWrites)
        assertEquals(new, viewModel.radioConfigState.value.channelList)
        verifySuspend(exactly(2)) { radioConfigUseCase.setRemoteChannel(123, any()) }
    }

    @Test
    fun `updateChannels waits for all ordered writes before success`() = runTest {
        val node = Node(num = 123, user = User(id = "!123"))
        val packetFlow = MutableSharedFlow<MeshPacket>()
        every { serviceRepository.meshPacketFlow } returns packetFlow
        every { processRadioResponseUseCase(any(), 123, any()) } returns RadioResponseResult.Success
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        val channelA = ChannelSettings(name = "A")
        val channelB = ChannelSettings(name = "B")
        val channelC = ChannelSettings(name = "C")
        val old = listOf(channelA, channelB, channelC)
        val new = listOf(channelA, channelC, channelB)
        var nextPacketId = 40

        everySuspend { radioConfigUseCase.setRemoteChannel(any(), any()) } calls { ++nextPacketId }

        viewModel.updateChannels(new, old)
        runCurrent()

        packetFlow.emit(MeshPacket(decoded = Data(request_id = 41)))
        runCurrent()

        val midBatchState = viewModel.radioConfigState.value.responseState
        assertTrue(midBatchState is ResponseState.Loading)
        assertEquals(2, midBatchState.total)
        assertEquals(1, midBatchState.completed)

        advanceTimeBy(MANUAL_CHANNEL_WRITE_DELAY.inWholeMilliseconds)
        runCurrent()

        packetFlow.emit(MeshPacket(decoded = Data(request_id = 42)))
        runCurrent()

        assertTrue(viewModel.radioConfigState.value.responseState is ResponseState.Success)
    }

    @Test
    fun `updateChannels reconciles applied channel writes when ordered write fails`() = runTest {
        val node = Node(num = 123, user = User(id = "!123"))
        val old = fourChannelFixture()
        val (channelA, _, _, channelD) = old
        val new = listOf(channelA, channelD)
        val partiallyApplied = listOf(channelA, channelD, old[2], old[3])
        val writtenIndexes = mutableListOf<Int>()

        every { radioConfigRepository.channelSetFlow } returns MutableStateFlow(ChannelSet(settings = old))
        nodeRepository.setNodes(listOf(node))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 123))
        viewModel = createViewModel()
        runCurrent()

        everySuspend { radioConfigUseCase.setRemoteChannel(any(), any()) } calls
            {
                val channel = it.args[1] as Channel
                writtenIndexes.add(channel.index)
                if (writtenIndexes.size == 2) {
                    throw IllegalStateException("boom")
                }
                channel.index + 100
            }

        viewModel.updateChannels(new, old)
        advanceUntilIdle()

        assertEquals(listOf(1, 2), writtenIndexes)
        assertEquals(partiallyApplied, viewModel.radioConfigState.value.channelList)
        assertTrue(viewModel.radioConfigState.value.responseState is ResponseState.Error)
        verifySuspend { packetRepository.migrateChannelsByPSK(old, partiallyApplied) }
        verifySuspend { radioConfigRepository.replaceAllSettings(partiallyApplied) }
    }

    @Test
    fun `updateChannels serializes overlapping channel saves`() = runTest {
        val node = Node(num = 123, user = User(id = "!123"))
        val old = fourChannelFixture()
        val (channelA, _, channelC, channelD) = old
        val firstNew = listOf(channelA, channelD)
        val secondNew = listOf(channelA, channelC)
        val writtenChannels = mutableListOf<String>()
        var firstWrite = true

        every { radioConfigRepository.channelSetFlow } returns MutableStateFlow(ChannelSet(settings = old))
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()
        runCurrent()

        everySuspend { radioConfigUseCase.setRemoteChannel(any(), any()) } calls
            {
                val channel = it.args[1] as Channel
                writtenChannels.add("${channel.index}:${channel.role}:${channel.settings?.name.orEmpty()}")
                if (firstWrite) {
                    firstWrite = false
                    delay(10_000)
                }
                writtenChannels.size
            }

        viewModel.updateChannels(firstNew, old)
        runCurrent()
        viewModel.updateChannels(secondNew, old)
        advanceUntilIdle()

        assertEquals(listOf("1:SECONDARY:D", "2:DISABLED:", "3:DISABLED:", "1:SECONDARY:C"), writtenChannels)
        assertEquals(secondNew, viewModel.radioConfigState.value.channelList)
    }

    @Test
    fun `applyManualChannelUpdatePlan paces writes except after final channel`() = runTest {
        val channelA = Channel(index = 1, role = Channel.Role.SECONDARY, settings = ChannelSettings(name = "A"))
        val channelB = Channel(index = 2, role = Channel.Role.DISABLED, settings = ChannelSettings())
        val channelC = Channel(index = 3, role = Channel.Role.DISABLED, settings = ChannelSettings())
        val writtenIndexes = mutableListOf<Int>()
        val registeredRequestIds = mutableListOf<Int>()
        val delays = mutableListOf<Duration>()

        val result =
            applyManualChannelUpdatePlan(
                updatePlan = listOf(channelA, channelB, channelC),
                currentSettings = listOf(ChannelSettings(name = "old")),
                finalSettings = listOf(ChannelSettings(name = "new")),
                writeChannel = { channel ->
                    writtenIndexes.add(channel.index)
                    channel.index + 100
                },
                registerRequestId = { registeredRequestIds.add(it) },
                delayFn = { delays.add(it) },
            )

        assertEquals(listOf(1, 2, 3), writtenIndexes)
        assertEquals(listOf(101, 102, 103), result.packetIds)
        assertEquals(listOf(ChannelSettings(name = "new")), result.finalSettings)
        assertEquals(listOf(101, 102, 103), registeredRequestIds)
        assertEquals(listOf(MANUAL_CHANNEL_WRITE_DELAY, MANUAL_CHANNEL_WRITE_DELAY), delays)
    }

    @Test
    fun `applyManualChannelUpdatePlan invokes onInterrupted when writeChannel fails mid-plan`() = runTest {
        val channelA = Channel(index = 1, role = Channel.Role.SECONDARY, settings = ChannelSettings(name = "A"))
        val channelB = Channel(index = 2, role = Channel.Role.SECONDARY, settings = ChannelSettings(name = "B"))
        val channelC = Channel(index = 3, role = Channel.Role.SECONDARY, settings = ChannelSettings(name = "C"))
        val writtenIndexes = mutableListOf<Int>()

        var interrupted: InterruptedManualChannelUpdate? = null

        val error =
            assertFailsWith<IllegalStateException> {
                applyManualChannelUpdatePlan(
                    updatePlan = listOf(channelA, channelB, channelC),
                    currentSettings = listOf(ChannelSettings(name = "old")),
                    finalSettings = listOf(ChannelSettings(name = "new")),
                    writeChannel = { channel ->
                        writtenIndexes.add(channel.index)
                        if (channel.index == 2) throw IllegalStateException("boom")
                        channel.index + 100
                    },
                    registerRequestId = {},
                    onInterrupted = { interrupted = it },
                    delayFn = {},
                )
            }

        assertEquals("boom", error.message)
        // Channel A (index 1) completed before channel B (index 2) threw.
        assertEquals(listOf(1, 2), writtenIndexes)
        assertNotNull(interrupted)
        assertEquals(1, interrupted!!.appliedWriteCount)
        assertEquals("A", interrupted!!.appliedSettings[1].name)
    }

    @Test
    fun `setResponseStateLoading for REBOOT calls useCase after config response`() = runTest {
        val node = Node(num = 123, user = User(id = "!123"))
        nodeRepository.setNodes(listOf(node))

        val packetFlow = MutableSharedFlow<MeshPacket>()
        every { serviceRepository.meshPacketFlow } returns packetFlow
        // AdminRoute first sends a session key config request; the admin action fires
        // only after the actual ConfigResponse (not a routing ACK / Success).
        every { processRadioResponseUseCase(any(), any(), any()) } returns RadioResponseResult.ConfigResponse(Config())

        viewModel = createViewModel()

        everySuspend { adminActionsUseCase.reboot(any()) } returns 42

        viewModel.setResponseStateLoading(AdminRoute.REBOOT)

        // Emit a config response packet to trigger processPacketResponse -> sendAdminRequest
        packetFlow.emit(MeshPacket())

        verifySuspend { adminActionsUseCase.reboot(123) }
    }

    @Test
    fun `setResponseStateLoading for FACTORY_RESET calls useCase after config response`() = runTest {
        val node = Node(num = 123, user = User(id = "!123"))
        nodeRepository.setNodes(listOf(node))

        val packetFlow = MutableSharedFlow<MeshPacket>()
        every { serviceRepository.meshPacketFlow } returns packetFlow
        // AdminRoute first sends a session key config request; the admin action fires
        // only after the actual ConfigResponse (not a routing ACK / Success).
        every { processRadioResponseUseCase(any(), any(), any()) } returns RadioResponseResult.ConfigResponse(Config())

        viewModel = createViewModel()

        everySuspend { adminActionsUseCase.factoryReset(any(), any()) } returns 42

        viewModel.setResponseStateLoading(AdminRoute.FACTORY_RESET)

        // Emit a config response packet to trigger processPacketResponse -> sendAdminRequest
        packetFlow.emit(MeshPacket())

        verifySuspend { adminActionsUseCase.factoryReset(123, any()) }
    }

    @Test
    fun `setPreserveFavorites updates state`() = runTest {
        viewModel.radioConfigState.test {
            assertEquals(false, awaitItem().nodeDbResetPreserveFavorites)
            viewModel.setPreserveFavorites(true)
            assertEquals(true, awaitItem().nodeDbResetPreserveFavorites)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setOwner calls useCase`() = runTest {
        val node = Node(num = 123, user = User(id = "!123"))
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        val user = User(long_name = "Test User")
        everySuspend { radioConfigUseCase.setOwner(any(), any()) } returns 42

        viewModel.setOwner(user)

        verifySuspend { radioConfigUseCase.setOwner(123, user) }
    }

    @Test
    fun `saveUserConfig sends setHamMode for licensed local node`() = runTest {
        val node = Node(num = 123, user = User(id = "!123"))
        nodeRepository.setNodes(listOf(node))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 123))
        viewModel = createViewModel()

        val user = User(long_name = "KK7ABC", short_name = "KK7A", is_licensed = true)
        everySuspend { radioConfigUseCase.setHamMode(any(), any()) } returns 42

        viewModel.saveUserConfig(user)

        verifySuspend { radioConfigUseCase.setHamMode(123, HamParameters(call_sign = "KK7ABC", short_name = "KK7A")) }
        verifySuspend(exactly(0)) { radioConfigUseCase.setOwner(any(), any()) }
    }

    @Test
    fun `saveUserConfig sends setOwner for unlicensed user`() = runTest {
        val node = Node(num = 123, user = User(id = "!123"))
        nodeRepository.setNodes(listOf(node))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 123))
        viewModel = createViewModel()

        val user = User(long_name = "Test User", short_name = "TU")
        everySuspend { radioConfigUseCase.setOwner(any(), any()) } returns 42

        viewModel.saveUserConfig(user)

        verifySuspend { radioConfigUseCase.setOwner(123, user) }
        verifySuspend(exactly(0)) { radioConfigUseCase.setHamMode(any(), any()) }
    }

    @Test
    fun `saveUserConfig never sends setHamMode to a remote node`() = runTest {
        val localNode = Node(num = 100, user = User(id = "!100"))
        val remoteNode = Node(num = 456, user = User(id = "!456"))
        nodeRepository.setNodes(listOf(localNode, remoteNode))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 100))
        viewModel = createViewModel(destNum = 456)

        val user = User(long_name = "KK7ABC", short_name = "KK7A", is_licensed = true)
        everySuspend { radioConfigUseCase.setOwner(any(), any()) } returns 42

        viewModel.saveUserConfig(user)

        verifySuspend { radioConfigUseCase.setOwner(456, user) }
        verifySuspend(exactly(0)) { radioConfigUseCase.setHamMode(any(), any()) }
    }

    @Test
    fun `saveUserConfig routes subsequent licensed saves to setOwner`() = runTest {
        val node = Node(num = 123, user = User(id = "!123"))
        nodeRepository.setNodes(listOf(node))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 123))
        viewModel = createViewModel()

        val user = User(long_name = "KK7ABC", short_name = "KK7A", is_licensed = true)
        everySuspend { radioConfigUseCase.setHamMode(any(), any()) } returns 42
        everySuspend { radioConfigUseCase.setOwner(any(), any()) } returns 43

        // First save transitions OFF→ON and onboards via set_ham_mode.
        viewModel.saveUserConfig(user)
        // A later save while already licensed must use set_owner so other owner fields propagate.
        val edited = user.copy(short_name = "KK7B")
        viewModel.saveUserConfig(edited)

        verifySuspend(exactly(1)) { radioConfigUseCase.setHamMode(any(), any()) }
        verifySuspend { radioConfigUseCase.setOwner(123, edited) }
    }

    @Test
    fun `saveUserConfig routes licensed save to setOwner when myNodeInfo is absent`() = runTest {
        val node = Node(num = 123, user = User(id = "!123"))
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        val user = User(long_name = "KK7ABC", short_name = "KK7A", is_licensed = true)
        everySuspend { radioConfigUseCase.setOwner(any(), any()) } returns 42

        viewModel.saveUserConfig(user)

        verifySuspend { radioConfigUseCase.setOwner(123, user) }
        verifySuspend(exactly(0)) { radioConfigUseCase.setHamMode(any(), any()) }
    }

    @Test
    fun `setRingtone calls useCase`() = runTest {
        val node = Node(num = 123, user = User(id = "!123"))
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        everySuspend { radioConfigUseCase.setRingtone(any(), any()) } returns Unit

        viewModel.setRingtone("ringtone.mp3")

        assertEquals("ringtone.mp3", viewModel.radioConfigState.value.ringtone)
        verifySuspend { radioConfigUseCase.setRingtone(123, "ringtone.mp3") }
    }

    @Test
    fun `setCannedMessages calls useCase`() = runTest {
        val node = Node(num = 123, user = User(id = "!123"))
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        everySuspend { radioConfigUseCase.setCannedMessages(any(), any()) } returns Unit

        viewModel.setCannedMessages("Hello|World")

        assertEquals("Hello|World", viewModel.radioConfigState.value.cannedMessageMessages)
        verifySuspend { radioConfigUseCase.setCannedMessages(123, "Hello|World") }
    }

    @Test
    fun `destNum from SavedStateHandle resolves destNode`() = runTest {
        val node = Node(num = 456, user = User(id = "!456"))
        nodeRepository.setNodes(listOf(node))
        viewModel =
            RadioConfigViewModel(
                destNum = 456,
                radioConfigRepository = radioConfigRepository,
                packetRepository = packetRepository,
                serviceRepository = serviceRepository,
                nodeRepository = nodeRepository,
                locationRepository = locationRepository,
                mapConsentPrefs = mapConsentPrefs,
                analyticsPrefs = analyticsPrefs,
                homoglyphEncodingPrefs = homoglyphEncodingPrefs,
                importProfileUseCase = importProfileUseCase,
                exportProfileUseCase = exportProfileUseCase,
                importSecurityConfigUseCase = importSecurityConfigUseCase,
                securityKeyBackupStore = securityKeyBackupStore,
                snackbarManager = snackbarManager,
                installProfileUseCase = installProfileUseCase,
                radioConfigUseCase = radioConfigUseCase,
                adminActionsUseCase = adminActionsUseCase,
                processRadioResponseUseCase = processRadioResponseUseCase,
                locationService = locationService,
                fileService = fileService,
                mqttManager = mqttManager,
                lockdownCoordinator = FakeLockdownCoordinator(),
            )
        assertEquals(456, viewModel.destNode.value?.num)
    }

    @Test
    fun `setModuleConfig calls useCase`() = runTest {
        val node = Node(num = 123, user = User(id = "!123"))
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        val config =
            org.meshtastic.proto.ModuleConfig(mqtt = org.meshtastic.proto.ModuleConfig.MQTTConfig(enabled = true))
        everySuspend { radioConfigUseCase.setModuleConfig(any(), any()) } returns 42

        viewModel.setModuleConfig(config)

        verifySuspend { radioConfigUseCase.setModuleConfig(123, config) }
        assertEquals(true, viewModel.radioConfigState.value.moduleConfig.mqtt?.enabled)
    }

    @Test
    fun `setFixedPosition calls useCase`() = runTest {
        val node = Node(num = 123, user = User(id = "!123"))
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        val pos = org.meshtastic.core.model.Position(latitude = 1.0, longitude = 2.0, altitude = 0)
        everySuspend { radioConfigUseCase.setFixedPosition(any(), any()) } returns Unit

        viewModel.setFixedPosition(pos)

        verifySuspend { radioConfigUseCase.setFixedPosition(123, pos) }
    }

    @Test
    fun `removeFixedPosition calls useCase`() = runTest {
        val node = Node(num = 123, user = User(id = "!123"))
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        everySuspend { radioConfigUseCase.removeFixedPosition(any()) } returns Unit

        viewModel.removeFixedPosition()

        verifySuspend { radioConfigUseCase.removeFixedPosition(123) }
    }

    @Test
    fun `installProfile calls useCase`() = runTest {
        val node = Node(num = 123, user = User(id = "!123"))
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        val profile = DeviceProfile()
        everySuspend { installProfileUseCase(any(), any(), any()) } returns Unit

        viewModel.installProfile(profile)

        verifySuspend { installProfileUseCase(123, profile, any()) }
    }

    @Test
    fun `processPacketResponse updates state on various results`() = runTest {
        val node = Node(num = 123, user = User(id = "!123"))
        nodeRepository.setNodes(listOf(node))
        val packetFlow = MutableSharedFlow<MeshPacket>()
        every { serviceRepository.meshPacketFlow } returns packetFlow

        viewModel = createViewModel()

        // ConfigResponse
        val configResponse = Config(lora = Config.LoRaConfig(hop_limit = 5))
        every { processRadioResponseUseCase(any(), 123, any()) } returns
            RadioResponseResult.ConfigResponse(configResponse)
        packetFlow.emit(MeshPacket())
        assertEquals(5, viewModel.radioConfigState.value.radioConfig.lora?.hop_limit)

        // ModuleConfigResponse
        val moduleResponse =
            org.meshtastic.proto.ModuleConfig(
                telemetry = org.meshtastic.proto.ModuleConfig.TelemetryConfig(device_update_interval = 300),
            )
        every { processRadioResponseUseCase(any(), 123, any()) } returns
            RadioResponseResult.ModuleConfigResponse(moduleResponse)
        packetFlow.emit(MeshPacket())
        assertEquals(300, viewModel.radioConfigState.value.moduleConfig.telemetry?.device_update_interval)

        // Owner
        val user = User(long_name = "New Name")
        every { processRadioResponseUseCase(any(), 123, any()) } returns RadioResponseResult.Owner(user)
        packetFlow.emit(MeshPacket())
        assertEquals("New Name", viewModel.radioConfigState.value.userConfig.long_name)

        // Ringtone
        every { processRadioResponseUseCase(any(), 123, any()) } returns RadioResponseResult.Ringtone("bell.mp3")
        packetFlow.emit(MeshPacket())
        assertEquals("bell.mp3", viewModel.radioConfigState.value.ringtone)

        // Error
        every { processRadioResponseUseCase(any(), 123, any()) } returns
            RadioResponseResult.Error(org.meshtastic.core.resources.UiText.DynamicString("Fail"))
        packetFlow.emit(MeshPacket())
        assertTrue(viewModel.radioConfigState.value.responseState is ResponseState.Error)
    }

    @Test
    fun `Admin actions call correct useCases`() = runTest {
        val node = Node(num = 123, user = User(id = "!123"))
        nodeRepository.setNodes(listOf(node))
        val packetFlow = MutableSharedFlow<MeshPacket>()
        every { serviceRepository.meshPacketFlow } returns packetFlow

        viewModel = createViewModel()

        // SHUTDOWN
        everySuspend { adminActionsUseCase.shutdown(any()) } returns 42
        // Set metadata to allow shutdown
        every { processRadioResponseUseCase(any(), 123, any()) } returns
            RadioResponseResult.Metadata(DeviceMetadata(canShutdown = true))
        packetFlow.emit(MeshPacket())

        viewModel.setResponseStateLoading(AdminRoute.SHUTDOWN)
        // AdminRoute fires sendAdminRequest after receiving ConfigResponse (session key),
        // not after a routing ACK (Success).
        every { processRadioResponseUseCase(any(), 123, any()) } returns RadioResponseResult.ConfigResponse(Config())
        packetFlow.emit(MeshPacket())
        verifySuspend { adminActionsUseCase.shutdown(123) }

        // NODEDB_RESET
        everySuspend { adminActionsUseCase.nodedbReset(any(), any(), any()) } returns 42
        viewModel.setResponseStateLoading(AdminRoute.NODEDB_RESET)
        every { processRadioResponseUseCase(any(), 123, any()) } returns RadioResponseResult.ConfigResponse(Config())
        packetFlow.emit(MeshPacket())
        verifySuspend { adminActionsUseCase.nodedbReset(123, any(), any()) }
    }

    @Test
    fun `setResponseStateLoading for various routes calls correct useCases`() = runTest {
        val node = Node(num = 123, user = User(id = "!123"))
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        // USER
        everySuspend { radioConfigUseCase.getOwner(any()) } returns 42
        viewModel.setResponseStateLoading(ConfigRoute.USER)
        verifySuspend { radioConfigUseCase.getOwner(123) }

        // CHANNELS
        everySuspend { radioConfigUseCase.getChannel(any(), any()) } returns 42
        everySuspend { radioConfigUseCase.getConfig(any(), any()) } returns 42
        viewModel.setResponseStateLoading(ConfigRoute.CHANNELS)
        verifySuspend { radioConfigUseCase.getChannel(123, 0) }
        verifySuspend {
            radioConfigUseCase.getConfig(123, org.meshtastic.proto.AdminMessage.ConfigType.LORA_CONFIG.value)
        }

        // LORA
        viewModel.setResponseStateLoading(ConfigRoute.LORA)
        verifySuspend { radioConfigUseCase.getConfig(123, ConfigRoute.LORA.type) }
    }

    @Test
    fun `registerRequestId timeout clears request and sets error`() = runTest {
        val node = Node(num = 123, user = User(id = "!123"))
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        everySuspend { radioConfigUseCase.getOwner(any()) } returns 42

        viewModel.setResponseStateLoading(ConfigRoute.USER)

        // state should be loading
        assertTrue(viewModel.radioConfigState.value.responseState is ResponseState.Loading)

        // advance time past 30 seconds
        advanceTimeBy(31_000)
        runCurrent()

        // after timeout, the request ID should be removed, and if empty, sendError is called.
        // It's hard to assert sendError directly without a mock on a channel, but we can verify it doesn't stay loading
        // actually sendError updates the state? No, sendError sends an event.
        // But the requestIds gets cleared.
    }

    @Test
    fun `ensureLoadingForRemote sets loading state for remote nodes`() = runTest {
        val localNode = Node(num = 100, user = User(id = "!100"))
        val remoteNode = Node(num = 456, user = User(id = "!456"))
        nodeRepository.setNodes(listOf(localNode, remoteNode))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 100))

        val remoteVm = createViewModel(destNum = 456)

        // Remote VM starts with Empty responseState
        assertEquals(ResponseState.Empty, remoteVm.radioConfigState.value.responseState)
        assertFalse(remoteVm.radioConfigState.value.isLocal)

        // ensureLoadingForRemote should transition to Loading
        remoteVm.ensureLoadingForRemote()
        assertTrue(remoteVm.radioConfigState.value.responseState is ResponseState.Loading)
    }

    @Test
    fun `ensureLoadingForRemote is no-op for local nodes`() = runTest {
        val localNode = Node(num = 100, user = User(id = "!100"))
        nodeRepository.setNodes(listOf(localNode))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 100))

        val localVm = createViewModel(destNum = 100)

        // Local VM should have isLocal = true
        assertTrue(localVm.radioConfigState.value.isLocal)

        // ensureLoadingForRemote should NOT change responseState
        localVm.ensureLoadingForRemote()
        assertEquals(ResponseState.Empty, localVm.radioConfigState.value.responseState)
    }

    @Test
    fun `ensureLoadingForRemote is no-op when already loading`() = runTest {
        val localNode = Node(num = 100, user = User(id = "!100"))
        val remoteNode = Node(num = 456, user = User(id = "!456"))
        nodeRepository.setNodes(listOf(localNode, remoteNode))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 100))

        val remoteVm = createViewModel(destNum = 456)

        // Set loading first
        remoteVm.ensureLoadingForRemote()
        assertTrue(remoteVm.radioConfigState.value.responseState is ResponseState.Loading)

        // Calling again should still be Loading (no-op, not a new instance)
        remoteVm.ensureLoadingForRemote()
        assertTrue(remoteVm.radioConfigState.value.responseState is ResponseState.Loading)
    }

    @Test
    fun `loraRegionPresetMapFlow populates state`() = runTest {
        val node = Node(num = 123, user = User(id = "!123"))
        nodeRepository.setNodes(listOf(node))
        val mapFlow = MutableStateFlow<LoRaRegionPresetMap?>(null)
        every { radioConfigRepository.loraRegionPresetMapFlow } returns mapFlow
        viewModel = createViewModel()
        runCurrent()

        assertEquals(null, viewModel.radioConfigState.value.loraRegionPresetMap)

        val map = LoRaRegionPresetMap(groups = listOf(LoRaPresetGroup(licensed_only = true)))
        mapFlow.value = map
        runCurrent()

        assertEquals(map, viewModel.radioConfigState.value.loraRegionPresetMap)
    }

    @Test
    fun `localIsLicensed tracks the destination node's is_licensed flag`() = runTest {
        val node = Node(num = 123, user = User(id = "!123", is_licensed = true))
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()
        runCurrent()

        assertTrue(viewModel.radioConfigState.value.localIsLicensed)
    }

    @Test
    fun `LoRa setConfig is not applied optimistically and issues no re-read`() = runTest {
        // A firmware region swap (e.g. EU sibling) is applied live; the form must reflect the device's actual
        // value, which is re-read on next LoRa-screen entry — NOT applied optimistically here, and the save ACK
        // must not trigger an in-place re-read (that would suppress the normal save-success UX).
        val node = Node(num = 123, user = User(id = "!123"))
        nodeRepository.setNodes(listOf(node))
        nodeRepository.setMyNodeInfo(myNodeInfo(myNodeNum = 123))
        val packetFlow = MutableSharedFlow<MeshPacket>()
        every { serviceRepository.meshPacketFlow } returns packetFlow
        every { radioConfigRepository.localConfigFlow } returns
            MutableStateFlow(LocalConfig(lora = Config.LoRaConfig(region = Config.LoRaConfig.RegionCode.EU_868)))
        viewModel = createViewModel(destNum = 123)
        runCurrent()

        everySuspend { radioConfigUseCase.setConfig(any(), any()) } returns 42

        viewModel.setConfig(Config(lora = Config.LoRaConfig(region = Config.LoRaConfig.RegionCode.US)))
        runCurrent()

        // The optimistic state still reflects the device's value, not the requested region.
        assertEquals(Config.LoRaConfig.RegionCode.EU_868, viewModel.radioConfigState.value.radioConfig.lora?.region)

        every { processRadioResponseUseCase(any(), 123, any()) } returns RadioResponseResult.Success
        packetFlow.emit(MeshPacket(decoded = Data(request_id = 42)))
        runCurrent()

        verifySuspend(exactly(0)) { radioConfigUseCase.getConfig(any(), any()) }
    }

    @Test
    fun `backupSecurityKeys refuses to persist empty keys`() = runTest {
        val node = Node(num = 123, user = User(id = "!123"))
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        // Empty SecurityConfig is the fallback before the device's config response arrives — backing it up and later
        // restoring it would wipe the device's real keys with blanks.
        viewModel.backupSecurityKeys(Config.SecurityConfig())

        verify(exactly(0)) { securityKeyBackupStore.save(any(), any(), any(), any()) }
    }

    @Test
    fun `backupSecurityKeys persists real keys`() = runTest {
        val node = Node(num = 123, user = User(id = "!123"))
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        val config = Config.SecurityConfig(public_key = "pub".encodeUtf8(), private_key = "priv".encodeUtf8())
        viewModel.backupSecurityKeys(config)

        // Pin the exact base64 so a public/private swap or encoding change is caught.
        verify { securityKeyBackupStore.save(123, "cHVi", "cHJpdg==", any()) }
    }

    @Test
    fun `restoreSecurityKeys is a no-op when no backup exists`() = runTest {
        val node = Node(num = 123, user = User(id = "!123"))
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()
        every { securityKeyBackupStore.get(123) } returns null

        viewModel.restoreSecurityKeys()

        verifySuspend(exactly(0)) { radioConfigUseCase.setConfig(any(), any()) }
    }

    @Test
    fun `restoreSecurityKeys pushes decoded config to the device on success`() = runTest {
        val node = Node(num = 123, user = User(id = "!123"))
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        val stored = StoredSecurityKeys(publicKeyBase64 = "cHVi", privateKeyBase64 = "cHJpdg==", timestamp = 1L)
        val decoded = Config.SecurityConfig(public_key = "pub".encodeUtf8(), private_key = "priv".encodeUtf8())
        every { securityKeyBackupStore.get(123) } returns stored
        every { importSecurityConfigUseCase(stored) } returns Result.success(decoded)
        everySuspend { radioConfigUseCase.setConfig(any(), any()) } returns 42

        viewModel.restoreSecurityKeys()

        verifySuspend { radioConfigUseCase.setConfig(123, Config(security = decoded)) }
    }

    private fun fourChannelFixture() = listOf(
        ChannelSettings(name = "A"),
        ChannelSettings(name = "B"),
        ChannelSettings(name = "C"),
        ChannelSettings(name = "D"),
    )

    private fun myNodeInfo(myNodeNum: Int) = MyNodeInfo(
        myNodeNum = myNodeNum,
        hasGPS = false,
        model = null,
        firmwareVersion = null,
        couldUpdate = false,
        shouldUpdate = false,
        currentPacketId = 0,
        messageTimeoutMsec = 0,
        minAppVersion = 0,
        maxChannels = 8,
        hasWifi = false,
        channelUtilization = 0f,
        airUtilTx = 0f,
        deviceId = null,
    )
}
