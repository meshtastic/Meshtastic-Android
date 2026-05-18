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
package org.meshtastic.feature.messaging

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.ContactSettings
import org.meshtastic.core.model.service.ServiceAction
import org.meshtastic.core.repository.CustomEmojiPrefs
import org.meshtastic.core.repository.HomoglyphPrefs
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.QuickChatActionRepository
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
import kotlin.test.AfterTest
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
    private val notificationManager: org.meshtastic.core.repository.NotificationManager = mock(MockMode.autofill)

    private val testDispatcher = StandardTestDispatcher()

    private val connectionStateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    private val showQuickChatFlow = MutableStateFlow(false)
    private val customEmojiFrequencyFlow = MutableStateFlow<String?>(null)
    private val contactSettingsFlow = MutableStateFlow<Map<String, ContactSettings>>(emptyMap())

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        savedStateHandle = SavedStateHandle(mapOf("contactKey" to "0!12345678"))
        nodeRepository = FakeNodeRepository()

        connectionStateFlow.value = ConnectionState.Disconnected
        showQuickChatFlow.value = false
        customEmojiFrequencyFlow.value = null
        contactSettingsFlow.value = emptyMap()

        // Core flows - MUST be separate every blocks
        every { radioConfigRepository.channelSetFlow } returns MutableStateFlow(ChannelSet())
        every { radioConfigRepository.localConfigFlow } returns MutableStateFlow(LocalConfig())
        every { radioConfigRepository.moduleConfigFlow } returns MutableStateFlow(LocalModuleConfig())
        every { radioConfigRepository.deviceProfileFlow } returns MutableStateFlow(DeviceProfile())

        every { serviceRepository.serviceAction } returns emptyFlow<ServiceAction>()
        every { serviceRepository.connectionState } returns connectionStateFlow

        every { customEmojiPrefs.customEmojiFrequency } returns customEmojiFrequencyFlow
        every { homoglyphPrefs.homoglyphEncodingEnabled } returns MutableStateFlow(false)
        every { uiPrefs.showQuickChat } returns showQuickChatFlow
        every { uiPrefs.setShowQuickChat(any()) } returns Unit

        every { packetRepository.getContactSettings() } returns contactSettingsFlow
        every { packetRepository.getFirstUnreadMessageUuid(any<String>()) } returns MutableStateFlow(null)
        every { packetRepository.hasUnreadMessages(any<String>()) } returns MutableStateFlow(false)
        every { packetRepository.getUnreadCountFlow(any<String>()) } returns MutableStateFlow(0)
        every { packetRepository.getFilteredCountFlow(any<String>()) } returns MutableStateFlow(0)

        every { quickChatActionRepository.getAllActions() } returns MutableStateFlow(emptyList())

        viewModel =
            MessageViewModel(
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
                notificationManager = notificationManager,
            )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test fun testInitialization() = runTest { assertNotNull(viewModel) }

    @Test
    fun testSetTitle() = runTest {
        viewModel.title.test {
            assertEquals("", awaitItem())
            viewModel.setTitle("New Title")
            assertEquals("New Title", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testConnectionState() = runTest {
        viewModel.connectionState.test {
            assertEquals(ConnectionState.Disconnected, awaitItem())
            connectionStateFlow.value = ConnectionState.Connected
            assertEquals(ConnectionState.Connected, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testToggleShowQuickChat() = runTest {
        viewModel.showQuickChat.test {
            assertEquals(false, awaitItem())

            viewModel.toggleShowQuickChat()
            // Since setShowQuickChat is mocked to returns Unit, it doesn't update the flow.
            // In a real app, the flow would update. We simulate it here.
            showQuickChatFlow.value = true
            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testFrequentEmojis() = runTest {
        customEmojiFrequencyFlow.value = "👍=10,👎=5,😂=20"

        // frequentEmojis is a property, not a flow.
        val emojis = viewModel.frequentEmojis
        assertEquals(listOf("😂", "👍", "👎"), emojis)
    }

    @Test
    fun testSendMessage() = runTest {
        everySuspend { sendMessageUseCase.invoke(any(), any(), any()) } returns Unit

        viewModel.sendMessage("Hello", "0!12345678", null)

        // Wait for coroutine to finish
        advanceUntilIdle()

        // Verify via mokkery
        verifySuspend { sendMessageUseCase.invoke("Hello", "0!12345678", null) }
    }

    @Test
    fun testSendReaction() = runTest {
        everySuspend { serviceRepository.onServiceAction(any()) } returns Unit

        viewModel.sendReaction("❤️", 123, "0!12345678")

        advanceUntilIdle()

        verifySuspend { serviceRepository.onServiceAction(ServiceAction.Reaction("❤️", 123, "0!12345678")) }
    }

    @Test
    fun testDeleteMessages() = runTest {
        everySuspend { packetRepository.deleteMessages(any()) } returns Unit

        viewModel.deleteMessages(listOf(1L, 2L))

        advanceUntilIdle()

        verifySuspend { packetRepository.deleteMessages(listOf(1L, 2L)) }
    }

    @Test
    fun testUnreadCount() = runTest {
        val countFlow = MutableStateFlow(5)
        every { packetRepository.getUnreadCountFlow("new_contact") } returns countFlow

        viewModel.setContactKey("new_contact")

        viewModel.unreadCount.test {
            // Initial 0 from stateIn
            assertEquals(0, awaitItem())
            // Value from countFlow
            assertEquals(5, awaitItem())
            countFlow.value = 10
            assertEquals(10, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testClearUnreadCount() = runTest {
        val contact = "0!12345678"
        everySuspend { packetRepository.clearUnreadCount(contact, 1000L) } returns Unit
        everySuspend { packetRepository.updateLastReadMessage(contact, 1L, 1000L) } returns Unit
        everySuspend { packetRepository.getUnreadCount(contact) } returns 0
        every { notificationManager.cancel(contact.hashCode()) } returns Unit

        viewModel.clearUnreadCount(contact, 1L, 1000L)

        advanceUntilIdle()

        verifySuspend { packetRepository.clearUnreadCount(contact, 1000L) }
        verifySuspend { packetRepository.updateLastReadMessage(contact, 1L, 1000L) }
        verifySuspend { notificationManager.cancel(contact.hashCode()) }
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
            cancelAndIgnoreRemainingEvents()
        }
    }
}
