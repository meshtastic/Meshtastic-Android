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
package org.meshtastic.core.ui.viewmodel

import app.cash.turbine.test
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.model.FirmwareRelease
import org.meshtastic.core.model.FirmwareUpdateDestination
import org.meshtastic.core.repository.ConnectionIdentity
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NodeRestartTracker
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.testing.FakeDeviceHardwareRepository
import org.meshtastic.core.testing.FakeFirmwareReleaseRepository
import org.meshtastic.core.testing.FakeMeshNotificationManager
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioPrefs
import org.meshtastic.core.testing.FakeServiceRepository
import org.meshtastic.core.testing.FakeUiPrefs
import org.meshtastic.core.testing.TestDataFactory
import org.meshtastic.proto.Config
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.User
import org.meshtastic.sdk.ConnectionState
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: ConnectionsViewModel
    private val radioConfigRepository: RadioConfigRepository = mock(MockMode.autofill)
    private val serviceRepository = FakeServiceRepository()
    private val nodeRestartTracker = NodeRestartTracker(CoroutineScope(SupervisorJob()))
    private val nodeRepository = FakeNodeRepository()
    private val connectionIdentity = MutableStateFlow<ConnectionIdentity?>(null)
    private val nodeManager: NodeManager = mock(MockMode.autofill)
    private val uiPrefs = FakeUiPrefs()
    private val deviceHardwareRepository = FakeDeviceHardwareRepository()
    private val firmwareReleaseRepository = FakeFirmwareReleaseRepository()
    private val radioPrefs = FakeRadioPrefs()
    private val serviceNotifications = FakeMeshNotificationManager()
    private val postedNotices
        get() = serviceNotifications.firmwareUpdateNotices

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { radioConfigRepository.localConfigFlow } returns MutableStateFlow(LocalConfig.Builder().build())
        every { nodeManager.connectionIdentity } returns connectionIdentity
        connectionIdentity.value = null
        uiPrefs.hasShownNotPairedWarning.value = false
        uiPrefs.firmwareUpdateNotificationKeys.value = emptySet()

        viewModel = newViewModel()
    }

    /** Rebuilt per test so a test can stub [radioConfigRepository] differently before construction. */
    private fun newViewModel() = ConnectionsViewModel(
        radioConfigRepository = radioConfigRepository,
        serviceRepository = serviceRepository,
        nodeRepository = nodeRepository,
        nodeManager = nodeManager,
        nodeRestartTracker = nodeRestartTracker,
        uiPrefs = uiPrefs,
        deviceHardwareRepository = deviceHardwareRepository,
        firmwareReleaseRepository = firmwareReleaseRepository,
        radioPrefs = radioPrefs,
        serviceNotifications = serviceNotifications,
    )

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialization() {
        assertNotNull(viewModel)
    }

    @Test
    fun `txDisabled follows the LoRa tx_enabled flag and stays false before config arrives`() = runTest {
        val configFlow = MutableStateFlow(LocalConfig.Builder().build())
        every { radioConfigRepository.localConfigFlow } returns configFlow
        val vm = newViewModel()

        vm.txDisabled.test {
            // No lora config yet: absence of the flag is not a disabled transmitter.
            assertEquals(false, awaitItem())

            configFlow.value =
                LocalConfig.Builder()
                    .also { wb -> wb.lora = Config.LoRaConfig.Builder().also { wb -> wb.tx_enabled = false }.build() }
                    .build()
            assertEquals(true, awaitItem())

            configFlow.value =
                LocalConfig.Builder()
                    .also { wb -> wb.lora = Config.LoRaConfig.Builder().also { wb -> wb.tx_enabled = true }.build() }
                    .build()
            assertEquals(false, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `suppressNoPairedWarning updates state and prefs`() {
        viewModel.suppressNoPairedWarning()

        assertEquals(true, viewModel.hasShownNotPairedWarning.value)
        assertEquals(true, uiPrefs.hasShownNotPairedWarning.value)
    }

    @Test
    fun `active node readiness is withdrawn when the identity is cleared or replaced`() = runTest {
        val vm = newViewModel()

        vm.activeNodeInfoReady.test {
            assertEquals(false, awaitItem())

            nodeRepository.setOurNode(
                org.meshtastic.core.model.Node(
                    num = 7,
                    user = User.Builder().also { wb -> wb.hw_model = HardwareModel.TBEAM }.build(),
                ),
            )
            connectionIdentity.value =
                ConnectionIdentity(sessionGeneration = 3, address = "first", nodeNum = 7, deviceId = null)
            assertEquals(true, awaitItem())

            connectionIdentity.value = null
            assertEquals(false, awaitItem())

            connectionIdentity.value =
                ConnectionIdentity(sessionGeneration = 4, address = "second", nodeNum = 8, deviceId = null)
            advanceUntilIdle()
            assertEquals(false, vm.activeNodeInfoReady.value)

            nodeRepository.setOurNode(
                org.meshtastic.core.model.Node(
                    num = 8,
                    user = User.Builder().also { wb -> wb.hw_model = HardwareModel.TBEAM }.build(),
                ),
            )
            assertEquals(true, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `connected older known node exposes Android firmware update notice`() = runTest {
        val hardwareModel = HardwareModel.TBEAM.value
        val target = "tbeam"
        deviceHardwareRepository.setHardware(
            hwModel = hardwareModel,
            target = target,
            device = DeviceHardware(architecture = "esp32", platformioTarget = target),
        )
        nodeRepository.setMyId("!local")
        nodeRepository.setMyNodeInfo(TestDataFactory.createMyNodeInfo(firmwareVersion = "2.7.0", pioEnv = target))
        nodeRepository.setOurNode(
            org.meshtastic.core.model.Node(
                num = 1,
                user = User.Builder().also { wb -> wb.hw_model = HardwareModel.TBEAM }.build(),
            ),
        )
        radioPrefs.setDevAddr("x:connected")
        firmwareReleaseRepository.setManifestTargets("v2.8.0", setOf(target))
        firmwareReleaseRepository.setStableRelease(FirmwareRelease(id = "v2.8.0"))
        serviceRepository.setConnectionState(ConnectionState.Connected)

        advanceUntilIdle()

        val notice = assertNotNull(viewModel.firmwareUpdateNotice.value)
        assertEquals("2.7.0", notice.currentVersion)
        assertEquals("2.8.0", notice.stableVersion)
        assertEquals(FirmwareUpdateDestination.AndroidUpdate, notice.destination)
        assertEquals(listOf(notice), postedNotices)
        assertEquals(setOf(notice.notificationKey), uiPrefs.firmwareUpdateNotificationKeys.value)
    }

    @Test
    fun `firmware notice for flasher-only hardware is still posted`() = runTest {
        val target = "tbeam"
        deviceHardwareRepository.setHardware(
            hwModel = HardwareModel.TBEAM.value,
            target = target,
            device = DeviceHardware(architecture = "esp32", platformioTarget = target),
        )
        nodeRepository.setMyId("!local")
        nodeRepository.setMyNodeInfo(TestDataFactory.createMyNodeInfo(firmwareVersion = "2.7.0", pioEnv = target))
        nodeRepository.setOurNode(
            org.meshtastic.core.model.Node(
                num = 1,
                user = User.Builder().also { wb -> wb.hw_model = HardwareModel.TBEAM }.build(),
            ),
        )
        // ESP32 over serial is not updatable in-app, so the notice's destination is the flasher.
        radioPrefs.setDevAddr("s:connected")
        firmwareReleaseRepository.setManifestTargets("v2.8.0", setOf(target))
        firmwareReleaseRepository.setStableRelease(FirmwareRelease(id = "v2.8.0"))
        serviceRepository.setConnectionState(ConnectionState.Connected)

        advanceUntilIdle()

        val notice = assertNotNull(viewModel.firmwareUpdateNotice.value)
        assertEquals(FirmwareUpdateDestination.MeshtasticFlasher, notice.destination)
        assertEquals(listOf(notice), postedNotices)
    }

    @Test
    fun `a device saved with the legacy bang prefix gets the firmware update notice`() = runTest {
        val target = "tbeam"
        deviceHardwareRepository.setHardware(
            hwModel = HardwareModel.TBEAM.value,
            target = target,
            device = DeviceHardware(architecture = "esp32", platformioTarget = target),
        )
        nodeRepository.setMyId("!local")
        nodeRepository.setMyNodeInfo(TestDataFactory.createMyNodeInfo(firmwareVersion = "2.7.0", pioEnv = target))
        nodeRepository.setOurNode(
            org.meshtastic.core.model.Node(
                num = 1,
                user = User.Builder().also { wb -> wb.hw_model = HardwareModel.TBEAM }.build(),
            ),
        )
        radioPrefs.setDevAddr("!AA:BB:CC:DD:EE:FF")
        firmwareReleaseRepository.setManifestTargets("v2.8.0", setOf(target))
        firmwareReleaseRepository.setStableRelease(FirmwareRelease(id = "v2.8.0"))
        serviceRepository.setConnectionState(ConnectionState.Connected)

        advanceUntilIdle()

        assertNotNull(viewModel.firmwareUpdateNotice.value)
    }

    @Test
    fun `does not persist firmware notification dedupe when scheduling is unavailable`() = runTest {
        val hardwareModel = HardwareModel.TBEAM.value
        val target = "tbeam"
        serviceNotifications.acceptsFirmwareUpdate = false
        deviceHardwareRepository.setHardware(
            hwModel = hardwareModel,
            target = target,
            device = DeviceHardware(architecture = "esp32", platformioTarget = target),
        )
        nodeRepository.setMyId("!local")
        nodeRepository.setMyNodeInfo(TestDataFactory.createMyNodeInfo(firmwareVersion = "2.7.0", pioEnv = target))
        nodeRepository.setOurNode(
            org.meshtastic.core.model.Node(
                num = 1,
                user = User.Builder().also { wb -> wb.hw_model = HardwareModel.TBEAM }.build(),
            ),
        )
        radioPrefs.setDevAddr("x:connected")
        firmwareReleaseRepository.setManifestTargets("v2.8.0", setOf(target))
        firmwareReleaseRepository.setStableRelease(FirmwareRelease(id = "v2.8.0"))
        serviceRepository.setConnectionState(ConnectionState.Connected)

        advanceUntilIdle()

        assertNotNull(viewModel.firmwareUpdateNotice.value)
        assertEquals(emptyList(), postedNotices)
        assertEquals(emptySet(), uiPrefs.firmwareUpdateNotificationKeys.value)
    }

    @Test
    fun `stale firmware catalog does not expose a notice`() = runTest {
        val hardwareModel = HardwareModel.TBEAM.value
        val target = "tbeam"
        deviceHardwareRepository.setHardware(
            hwModel = hardwareModel,
            target = target,
            device = DeviceHardware(architecture = "esp32", platformioTarget = target),
        )
        nodeRepository.setMyId("!local")
        nodeRepository.setMyNodeInfo(TestDataFactory.createMyNodeInfo(firmwareVersion = "2.7.0", pioEnv = target))
        nodeRepository.setOurNode(
            org.meshtastic.core.model.Node(
                num = 1,
                user = User.Builder().also { wb -> wb.hw_model = HardwareModel.TBEAM }.build(),
            ),
        )
        radioPrefs.setDevAddr("x:connected")
        firmwareReleaseRepository.setStableRelease(FirmwareRelease(id = "v2.8.0", lastUpdated = 0))
        serviceRepository.setConnectionState(ConnectionState.Connected)

        advanceUntilIdle()

        assertEquals(null, viewModel.firmwareUpdateNotice.value)
    }

    @Test
    fun `stable manifest missing the connected target does not expose a notice`() = runTest {
        val hardwareModel = HardwareModel.TBEAM.value
        val target = "tbeam"
        deviceHardwareRepository.setHardware(
            hwModel = hardwareModel,
            target = target,
            device = DeviceHardware(architecture = "esp32", platformioTarget = target),
        )
        nodeRepository.setMyId("!local")
        nodeRepository.setMyNodeInfo(TestDataFactory.createMyNodeInfo(firmwareVersion = "2.7.0", pioEnv = target))
        nodeRepository.setOurNode(
            org.meshtastic.core.model.Node(
                num = 1,
                user = User.Builder().also { wb -> wb.hw_model = HardwareModel.TBEAM }.build(),
            ),
        )
        radioPrefs.setDevAddr("x:connected")
        firmwareReleaseRepository.setManifestTargets("v2.8.0", setOf("t-echo"))
        firmwareReleaseRepository.setStableRelease(FirmwareRelease(id = "v2.8.0"))
        serviceRepository.setConnectionState(ConnectionState.Connected)

        advanceUntilIdle()

        assertEquals(null, viewModel.firmwareUpdateNotice.value)
        assertEquals(emptyList(), postedNotices)
    }
}
