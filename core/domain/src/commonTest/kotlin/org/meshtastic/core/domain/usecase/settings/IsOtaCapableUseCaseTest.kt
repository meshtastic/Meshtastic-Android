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

import app.cash.turbine.test
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.repository.DeviceHardwareRepository
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioPrefs
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.User
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IsOtaCapableUseCaseTest {

    private lateinit var nodeRepository: NodeRepository
    private lateinit var radioController: RadioController
    private lateinit var deviceHardwareRepository: DeviceHardwareRepository
    private lateinit var radioPrefs: RadioPrefs
    private lateinit var useCase: IsOtaCapableUseCase

    @BeforeTest
    fun setUp() {
        nodeRepository = mock(MockMode.autofill)
        radioController = mock(MockMode.autofill)
        deviceHardwareRepository = mock(MockMode.autofill)
        radioPrefs = mock(MockMode.autofill)

        useCase =
            IsOtaCapableUseCaseImpl(
                nodeRepository = nodeRepository,
                radioController = radioController,
                radioPrefs = radioPrefs,
                deviceHardwareRepository = deviceHardwareRepository,
            )
    }

    @Test
    fun `invoke returns true when ota capable`() = runTest {
        // Arrange
        val node = Node(num = 123, user = User(hw_model = HardwareModel.TBEAM))
        dev.mokkery.every { nodeRepository.ourNodeInfo } returns MutableStateFlow(node)
        dev.mokkery.every { radioController.connectionState } returns
            MutableStateFlow(org.meshtastic.core.model.ConnectionState.Connected)
        dev.mokkery.every { radioPrefs.devAddr } returns MutableStateFlow("x12345678") // x for BLE

        val hw =
            DeviceHardware(
                activelySupported = true,
                architecture = "esp32",
                hwModel = HardwareModel.TBEAM.value,
                requiresDfu = false,
            )
        everySuspend { deviceHardwareRepository.getDeviceHardwareByModel(any()) } returns Result.success(hw)

        useCase().test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `invoke returns false when ota not capable`() = runTest {
        // Arrange
        val node = Node(num = 123, user = User(hw_model = HardwareModel.TBEAM))
        dev.mokkery.every { nodeRepository.ourNodeInfo } returns MutableStateFlow(node)
        dev.mokkery.every { radioController.connectionState } returns
            MutableStateFlow(org.meshtastic.core.model.ConnectionState.Connected)
        dev.mokkery.every { radioPrefs.devAddr } returns MutableStateFlow("x12345678") // x for BLE

        val hw = DeviceHardware(activelySupported = false, hwModel = HardwareModel.TBEAM.value)
        everySuspend { deviceHardwareRepository.getDeviceHardwareByModel(any()) } returns Result.success(hw)

        useCase().test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `invoke returns true when requires Dfu and actively supported`() = runTest {
        // Arrange
        val node = Node(num = 123, user = User(hw_model = HardwareModel.TBEAM))
        dev.mokkery.every { nodeRepository.ourNodeInfo } returns MutableStateFlow(node)
        dev.mokkery.every { radioController.connectionState } returns
            MutableStateFlow(org.meshtastic.core.model.ConnectionState.Connected)
        dev.mokkery.every { radioPrefs.devAddr } returns MutableStateFlow("x12345678") // x for BLE

        val hw =
            DeviceHardware(
                activelySupported = true,
                architecture = "nrf52840",
                hwModel = HardwareModel.TBEAM.value,
                requiresDfu = true,
            )
        everySuspend { deviceHardwareRepository.getDeviceHardwareByModel(any()) } returns Result.success(hw)

        useCase().test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `invoke returns false when hardware model is UNSET`() = runTest {
        // Arrange
        val node = Node(num = 123, user = User(hw_model = HardwareModel.UNSET))
        dev.mokkery.every { nodeRepository.ourNodeInfo } returns MutableStateFlow(node)
        dev.mokkery.every { radioController.connectionState } returns
            MutableStateFlow(org.meshtastic.core.model.ConnectionState.Connected)
        dev.mokkery.every { radioPrefs.devAddr } returns MutableStateFlow("x12345678") // x for BLE

        everySuspend { deviceHardwareRepository.getDeviceHardwareByModel(any()) } returns Result.failure(Exception())

        useCase().test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `invoke returns false when disconnected`() = runTest {
        dev.mokkery.every { nodeRepository.ourNodeInfo } returns MutableStateFlow(Node(num = 123))
        dev.mokkery.every { radioController.connectionState } returns
            MutableStateFlow(org.meshtastic.core.model.ConnectionState.Disconnected)

        useCase().test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `invoke returns false when node is null`() = runTest {
        dev.mokkery.every { nodeRepository.ourNodeInfo } returns MutableStateFlow(null)
        dev.mokkery.every { radioController.connectionState } returns
            MutableStateFlow(org.meshtastic.core.model.ConnectionState.Connected)

        useCase().test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `invoke returns false when address is not ota capable`() = runTest {
        val node = Node(num = 123, user = User(hw_model = HardwareModel.TBEAM))
        dev.mokkery.every { nodeRepository.ourNodeInfo } returns MutableStateFlow(node)
        dev.mokkery.every { radioController.connectionState } returns
            MutableStateFlow(org.meshtastic.core.model.ConnectionState.Connected)
        dev.mokkery.every { radioPrefs.devAddr } returns MutableStateFlow("mqtt://example.com")

        useCase().test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
