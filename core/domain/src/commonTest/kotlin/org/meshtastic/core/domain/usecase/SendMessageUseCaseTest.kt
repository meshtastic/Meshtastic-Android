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
package org.meshtastic.core.domain.usecase

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.domain.FakeRadioController
import org.meshtastic.core.model.Capabilities
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.HomoglyphPrefs
import org.meshtastic.core.repository.MessageQueue
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.usecase.SendMessageUseCase
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceMetadata
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SendMessageUseCaseTest {

    private lateinit var nodeRepository: NodeRepository
    private lateinit var packetRepository: PacketRepository
    private lateinit var radioController: FakeRadioController
    private lateinit var homoglyphEncodingPrefs: HomoglyphPrefs
    private lateinit var messageQueue: MessageQueue
    private lateinit var useCase: SendMessageUseCase

    @BeforeTest
    fun setUp() {
        nodeRepository = mockk(relaxed = true)
        packetRepository = mockk(relaxed = true)
        radioController = FakeRadioController()
        homoglyphEncodingPrefs = mockk(relaxed = true)
        messageQueue = mockk(relaxed = true)

        useCase =
            SendMessageUseCase(
                nodeRepository = nodeRepository,
                packetRepository = packetRepository,
                radioController = radioController,
                homoglyphEncodingPrefs = homoglyphEncodingPrefs,
                messageQueue = messageQueue,
            )

        mockkConstructor(Capabilities::class)
    }

    @AfterTest
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `invoke with broadcast message simply sends data packet`() = runTest {
        // Arrange
        val ourNode = mockk<Node>(relaxed = true)
        every { ourNode.user.id } returns "!1234"
        every { nodeRepository.ourNodeInfo } returns MutableStateFlow(ourNode)
        every { homoglyphEncodingPrefs.homoglyphEncodingEnabled.value } returns false

        // Act
        useCase("Hello broadcast", "0${DataPacket.ID_BROADCAST}", null)

        // Assert
        assertEquals(0, radioController.favoritedNodes.size)
        assertEquals(0, radioController.sentSharedContacts.size)

        coVerify { packetRepository.savePacket(any(), any(), any(), any()) }
        coVerify { messageQueue.enqueue(any()) }
    }

    @Test
    fun `invoke with direct message to older firmware triggers favoriteNode`() = runTest {
        // Arrange
        val ourNode = mockk<Node>(relaxed = true)
        val metadata = mockk<DeviceMetadata>(relaxed = true)
        every { ourNode.user.id } returns "!local"
        every { ourNode.user.role } returns Config.DeviceConfig.Role.CLIENT
        every { ourNode.metadata } returns metadata
        every { metadata.firmware_version } returns "2.0.0" // Older firmware
        every { nodeRepository.ourNodeInfo } returns MutableStateFlow(ourNode)

        val destNode = mockk<Node>(relaxed = true)
        every { destNode.isFavorite } returns false
        every { destNode.num } returns 12345
        every { nodeRepository.getNode("!dest") } returns destNode

        every { homoglyphEncodingPrefs.homoglyphEncodingEnabled.value } returns false
        every { anyConstructed<Capabilities>().canSendVerifiedContacts } returns false

        // Act
        useCase("Direct message", "!dest", null)

        // Assert
        assertEquals(1, radioController.favoritedNodes.size)
        assertEquals(12345, radioController.favoritedNodes[0])

        coVerify { packetRepository.savePacket(any(), any(), any(), any()) }
        coVerify { messageQueue.enqueue(any()) }
    }

    @Test
    fun `invoke with direct message to new firmware triggers sendSharedContact`() = runTest {
        // Arrange
        val ourNode = mockk<Node>(relaxed = true)
        val metadata = mockk<DeviceMetadata>(relaxed = true)
        every { ourNode.user.id } returns "!local"
        every { ourNode.user.role } returns Config.DeviceConfig.Role.CLIENT
        every { ourNode.metadata } returns metadata
        every { metadata.firmware_version } returns "2.7.12" // Newer firmware
        every { nodeRepository.ourNodeInfo } returns MutableStateFlow(ourNode)

        val destNode = mockk<Node>(relaxed = true)
        every { destNode.num } returns 67890
        every { nodeRepository.getNode("!dest") } returns destNode

        every { homoglyphEncodingPrefs.homoglyphEncodingEnabled.value } returns false
        every { anyConstructed<Capabilities>().canSendVerifiedContacts } returns true

        // Act
        useCase("Direct message", "!dest", null)

        // Assert
        assertEquals(1, radioController.sentSharedContacts.size)
        assertEquals(67890, radioController.sentSharedContacts[0])

        coVerify { packetRepository.savePacket(any(), any(), any(), any()) }
        coVerify { messageQueue.enqueue(any()) }
    }

    @Test
    fun `invoke with homoglyph enabled transforms text`() = runTest {
        // Arrange
        val ourNode = mockk<Node>(relaxed = true)
        every { nodeRepository.ourNodeInfo } returns MutableStateFlow(ourNode)
        every { homoglyphEncodingPrefs.homoglyphEncodingEnabled.value } returns true

        val originalText = "\u0410pple" // Cyrillic A

        // Act
        useCase(originalText, "0${DataPacket.ID_BROADCAST}", null)

        // Assert
        val packetSlot = slot<DataPacket>()
        coVerify { packetRepository.savePacket(any(), any(), capture(packetSlot), any()) }
        assertTrue(packetSlot.captured.text?.contains("Apple") == true)
        coVerify { messageQueue.enqueue(any()) }
    }
}
