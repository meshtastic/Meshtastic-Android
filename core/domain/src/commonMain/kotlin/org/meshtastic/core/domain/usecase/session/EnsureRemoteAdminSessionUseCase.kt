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
package org.meshtastic.core.domain.usecase.session

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.SessionStatus
import org.meshtastic.core.model.service.ServiceAction
import org.meshtastic.core.repository.MeshActionHandler
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.SessionManager
import kotlin.time.Duration.Companion.seconds

/**
 * Ensures a remote-admin session exists for the target node, dispatching a metadata request and awaiting a refreshed
 * passkey if necessary.
 *
 * Why this exists: the firmware embeds an 8-byte rotating passkey in every admin response and rejects admin traffic
 * lacking a fresh key (`firmware/src/modules/AdminModule.cpp:1460-1481`). Before this use case the UI silently tunneled
 * the user into a remote-admin screen that immediately failed if no metadata had been requested first.
 *
 * Concurrency model:
 * - One in-flight ensure per `destNum`. Concurrent callers dedupe onto the same `Deferred` so a double-tap doesn't
 *   blast two metadata requests at the radio.
 * - The refresh-flow subscription is established **before** the metadata request is dispatched to avoid losing the
 *   response on the inherently raceful `MutableSharedFlow`.
 * - The `withTimeoutOrNull` is a UX deadline only — late responses still update the durable `SessionStatus` flow that
 *   the UI observes, so a "Timeout" outcome here can self-heal in the chip without re-tapping.
 */
@Single
open class EnsureRemoteAdminSessionUseCase(
    private val sessionManager: SessionManager,
    private val meshActionHandler: MeshActionHandler,
    private val serviceRepository: ServiceRepository,
    @Named("ServiceScope") private val serviceScope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val inFlight = mutableMapOf<Int, Deferred<EnsureSessionResult>>()

    @Suppress("ReturnCount")
    open suspend operator fun invoke(destNum: Int): EnsureSessionResult {
        if (serviceRepository.connectionState.value != ConnectionState.Connected) {
            return EnsureSessionResult.Disconnected
        }
        if (sessionManager.observeSessionStatus(destNum).first() is SessionStatus.Active) {
            return EnsureSessionResult.AlreadyActive
        }

        val deferred =
            mutex.withLock {
                inFlight[destNum]
                    ?: serviceScope
                        .async(start = CoroutineStart.LAZY) { runEnsure(destNum) }
                        .also { inFlight[destNum] = it }
            }
        return try {
            deferred.await()
        } finally {
            mutex.withLock { if (inFlight[destNum] === deferred) inFlight.remove(destNum) }
        }
    }

    private suspend fun runEnsure(destNum: Int): EnsureSessionResult {
        Logger.d { "EnsureRemoteAdminSession dispatching metadata request to $destNum" }
        return withTimeoutOrNull(UX_TIMEOUT) {
            // Subscribe BEFORE dispatching so we don't miss the refresh emission.
            val refreshed =
                serviceScope.async(start = CoroutineStart.UNDISPATCHED) {
                    sessionManager.sessionRefreshFlow.filter { it == destNum }.first()
                }
            try {
                meshActionHandler.onServiceAction(ServiceAction.GetDeviceMetadata(destNum))
                refreshed.await()
                EnsureSessionResult.Refreshed
            } finally {
                refreshed.cancel()
            }
        } ?: EnsureSessionResult.Timeout
    }

    companion object {
        /**
         * UX deadline for surfacing a result to the user. The metadata request keeps flying after this — late responses
         * still update the durable `SessionStatus` flow.
         */
        val UX_TIMEOUT = 10.seconds
    }
}
