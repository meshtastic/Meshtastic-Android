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

import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.meshtastic.core.data.repository.DeviceHardwareRepository
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.prefs.radio.RadioPrefs

class IsOtaCapableUseCaseTest {

    private lateinit var nodeRepository: NodeRepository
    private lateinit var radioController: RadioController
    private lateinit var radioPrefs: RadioPrefs
    private lateinit var deviceHardwareRepository: DeviceHardwareRepository
    private lateinit var useCase: IsOtaCapableUseCase

    private val ourNodeInfoFlow = MutableStateFlow<Node?>(null)
    private val connectionStateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)

    @Before
    fun setUp() {
        nodeRepository = mockk {
            every { ourNodeInfo } returns ourNodeInfoFlow
        }
        radioController = mockk {
            every { connectionState } returns connectionStateFlow
        }
        radioPrefs = mockk(relaxed = true)
        deviceHardwareRepository = mockk(relaxed = true)
        
        useCase = IsOtaCapableUseCase(
            nodeRepository,
            radioController,
            radioPrefs,
            deviceHardwareRepository
        )
    }

    @Test
    fun `returns false when node is null`() = runTest {
        ourNodeInfoFlow.value = null
        connectionStateFlow.value = ConnectionState.Connected
        
        useCase().test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `returns false when not connected`() = runTest {
        val node = mockk<Node>(relaxed = true)
        ourNodeInfoFlow.value = node
        connectionStateFlow.value = ConnectionState.Disconnected
        
        useCase().test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
