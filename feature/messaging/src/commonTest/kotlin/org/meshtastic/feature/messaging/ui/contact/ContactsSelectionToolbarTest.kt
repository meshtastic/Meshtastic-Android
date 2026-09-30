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
package org.meshtastic.feature.messaging.ui.contact

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.SavedStateHandle
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.ContactKey
import org.meshtastic.core.model.ContactSettings
import org.meshtastic.core.repository.ConnectionStateProvider
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.TestDataFactory
import org.meshtastic.core.ui.util.SnackbarManager
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The toolbar derives pin and mute state from the conversations currently selected, so it has to follow the selection
 * as rows are long-pressed rather than the list as it stood when the selection was last empty.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class ContactsSelectionToolbarTest {

    private val channelKey = ContactKey.broadcast(0).value
    private val nodeRepository = FakeNodeRepository()
    private val packetRepository: PacketRepository = mock(MockMode.autofill)
    private val radioConfigRepository: RadioConfigRepository = mock(MockMode.autofill)
    private val connectionStateProvider: ConnectionStateProvider = mock(MockMode.autofill)
    private val pinWrites = MutableStateFlow(emptyList<Boolean>())

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        nodeRepository.setMyNodeInfo(TestDataFactory.createMyNodeInfo())
        every { connectionStateProvider.connectionState } returns MutableStateFlow(ConnectionState.Disconnected)
        every { packetRepository.getUnreadCountTotal() } returns MutableStateFlow(0)
        every { packetRepository.getContacts() } returns MutableStateFlow(emptyMap())
        every { radioConfigRepository.channelSetFlow } returns
            MutableStateFlow(
                ChannelSet.Builder().settings(listOf(ChannelSettings.Builder().name(CHANNEL_NAME).build())).build(),
            )
        everySuspend { packetRepository.setPinned(any(), true) } calls { pinWrites.update { it + true } }
        everySuspend { packetRepository.setPinned(any(), false) } calls { pinWrites.update { it + false } }
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun ComposeUiTest.showContacts(settings: ContactSettings) {
        every { packetRepository.getContactSettings() } returns MutableStateFlow(mapOf(channelKey to settings))
        val viewModel =
            ContactsViewModel(
                savedStateHandle = SavedStateHandle(),
                nodeRepository = nodeRepository,
                packetRepository = packetRepository,
                snackbarManager = SnackbarManager(),
                radioConfigRepository = radioConfigRepository,
                connectionStateProvider = connectionStateProvider,
            )
        setContent {
            MaterialTheme {
                ContactsScreen(
                    onNavigateToShare = {},
                    onHandleDeepLink = { _, _ -> },
                    viewModel = viewModel,
                    onClickNodeChip = {},
                    onNavigateToMessages = {},
                    onNavigateToNodeDetails = {},
                    onNavigateToFilterSettings = {},
                    scrollToTopEvents = null,
                    activeContactKey = null,
                )
            }
        }
        onNodeWithText(CHANNEL_NAME).performTouchInput { longClick() }
        waitForIdle()
    }

    @Test
    fun selectingAPinnedConversationOffersUnpin() = runComposeUiTest {
        showContacts(ContactSettings(contactKey = channelKey, pinned = true))

        onNodeWithContentDescription("Unpin").performClick()
        waitUntil { pinWrites.value.isNotEmpty() }
        assertEquals(listOf(false), pinWrites.value)
    }

    @Test
    fun selectingAnUnmutedConversationOffersMute() = runComposeUiTest {
        showContacts(ContactSettings(contactKey = channelKey))

        onNodeWithContentDescription("Mute selected").assertExists()
        onNodeWithContentDescription("Pin").assertExists()
    }

    private companion object {
        const val CHANNEL_NAME = "Ops"
    }
}
