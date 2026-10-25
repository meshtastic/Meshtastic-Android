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

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.ByteString.Companion.encodeUtf8
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.ConnectionStateProvider
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.ui.util.SnackbarManager
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.User
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ContactsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: ContactsViewModel
    private val nodeRepository = FakeNodeRepository()
    private val packetRepository: PacketRepository = mock(MockMode.autofill)
    private val radioConfigRepository: RadioConfigRepository = mock(MockMode.autofill)
    private val connectionStateProvider: ConnectionStateProvider = mock(MockMode.autofill)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { connectionStateProvider.connectionState } returns MutableStateFlow(ConnectionState.Disconnected)
        every { packetRepository.getUnreadCountTotal() } returns MutableStateFlow(0)
        every { radioConfigRepository.channelSetFlow } returns MutableStateFlow(ChannelSet.Builder().build())

        viewModel =
            ContactsViewModel(
                savedStateHandle = SavedStateHandle(),
                nodeRepository = nodeRepository,
                packetRepository = packetRepository,
                snackbarManager = SnackbarManager(),
                radioConfigRepository = radioConfigRepository,
                connectionStateProvider = connectionStateProvider,
            )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialization() {
        assertNotNull(viewModel)
    }

    @Test
    fun `unreadCountTotal reflects updates from repository`() = runTest(testDispatcher) {
        val countFlow = MutableStateFlow(0)
        every { packetRepository.getUnreadCountTotal() } returns countFlow

        // Re-init VM
        viewModel =
            ContactsViewModel(
                SavedStateHandle(),
                nodeRepository,
                packetRepository,
                SnackbarManager(),
                radioConfigRepository,
                connectionStateProvider,
            )

        viewModel.unreadCountTotal.test {
            assertEquals(0, awaitItem())
            countFlow.value = 5
            assertEquals(5, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggleSectionCollapse flips section membership`() = runTest(testDispatcher) {
        viewModel.collapsedSections.test {
            assertEquals(emptySet(), awaitItem())

            viewModel.toggleSectionCollapse(ContactSection.CHANNELS.key)
            assertTrue(ContactSection.CHANNELS.key in awaitItem())

            viewModel.toggleSectionCollapse(ContactSection.CHANNELS.key)
            assertFalse(ContactSection.CHANNELS.key in awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `collapsedSections restores persisted state from SavedStateHandle`() = runTest(testDispatcher) {
        viewModel =
            ContactsViewModel(
                SavedStateHandle(mapOf("collapsed_contact_sections" to ContactSection.DIRECT_MESSAGES.key)),
                nodeRepository,
                packetRepository,
                SnackbarManager(),
                radioConfigRepository,
                connectionStateProvider,
            )

        viewModel.collapsedSections.test {
            assertEquals(setOf(ContactSection.DIRECT_MESSAGES.key), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `contact name follows a display name change`() = runTest(testDispatcher) {
        val peer =
            Node(
                num = 2,
                user =
                User.Builder()
                    .also { wb ->
                        wb.id = "!00000002"
                        wb.long_name = "Broadcast"
                        wb.short_name = "BC"
                    }
                    .build(),
            )
        nodeRepository.setNodes(listOf(peer))
        nodeRepository.setMyNodeInfo(
            MyNodeInfo(
                myNodeNum = 1,
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
            ),
        )
        nodeRepository.setMyId("!00000001")
        val dm = DataPacket(to = "!00000001", from = "!00000002", bytes = "hi".encodeUtf8(), dataType = 1)
        every { packetRepository.getContacts() } returns MutableStateFlow(mapOf("0!00000002" to dm))
        every { packetRepository.getContactSettings() } returns MutableStateFlow(emptyMap())
        viewModel =
            ContactsViewModel(
                SavedStateHandle(),
                nodeRepository,
                packetRepository,
                SnackbarManager(),
                radioConfigRepository,
                connectionStateProvider,
            )

        viewModel.contactList.test {
            var latest = awaitItem()
            while (latest.none { it.contactKey == "0!00000002" }) latest = awaitItem()
            assertEquals("Broadcast", latest.single { it.contactKey == "0!00000002" }.longName)

            nodeRepository.setNodeDisplayName(2, "Nickname")

            var renamed = awaitItem()
            while (renamed.single { it.contactKey == "0!00000002" }.longName != "Nickname") renamed = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
