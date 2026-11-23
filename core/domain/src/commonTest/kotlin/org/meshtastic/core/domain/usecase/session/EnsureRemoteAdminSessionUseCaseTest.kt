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
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.common.di.asServiceScope
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.testing.FakeServiceRepository
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.Routing
import org.meshtastic.sdk.AdminApi
import org.meshtastic.sdk.AdminResult
import org.meshtastic.sdk.AdminSessionStatus
import org.meshtastic.sdk.ConnectionState
import org.meshtastic.sdk.NodeId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class EnsureRemoteAdminSessionUseCaseTest {
    @Test
    fun disconnectedDoesNotRequestMetadata() = runTest {
        val fixture = fixture()
        fixture.service.setConnectionState(ConnectionState.Disconnected)
        assertEquals(EnsureSessionResult.Disconnected, fixture.ensure(DEST.raw))
        verifySuspend(exactly(0)) { fixture.admin.getDeviceMetadata() }
    }

    @Test
    fun activeSdkSessionDoesNotRequestMetadata() = runTest {
        val fixture = fixture()
        fixture.status.value = ACTIVE
        assertEquals(EnsureSessionResult.AlreadyActive, fixture.ensure(DEST.raw))
        verifySuspend(exactly(0)) { fixture.admin.getDeviceMetadata() }
    }

    @Test
    fun concurrentCallsShareTheTypedMetadataRequest() = runTest {
        val fixture = fixture()
        val reply = CompletableDeferred<AdminResult<DeviceMetadata>>()
        everySuspend { fixture.admin.getDeviceMetadata() } calls { reply.await() }
        val first = async { fixture.ensure(DEST.raw) }
        val second = async { fixture.ensure(DEST.raw) }
        runCurrent()

        fixture.status.value = ACTIVE
        reply.complete(AdminResult.Success(DeviceMetadata.Builder().build()))
        assertEquals(EnsureSessionResult.Refreshed, first.await())
        assertEquals(EnsureSessionResult.Refreshed, second.await())
        verifySuspend(exactly(1)) { fixture.admin.getDeviceMetadata() }
    }

    @Test
    fun metadataWithoutAUsableSessionDoesNotOpenAdmin() = runTest {
        val fixture = fixture()
        everySuspend { fixture.admin.getDeviceMetadata() } returns AdminResult.Success(DeviceMetadata.Builder().build())
        assertEquals(EnsureSessionResult.Timeout, fixture.ensure(DEST.raw))
    }

    @Test
    fun retiredSdkSessionCannotAuthorizeReplacement() = runTest {
        val fixture = fixture()
        val reply = CompletableDeferred<AdminResult<DeviceMetadata>>()
        everySuspend { fixture.admin.getDeviceMetadata() } calls { reply.await() }
        val result = async { fixture.ensure(DEST.raw) }
        runCurrent()
        fixture.session.value = SESSION.copy(generation = 2)
        fixture.status.value = ACTIVE
        reply.complete(AdminResult.Success(DeviceMetadata.Builder().build()))
        assertEquals(EnsureSessionResult.Disconnected, result.await())
    }

    @Test
    fun sdkAuthorizationFailureRemainsActionable() = runTest {
        val fixture = fixture()
        everySuspend { fixture.admin.getDeviceMetadata() } returns AdminResult.Unauthorized
        assertEquals(
            EnsureSessionResult.Rejected(Routing.Error.ADMIN_PUBLIC_KEY_UNAUTHORIZED.value),
            fixture.ensure(DEST.raw),
        )
    }

    private fun TestScope.fixture(): Fixture {
        val session = MutableStateFlow<RadioSessionContext?>(SESSION)
        val status = MutableStateFlow<AdminSessionStatus>(AdminSessionStatus.NoSession)
        val radio = mock<RadioInterfaceService>(MockMode.autofill)
        val admin = mock<AdminApi>(MockMode.autofill)
        every { radio.activeSession } returns session
        every { radio.isSessionActive(SESSION) } calls { session.value == SESSION }
        every { radio.adminForSession(SESSION) } returns admin
        every { radio.adminSessionStatus(DEST) } returns status
        every { admin.forNode(DEST) } returns admin
        val service = FakeServiceRepository().apply { setConnectionState(ConnectionState.Connected) }
        return Fixture(
            EnsureRemoteAdminSessionUseCase(radio, service, backgroundScope.asServiceScope()),
            admin,
            service,
            status,
            session,
        )
    }

    private data class Fixture(
        val ensure: EnsureRemoteAdminSessionUseCase,
        val admin: AdminApi,
        val service: FakeServiceRepository,
        val status: MutableStateFlow<AdminSessionStatus>,
        val session: MutableStateFlow<RadioSessionContext?>,
    )

    private companion object {
        val SESSION = RadioSessionContext(1, "tselected")
        val DEST = NodeId(1234)
        val ACTIVE = AdminSessionStatus.Active(Instant.fromEpochSeconds(1), Instant.fromEpochSeconds(151))
    }
}
