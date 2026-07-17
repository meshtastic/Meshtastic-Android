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
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.model.FirmwareUpdateDestination
import org.meshtastic.core.repository.Notification
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.testing.FakeDeviceHardwareRepository
import org.meshtastic.core.testing.FakeFirmwareReleaseRepository
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioPrefs
import org.meshtastic.core.testing.FakeServiceRepository
import org.meshtastic.core.testing.FakeUiPrefs
import org.meshtastic.core.testing.TestDataFactory
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.User
import org.meshtastic.proto.LocalConfig
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
    private val nodeRepository = FakeNodeRepository()
    private val uiPrefs = FakeUiPrefs()
    private val deviceHardwareRepository = FakeDeviceHardwareRepository()
    private val firmwareReleaseRepository = FakeFirmwareReleaseRepository()
    private val radioPrefs = FakeRadioPrefs()
    private val dispatchedNotifications = mutableListOf<Notification>()
    private var notificationsCanBeScheduled = true
    private val notificationManager =
        object : NotificationManager {
            override fun dispatch(notification: Notification): Boolean {
                if (notificationsCanBeScheduled) dispatchedNotifications += notification
                return notificationsCanBeScheduled
            }

            override fun cancel(id: Int) = Unit

            override fun cancelAll() = Unit
        }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        dispatchedNotifications.clear()
        notificationsCanBeScheduled = true

        every { radioConfigRepository.localConfigFlow } returns MutableStateFlow(LocalConfig())
        uiPrefs.hasShownNotPairedWarning.value = false
        uiPrefs.firmwareUpdateNotificationKeys.value = emptySet()

        viewModel =
            ConnectionsViewModel(
                radioConfigRepository = radioConfigRepository,
                serviceRepository = serviceRepository,
                nodeRepository = nodeRepository,
                uiPrefs = uiPrefs,
                deviceHardwareRepository = deviceHardwareRepository,
                firmwareReleaseRepository = firmwareReleaseRepository,
                radioPrefs = radioPrefs,
                notificationManager = notificationManager,
            )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialization() {
        assertNotNull(viewModel)
    }

    @Test
    fun `suppressNoPairedWarning updates state and prefs`() {
        viewModel.suppressNoPairedWarning()

        assertEquals(true, viewModel.hasShownNotPairedWarning.value)
        assertEquals(true, uiPrefs.hasShownNotPairedWarning.value)
    }

    @Test
    fun `Disconnected with Reconnecting progress maps to RECONNECTING`() = runTest {
        viewModel.connectionStatus.test {
            // Initial value from stateInWhileSubscribed.
            assertEquals(ConnectionStatus.NOT_CONNECTED, awaitItem())

            // Track A contract: progress is set BEFORE the Disconnected transition.
            serviceRepository.setConnectionProgress(ServiceRepository.RECONNECTING_PROGRESS_TEXT)
            assertEquals(ConnectionStatus.RECONNECTING, awaitItem())

            // State catch-up: the Disconnected transition is a no-op because FakeServiceRepository
            // starts Disconnected, and distinctUntilChanged suppresses the re-emission.
            serviceRepository.setConnectionState(ConnectionState.Disconnected)
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Connecting state maps to CONNECTING regardless of progress text`() = runTest {
        viewModel.connectionStatus.test {
            assertEquals(ConnectionStatus.NOT_CONNECTED, awaitItem())

            // Progress set first would transiently surface RECONNECTING while state is still Disconnected.
            serviceRepository.setConnectionProgress(ServiceRepository.RECONNECTING_PROGRESS_TEXT)
            assertEquals(ConnectionStatus.RECONNECTING, awaitItem())

            // The Connecting transition overrides progress: Connecting always maps to CONNECTING.
            serviceRepository.setConnectionState(ConnectionState.Connecting)
            assertEquals(ConnectionStatus.CONNECTING, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Disconnected without Reconnecting progress stays NOT_CONNECTED`() = runTest {
        viewModel.connectionStatus.test {
            assertEquals(ConnectionStatus.NOT_CONNECTED, awaitItem())

            serviceRepository.setConnectionProgress("Downloading Node DB...")
            serviceRepository.setConnectionState(ConnectionState.Disconnected)
            // No emission: state and progress resolve to NOT_CONNECTED, equal to the current value.
            expectNoEvents()

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
        nodeRepository.setOurNode(org.meshtastic.core.model.Node(num = 1, user = User(hw_model = HardwareModel.TBEAM)))
        radioPrefs.setDevAddr("x:connected")
        firmwareReleaseRepository.setManifestTargets("v2.8.0", setOf(target))
        firmwareReleaseRepository.setStableRelease(FirmwareRelease(id = "v2.8.0"))
        serviceRepository.setConnectionState(ConnectionState.Connected)

        advanceUntilIdle()

        val notice = assertNotNull(viewModel.firmwareUpdateNotice.value)
        assertEquals("2.7.0", notice.currentVersion)
        assertEquals("2.8.0", notice.stableVersion)
        assertEquals(FirmwareUpdateDestination.AndroidUpdate, notice.destination)
        assertEquals(1, dispatchedNotifications.size)
        assertEquals(setOf(notice.notificationKey), uiPrefs.firmwareUpdateNotificationKeys.value)
        assertEquals("Firmware update available", dispatchedNotifications.single().title)
        assertEquals(Notification.Type.Info, dispatchedNotifications.single().type)
        assertEquals("meshtastic:///firmware/update", dispatchedNotifications.single().deepLinkUri)
    }

    @Test
    fun `does not persist firmware notification dedupe when scheduling is unavailable`() = runTest {
        val hardwareModel = HardwareModel.TBEAM.value
        val target = "tbeam"
        notificationsCanBeScheduled = false
        deviceHardwareRepository.setHardware(
            hwModel = hardwareModel,
            target = target,
            device = DeviceHardware(architecture = "esp32", platformioTarget = target),
        )
        nodeRepository.setMyId("!local")
        nodeRepository.setMyNodeInfo(TestDataFactory.createMyNodeInfo(firmwareVersion = "2.7.0", pioEnv = target))
        nodeRepository.setOurNode(org.meshtastic.core.model.Node(num = 1, user = User(hw_model = HardwareModel.TBEAM)))
        radioPrefs.setDevAddr("x:connected")
        firmwareReleaseRepository.setManifestTargets("v2.8.0", setOf(target))
        firmwareReleaseRepository.setStableRelease(FirmwareRelease(id = "v2.8.0"))
        serviceRepository.setConnectionState(ConnectionState.Connected)

        advanceUntilIdle()

        assertNotNull(viewModel.firmwareUpdateNotice.value)
        assertEquals(emptyList(), dispatchedNotifications)
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
        nodeRepository.setOurNode(org.meshtastic.core.model.Node(num = 1, user = User(hw_model = HardwareModel.TBEAM)))
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
        nodeRepository.setOurNode(org.meshtastic.core.model.Node(num = 1, user = User(hw_model = HardwareModel.TBEAM)))
        radioPrefs.setDevAddr("x:connected")
        firmwareReleaseRepository.setManifestTargets("v2.8.0", setOf("t-echo"))
        firmwareReleaseRepository.setStableRelease(FirmwareRelease(id = "v2.8.0"))
        serviceRepository.setConnectionState(ConnectionState.Connected)

        advanceUntilIdle()

        assertEquals(null, viewModel.firmwareUpdateNotice.value)
        assertEquals(emptyList(), dispatchedNotifications)
    }

    /**
     * Cross-track contract pin: Track A (MeshConnectionManagerImpl.runSiblingHandshakeRecovery) writes the literal
     * "Reconnecting…" (with U+2026) to ServiceRepository.connectionProgress. This constant is what Track C compares
     * against. If either side changes (e.g., localization, ASCII normalization), the UI would silently fall back to
     * NOT_CONNECTED instead of RECONNECTING. This test pins the canonical constant in [ServiceRepository]; combined
     * with the existing ordering test in MeshConnectionManagerImplTest that asserts the same literal is set, this
     * transitively enforces the contract via the shared constant.
     */
    @Test
    fun `RECONNECTING_PROGRESS_TEXT pins the cross-track literal value`() {
        assertEquals("Reconnecting\u2026", ServiceRepository.RECONNECTING_PROGRESS_TEXT)
    }
}
