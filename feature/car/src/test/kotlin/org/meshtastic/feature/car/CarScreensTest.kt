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
@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package org.meshtastic.feature.car

import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.TabTemplate
import androidx.car.app.testing.TestCarContext
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okio.ByteString.Companion.encodeUtf8
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.usecase.SendMessageUseCase
import org.meshtastic.feature.car.alerts.EmergencyHandler
import org.meshtastic.feature.car.model.CarSessionState
import org.meshtastic.feature.car.model.ChannelUi
import org.meshtastic.feature.car.model.MessagingUiState
import org.meshtastic.feature.car.screens.HomeScreen
import org.meshtastic.feature.car.service.CarStateCoordinator
import org.meshtastic.feature.car.util.MessageFilter
import org.meshtastic.proto.PortNum
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Drives the real [HomeScreen] (via the androidx.car.app testing context) with state pushed through the
 * [CarStateCoordinator] test seam, asserting the correct template renders per connection state, and verifies the
 * emergency flow turns an ALERT_APP packet into an [org.meshtastic.feature.car.model.EmergencyAlert].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36]) // Robolectric has no SDK 37 jar yet; the car templates are SDK-agnostic.
class CarScreensTest {

    private val packetRepo = mock<PacketRepository>(MockMode.autofill)
    private val nodeRepo = mock<NodeRepository>(MockMode.autofill)

    private fun coordinator(): CarStateCoordinator {
        every { packetRepo.getContacts() } returns flowOf(emptyMap())
        every { nodeRepo.nodeDBbyNum } returns MutableStateFlow(emptyMap<Int, Node>())
        return CarStateCoordinator(
            nodeRepository = nodeRepo,
            packetRepository = packetRepo,
            serviceRepository = mock<ServiceRepository>(MockMode.autofill),
            radioConfigRepository = mock<RadioConfigRepository>(MockMode.autofill),
            sendMessageUseCase = mock<SendMessageUseCase>(MockMode.autofill),
            messageFilter = MessageFilter(),
        )
    }

    private fun homeScreen(coord: CarStateCoordinator): HomeScreen {
        val ctx = TestCarContext.createCarContext(RuntimeEnvironment.getApplication())
        return HomeScreen(ctx, coord, EmergencyHandler())
    }

    private fun session(state: ConnectionState) = CarSessionState(
        connectionStatus = state,
        onlineNodeCount = 0,
        lastMessageTime = null,
        activeEmergencies = emptyList(),
        meshName = "Test Mesh",
    )

    @Test
    fun `disconnected renders a pane template`() {
        val coord = coordinator().apply { setStateForTest(session = session(ConnectionState.Disconnected)) }
        assertTrue(homeScreen(coord).onGetTemplate() is PaneTemplate)
    }

    @Test
    fun `connected with no channels renders the onboarding pane`() {
        val coord =
            coordinator().apply {
                setStateForTest(
                    session = session(ConnectionState.Connected),
                    messaging = MessagingUiState(emptyList(), 0, emptyList(), null),
                )
            }
        assertTrue(homeScreen(coord).onGetTemplate() is PaneTemplate)
    }

    @Test
    fun `connected with channels renders the tab template`() {
        val coord =
            coordinator().apply {
                setStateForTest(
                    session = session(ConnectionState.Connected),
                    messaging = MessagingUiState(listOf(ChannelUi(0, "LongFast", 0)), 0, emptyList(), null),
                )
            }
        assertTrue(homeScreen(coord).onGetTemplate() is TabTemplate)
    }

    @Test
    fun `ALERT_APP packet becomes an emergency alert`() = runBlocking {
        every { nodeRepo.nodeDBbyNum } returns MutableStateFlow(emptyMap<Int, Node>())
        val alert =
            DataPacket(
                to = null,
                bytes = "HELP".encodeUtf8(),
                dataType = PortNum.ALERT_APP.value,
                from = "!abcd1234",
                time = 100L,
                id = 7,
            )
        every { packetRepo.getContacts() } returns flowOf(mapOf("k" to alert))
        val coord =
            CarStateCoordinator(
                nodeRepository = nodeRepo,
                packetRepository = packetRepo,
                serviceRepository = mock<ServiceRepository>(MockMode.autofill),
                radioConfigRepository = mock<RadioConfigRepository>(MockMode.autofill),
                sendMessageUseCase = mock<SendMessageUseCase>(MockMode.autofill),
                messageFilter = MessageFilter(),
            )

        val emergency = coord.emergencyAlerts.first()

        assertEquals("HELP", emergency.message)
        assertEquals("!abcd1234", emergency.nodeName)
        assertTrue(emergency.isActive)
    }
}
