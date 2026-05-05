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
package org.meshtastic.core.data.radio

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.sdk.MessageHandle
import org.meshtastic.sdk.RetryPolicy
import org.meshtastic.sdk.SendOutcome
import org.meshtastic.sdk.SendState
import org.meshtastic.sdk.retryWith
import kotlin.time.Duration.Companion.seconds

/**
 * Tracks in-flight message delivery via SDK [MessageHandle]s.
 * Maps SDK [SendState] transitions to app [MessageStatus] and persists updates.
 */
@Single
class MessageDeliveryTracker(
    private val packetRepository: Lazy<PacketRepository>,
    dispatchers: CoroutineDispatchers,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val activeHandles = mutableMapOf<Int, MessageHandle>()
    private val activeHandlesMutex = Mutex()
    private val defaultRetryPolicy = RetryPolicy.ExponentialBackoff(maxAttempts = 3, initialDelay = 2.seconds)

    /**
     * Begin tracking a [MessageHandle] for the given packet ID.
     * Observes intermediate state transitions and resolves the terminal status via SDK retries.
     */
    fun track(packetId: Int, handle: MessageHandle, policy: RetryPolicy = defaultRetryPolicy) {
        scope.launch {
            activeHandlesMutex.withLock {
                activeHandles[packetId] = handle
            }

            val repository = packetRepository.value
            val stateObserver = launch {
                handle.state
                    .onEach { state ->
                        val status = mapObservedState(state)
                        Logger.d { "[DeliveryTracker] Packet $packetId state=$state → $status" }
                        repository.updateMessageStatus(packetId, status)
                    }
                    .first { state -> state.isTerminal() }
            }

            try {
                val outcome = handle.retryWith(policy)
                stateObserver.join()
                val status = outcome.toMessageStatus()
                Logger.d { "[DeliveryTracker] Packet $packetId outcome=$outcome → $status" }
                repository.updateMessageStatus(packetId, status)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(e) { "[DeliveryTracker] Packet $packetId retry tracking failed" }
                repository.updateMessageStatus(packetId, MessageStatus.ERROR)
            } finally {
                stateObserver.cancel()
                activeHandlesMutex.withLock {
                    if (activeHandles[packetId] === handle) {
                        activeHandles.remove(packetId)
                    }
                }
            }
        }
    }

    private fun mapObservedState(state: SendState): MessageStatus = when (state) {
        SendState.Queued -> MessageStatus.QUEUED
        SendState.Sent -> MessageStatus.ENROUTE
        SendState.Acked -> MessageStatus.DELIVERED
        SendState.Delivered -> MessageStatus.DELIVERED
        is SendState.Failed -> MessageStatus.ENROUTE
    }

    private fun SendOutcome.toMessageStatus(): MessageStatus = when (this) {
        SendOutcome.Success -> MessageStatus.DELIVERED
        is SendOutcome.Failure -> MessageStatus.ERROR
    }

    private fun SendState.isTerminal(): Boolean =
        this is SendState.Acked || this is SendState.Delivered || this is SendState.Failed
}
