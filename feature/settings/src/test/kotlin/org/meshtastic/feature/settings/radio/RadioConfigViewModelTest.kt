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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.meshtastic.core.data.repository.LocationRepository
import org.meshtastic.core.domain.usecase.settings.AdminActionsUseCase
import org.meshtastic.core.domain.usecase.settings.ExportProfileUseCase
import org.meshtastic.core.domain.usecase.settings.ExportSecurityConfigUseCase
import org.meshtastic.core.domain.usecase.settings.ImportProfileUseCase
import org.meshtastic.core.domain.usecase.settings.InstallProfileUseCase
import org.meshtastic.core.domain.usecase.settings.ProcessRadioResponseUseCase
import org.meshtastic.core.domain.usecase.settings.RadioConfigUseCase
import org.meshtastic.core.domain.usecase.settings.RadioResponseResult
import org.meshtastic.core.domain.usecase.settings.ToggleAnalyticsUseCase
import org.meshtastic.core.domain.usecase.settings.ToggleHomoglyphEncodingUseCase
import org.meshtastic.core.model.Node
import org.meshtastic.core.prefs.analytics.AnalyticsPrefs
import org.meshtastic.core.prefs.homoglyph.HomoglyphPrefs
import org.meshtastic.core.prefs.map.MapConsentPrefs
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.DeviceProfile
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.MeshPacket

