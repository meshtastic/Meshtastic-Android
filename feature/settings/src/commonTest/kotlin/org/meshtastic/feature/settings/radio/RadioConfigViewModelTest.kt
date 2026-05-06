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

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verifySuspend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.domain.usecase.settings.AdminActionsUseCase
import org.meshtastic.core.domain.usecase.settings.ExportProfileUseCase
import org.meshtastic.core.domain.usecase.settings.ExportSecurityConfigUseCase
import org.meshtastic.core.domain.usecase.settings.ImportProfileUseCase
import org.meshtastic.core.domain.usecase.settings.InstallProfileUseCase
import org.meshtastic.core.domain.usecase.settings.RadioConfigUseCase
import org.meshtastic.core.model.AdminException
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.ResponseState
import org.meshtastic.core.repository.AnalyticsPrefs
import org.meshtastic.core.repository.FileService
import org.meshtastic.core.repository.HomoglyphPrefs
import org.meshtastic.core.repository.LocationRepository
import org.meshtastic.core.repository.LocationService
import org.meshtastic.core.repository.MapConsentPrefs
import org.meshtastic.core.repository.MqttManager
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.feature.settings.navigation.ConfigRoute
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.DeviceProfile
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.User
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    private val exportSecurityConfigUseCase: ExportSecurityConfigUseCase = mock(MockMode.autofill)
    private val installProfileUseCase: InstallProfileUseCase = mock(MockMode.autofill)
    private val radioConfigUseCase: RadioConfigUseCase = mock(MockMode.autofill)
    private val adminActionsUseCase: AdminActionsUseCase = mock(MockMode.autofill)
    private val locationService: LocationService = mock(MockMode.autofill)
    private val fileService: FileService = mock(MockMode.autofill)
    private val mqttManager: MqttManager = mock(MockMode.autofill)

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

        every { analyticsPrefs.analyticsAllowed } returns MutableStateFlow(false)
        every { homoglyphEncodingPrefs.homoglyphEncodingEnabled } returns MutableStateFlow(false)

        every { serviceRepository.connectionState } returns
            MutableStateFlow(org.meshtastic.core.model.ConnectionState.Connected)

        every { mqttManager.mqttConnectionState } returns
            MutableStateFlow(org.meshtastic.core.model.MqttConnectionState.Inactive)

        viewModel = createViewModel()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = RadioConfigViewModel(
        savedStateHandle = SavedStateHandle(),
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
        exportSecurityConfigUseCase = exportSecurityConfigUseCase,
        installProfileUseCase = installProfileUseCase,
        radioConfigUseCase = radioConfigUseCase,
        adminActionsUseCase = adminActionsUseCase,
        locationService = locationService,
        fileService = fileService,
        mqttManager = mqttManager,
    )

    @Test
    fun `setConfig calls useCase`() = runTest {
        val node = Node(num = 123, user = User(id = "!123"))
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        val config = Config(device = Config.DeviceConfig(role = Config.DeviceConfig.Role.ROUTER))
        everySuspend { radioConfigUseCase.setConfig(any(), any()) } returns Unit

        viewModel.setConfig(config)

        viewModel.radioConfigState.test {
            val state = awaitItem()
            assertEquals(Config.DeviceConfig.Role.ROUTER, state.radioConfig.device?.role)
            cancelAndIgnoreRemainingEvents()
        }

        verifySuspend { radioConfigUseCase.setConfig(123, config) }
    }

    @Test
    fun `toggleAnalyticsAllowed updates prefs`() {
        every { analyticsPrefs.setAnalyticsAllowed(any()) } returns Unit

        viewModel.toggleAnalyticsAllowed()

        verify { analyticsPrefs.setAnalyticsAllowed(true) }
    }

    @Test
    fun `toggleHomoglyphCharactersEncodingEnabled updates prefs`() {
        every { homoglyphEncodingPrefs.setHomoglyphEncodingEnabled(any()) } returns Unit

        viewModel.toggleHomoglyphCharactersEncodingEnabled()

        verify { homoglyphEncodingPrefs.setHomoglyphEncodingEnabled(true) }
    }

    @Test
    fun `updateChannels calls useCase for each changed channel`() = runTest {
        val node = Node(num = 123, user = User(id = "!123"))
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        val old = listOf(ChannelSettings(name = "Old"))
        val new = listOf(ChannelSettings(name = "New"))

        everySuspend { radioConfigUseCase.setRemoteChannel(any(), any()) } returns Unit

        viewModel.updateChannels(new, old)

        verifySuspend { radioConfigUseCase.setRemoteChannel(123, any()) }
        assertEquals(new, viewModel.radioConfigState.value.channelList)
    }

    @Test
    fun `setResponseStateLoading for USER fetches owner directly`() = runTest {
        val node = Node(num = 123, user = User(id = "!123"))
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        val user = User(long_name = "Fetched User")
        everySuspend { radioConfigUseCase.getOwner(any()) } returns user

        viewModel.setResponseStateLoading(ConfigRoute.USER)
        runCurrent()

        assertEquals("Fetched User", viewModel.radioConfigState.value.userConfig.long_name)
        assertTrue(viewModel.radioConfigState.value.responseState is ResponseState.Success)
    }

    @Test
    fun `setResponseStateLoading for CHANNELS fetches channels and lora config`() = runTest {
        val node = Node(num = 123, user = User(id = "!123"))
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        val channels = listOf(
            Channel(index = 0, settings = ChannelSettings(name = "Primary")),
            Channel(index = 1, settings = ChannelSettings(name = "Secondary")),
        )
        val loraConfig = Config(lora = Config.LoRaConfig(hop_limit = 5))
        everySuspend { radioConfigUseCase.listChannels(any()) } returns channels
        everySuspend { radioConfigUseCase.getConfig(any(), any()) } returns loraConfig

        viewModel.setResponseStateLoading(ConfigRoute.CHANNELS)
        runCurrent()

        assertEquals(2, viewModel.radioConfigState.value.channelList.size)
        assertEquals("Primary", viewModel.radioConfigState.value.channelList[0].name)
        assertEquals(5, viewModel.radioConfigState.value.radioConfig.lora?.hop_limit)
        assertTrue(viewModel.radioConfigState.value.responseState is ResponseState.Success)
    }

    @Test
    fun `setResponseStateLoading for REBOOT calls admin action directly`() = runTest {
        val node = Node(num = 123, user = User(id = "!123"))
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        everySuspend { adminActionsUseCase.reboot(any()) } returns Unit

        viewModel.setResponseStateLoading(AdminRoute.REBOOT)
        runCurrent()

        verifySuspend { adminActionsUseCase.reboot(123) }
        assertTrue(viewModel.radioConfigState.value.responseState is ResponseState.Success)
    }

    @Test
    fun `setResponseStateLoading for SHUTDOWN blocked when canShutdown is false`() = runTest {
        val node = Node(num = 123, user = User(id = "!123"), metadata = DeviceMetadata(canShutdown = false))
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        viewModel.setResponseStateLoading(AdminRoute.SHUTDOWN)
        runCurrent()

        assertTrue(viewModel.radioConfigState.value.responseState is ResponseState.Error)
    }

    @Test
    fun `setResponseStateLoading for FACTORY_RESET calls admin action`() = runTest {
        val node = Node(num = 123, user = User(id = "!123"))
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        everySuspend { adminActionsUseCase.factoryReset(any(), any()) } returns Unit

        viewModel.setResponseStateLoading(AdminRoute.FACTORY_RESET)
        runCurrent()

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
        everySuspend { radioConfigUseCase.setOwner(any(), any()) } returns Unit

        viewModel.setOwner(user)

        verifySuspend { radioConfigUseCase.setOwner(123, user) }
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
    fun `initDestNum updates value correctly including null`() = runTest {
        viewModel = createViewModel()

        viewModel.initDestNum(123)
        assertEquals(
            123,
            viewModel.destNode.value?.num ?: 123,
        )

        viewModel.initDestNum(null)
    }

    @Test
    fun `setModuleConfig calls useCase`() = runTest {
        val node = Node(num = 123, user = User(id = "!123"))
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        val config =
            org.meshtastic.proto.ModuleConfig(mqtt = org.meshtastic.proto.ModuleConfig.MQTTConfig(enabled = true))
        everySuspend { radioConfigUseCase.setModuleConfig(any(), any()) } returns Unit

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
    fun `setResponseStateLoading shows error on AdminException timeout`() = runTest {
        val node = Node(num = 123, user = User(id = "!123"))
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        everySuspend { radioConfigUseCase.getOwner(any()) } throws AdminException.Timeout()

        viewModel.setResponseStateLoading(ConfigRoute.USER)
        runCurrent()

        assertTrue(viewModel.radioConfigState.value.responseState is ResponseState.Error)
    }

    @Test
    fun `setResponseStateLoading for ConfigRoute fetches config`() = runTest {
        val node = Node(num = 123, user = User(id = "!123"))
        nodeRepository.setNodes(listOf(node))
        viewModel = createViewModel()

        val config = Config(device = Config.DeviceConfig(role = Config.DeviceConfig.Role.ROUTER))
        everySuspend { radioConfigUseCase.getConfig(any(), any()) } returns config

        viewModel.setResponseStateLoading(ConfigRoute.DEVICE)
        runCurrent()

        assertEquals(Config.DeviceConfig.Role.ROUTER, viewModel.radioConfigState.value.radioConfig.device?.role)
        assertTrue(viewModel.radioConfigState.value.responseState is ResponseState.Success)
    }
}
