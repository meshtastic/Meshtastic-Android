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
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.data.repository.QuickChatActionRepository
import org.meshtastic.core.model.service.ServiceAction
import org.meshtastic.core.repository.CustomEmojiPrefs
import org.meshtastic.core.repository.HomoglyphPrefs
import org.meshtastic.core.repository.MeshServiceNotifications
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

        // Mock other dependencies with proper type hints
        radioConfigRepository =
            mockk(relaxed = true) {
                every { channelSetFlow } returns MutableStateFlow<ChannelSet>(mockk(relaxed = true))
                every { localConfigFlow } returns MutableStateFlow<LocalConfig>(mockk(relaxed = true))
                every { moduleConfigFlow } returns MutableStateFlow<LocalModuleConfig>(mockk(relaxed = true))
                every { deviceProfileFlow } returns MutableStateFlow<DeviceProfile>(mockk(relaxed = true))
            }
        quickChatActionRepository = mockk(relaxed = true)
        packetRepository = mockk(relaxed = true)
        serviceRepository = mockk(relaxed = true) { every { serviceAction } returns emptyFlow<ServiceAction>() }
        sendMessageUseCase = mockk(relaxed = true)
        customEmojiPrefs =
            mockk(relaxed = true) { every { customEmojiFrequency } returns MutableStateFlow<String?>(null) }
        homoglyphPrefs =
            mockk(relaxed = true) { every { homoglyphEncodingEnabled } returns MutableStateFlow<Boolean>(false) }
        uiPrefs = mockk(relaxed = true) { every { showQuickChat } returns MutableStateFlow<Boolean>(false) }
        meshServiceNotifications = mockk(relaxed = true)

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
                homoglyphEncodingPrefs = homoglyphPrefs,
                uiPrefs = uiPrefs,
                notificationManager = mockk(relaxed = true),
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
