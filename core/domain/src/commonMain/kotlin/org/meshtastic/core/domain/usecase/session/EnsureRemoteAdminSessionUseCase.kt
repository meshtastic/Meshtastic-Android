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

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Single
import org.meshtastic.core.common.di.ServiceScope
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.routingErrorOrNull
import org.meshtastic.sdk.AdminApi
import org.meshtastic.sdk.AdminResult
import org.meshtastic.sdk.AdminSessionStatus
import org.meshtastic.sdk.ConnectionState
import org.meshtastic.sdk.NodeId
import kotlin.time.Duration.Companion.seconds

/** Opens the SDK's remote admin session before presenting an admin screen. */
@Single
open class EnsureRemoteAdminSessionUseCase(
    private val radio: RadioInterfaceService,
    private val serviceRepository: ServiceRepository,
    private val serviceScope: ServiceScope,
) {
    private data class Request(val session: RadioSessionContext, val node: NodeId)

    private val inFlightMutex = Mutex()
    private val inFlight = mutableMapOf<Request, Deferred<EnsureSessionResult>>()

    @Suppress("ReturnCount")
    open suspend operator fun invoke(destNum: Int): EnsureSessionResult {
        if (serviceRepository.connectionState.value != ConnectionState.Connected) {
            return EnsureSessionResult.Disconnected
        }
        val session = radio.activeSession.value ?: return EnsureSessionResult.Disconnected
        val request = Request(session, NodeId(destNum))
        val admin = radio.adminForSession(session)?.forNode(request.node) ?: return EnsureSessionResult.Disconnected
        val status = radio.adminSessionStatus(request.node).first()
        if (!radio.isSessionActive(session)) return EnsureSessionResult.Disconnected
        if (status is AdminSessionStatus.Active) return EnsureSessionResult.AlreadyActive

        val deferred = inFlightMutex.withLock {
            inFlight[request]?.takeIf { !it.isCompleted } ?: startRequest(request, admin)
        }
        return deferred.await()
    }

    private fun startRequest(request: Request, admin: AdminApi): Deferred<EnsureSessionResult> {
        lateinit var task: Deferred<EnsureSessionResult>
        task =
            serviceScope.async(start = CoroutineStart.LAZY) {
                try {
                    refresh(request, admin)
                } finally {
                    withContext(NonCancellable) {
                        inFlightMutex.withLock { if (inFlight[request] === task) inFlight.remove(request) }
                    }
                }
            }
        inFlight[request] = task
        if (!task.start()) inFlight.remove(request)
        return task
    }

    @Suppress("ReturnCount")
    private suspend fun refresh(request: Request, admin: AdminApi): EnsureSessionResult {
        if (!radio.isSessionActive(request.session)) return EnsureSessionResult.Disconnected
        val result = withTimeoutOrNull(UX_TIMEOUT) { admin.getDeviceMetadata() }
        if (!radio.isSessionActive(request.session)) return EnsureSessionResult.Disconnected
        return when (result) {
            is AdminResult.Success -> {
                val status = radio.adminSessionStatus(request.node).first()
                if (!radio.isSessionActive(request.session)) {
                    EnsureSessionResult.Disconnected
                } else if (status is AdminSessionStatus.Active) {
                    EnsureSessionResult.Refreshed
                } else {
                    EnsureSessionResult.Timeout
                }
            }

            else ->
                result?.routingErrorOrNull()?.let { EnsureSessionResult.Rejected(it) } ?: EnsureSessionResult.Timeout
        }
    }

    companion object {
        val UX_TIMEOUT = 30.seconds
    }
}
