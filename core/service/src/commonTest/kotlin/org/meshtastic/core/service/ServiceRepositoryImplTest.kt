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

import co.touchlab.kermit.Severity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.service.TracerouteResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ServiceRepositoryImplTest {

    @Test
    fun initialStateExposesDefaultsAndNoBufferedEvents() = runTest {
        val repository = ServiceRepositoryImpl()

        assertEquals(ConnectionState.Disconnected, repository.connectionState.value)
        assertNull(repository.clientNotification.value)
        assertNull(repository.errorMessage.value)
        assertNull(repository.connectionProgress.value)
        assertNull(repository.tracerouteResponse.value)
        assertNull(repository.neighborInfoResponse.value)

        val initialMeshPacket = async { withTimeoutOrNull(1) { repository.meshPacketFlow.first() } }

        runCurrent()
        advanceTimeBy(1)

        assertNull(initialMeshPacket.await())
    }

    @Test
    fun setConnectionStateUpdatesStateFlow() = runTest {
        val repository = ServiceRepositoryImpl()
        val emittedState = async { repository.connectionState.drop(1).first() }

        runCurrent()
        repository.setConnectionState(ConnectionState.Connecting)

        assertEquals(ConnectionState.Connecting, emittedState.await())
        assertEquals(ConnectionState.Connecting, repository.connectionState.value)
    }

    @Test
    fun setErrorMessageEmitsAndCanBeCleared() = runTest {
        val repository = ServiceRepositoryImpl()
        val emittedMessage = async { repository.errorMessage.drop(1).first() }

        runCurrent()
        repository.setErrorMessage("BLE connection lost", Severity.Warn)

        assertEquals("BLE connection lost", emittedMessage.await())
        assertEquals("BLE connection lost", repository.errorMessage.value)

        repository.clearErrorMessage()

        assertNull(repository.errorMessage.value)
    }

    @Test
    fun setTracerouteResponseEmitsAndCanBeCleared() = runTest {
        val repository = ServiceRepositoryImpl()
        val response =
            TracerouteResponse(
                message = "Traceroute complete",
                destinationNodeNum = 123,
                requestId = 456,
                forwardRoute = listOf(1, 2, 3),
                returnRoute = listOf(3, 2, 1),
            )
        val emittedResponse = async { repository.tracerouteResponse.drop(1).first() }

        runCurrent()
        repository.setTracerouteResponse(response)

        assertEquals(response, emittedResponse.await())
        assertEquals(response, repository.tracerouteResponse.value)

        repository.clearTracerouteResponse()

        assertNull(repository.tracerouteResponse.value)
    }
}
