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
package org.meshtastic.feature.messaging.domain.usecase

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.Capabilities
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.prefs.homoglyph.HomoglyphPrefs
import org.meshtastic.core.service.ServiceAction
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceMetadata

class SendMessageUseCaseTest {

    private lateinit var nodeRepository: NodeRepository
    private lateinit var serviceRepository: ServiceRepository
    private lateinit var homoglyphEncodingPrefs: HomoglyphPrefs
    private lateinit var useCase: SendMessageUseCase

    @Before
    fun setUp() {
        nodeRepository = mockk(relaxed = true)
        serviceRepository = mockk(relaxed = true)
        homoglyphEncodingPrefs = mockk(relaxed = true)

        useCase =
            SendMessageUseCase(
                nodeRepository = nodeRepository,
                serviceRepository = serviceRepository,
                homoglyphEncodingPrefs = homoglyphEncodingPrefs,
            )

        mockkConstructor(Capabilities::class)
    }

    @Test
    fun `invoke with broadcast message simply sends data packet`() = runTest {
        // Arrange
        val ourNode = mockk<Node>(relaxed = true)
        every { ourNode.user.id } returns "!1234"
        every { nodeRepository.ourNodeInfo } returns MutableStateFlow(ourNode)
        every { homoglyphEncodingPrefs.homoglyphEncodingEnabled } returns false

        // Act
        useCase("Hello broadcast", "0${DataPacket.ID_BROADCAST}", null)

        // Assert
        coVerify(exactly = 0) { serviceRepository.onServiceAction(any()) }
        coVerify(exactly = 1) { serviceRepository.meshService?.send(any()) }
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
        every { nodeRepository.getNode("!dest") } returns destNode

        every { homoglyphEncodingPrefs.homoglyphEncodingEnabled } returns false

        every { anyConstructed<Capabilities>().canSendVerifiedContacts } returns false

        // Act
        useCase("Direct message", "!dest", null)

        // Assert
        coVerify(exactly = 1) { serviceRepository.onServiceAction(match { it is ServiceAction.Favorite }) }
        coVerify(exactly = 1) { serviceRepository.meshService?.send(any()) }
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
        every { nodeRepository.getNode("!dest") } returns destNode

        every { homoglyphEncodingPrefs.homoglyphEncodingEnabled } returns false

        every { anyConstructed<Capabilities>().canSendVerifiedContacts } returns true

        // Act
        useCase("Direct message", "!dest", null)

        // Assert
        coVerify(exactly = 1) { serviceRepository.onServiceAction(match { it is ServiceAction.SendContact }) }
        coVerify(exactly = 1) { serviceRepository.meshService?.send(any()) }
    }

    @Test
    fun `invoke with homoglyph enabled transforms text`() = runTest {
        // Arrange
        val ourNode = mockk<Node>(relaxed = true)
        every { nodeRepository.ourNodeInfo } returns MutableStateFlow(ourNode)
        every { homoglyphEncodingPrefs.homoglyphEncodingEnabled } returns true

        // Let's use a cyrillic character 'A' (U+0410) that will be mapped to Latin 'A'
        val originalText = "\u0410pple"

        // Act
        useCase(originalText, "0${DataPacket.ID_BROADCAST}", null)

        // Assert
        // We verify that send was called with the transformed text (Latin 'A'pple)
        coVerify(exactly = 1) { serviceRepository.meshService?.send(match { it.text?.contains("Apple") == true }) }
    }
}
