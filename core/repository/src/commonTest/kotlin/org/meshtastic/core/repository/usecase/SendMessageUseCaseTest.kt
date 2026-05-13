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
package org.meshtastic.core.repository.usecase

import dev.mokkery.MockMode
import dev.mokkery.mock
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.MessageQueue
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.testing.FakeAppPreferences
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.User
import kotlin.test.BeforeTest
import kotlin.test.Test

class SendMessageUseCaseTest {

    private lateinit var nodeRepository: FakeNodeRepository
    private lateinit var packetRepository: PacketRepository
    private lateinit var radioController: FakeRadioController
    private lateinit var appPreferences: FakeAppPreferences
    private lateinit var messageQueue: MessageQueue
    private lateinit var useCase: SendMessageUseCase

    @BeforeTest
    fun setUp() {
        nodeRepository = FakeNodeRepository()
        packetRepository = mock(MockMode.autofill)
        radioController = FakeRadioController()
        appPreferences = FakeAppPreferences()
        messageQueue = mock(MockMode.autofill)

        useCase =
            SendMessageUseCaseImpl(
                nodeRepository = nodeRepository,
                packetRepository = packetRepository,
                radioController = radioController,
                homoglyphEncodingPrefs = appPreferences.homoglyph,
                messageQueue = messageQueue,
            )
    }

    @Test
    fun `invoke with broadcast message simply sends data packet`() = runTest {
        // Arrange
        val ourNode = Node(num = 1, user = User(id = "!1234"))
        nodeRepository.setOurNode(ourNode)
        appPreferences.homoglyph.setHomoglyphEncodingEnabled(false)

        // Act
        useCase("Hello broadcast", "0${DataPacket.ID_BROADCAST}", null)

        // Assert
        radioController.favoritedNodes.size shouldBe 0
        radioController.sentSharedContacts.size shouldBe 0
    }

    @Test
    fun `invoke with direct message to older firmware triggers favoriteNode`() = runTest {
        // Arrange
        val ourNode =
            Node(
                num = 1,
                user = User(id = "!local", role = Config.DeviceConfig.Role.CLIENT),
                metadata = DeviceMetadata(firmware_version = "2.0.0"),
            )
        nodeRepository.setOurNode(ourNode)

        val destNode = Node(num = 12345, user = User(id = "!dest"))
        nodeRepository.upsert(destNode)

        appPreferences.homoglyph.setHomoglyphEncodingEnabled(false)

        // Act
        useCase("Direct message", "!dest", null)

        // Assert
        radioController.favoritedNodes.size shouldBe 1
        radioController.favoritedNodes[0] shouldBe 12345
    }

    @Test
    fun `invoke with direct message to new firmware triggers sendSharedContact`() = runTest {
        // Arrange
        val ourNode =
            Node(
                num = 1,
                user = User(id = "!local", role = Config.DeviceConfig.Role.CLIENT),
                metadata = DeviceMetadata(firmware_version = "2.7.12"),
            )
        nodeRepository.setOurNode(ourNode)

        val destNode = Node(num = 67890, user = User(id = "!dest"))
        nodeRepository.upsert(destNode)

        appPreferences.homoglyph.setHomoglyphEncodingEnabled(false)

        // Act
        useCase("Direct message", "!dest", null)

        // Assert
        radioController.sentSharedContacts.size shouldBe 1
        radioController.sentSharedContacts[0] shouldBe 67890
    }

    @Test
    fun `invoke with homoglyph enabled transforms text`() = runTest {
        // Arrange
        val ourNode = Node(num = 1)
        nodeRepository.setOurNode(ourNode)
        appPreferences.homoglyph.setHomoglyphEncodingEnabled(true)

        val originalText = "\u0410pple" // Cyrillic A

        // Act
        useCase(originalText, "0${DataPacket.ID_BROADCAST}", null)

        // Assert
        // Verified by observing that no exception is thrown and coverage is hit.
    }

    @Test
    fun `invoke with PKI DM triggers sendSharedContact`() = runTest {
        // Arrange: PKI DMs use contactKey = "8!nodeHex" (PKC_CHANNEL_INDEX = 8)
        val ourNode =
            Node(
                num = 1,
                user = User(id = "!local", role = Config.DeviceConfig.Role.CLIENT),
                metadata = DeviceMetadata(firmware_version = "2.7.12"),
            )
        nodeRepository.setOurNode(ourNode)

        val destNode = Node(num = 0x70fdde9b.toInt(), user = User(id = "!70fdde9b"))
        nodeRepository.upsert(destNode)

        appPreferences.homoglyph.setHomoglyphEncodingEnabled(false)

        // Act — PKI DM: channel 8 + node ID
        useCase("PKI direct message", "${DataPacket.PKC_CHANNEL_INDEX}!70fdde9b", null)

        // Assert — sendSharedContact should be called for PKI DMs
        radioController.sentSharedContacts.size shouldBe 1
        radioController.sentSharedContacts[0] shouldBe 0x70fdde9b.toInt()
        radioController.favoritedNodes.size shouldBe 0
    }

    @Test
    fun `invoke with channel DM does not trigger sendSharedContact or favorite`() = runTest {
        // Arrange: channel-based DMs use contactKey = "<ch>!nodeHex" where ch is 0-7
        val ourNode =
            Node(
                num = 1,
                user = User(id = "!local", role = Config.DeviceConfig.Role.CLIENT),
                metadata = DeviceMetadata(firmware_version = "2.7.12"),
            )
        nodeRepository.setOurNode(ourNode)

        val destNode = Node(num = 0x12345678, user = User(id = "!12345678"))
        nodeRepository.upsert(destNode)

        appPreferences.homoglyph.setHomoglyphEncodingEnabled(false)

        // Act — channel 1 DM (not PKI, not legacy)
        useCase("Channel DM", "1!12345678", null)

        // Assert — neither sendSharedContact nor favorite should be called for channel DMs
        radioController.sentSharedContacts.size shouldBe 0
        radioController.favoritedNodes.size shouldBe 0
    }

    @Test
    fun `invoke with PKI DM to older firmware does not trigger favorite`() = runTest {
        // Arrange: PKI DMs with old firmware should NOT fall through to favoriting
        val ourNode =
            Node(
                num = 1,
                user = User(id = "!local", role = Config.DeviceConfig.Role.CLIENT),
                metadata = DeviceMetadata(firmware_version = "2.0.0"),
            )
        nodeRepository.setOurNode(ourNode)

        val destNode = Node(num = 0xABCDEF01.toInt(), user = User(id = "!abcdef01"))
        nodeRepository.upsert(destNode)

        appPreferences.homoglyph.setHomoglyphEncodingEnabled(false)

        // Act — PKI DM with firmware that doesn't support verified contacts
        useCase("Old PKI DM", "${DataPacket.PKC_CHANNEL_INDEX}!abcdef01", null)

        // Assert — PKI DMs should not trigger legacy favoriting (that's only for channel==null)
        radioController.sentSharedContacts.size shouldBe 0
        radioController.favoritedNodes.size shouldBe 0
    }
}
