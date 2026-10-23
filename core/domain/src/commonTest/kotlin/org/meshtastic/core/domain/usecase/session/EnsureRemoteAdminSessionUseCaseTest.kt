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
package org.meshtastic.core.domain.usecase.session

import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.common.di.asServiceScope
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.RoutingNak
import org.meshtastic.core.model.SessionStatus
import org.meshtastic.core.repository.RadioController
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.SessionManager
import org.meshtastic.proto.Data
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Routing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class EnsureRemoteAdminSessionUseCaseTest {

    private val destNum = 0xCAFE

    private fun stubSessionManager(
        initialStatus: SessionStatus = SessionStatus.NoSession,
        refreshFlow: MutableSharedFlow<Int> = MutableSharedFlow(extraBufferCapacity = 8),
    ): SessionManager {
        val mgr = mock<SessionManager>(MockMode.autofill)
        every { mgr.observeSessionStatus(any()) } returns flowOf(initialStatus)
        every { mgr.sessionRefreshFlow } returns refreshFlow
        every { mgr.getPasskey(any()) } returns ByteString.EMPTY
        return mgr
    }

    private fun connectedRepo(
        state: ConnectionState = ConnectionState.Connected,
        packets: MutableSharedFlow<MeshPacket> = MutableSharedFlow(extraBufferCapacity = 8),
    ): ServiceRepository {
        val repo = mock<ServiceRepository>(MockMode.autofill)
        every { repo.connectionState } returns MutableStateFlow(state)
        every { repo.meshPacketFlow } returns packets
        return repo
    }

    private fun routingNak(requestId: Int, error: Routing.Error): MeshPacket {
        val routing = Routing.Builder().also { wb -> wb.error_reason = error }.build()
        val data =
            Data.Builder()
                .also { wb ->
                    wb.portnum = PortNum.ROUTING_APP
                    wb.request_id = requestId
                    wb.payload = routing.encode().toByteString()
                }
                .build()
        return MeshPacket.Builder()
            .also { wb ->
                wb.from = destNum
                wb.decoded = data
            }
            .build()
    }

    @Test
    fun `returns Disconnected without dispatching when not connected`() = runTest {
        val sessionManager = stubSessionManager()
        val controller = mock<RadioController>(MockMode.autofill)
        val useCase =
            EnsureRemoteAdminSessionUseCase(
                sessionManager,
                controller,
                connectedRepo(ConnectionState.Disconnected),
                this.asServiceScope(),
            )

        val result = useCase(destNum)

        assertEquals(EnsureSessionResult.Disconnected, result)
    }

    @Test
    fun `returns AlreadyActive without dispatching when status already Active`() = runTest {
        val active = SessionStatus.Active(Clock.System.now())
        val sessionManager = stubSessionManager(initialStatus = active)
        val controller = mock<RadioController>(MockMode.autofill)
        val useCase =
            EnsureRemoteAdminSessionUseCase(sessionManager, controller, connectedRepo(), this.asServiceScope())

        val result = useCase(destNum)

        assertEquals(EnsureSessionResult.AlreadyActive, result)
    }

    @Test
    fun `dispatches metadata request and returns Refreshed when refresh flow emits`() = runTest {
        val refresh = MutableSharedFlow<Int>(extraBufferCapacity = 8)
        val sessionManager = stubSessionManager(refreshFlow = refresh)
        val controller = mock<RadioController>(MockMode.autofill)
        // Simulate the radio responding by emitting on the refresh flow when the metadata request fires.
        everySuspend { controller.refreshMetadata(any(), any()) } calls { refresh.tryEmit(destNum) }

        val useCase =
            EnsureRemoteAdminSessionUseCase(sessionManager, controller, connectedRepo(), this.asServiceScope())

        val result = useCase(destNum)

        assertEquals(EnsureSessionResult.Refreshed, result)
        verifySuspend { controller.refreshMetadata(destNum, any()) }
    }

    @Test
    fun `returns Rejected when a routing nak answers the metadata request`() = runTest {
        val packets = MutableSharedFlow<MeshPacket>(extraBufferCapacity = 8)
        val controller = mock<RadioController>(MockMode.autofill)
        every { controller.generatePacketId() } returns REQUEST_ID
        everySuspend { controller.refreshMetadata(any(), any()) } calls
            {
                packets.tryEmit(routingNak(REQUEST_ID, Routing.Error.ADMIN_PUBLIC_KEY_UNAUTHORIZED))
            }
        val useCase =
            EnsureRemoteAdminSessionUseCase(
                stubSessionManager(),
                controller,
                connectedRepo(packets = packets),
                this.asServiceScope(),
            )

        val result = useCase(destNum)

        val expected = RoutingNak(REQUEST_ID, destNum, Routing.Error.ADMIN_PUBLIC_KEY_UNAUTHORIZED.value)
        assertEquals(EnsureSessionResult.Rejected(expected), result)
        verifySuspend { controller.refreshMetadata(destNum, REQUEST_ID) }
    }

    @Test
    fun `ignores a nak for another request and a plain ack`() = runTest {
        val refresh = MutableSharedFlow<Int>(extraBufferCapacity = 8)
        val packets = MutableSharedFlow<MeshPacket>(extraBufferCapacity = 8)
        val controller = mock<RadioController>(MockMode.autofill)
        every { controller.generatePacketId() } returns REQUEST_ID
        everySuspend { controller.refreshMetadata(any(), any()) } calls
            {
                packets.tryEmit(routingNak(REQUEST_ID + 1, Routing.Error.ADMIN_PUBLIC_KEY_UNAUTHORIZED))
                packets.tryEmit(routingNak(REQUEST_ID, Routing.Error.NONE))
                refresh.tryEmit(destNum)
            }
        val useCase =
            EnsureRemoteAdminSessionUseCase(
                stubSessionManager(refreshFlow = refresh),
                controller,
                connectedRepo(packets = packets),
                this.asServiceScope(),
            )

        assertEquals(EnsureSessionResult.Refreshed, useCase(destNum))
    }

    @Test
    fun `accepts a valid refresh arriving after the former ten second deadline`() = runTest {
        val refresh = MutableSharedFlow<Int>(extraBufferCapacity = 8)
        val sessionManager = stubSessionManager(refreshFlow = refresh)
        val controller = mock<RadioController>(MockMode.autofill)
        everySuspend { controller.refreshMetadata(any(), any()) } returns Unit
        val useCase =
            EnsureRemoteAdminSessionUseCase(sessionManager, controller, connectedRepo(), this.asServiceScope())

        var observed: EnsureSessionResult? = null
        val job = launch { observed = useCase(destNum) }
        runCurrent()
        // Keep the emission late while deriving the boundary from the production UX deadline.
        advanceTimeBy(EnsureRemoteAdminSessionUseCase.UX_TIMEOUT.inWholeMilliseconds - 1.seconds.inWholeMilliseconds)
        refresh.emit(destNum)
        advanceUntilIdle()
        job.join()

        assertEquals(EnsureSessionResult.Refreshed, observed)
        verifySuspend { controller.refreshMetadata(destNum, any()) }
    }

    @Test
    fun `returns Timeout when no refresh arrives within deadline`() = runTest {
        val refresh = MutableSharedFlow<Int>(extraBufferCapacity = 8)
        val sessionManager = stubSessionManager(refreshFlow = refresh)
        val controller = mock<RadioController>(MockMode.autofill)
        everySuspend { controller.refreshMetadata(any(), any()) } returns Unit

        val useCase =
            EnsureRemoteAdminSessionUseCase(sessionManager, controller, connectedRepo(), this.asServiceScope())

        var observed: EnsureSessionResult? = null
        val job = launch { observed = useCase(destNum) }
        advanceTimeBy(EnsureRemoteAdminSessionUseCase.UX_TIMEOUT.inWholeMilliseconds + 100)
        advanceUntilIdle()
        job.join()

        assertEquals(EnsureSessionResult.Timeout, observed)
    }

    @Test
    fun `canceling one caller keeps the shared ensure alive for another caller`() = runTest {
        val refresh = MutableSharedFlow<Int>(extraBufferCapacity = 8)
        val sessionManager = stubSessionManager(refreshFlow = refresh)
        val controller = mock<RadioController>(MockMode.autofill)
        var dispatches = 0
        everySuspend { controller.refreshMetadata(any(), any()) } calls { dispatches++ }
        val useCase =
            EnsureRemoteAdminSessionUseCase(sessionManager, controller, connectedRepo(), this.asServiceScope())

        val firstCaller = launch { useCase(destNum) }
        runCurrent()
        val secondCaller = async { useCase(destNum) }
        runCurrent()

        firstCaller.cancelAndJoin()
        assertEquals(1, dispatches)
        refresh.emit(destNum)
        runCurrent()

        assertEquals(EnsureSessionResult.Refreshed, secondCaller.await())
        assertEquals(1, dispatches)
    }

    @Test
    fun `completed ensure is not reused by a later caller`() = runTest {
        val refresh = MutableSharedFlow<Int>(extraBufferCapacity = 8)
        val sessionManager = stubSessionManager(refreshFlow = refresh)
        val controller = mock<RadioController>(MockMode.autofill)
        var dispatches = 0
        everySuspend { controller.refreshMetadata(any(), any()) } calls { dispatches++ }
        val useCase =
            EnsureRemoteAdminSessionUseCase(sessionManager, controller, connectedRepo(), this.asServiceScope())

        val first = async { useCase(destNum) }
        runCurrent()
        refresh.emit(destNum)
        runCurrent()
        assertEquals(EnsureSessionResult.Refreshed, first.await())

        val second = async { useCase(destNum) }
        runCurrent()
        assertEquals(2, dispatches)
        refresh.emit(destNum)
        runCurrent()

        assertEquals(EnsureSessionResult.Refreshed, second.await())
    }

    @Test
    fun `departed ensure is not reused after reconnect`() = runTest {
        val refresh = MutableSharedFlow<Int>(extraBufferCapacity = 8)
        val sessionManager = stubSessionManager(refreshFlow = refresh)
        val controller = mock<RadioController>(MockMode.autofill)
        var dispatches = 0
        everySuspend { controller.refreshMetadata(any(), any()) } calls { dispatches++ }
        val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Connected)
        val repository = mock<ServiceRepository>(MockMode.autofill)
        every { repository.connectionState } returns connectionState
        every { repository.meshPacketFlow } returns MutableSharedFlow()
        val useCase = EnsureRemoteAdminSessionUseCase(sessionManager, controller, repository, this.asServiceScope())

        val departed = async { useCase(destNum) }
        runCurrent()
        assertEquals(1, dispatches)
        connectionState.value = ConnectionState.Disconnected
        runCurrent()
        assertEquals(EnsureSessionResult.Disconnected, departed.await())

        connectionState.value = ConnectionState.Connected
        val reconnected = async { useCase(destNum) }
        runCurrent()
        assertEquals(2, dispatches, "Reconnect must dispatch a new metadata request")
        refresh.emit(destNum)
        runCurrent()

        assertEquals(EnsureSessionResult.Refreshed, reconnected.await())
    }

    private companion object {
        const val REQUEST_ID = 0x1234
    }
}
