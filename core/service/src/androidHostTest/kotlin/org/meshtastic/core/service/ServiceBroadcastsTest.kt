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
package org.meshtastic.core.service

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import co.touchlab.kermit.Severity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.service.ServiceAction
import org.meshtastic.core.model.service.TracerouteResponse
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.MeshPacket
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ServiceBroadcastsTest {

    private lateinit var context: Context
    private val serviceRepository = FakeServiceRepository()
    private lateinit var broadcasts: ServiceBroadcasts

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        broadcasts = ServiceBroadcasts(context, serviceRepository)
        serviceRepository.setConnectionState(ConnectionState.Connected)
    }

    @Test
    fun `broadcastConnection sends uppercase state string for ATAK`() {
        broadcasts.broadcastConnection()

        val shadowApp = shadowOf(context as Application)
        val intent = shadowApp.broadcastIntents.find { it.action == ACTION_MESH_CONNECTED }
        assertEquals("CONNECTED", intent?.getStringExtra(EXTRA_CONNECTED))
    }

    @Test
    fun `broadcastConnection sends legacy connection intent`() {
        broadcasts.broadcastConnection()

        val shadowApp = shadowOf(context as Application)
        val intent = shadowApp.broadcastIntents.find { it.action == ACTION_CONNECTION_CHANGED }
        assertEquals("CONNECTED", intent?.getStringExtra(EXTRA_CONNECTED))
        assertEquals(true, intent?.getBooleanExtra("connected", false))
    }

    private class FakeServiceRepository : ServiceRepository {
        override val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        override val clientNotification = MutableStateFlow<ClientNotification?>(null)
        override val errorMessage = MutableStateFlow<String?>(null)
        override val connectionProgress = MutableStateFlow<String?>(null)
        private val meshPackets = MutableSharedFlow<MeshPacket>()
        override val meshPacketFlow: SharedFlow<MeshPacket> = meshPackets
        override val tracerouteResponse = MutableStateFlow<TracerouteResponse?>(null)
        override val neighborInfoResponse = MutableStateFlow<String?>(null)
        private val serviceActions = MutableSharedFlow<ServiceAction>()
        override val serviceAction: Flow<ServiceAction> = serviceActions

        override fun setConnectionState(connectionState: ConnectionState) {
            this.connectionState.value = connectionState
        }

        override fun setClientNotification(notification: ClientNotification?) {
            clientNotification.value = notification
        }

        override fun clearClientNotification() {
            clientNotification.value = null
        }

        override fun setErrorMessage(text: String, severity: Severity) {
            errorMessage.value = text
        }

        override fun clearErrorMessage() {
            errorMessage.value = null
        }

        override fun setConnectionProgress(text: String) {
            connectionProgress.value = text
        }

        override suspend fun emitMeshPacket(packet: MeshPacket) {
            meshPackets.emit(packet)
        }

        override fun setTracerouteResponse(value: TracerouteResponse?) {
            tracerouteResponse.value = value
        }

        override fun clearTracerouteResponse() {
            tracerouteResponse.value = null
        }

        override fun setNeighborInfoResponse(value: String?) {
            neighborInfoResponse.value = value
        }

        override fun clearNeighborInfoResponse() {
            neighborInfoResponse.value = null
        }

        override suspend fun onServiceAction(action: ServiceAction) {
            serviceActions.emit(action)
        }
    }
}
