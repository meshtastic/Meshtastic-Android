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

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Single
import org.meshtastic.core.common.di.ServiceScope
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.SessionStatus
import org.meshtastic.core.repository.RadioController
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
 * - A connection departure completes the shared ensure as [EnsureSessionResult.Disconnected], so a later connection
 *   never inherits an old request or UX deadline.
 * - The `withTimeoutOrNull` is a UX deadline only — late responses still update the durable `SessionStatus` flow that
 *   the UI observes, so a "Timeout" outcome here can self-heal in the chip without re-tapping.
 */
@Single
open class EnsureRemoteAdminSessionUseCase(
    private val sessionManager: SessionManager,
    private val radioController: RadioController,
    private val serviceRepository: ServiceRepository,
    private val serviceScope: ServiceScope,
) {
    private val inFlightMutex = Mutex()
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
            inFlightMutex.withLock {
                inFlight[destNum]?.takeIf { !it.isCompleted }
                    ?: run {
                        lateinit var newDeferred: Deferred<EnsureSessionResult>
                        newDeferred =
                            serviceScope.async(start = CoroutineStart.LAZY) {
                                try {
                                    runEnsure(destNum)
                                } finally {
                                    // Cleanup belongs to the shared Deferred itself. NonCancellable guarantees that a
                                    // service-scope cancellation cannot strand a completed entry in the dedupe map.
                                    withContext(NonCancellable) {
                                        inFlightMutex.withLock {
                                            if (inFlight[destNum] === newDeferred) inFlight.remove(destNum)
                                        }
                                    }
                                }
                            }
                        // Register before the lazy deferred starts. The identity check above prevents an old completion
                        // from removing a newer ensure for the same node.
                        inFlight[destNum] = newDeferred
                        // The service scope, not the first awaiting caller, owns dispatch once the entry is visible.
                        // A lazy child can already be cancelled when its parent scope is shutting down; in that case
                        // its body never runs, so remove the entry here instead of relying on the body-level finally.
                        if (!newDeferred.start() && inFlight[destNum] === newDeferred) inFlight.remove(destNum)
                        newDeferred
                    }
            }
        return deferred.await()
    }

    private suspend fun runEnsure(destNum: Int): EnsureSessionResult {
        Logger.d { "EnsureRemoteAdminSession dispatching metadata request to $destNum" }
        return withTimeoutOrNull(UX_TIMEOUT) {
            coroutineScope {
                // Subscribe to both terminal events before dispatch so neither a synchronous response nor a
                // concurrent connection departure can be missed. UNDISPATCHED is deliberate: these children do no
                // work before suspending in first(), so starting inline only establishes the subscriptions.
                val refreshed =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        sessionManager.sessionRefreshFlow.filter { it == destNum }.first()
                    }
                val disconnected =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        serviceRepository.connectionState.filter { it != ConnectionState.Connected }.first()
                    }
                try {
                    if (disconnected.isCompleted) return@coroutineScope EnsureSessionResult.Disconnected
                    radioController.refreshMetadata(destNum)
                    select<EnsureSessionResult> {
                        disconnected.onAwait { EnsureSessionResult.Disconnected }
                        refreshed.onAwait { EnsureSessionResult.Refreshed }
                    }
                } finally {
                    disconnected.cancel()
                    refreshed.cancel()
                }
            }
        } ?: EnsureSessionResult.Timeout
    }

    companion object {
        /**
         * UX deadline for surfacing a result to the user. Multi-hop remote-admin responses can legitimately take well
         * over ten seconds; the metadata request keeps flying after this, and late responses still update the durable
         * `SessionStatus` flow.
         */
        val UX_TIMEOUT = 30.seconds
    }
}