@OptIn(ExperimentalCoroutinesApi::class)
class RadioConfigViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val radioConfigRepository: RadioConfigRepository = mockk(relaxed = true)
    private val packetRepository: PacketRepository = mockk(relaxed = true)
    private val serviceRepository: ServiceRepository = mockk(relaxed = true)
    private val nodeRepository: NodeRepository = mockk(relaxed = true)
    private val locationRepository: LocationRepository = mockk(relaxed = true)
    private val mapConsentPrefs: MapConsentPrefs = mockk(relaxed = true)
    private val analyticsPrefs: AnalyticsPrefs = mockk(relaxed = true)
    private val homoglyphEncodingPrefs: HomoglyphPrefs = mockk(relaxed = true)
    private val toggleAnalyticsUseCase: ToggleAnalyticsUseCase = mockk(relaxed = true)
    private val toggleHomoglyphEncodingUseCase: ToggleHomoglyphEncodingUseCase = mockk(relaxed = true)
    private val importProfileUseCase: ImportProfileUseCase = mockk(relaxed = true)
    private val exportProfileUseCase: ExportProfileUseCase = mockk(relaxed = true)
    private val exportSecurityConfigUseCase: ExportSecurityConfigUseCase = mockk(relaxed = true)
    private val installProfileUseCase: InstallProfileUseCase = mockk(relaxed = true)
    private val radioConfigUseCase: RadioConfigUseCase = mockk(relaxed = true)
    private val adminActionsUseCase: AdminActionsUseCase = mockk(relaxed = true)
    private val processRadioResponseUseCase: ProcessRadioResponseUseCase = mockk(relaxed = true)

    private lateinit var viewModel: RadioConfigViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        
        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(emptyMap())
        every { radioConfigRepository.deviceProfileFlow } returns MutableStateFlow(DeviceProfile())
        every { radioConfigRepository.localConfigFlow } returns MutableStateFlow(LocalConfig())
        every { radioConfigRepository.channelSetFlow } returns MutableStateFlow(ChannelSet())
        every { radioConfigRepository.moduleConfigFlow } returns MutableStateFlow(LocalModuleConfig())
        every { serviceRepository.meshPacketFlow } returns MutableSharedFlow()
        every { serviceRepository.connectionState } returns MutableStateFlow(org.meshtastic.core.model.ConnectionState.Connected)
        every { nodeRepository.myNodeInfo } returns MutableStateFlow(null)

        viewModel = createViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = RadioConfigViewModel(
        savedStateHandle = SavedStateHandle(),
        app = mockk(),
        radioConfigRepository = radioConfigRepository,
        packetRepository = packetRepository,
        serviceRepository = serviceRepository,
        nodeRepository = nodeRepository,
        locationRepository = locationRepository,
        mapConsentPrefs = mapConsentPrefs,
        analyticsPrefs = analyticsPrefs,
        homoglyphEncodingPrefs = homoglyphEncodingPrefs,
        toggleAnalyticsUseCase = toggleAnalyticsUseCase,
        toggleHomoglyphEncodingUseCase = toggleHomoglyphEncodingUseCase,
        importProfileUseCase = importProfileUseCase,
        exportProfileUseCase = exportProfileUseCase,
        exportSecurityConfigUseCase = exportSecurityConfigUseCase,
        installProfileUseCase = installProfileUseCase,
        radioConfigUseCase = radioConfigUseCase,
        adminActionsUseCase = adminActionsUseCase,
        processRadioResponseUseCase = processRadioResponseUseCase,
    )

    @Test
    fun `setConfig updates state and calls useCase`() = runTest {
        val node = Node(num = 123)
        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(mapOf(123 to node))
        viewModel = createViewModel()
        
        val config = Config(device = Config.DeviceConfig(role = Config.DeviceConfig.Role.ROUTER))
        coEvery { radioConfigUseCase.setConfig(123, any()) } returns 42

        viewModel.setConfig(config)
        
        val state = viewModel.radioConfigState.value
        assertEquals(Config.DeviceConfig.Role.ROUTER, state.radioConfig.device?.role)
        coVerify { radioConfigUseCase.setConfig(123, config) }
    }

    @Test
    fun `processPacketResponse updates state on metadata result`() = runTest {
        val node = Node(num = 123)
        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(mapOf(123 to node))
        
        val packet = MeshPacket()
        val metadata = DeviceMetadata(firmware_version = "3.0.0")
        val packetFlow = MutableSharedFlow<MeshPacket>()
        
        every { serviceRepository.meshPacketFlow } returns packetFlow
        every { processRadioResponseUseCase(any(), 123, any()) } returns RadioResponseResult.Metadata(metadata)
        
        viewModel = createViewModel()

        packetFlow.emit(packet)

        val state = viewModel.radioConfigState.value
        assertEquals("3.0.0", state.metadata?.firmware_version)
    }

    @Test
    fun `setOwner calls useCase`() = runTest {
        val node = Node(num = 123)
        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(mapOf(123 to node))
        viewModel = createViewModel()

        val user = org.meshtastic.proto.User(long_name = "Test")
        coEvery { radioConfigUseCase.setOwner(123, any()) } returns 42
        
        viewModel.setOwner(user)
        
        coVerify { radioConfigUseCase.setOwner(123, user) }
    }

    @Test
    fun `updateChannels calls useCase for each changed channel`() = runTest {
        val node = Node(num = 123)
        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(mapOf(123 to node))
        viewModel = createViewModel()

        val old = listOf(ChannelSettings(name = "Old"))
        val new = listOf(ChannelSettings(name = "New"))
        
        coEvery { radioConfigUseCase.setRemoteChannel(123, any()) } returns 42
        
        viewModel.updateChannels(new, old)
        
        coVerify { radioConfigUseCase.setRemoteChannel(123, any()) }
        assertEquals(new, viewModel.radioConfigState.value.channelList)
    }

    @Test
    fun `setResponseStateLoading for REBOOT calls useCase after packet response`() = runTest {
        val node = Node(num = 123)
        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(mapOf(123 to node))
        
        val packetFlow = MutableSharedFlow<MeshPacket>()
        every { serviceRepository.meshPacketFlow } returns packetFlow
        every { processRadioResponseUseCase(any(), any(), any()) } returns RadioResponseResult.Success
        
        viewModel = createViewModel()
        
        coEvery { adminActionsUseCase.reboot(123) } returns 42
        
        viewModel.setResponseStateLoading(AdminRoute.REBOOT)
        
        // Emit a packet to trigger processPacketResponse -> sendAdminRequest
        packetFlow.emit(MeshPacket())
        
        coVerify { adminActionsUseCase.reboot(123) }
    }

    @Test
    fun `setResponseStateLoading for FACTORY_RESET calls useCase after packet response`() = runTest {
        val node = Node(num = 123)
        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(mapOf(123 to node))
        
        val packetFlow = MutableSharedFlow<MeshPacket>()
        every { serviceRepository.meshPacketFlow } returns packetFlow
        every { processRadioResponseUseCase(any(), any(), any()) } returns RadioResponseResult.Success
        
        viewModel = createViewModel()
        
        coEvery { adminActionsUseCase.factoryReset(123, any()) } returns 42
        
        viewModel.setResponseStateLoading(AdminRoute.FACTORY_RESET)
        
        // Emit a packet to trigger processPacketResponse -> sendAdminRequest
        packetFlow.emit(MeshPacket())
        
        coVerify { adminActionsUseCase.factoryReset(123, any()) }
    }
}
