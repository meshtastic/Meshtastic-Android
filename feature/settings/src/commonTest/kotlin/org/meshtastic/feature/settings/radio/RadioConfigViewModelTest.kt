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
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.matcher.any
import dev.mokkery.verifySuspend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.domain.usecase.settings.*
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.*
import org.meshtastic.proto.*
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class RadioConfigViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val radioConfigRepository: RadioConfigRepository = mock(MockMode.autofill)
    private val packetRepository: PacketRepository = mock(MockMode.autofill)
    private val serviceRepository: ServiceRepository = mock(MockMode.autofill)
    private val nodeRepository: NodeRepository = mock(MockMode.autofill)
    private val locationRepository: LocationRepository = mock(MockMode.autofill)
    private val mapConsentPrefs: MapConsentPrefs = mock(MockMode.autofill)
    private val analyticsPrefs: AnalyticsPrefs = mock(MockMode.autofill)
    private val homoglyphEncodingPrefs: HomoglyphPrefs = mock(MockMode.autofill)
    
    private val toggleAnalyticsUseCase: ToggleAnalyticsUseCase = mock(MockMode.autofill)
    private val toggleHomoglyphEncodingUseCase: ToggleHomoglyphEncodingUseCase = mock(MockMode.autofill)
    private val importProfileUseCase: ImportProfileUseCase = mock(MockMode.autofill)
    private val exportProfileUseCase: ExportProfileUseCase = mock(MockMode.autofill)
    private val exportSecurityConfigUseCase: ExportSecurityConfigUseCase = mock(MockMode.autofill)
    private val installProfileUseCase: InstallProfileUseCase = mock(MockMode.autofill)
    private val radioConfigUseCase: RadioConfigUseCase = mock(MockMode.autofill)
    private val adminActionsUseCase: AdminActionsUseCase = mock(MockMode.autofill)
    private val processRadioResponseUseCase: ProcessRadioResponseUseCase = mock(MockMode.autofill)
    private val locationService: LocationService = mock(MockMode.autofill)
    private val fileService: FileService = mock(MockMode.autofill)
    private val uiPrefs: UiPrefs = mock(MockMode.autofill)

    private lateinit var viewModel: RadioConfigViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(emptyMap())
        every { nodeRepository.myNodeInfo } returns MutableStateFlow(null)
        every { radioConfigRepository.deviceProfileFlow } returns MutableStateFlow(DeviceProfile())
        every { radioConfigRepository.localConfigFlow } returns MutableStateFlow(LocalConfig())
        every { radioConfigRepository.channelSetFlow } returns MutableStateFlow(ChannelSet())
        every { radioConfigRepository.moduleConfigFlow } returns MutableStateFlow(LocalModuleConfig())
        every { serviceRepository.meshPacketFlow } returns MutableSharedFlow()
        every { serviceRepository.connectionState } returns
            MutableStateFlow(org.meshtastic.core.model.ConnectionState.Connected)
        
        every { uiPrefs.showQuickChat } returns MutableStateFlow(false)

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
        toggleAnalyticsUseCase = toggleAnalyticsUseCase,
        toggleHomoglyphEncodingUseCase = toggleHomoglyphEncodingUseCase,
        importProfileUseCase = importProfileUseCase,
        exportProfileUseCase = exportProfileUseCase,
        exportSecurityConfigUseCase = exportSecurityConfigUseCase,
        installProfileUseCase = installProfileUseCase,
        radioConfigUseCase = radioConfigUseCase,
        adminActionsUseCase = adminActionsUseCase,
        processRadioResponseUseCase = processRadioResponseUseCase,
        locationService = locationService,
        fileService = fileService,
        
    )

    @Test
    fun `setConfig calls useCase`() = runTest {
        val node = Node(num = 123, user = User(id = "!123"))
        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(mapOf(123 to node))
        viewModel = createViewModel()

        val config = Config(device = Config.DeviceConfig(role = Config.DeviceConfig.Role.ROUTER))
        everySuspend { radioConfigUseCase.setConfig(any(), any()) } returns 42

        viewModel.setConfig(config)

        viewModel.radioConfigState.test {
            val state = awaitItem()
            assertEquals(Config.DeviceConfig.Role.ROUTER, state.radioConfig.device?.role)
        }
        
        verifySuspend { radioConfigUseCase.setConfig(123, config) }
    }

    @Test
    fun `toggleAnalyticsAllowed calls useCase`() {
        every { toggleAnalyticsUseCase() } returns Unit
        
        viewModel.toggleAnalyticsAllowed()
        
        dev.mokkery.verify { toggleAnalyticsUseCase() }
    }

    @Test
    fun `toggleHomoglyphCharactersEncodingEnabled calls useCase`() {
        every { toggleHomoglyphEncodingUseCase() } returns Unit
        
        viewModel.toggleHomoglyphCharactersEncodingEnabled()
        
        dev.mokkery.verify { toggleHomoglyphEncodingUseCase() }
    }

    @Test
    fun `setPreserveFavorites updates state`() = runTest {
        viewModel.radioConfigState.test {
            assertEquals(false, awaitItem().nodeDbResetPreserveFavorites)
            viewModel.setPreserveFavorites(true)
            assertEquals(true, awaitItem().nodeDbResetPreserveFavorites)
        }
    }

    @Test
    fun `setOwner calls useCase`() = runTest {
        val node = Node(num = 123, user = User(id = "!123"))
        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(mapOf(123 to node))
        viewModel = createViewModel()

        val user = User(long_name = "Test User")
        everySuspend { radioConfigUseCase.setOwner(any(), any()) } returns 42

        viewModel.setOwner(user)
        
        verifySuspend { radioConfigUseCase.setOwner(123, user) }
    }

    @Test
    fun `setRingtone calls useCase`() = runTest {
        val node = Node(num = 123, user = User(id = "!123"))
        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(mapOf(123 to node))
        viewModel = createViewModel()

        everySuspend { radioConfigUseCase.setRingtone(any(), any()) } returns Unit

        viewModel.setRingtone("ringtone.mp3")
        
        assertEquals("ringtone.mp3", viewModel.radioConfigState.value.ringtone)
        verifySuspend { radioConfigUseCase.setRingtone(123, "ringtone.mp3") }
    }

    @Test
    fun `setCannedMessages calls useCase`() = runTest {
        val node = Node(num = 123, user = User(id = "!123"))
        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(mapOf(123 to node))
        viewModel = createViewModel()

        everySuspend { radioConfigUseCase.setCannedMessages(any(), any()) } returns Unit

        viewModel.setCannedMessages("Hello|World")
        
        assertEquals("Hello|World", viewModel.radioConfigState.value.cannedMessageMessages)
        verifySuspend { radioConfigUseCase.setCannedMessages(123, "Hello|World") }
    }
}
