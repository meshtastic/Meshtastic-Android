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
import org.meshtastic.sdk.SendState

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

    /**
     * Begin tracking a [MessageHandle] for the given packet ID.
     * Observes state transitions and updates message status in the repository.
     */
    fun track(packetId: Int, handle: MessageHandle) {
        scope.launch {
            activeHandlesMutex.withLock {
                activeHandles[packetId] = handle
            }

            val repository = packetRepository.value
            handle.state
                .onEach { state ->
                    val status = mapSendState(state)
                    Logger.d { "[DeliveryTracker] Packet $packetId → $status" }
                    repository.updateMessageStatus(packetId, status)
                }
                .first { state ->
                    val terminal = state.isTerminal()
                    if (terminal) {
                        activeHandlesMutex.withLock {
                            if (activeHandles[packetId] === handle) {
                                activeHandles.remove(packetId)
                            }
                        }
                    }
                    terminal
                }
        }
    }

    private fun mapSendState(state: SendState): MessageStatus = when (state) {
        SendState.Queued -> MessageStatus.QUEUED
        SendState.Sent -> MessageStatus.ENROUTE
        SendState.Acked -> MessageStatus.DELIVERED
        SendState.Delivered -> MessageStatus.DELIVERED
        is SendState.Failed -> MessageStatus.ERROR
    }

    private fun SendState.isTerminal(): Boolean =
        this is SendState.Acked || this is SendState.Delivered || this is SendState.Failed
}
