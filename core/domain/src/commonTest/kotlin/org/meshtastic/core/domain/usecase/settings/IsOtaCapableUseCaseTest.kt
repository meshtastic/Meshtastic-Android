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

import app.cash.turbine.test
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.DeviceHardwareRepository
import org.meshtastic.core.repository.RadioPrefs
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.proto.User
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IsOtaCapableUseCaseTest {

    private lateinit var nodeRepository: FakeNodeRepository
    private lateinit var radioController: FakeRadioController
    private lateinit var radioPrefs: RadioPrefs
    private lateinit var deviceHardwareRepository: DeviceHardwareRepository
    private lateinit var useCase: IsOtaCapableUseCase

    @BeforeTest
    fun setUp() {
        nodeRepository = FakeNodeRepository()
        radioController = FakeRadioController()
        radioPrefs = mock(MockMode.autofill)
        deviceHardwareRepository = mock(MockMode.autofill)
        
        useCase = IsOtaCapableUseCaseImpl(
            nodeRepository = nodeRepository,
            radioController = radioController,
            radioPrefs = radioPrefs,
            deviceHardwareRepository = deviceHardwareRepository,
        )
    }

    @Test
    fun `invoke returns true when ota capable`() = runTest {
        // Arrange
        val node = Node(num = 123, user = User(hw_model = org.meshtastic.proto.HardwareModel.TBEAM.value.toUInt()))
        nodeRepository.setOurNodeInfo(node)
        radioController.setConnectionState(ConnectionState.Connected)
        
        every { radioPrefs.devAddr } returns MutableStateFlow("x1234") // x prefix means BLE

        useCase().test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `invoke returns false when ota not capable`() = runTest {
        // Arrange
        val node = Node(num = 123, user = User(hw_model = org.meshtastic.proto.HardwareModel.TBEAM.value.toUInt()))
        nodeRepository.setOurNodeInfo(node)
        radioController.setConnectionState(ConnectionState.Connected)
        
        every { radioPrefs.devAddr } returns MutableStateFlow("w1234") // not x, s, or m

        useCase().test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
