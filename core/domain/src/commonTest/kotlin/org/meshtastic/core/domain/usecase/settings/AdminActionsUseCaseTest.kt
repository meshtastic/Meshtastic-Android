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
import org.meshtastic.proto.Routing
import org.meshtastic.sdk.AdminResult
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdminActionsUseCaseTest {

    private lateinit var radioController: FakeRadioController
    private lateinit var nodeRepository: FakeNodeRepository
    private lateinit var useCase: AdminActionsUseCase

    @BeforeTest
    fun setUp() {
        radioController = FakeRadioController()
        nodeRepository = FakeNodeRepository()
        useCase = AdminActionsUseCase(radioController, nodeRepository)
    }

    @Test
    fun `reboot reports the radio answer`() = runTest {
        assertEquals(AdminResult.Success(Unit), useCase.reboot(1234))
    }

    @Test
    fun `shutdown reports the radio answer`() = runTest {
        radioController.writeResult = AdminResult.Timeout
        assertEquals(AdminResult.Timeout, useCase.shutdown(1234))
    }

    @Test
    fun `factoryReset local node clears local NodeDB`() = runTest {
        nodeRepository.upsert(Node(num = 1))
        useCase.factoryReset(1234, isLocal = true)
        assertTrue(nodeRepository.nodeDBbyNum.value.isEmpty())
    }

    @Test
    fun `a factory reset the radio does not apply leaves the local NodeDB`() = runTest {
        nodeRepository.upsert(Node(num = 1))
        radioController.writeResult = AdminResult.Failed(Routing.Error.NO_ROUTE)
        useCase.factoryReset(1234, isLocal = true)
        assertEquals(1, nodeRepository.nodeDBbyNum.value.size)
    }

    @Test
    fun `nodedbReset local node clears local NodeDB with preserveFavorites`() = runTest {
        nodeRepository.setNodes(listOf(Node(num = 1, isFavorite = true), Node(num = 2, isFavorite = false)))
        useCase.nodedbReset(1234, preserveFavorites = true, isLocal = true)
        assertEquals(1, nodeRepository.nodeDBbyNum.value.size)
        assertTrue(nodeRepository.nodeDBbyNum.value.containsKey(1))
    }
}
