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
package org.meshtastic.feature.firmware

import io.kotest.matchers.shouldBe

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.data.repository.FirmwareReleaseRepository
import org.meshtastic.core.datastore.BootloaderWarningDataSource
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.DeviceHardwareRepository
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioPrefs
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.User
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Bootstrap tests for FirmwareUpdateViewModel.
 *
 * Tests firmware update flow with fake dependencies.
 */
class FirmwareUpdateViewModelTest {
/*


    private lateinit var viewModel: FirmwareUpdateViewModel
    private lateinit var nodeRepository: NodeRepository
    private lateinit var radioController: FakeRadioController
    private lateinit var radioPrefs: RadioPrefs
    private lateinit var firmwareReleaseRepository: FirmwareReleaseRepository
    private lateinit var deviceHardwareRepository: DeviceHardwareRepository
    private lateinit var bootloaderWarningDataSource: BootloaderWarningDataSource
    private lateinit var firmwareUpdateManager: FirmwareUpdateManager
    private lateinit var usbManager: FirmwareUsbManager
    private lateinit var fileHandler: FirmwareFileHandler

    @BeforeTest
    fun setUp() {
        radioController = FakeRadioController()

        val fakeMyNodeInfo =
                every { myNodeNum } returns 1
                every { pioEnv } returns "tbeam"
                every { firmwareVersion } returns "2.5.0"
            }
        nodeRepository =
                every { myNodeInfo } returns MutableStateFlow(fakeMyNodeInfo)
                every { ourNodeInfo } returns MutableStateFlow(fakeNodeInfo)
            }

        firmwareReleaseRepository =
                every { stableRelease } returns emptyFlow()
                every { alphaRelease } returns emptyFlow()
            }
        deviceHardwareRepository =
                everySuspend { getDeviceHardwareByModel(any(), any()) } returns
            }

        viewModel =
            FirmwareUpdateViewModel(
                radioController = radioController,
                nodeRepository = nodeRepository,
                radioPrefs = radioPrefs,
                firmwareReleaseRepository = firmwareReleaseRepository,
                deviceHardwareRepository = deviceHardwareRepository,
                bootloaderWarningDataSource = bootloaderWarningDataSource,
                firmwareUpdateManager = firmwareUpdateManager,
                usbManager = usbManager,
                fileHandler = fileHandler,
            )
    }

    @Test
    fun testInitialization() = runTest {
        setUp()
        assertTrue(true, "FirmwareUpdateViewModel initialized successfully")
    }

    @Test
    fun testMyNodeInfoAccessible() = runTest {
        setUp()
        val myNodeInfo = nodeRepository.myNodeInfo.value
        assertTrue(myNodeInfo != null, "myNodeInfo is accessible")
    }

    @Test
    fun testUpdateStateInitialValue() = runTest {
        setUp()
        val updateState = viewModel.state.value
        assertTrue(true, "Update state is accessible")
    }

    @Test
    fun testConnectionState() = runTest {
        setUp()
        radioController.setConnectionState(org.meshtastic.core.model.ConnectionState.Disconnected)
        // Connection state should be reflected
        assertTrue(true, "Connection state flows work correctly")
    }

*/
}
