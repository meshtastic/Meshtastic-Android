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
package org.meshtastic.core.domain.usecase.settings

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.meshtastic.core.domain.FakeRadioController
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.NodeRepository
import kotlin.time.Duration.Companion.days

class CleanNodeDatabaseUseCaseTest {

    private lateinit var nodeRepository: NodeRepository
    private lateinit var radioController: FakeRadioController
    private lateinit var useCase: CleanNodeDatabaseUseCase

    @Before
    fun setUp() {
        nodeRepository = mockk(relaxed = true)
        radioController = FakeRadioController()
        useCase = CleanNodeDatabaseUseCase(nodeRepository, radioController)
    }

    @Test
    fun `getNodesToClean filters nodes correctly`() = runTest {
        // Arrange
        val currentTime = 1000000L
        val olderThanTimestamp = currentTime - 30.days.inWholeSeconds

        val oldNode = Node(num = 1, lastHeard = (olderThanTimestamp - 1).toInt())
        val newNode = Node(num = 2, lastHeard = (currentTime - 1).toInt())
        val ignoredNode = Node(num = 3, lastHeard = (olderThanTimestamp - 1).toInt(), isIgnored = true)

        coEvery { nodeRepository.getNodesOlderThan(any()) } returns listOf(oldNode, ignoredNode)

        // Act
        val result = useCase.getNodesToClean(30f, false, currentTime)

        // Assert
        assertEquals(1, result.size)
        assertEquals(1, result[0].num)
    }

    @Test
    fun `cleanNodes calls repository and controller`() = runTest {
        // Act
        useCase.cleanNodes(listOf(1, 2))

        // Assert
        coVerify { nodeRepository.deleteNodes(listOf(1, 2)) }
        // Note: we can't easily verify removeByNodenum on FakeRadioController without adding tracking
    }
}
