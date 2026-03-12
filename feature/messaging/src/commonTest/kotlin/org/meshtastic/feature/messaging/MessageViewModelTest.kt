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
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.data.repository.QuickChatActionRepository
import org.meshtastic.core.repository.CustomEmojiPrefs
import org.meshtastic.core.repository.HomoglyphPrefs
import org.meshtastic.core.repository.MeshServiceNotifications
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.core.repository.usecase.SendMessageUseCase
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.TestDataFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Example test for MessageViewModel demonstrating the use of core:testing utilities.
 *
 * This test is intentionally minimal to serve as a bootstrap template. Add more comprehensive tests as the feature
 * evolves.
 */
class MessageViewModelTest {

    private lateinit var viewModel: MessageViewModel
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var nodeRepository: FakeNodeRepository
    private lateinit var radioConfigRepository: RadioConfigRepository
    private lateinit var quickChatActionRepository: QuickChatActionRepository
    private lateinit var packetRepository: org.meshtastic.core.repository.PacketRepository
    private lateinit var serviceRepository: ServiceRepository
    private lateinit var sendMessageUseCase: SendMessageUseCase
    private lateinit var customEmojiPrefs: CustomEmojiPrefs
    private lateinit var homoglyphPrefs: HomoglyphPrefs
    private lateinit var uiPrefs: UiPrefs
    private lateinit var meshServiceNotifications: MeshServiceNotifications

    private fun setUp() {
        // Create saved state with test contact ID
        savedStateHandle = SavedStateHandle(mapOf("contactId" to 1L))

        // Use real fake implementation
        nodeRepository = FakeNodeRepository()

        // Mock other dependencies
        radioConfigRepository =
            mockk(relaxed = true) { every { loRaConfigFlow } returns MutableStateFlow(mockk(relaxed = true)) }
        quickChatActionRepository = mockk(relaxed = true)
        packetRepository = mockk(relaxed = true)
        serviceRepository = mockk(relaxed = true) { coEvery { getServiceActions() } returns emptyFlow() }
        sendMessageUseCase = mockk(relaxed = true)
        customEmojiPrefs = mockk(relaxed = true) { every { customEmojiFlow } returns MutableStateFlow(null) }
        homoglyphPrefs = mockk(relaxed = true) { every { homoglyphDecodingFlow } returns MutableStateFlow(false) }
        uiPrefs = mockk(relaxed = true) { every { logMessagesFlow } returns MutableStateFlow(false) }
        meshServiceNotifications =
            mockk(relaxed = true) { every { contactSettingsNotification } returns MutableStateFlow(null) }

        // Create ViewModel with mocked dependencies
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
                homoglyphPrefs = homoglyphPrefs,
                uiPrefs = uiPrefs,
                meshServiceNotifications = meshServiceNotifications,
            )
    }

    @Test
    fun testInitialization() = runTest {
        setUp()
        // ViewModel should initialize without errors
        assertTrue(true, "ViewModel created successfully")
    }

    @Test
    fun testNodeRepositoryIntegration() = runTest {
        setUp()

        // Add test nodes to the fake repository
        val testNodes = TestDataFactory.createTestNodes(3)
        nodeRepository.setNodes(testNodes)

        // Verify nodes are accessible
        assertEquals(3, nodeRepository.nodeDBbyNum.value.size)
        assertEquals("Test User 0", nodeRepository.nodeDBbyNum.value[1]?.user?.long_name)
    }
}
