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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.meshtastic.core.data.repository.LocationRepository
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.data.repository.PacketRepository
import org.meshtastic.core.data.repository.RadioConfigRepository
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.domain.usecase.settings.AdminActionsUseCase
import org.meshtastic.core.domain.usecase.settings.ExportProfileUseCase
import org.meshtastic.core.domain.usecase.settings.ExportSecurityConfigUseCase
import org.meshtastic.core.domain.usecase.settings.ImportProfileUseCase
import org.meshtastic.core.domain.usecase.settings.InstallProfileUseCase
import org.meshtastic.core.domain.usecase.settings.ProcessRadioResponseUseCase
import org.meshtastic.core.domain.usecase.settings.RadioResponseResult
import org.meshtastic.core.domain.usecase.settings.ToggleAnalyticsUseCase
import org.meshtastic.core.domain.usecase.settings.ToggleHomoglyphEncodingUseCase
import org.meshtastic.core.domain.usecase.settings.UpdateRadioConfigUseCase
import org.meshtastic.core.prefs.analytics.AnalyticsPrefs
import org.meshtastic.core.prefs.homoglyph.HomoglyphPrefs
import org.meshtastic.core.prefs.map.MapConsentPrefs
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.DeviceProfile
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.MeshPacket

@OptIn(ExperimentalCoroutinesApi::class)
class RadioConfigViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var radioConfigRepository: RadioConfigRepository
    private lateinit var packetRepository: PacketRepository
    private lateinit var serviceRepository: ServiceRepository
    private lateinit var nodeRepository: NodeRepository
    private lateinit var locationRepository: LocationRepository
    private lateinit var mapConsentPrefs: MapConsentPrefs
    private lateinit var analyticsPrefs: AnalyticsPrefs
    private lateinit var homoglyphEncodingPrefs: HomoglyphPrefs
    private lateinit var toggleAnalyticsUseCase: ToggleAnalyticsUseCase
    private lateinit var toggleHomoglyphEncodingUseCase: ToggleHomoglyphEncodingUseCase
    private lateinit var importProfileUseCase: ImportProfileUseCase
    private lateinit var exportProfileUseCase: ExportProfileUseCase
    private lateinit var exportSecurityConfigUseCase: ExportSecurityConfigUseCase
    private lateinit var installProfileUseCase: InstallProfileUseCase
    private lateinit var updateRadioConfigUseCase: UpdateRadioConfigUseCase
    private lateinit var adminActionsUseCase: AdminActionsUseCase
    private lateinit var processRadioResponseUseCase: ProcessRadioResponseUseCase

    private lateinit var viewModel: RadioConfigViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        
        radioConfigRepository = mockk(relaxed = true)
        packetRepository = mockk(relaxed = true)
        serviceRepository = mockk(relaxed = true)
        nodeRepository = mockk(relaxed = true)
        locationRepository = mockk(relaxed = true)
        mapConsentPrefs = mockk(relaxed = true)
        analyticsPrefs = mockk(relaxed = true)
        homoglyphEncodingPrefs = mockk(relaxed = true)
        toggleAnalyticsUseCase = mockk(relaxed = true)
        toggleHomoglyphEncodingUseCase = mockk(relaxed = true)
        importProfileUseCase = mockk(relaxed = true)
        exportProfileUseCase = mockk(relaxed = true)
        exportSecurityConfigUseCase = mockk(relaxed = true)
        installProfileUseCase = mockk(relaxed = true)
        updateRadioConfigUseCase = mockk(relaxed = true)
        adminActionsUseCase = mockk(relaxed = true)
        processRadioResponseUseCase = mockk(relaxed = true)

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
        updateRadioConfigUseCase = updateRadioConfigUseCase,
        adminActionsUseCase = adminActionsUseCase,
        processRadioResponseUseCase = processRadioResponseUseCase,
    )

    @Test
    fun `setConfig updates state and calls useCase`() = runTest {
        // Arrange
        val node = Node(num = 123)
        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(mapOf(123 to node))
        viewModel = createViewModel()
        advanceUntilIdle()

        val config = Config(device = Config.DeviceConfig(role = Config.DeviceConfig.Role.ROUTER))
        coEvery { updateRadioConfigUseCase.setConfig(any(), any()) } returns 42

        // Act
        viewModel.setConfig(config)
        advanceUntilIdle()

        // Assert
        val state = viewModel.radioConfigState.value
        assertEquals(Config.DeviceConfig.Role.ROUTER, state.radioConfig.device?.role)
        assertTrue(state.responseState is ResponseState.Loading)
        coVerify { updateRadioConfigUseCase.setConfig(123, config) }
    }

    @Test
    fun `processPacketResponse updates state on metadata result`() = runTest {
        // Arrange
        val node = Node(num = 123)
        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(mapOf(123 to node))
        
        val packet = MeshPacket()
        val metadata = DeviceMetadata(firmware_version = "3.0.0")
        val packetFlow = MutableSharedFlow<MeshPacket>()
        
        every { serviceRepository.meshPacketFlow } returns packetFlow
        every { processRadioResponseUseCase(any(), 123, any()) } returns RadioResponseResult.Metadata(metadata)
        
        viewModel = createViewModel()
        advanceUntilIdle()

        // Act
        packetFlow.emit(packet)
        advanceUntilIdle()

        // Assert
        val state = viewModel.radioConfigState.value
        assertEquals("3.0.0", state.metadata?.firmware_version)
    }
}
