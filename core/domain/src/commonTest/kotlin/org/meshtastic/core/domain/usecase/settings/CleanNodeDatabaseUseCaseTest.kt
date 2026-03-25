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
package org.meshtastic.core.domain.usecase.settings

import kotlinx.coroutines.test.runTest
import org.meshtastic.core.model.Node
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.proto.User
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class CleanNodeDatabaseUseCaseTest {

    private lateinit var nodeRepository: FakeNodeRepository
    private lateinit var radioController: FakeRadioController
    private lateinit var useCase: CleanNodeDatabaseUseCase

    @BeforeTest
    fun setUp() {
        nodeRepository = FakeNodeRepository()
        radioController = FakeRadioController()
        useCase = CleanNodeDatabaseUseCase(nodeRepository, radioController)
    }

    @Test
    fun `getNodesToClean returns nodes older than threshold`() = runTest {
        val now = 1000000000L
        val olderThan = now - 30.days.inWholeSeconds
        val node1 = Node(num = 1, lastHeard = (olderThan - 100).toInt())
        val node2 = Node(num = 2, lastHeard = (olderThan + 100).toInt())
        nodeRepository.setNodes(listOf(node1, node2))

        val result = useCase.getNodesToClean(30f, false, now)

        assertEquals(1, result.size)
        assertEquals(1, result[0].num)
    }

    @Test
    fun `getNodesToClean filters out favorites and ignored`() = runTest {
        val now = 1000000000L
        val olderThan = now - 30.days.inWholeSeconds
        val node1 = Node(num = 1, lastHeard = (olderThan - 100).toInt(), isFavorite = true)
        val node2 = Node(num = 2, lastHeard = (olderThan - 100).toInt(), isIgnored = true)
        nodeRepository.setNodes(listOf(node1, node2))

        val result = useCase.getNodesToClean(30f, false, now)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `cleanNodes deletes from repo and controller`() = runTest {
        nodeRepository.setNodes(listOf(Node(num = 1), Node(num = 2)))
        useCase.cleanNodes(listOf(1))

        assertEquals(1, nodeRepository.nodeDBbyNum.value.size)
        assertTrue(nodeRepository.nodeDBbyNum.value.containsKey(2))
    }
}
