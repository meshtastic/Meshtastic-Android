/*
 * Copyright (c) 2025 Meshtastic LLC
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

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.model.RadioController

class AdminActionsUseCaseTest {

    private lateinit var radioController: RadioController
    private lateinit var nodeRepository: NodeRepository
    private lateinit var useCase: AdminActionsUseCase

    @Before
    fun setUp() {
        radioController = mockk(relaxed = true)
        nodeRepository = mockk(relaxed = true)
        useCase = AdminActionsUseCase(radioController, nodeRepository)
        every { radioController.getPacketId() } returns 42
    }

    @Test
    fun `reboot calls radioController and returns packetId`() = runTest {
        val result = useCase.reboot(123)
        coVerify { radioController.reboot(123, 42) }
        assertEquals(42, result)
    }

    @Test
    fun `shutdown calls radioController and returns packetId`() = runTest {
        val result = useCase.shutdown(123)
        coVerify { radioController.shutdown(123, 42) }
        assertEquals(42, result)
    }

    @Test
    fun `factoryReset calls radioController and clears DB if local`() = runTest {
        val result = useCase.factoryReset(123, isLocal = true)
        coVerify { radioController.factoryReset(123, 42) }
        coVerify { nodeRepository.clearNodeDB() }
        assertEquals(42, result)
    }

    @Test
    fun `nodedbReset calls radioController and clears DB if local`() = runTest {
        val result = useCase.nodedbReset(123, preserveFavorites = true, isLocal = true)
        coVerify { radioController.nodedbReset(123, 42, true) }
        coVerify { nodeRepository.clearNodeDB(true) }
        assertEquals(42, result)
    }
}
