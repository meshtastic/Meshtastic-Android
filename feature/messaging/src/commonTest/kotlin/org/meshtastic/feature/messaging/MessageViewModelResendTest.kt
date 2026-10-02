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
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.ContactKey
import org.meshtastic.core.repository.ActiveConversationTracker
import org.meshtastic.core.repository.ConnectionStateProvider
import org.meshtastic.core.repository.CustomEmojiPrefs
import org.meshtastic.core.repository.HomoglyphPrefs
import org.meshtastic.core.repository.MeshNotificationManager
import org.meshtastic.core.repository.MessagingController
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.QuickChatActionRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.core.repository.usecase.SendMessageOutcome
import org.meshtastic.core.repository.usecase.SendMessageUseCase
import org.meshtastic.core.testing.FakeFilterPrefs
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.ui.util.SnackbarManager
import org.meshtastic.feature.messaging.translation.MessageTranslationService
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.DeviceProfile
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MessageViewModelResendTest {

    private val packetRepository: PacketRepository = mock(MockMode.autofill)
    private val sendMessageUseCase: SendMessageUseCase = mock(MockMode.autofill)
    private val radioConfigRepository: RadioConfigRepository = mock(MockMode.autofill)
    private val quickChatActionRepository: QuickChatActionRepository = mock(MockMode.autofill)
    private val connectionStateProvider: ConnectionStateProvider = mock(MockMode.autofill)
    private val customEmojiPrefs: CustomEmojiPrefs = mock(MockMode.autofill)
    private val homoglyphPrefs: HomoglyphPrefs = mock(MockMode.autofill)
    private val uiPrefs: UiPrefs = mock(MockMode.autofill)
    private val testDispatcher = StandardTestDispatcher()
    private val calls = mutableListOf<String>()
    private lateinit var viewModel: MessageViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { radioConfigRepository.channelSetFlow } returns MutableStateFlow(ChannelSet.Builder().build())
        every { radioConfigRepository.localConfigFlow } returns MutableStateFlow(LocalConfig.Builder().build())
        every { radioConfigRepository.moduleConfigFlow } returns MutableStateFlow(LocalModuleConfig.Builder().build())
        every { radioConfigRepository.deviceProfileFlow } returns MutableStateFlow(DeviceProfile.Builder().build())
        every { customEmojiPrefs.customEmojiFrequency } returns MutableStateFlow(null)
        every { homoglyphPrefs.homoglyphEncodingEnabled } returns MutableStateFlow(false)
        every { uiPrefs.showQuickChat } returns MutableStateFlow(false)
        every { uiPrefs.showFullMessageTimestamps } returns MutableStateFlow(false)
        every { connectionStateProvider.connectionState } returns MutableStateFlow(ConnectionState.Disconnected)
        every { packetRepository.getContactSettings() } returns MutableStateFlow(emptyMap())
        every { packetRepository.getFirstUnreadMessageUuid(any<String>()) } returns MutableStateFlow(null)
        every { packetRepository.hasUnreadMessages(any<String>()) } returns MutableStateFlow(false)
        every { packetRepository.getUnreadCountFlow(any<String>()) } returns MutableStateFlow(0)
        every { packetRepository.getFilteredCountFlow(any<String>()) } returns MutableStateFlow(0)
        every { quickChatActionRepository.getAllActions() } returns MutableStateFlow(emptyList())
        everySuspend { packetRepository.replaceMessage(any(), any()) } calls
            {
                calls += "replace:${it.args[0]}"
                @Suppress("UNCHECKED_CAST")
                val send = it.args[1] as suspend () -> Unit
                send()
            }

        viewModel =
            MessageViewModel(
                savedStateHandle = SavedStateHandle(mapOf("contactKey" to LIVE_CONTACT)),
                nodeRepository = FakeNodeRepository(),
                radioConfigRepository = radioConfigRepository,
                quickChatActionRepository = quickChatActionRepository,
                connectionStateProvider = connectionStateProvider,
                messagingController = mock<MessagingController>(MockMode.autofill),
                packetRepository = packetRepository,
                sendMessageUseCase = sendMessageUseCase,
                customEmojiPrefs = customEmojiPrefs,
                homoglyphEncodingPrefs = homoglyphPrefs,
                filterPrefs = FakeFilterPrefs(),
                uiPrefs = uiPrefs,
                meshNotificationManager = mock<MeshNotificationManager>(MockMode.autofill),
                activeConversationTracker = ActiveConversationTracker(),
                messageTranslationService = mock<MessageTranslationService>(MockMode.autofill),
                snackbarManager = SnackbarManager(),
            )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `resend sends the new message inside the replacement of the original`() = runTest {
        everySuspend { sendMessageUseCase.invoke(any(), any(), any()) } calls
            {
                calls += "send"
                SendMessageOutcome.Queued(1)
            }

        viewModel.resendMessage(uuid = 42L, text = "Hello", contactKey = LIVE_CONTACT)
        advanceUntilIdle()

        assertEquals(listOf("replace:42", "send"), calls)
        verifySuspend { sendMessageUseCase.invoke("Hello", LIVE_CONTACT, null) }
    }

    @Test
    fun `resend into a retired conversation neither sends nor deletes`() = runTest {
        val retired = ContactKey.retiredBroadcast("a1b2c3d4").value

        viewModel.resendMessage(uuid = 42L, text = "Hello", contactKey = retired)
        advanceUntilIdle()

        verifySuspend(VerifyMode.not) { sendMessageUseCase.invoke(any(), any(), any()) }
        verifySuspend(VerifyMode.not) { packetRepository.replaceMessage(any(), any()) }
    }

    private companion object {
        const val LIVE_CONTACT = "0!12345678"
    }
}
