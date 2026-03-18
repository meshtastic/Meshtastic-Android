/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.feature.messaging

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import dev.mokkery.matcher.any
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.repository.QuickChatActionRepository
import org.meshtastic.core.model.service.ServiceAction
import org.meshtastic.core.repository.CustomEmojiPrefs
import org.meshtastic.core.repository.HomoglyphPrefs
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.core.repository.usecase.SendMessageUseCase
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.TestDataFactory
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.DeviceProfile
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MessageViewModelTest {

    private lateinit var viewModel: MessageViewModel
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var nodeRepository: FakeNodeRepository
    private val radioConfigRepository: RadioConfigRepository = mock(MockMode.autofill)
    private val quickChatActionRepository: QuickChatActionRepository = mock(MockMode.autofill)
    private val packetRepository: PacketRepository = mock(MockMode.autofill)
    private val serviceRepository: ServiceRepository = mock(MockMode.autofill)
    private val sendMessageUseCase: SendMessageUseCase = mock(MockMode.autofill)
    private val customEmojiPrefs: CustomEmojiPrefs = mock(MockMode.autofill)
    private val homoglyphPrefs: HomoglyphPrefs = mock(MockMode.autofill)
    private val uiPrefs: UiPrefs = mock(MockMode.autofill)

    @BeforeTest
    fun setUp() {
        savedStateHandle = SavedStateHandle(mapOf("contactKey" to "0!12345678"))
        nodeRepository = FakeNodeRepository()

        // Core flows - MUST be separate every blocks
        every { radioConfigRepository.channelSetFlow } returns MutableStateFlow(ChannelSet())
        every { radioConfigRepository.localConfigFlow } returns MutableStateFlow(LocalConfig())
        every { radioConfigRepository.moduleConfigFlow } returns MutableStateFlow(LocalModuleConfig())
        every { radioConfigRepository.deviceProfileFlow } returns MutableStateFlow(DeviceProfile())
        
        every { serviceRepository.serviceAction } returns emptyFlow<ServiceAction>()
        every { serviceRepository.connectionState } returns MutableStateFlow(org.meshtastic.core.model.ConnectionState.Disconnected)
        
        every { customEmojiPrefs.customEmojiFrequency } returns MutableStateFlow<String?>(null)
        every { homoglyphPrefs.homoglyphEncodingEnabled } returns MutableStateFlow(false)
        every { uiPrefs.showQuickChat } returns MutableStateFlow(false)
        
        every { packetRepository.getContactSettings() } returns MutableStateFlow(emptyMap())
        every { packetRepository.getFirstUnreadMessageUuid(any<String>()) } returns MutableStateFlow(null)
        every { packetRepository.hasUnreadMessages(any<String>()) } returns MutableStateFlow(false)
        every { packetRepository.getUnreadCountFlow(any<String>()) } returns MutableStateFlow(0)
        every { packetRepository.getFilteredCountFlow(any<String>()) } returns MutableStateFlow(0)
        
        every { quickChatActionRepository.getAllActions() } returns MutableStateFlow(emptyList())

        viewModel = MessageViewModel(
            savedStateHandle = savedStateHandle,
            nodeRepository = nodeRepository,
            radioConfigRepository = radioConfigRepository,
            quickChatActionRepository = quickChatActionRepository,
            packetRepository = packetRepository,
            serviceRepository = serviceRepository,
            sendMessageUseCase = sendMessageUseCase,
            customEmojiPrefs = customEmojiPrefs,
            homoglyphEncodingPrefs = homoglyphPrefs,
            uiPrefs = uiPrefs,
            notificationManager = mock(MockMode.autofill),
        )
    }

    @Test
    fun testInitialization() = runTest {
        assertNotNull(viewModel)
    }

    @Test
    fun testNodeRepositoryIntegration() = runTest {
        val testNodes = TestDataFactory.createTestNodes(3)
        nodeRepository.setNodes(testNodes)
        
        viewModel.nodeList.test {
            // Initial value from stateIn
            assertEquals(emptyList(), awaitItem())
            // First actual list from repo
            val list = awaitItem()
            assertEquals(3, list.size)
        }
    }
}
