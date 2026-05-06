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
import dev.mokkery.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.TracerouteMapAvailability
import org.meshtastic.core.model.util.getSharedContactUrl
import org.meshtastic.core.navigation.ContactsRoute
import org.meshtastic.core.navigation.DEEP_LINK_BASE_URI
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioPrefs
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.core.testing.FakeFirmwareReleaseRepository
import org.meshtastic.core.testing.FakeMeshLogRepository
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.core.testing.FakeServiceRepository
import org.meshtastic.core.testing.TestDataFactory
import org.meshtastic.core.ui.util.AlertManager
import org.meshtastic.core.ui.util.SnackbarManager
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.Position
import org.meshtastic.proto.SharedContact
import org.meshtastic.proto.User
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UIViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var viewModel: UIViewModel
    private lateinit var nodeRepository: FakeNodeRepository
    private lateinit var serviceRepository: FakeServiceRepository
    private lateinit var radioController: FakeRadioController
    private lateinit var meshLogRepository: FakeMeshLogRepository
    private lateinit var firmwareReleaseRepository: FakeFirmwareReleaseRepository
    private lateinit var radioPrefs: RadioPrefs
    private lateinit var uiPrefs: UiPrefs
    private lateinit var notificationManager: NotificationManager
    private lateinit var packetRepository: PacketRepository

    private lateinit var devAddrFlow: MutableStateFlow<String?>
    private lateinit var themeFlow: MutableStateFlow<Int>
    private lateinit var appIntroCompletedFlow: MutableStateFlow<Boolean>
    private lateinit var unreadMessageCountFlow: MutableStateFlow<Int>

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        nodeRepository = FakeNodeRepository()
        serviceRepository = FakeServiceRepository()
        radioController = FakeRadioController()
        meshLogRepository = FakeMeshLogRepository()
        firmwareReleaseRepository = FakeFirmwareReleaseRepository()
        radioPrefs = mock(MockMode.autofill)
        uiPrefs = mock(MockMode.autofill)
        notificationManager = mock(MockMode.autofill)
        packetRepository = mock(MockMode.autofill)

        devAddrFlow = MutableStateFlow(null)
        themeFlow = MutableStateFlow(1)
        appIntroCompletedFlow = MutableStateFlow(false)
        unreadMessageCountFlow = MutableStateFlow(0)

        every { radioPrefs.devAddr } returns devAddrFlow
        every { uiPrefs.theme } returns themeFlow
        every { uiPrefs.appIntroCompleted } returns appIntroCompletedFlow
        every { uiPrefs.setAppIntroCompleted(any()) } returns Unit
        every { notificationManager.cancel(any()) } returns Unit
        every { packetRepository.getUnreadCountTotal() } returns unreadMessageCountFlow

        viewModel = createViewModel()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun handleDeepLink_triesDeepLinkRouterFirst() = runTest {
        viewModel.navigationDeepLink.test {
            viewModel.handleDeepLink(CommonUri.parse("$DEEP_LINK_BASE_URI/messages/contact1"))

            assertEquals(
                listOf(ContactsRoute.ContactsGraph, ContactsRoute.Messages(contactKey = "contact1", message = "")),
                awaitItem(),
            )
            assertNull(viewModel.sharedContactRequested.value)
            assertNull(viewModel.requestChannelSet.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun handleDeepLink_fallsBackToDispatchMeshtasticUri() {
        val sharedContact = SharedContact(user = User(long_name = "Suzume", short_name = "SZ"), node_num = 12345)

        viewModel.handleDeepLink(sharedContact.getSharedContactUrl())

        assertEquals(sharedContact, viewModel.sharedContactRequested.value)
        assertNull(viewModel.requestChannelSet.value)
    }

    @Test
    fun sharedContactRequested_setAndClear() = runTest {
        val sharedContact = SharedContact(user = User(long_name = "Suzume"), node_num = 12345)

        viewModel.sharedContactRequested.test {
            assertNull(awaitItem())

            viewModel.setSharedContactRequested(sharedContact)
            assertEquals(sharedContact, awaitItem())

            viewModel.clearSharedContactRequested()
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun requestChannelSet_setAndClear() = runTest {
        val channelSet = ChannelSet(settings = listOf(ChannelSettings(name = "LongFast")))

        viewModel.requestChannelSet.test {
            assertNull(awaitItem())

            viewModel.setRequestChannelSet(channelSet)
            assertEquals(channelSet, awaitItem())

            viewModel.clearRequestChannelUrl()
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun connectionState_delegatesToServiceRepository() = runTest {
        viewModel.connectionState.test {
            assertEquals(ConnectionState.Disconnected, awaitItem())

            serviceRepository.setConnectionState(ConnectionState.Connected)
            assertEquals(ConnectionState.Connected, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun unreadMessageCount_coercesToZero() = runTest {
        unreadMessageCountFlow.value = -1

        viewModel.unreadMessageCount.test {
            advanceUntilIdle()
            assertEquals(0, expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun unreadMessageCount_emitsPositiveValues() = runTest {
        unreadMessageCountFlow.value = 5

        viewModel.unreadMessageCount.test {
            advanceUntilIdle()
            assertEquals(5, expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun appIntroCompleted_readsFromPrefs() {
        appIntroCompletedFlow.value = true
        viewModel = createViewModel()

        assertTrue(viewModel.appIntroCompleted.value)
    }

    @Test
    fun onAppIntroCompleted_setsPrefs() {
        viewModel.onAppIntroCompleted()

        verify { uiPrefs.setAppIntroCompleted(true) }
    }

    @Test
    fun clearClientNotification_clearsServiceRepoAndCancelsNotification() {
        val notification = ClientNotification(message = "Check me")
        serviceRepository.setClientNotification(notification)

        viewModel.clearClientNotification(notification)

        assertNull(serviceRepository.clientNotification.value)
        verify { notificationManager.cancel(notification.toString().hashCode()) }
    }

    @Test
    fun myNodeInfo_delegatesToNodeDB() {
        val myNodeInfo = TestDataFactory.createMyNodeInfo(myNodeNum = 42)
        nodeRepository.setMyNodeInfo(myNodeInfo)

        assertEquals(myNodeInfo, viewModel.myNodeInfo.value)
    }

    @Test
    fun theme_delegatesToUiPrefs() {
        themeFlow.value = 2
        viewModel = createViewModel()

        assertEquals(2, viewModel.theme.value)

        themeFlow.value = 4
        assertEquals(4, viewModel.theme.value)
    }

    @Test
    fun tracerouteMapAvailability_correctlyEvaluatesForwardAndReturnRoutes() {
        nodeRepository.setNodes(
            listOf(
                Node(num = 1, position = Position(latitude_i = 100000000, longitude_i = 200000000)),
                Node(num = 2),
                Node(num = 3, position = Position(latitude_i = 300000000, longitude_i = 400000000)),
            ),
        )

        val result = viewModel.tracerouteMapAvailability(forwardRoute = listOf(1, 2, 3), returnRoute = listOf(3, 2, 1))

        assertEquals(TracerouteMapAvailability.Ok, result)
    }

    private fun createViewModel() =
        UIViewModel(
            nodeDB = nodeRepository,
            serviceRepository = serviceRepository,
            radioController = radioController,
            radioPrefs = radioPrefs,
            meshLogRepository = meshLogRepository,
            firmwareReleaseRepository = firmwareReleaseRepository,
            uiPrefs = uiPrefs,
            notificationManager = notificationManager,
            packetRepository = packetRepository,
            alertManager = AlertManager(),
            snackbarManager = SnackbarManager(),
        )
}
