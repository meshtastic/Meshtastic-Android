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

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.repository.MeshWorkerManager
import org.meshtastic.core.repository.MessageQueue
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.PersistedPacketId
import org.meshtastic.core.repository.RadioController

/**
 * [MessageQueue] and [MeshWorkerManager] for hosts without a background work scheduler (desktop and web): both hand the
 * packet straight to [radioController] from this process.
 *
 * A send waits out a handshake in progress, so the queued packets `MeshConnectionManager` re-enqueues once the radio's
 * config loads go out when the handshake completes. A packet enqueued while disconnected stays QUEUED for that
 * re-enqueue on the next connect.
 */
class InProcessMessageQueue(
    private val packetRepository: PacketRepository,
    private val radioController: RadioController,
    dispatchers: CoroutineDispatchers,
) : MessageQueue,
    MeshWorkerManager {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)

    override suspend fun enqueue(persistedId: PersistedPacketId) = enqueueSendMessage(persistedId)

    override fun enqueueSendMessage(persistedId: PersistedPacketId) {
        scope.launch { send(persistedId) }
    }

    // A failed or cancelled send rolls its claim back (NonCancellable in the repository); cancellation is rethrown.
    @Suppress("SuspendFunSwallowedCancellation")
    private suspend fun send(persistedId: PersistedPacketId) {
        if (persistedId.uuid <= 0L) return
        val connected = radioController.connectionState.first { it != ConnectionState.Connecting }
        val claimed =
            if (connected == ConnectionState.Connected) packetRepository.claimQueuedPacket(persistedId) else null
        if (claimed?.packet?.status != MessageStatus.QUEUED) return

        try {
            radioController.sendMessage(claimed.packet)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Logger.w(e) { "Failed to send packet ${claimed.packet.id}, re-queuing" }
            packetRepository.rollbackEnroutePacket(claimed.id)
            if (e is CancellationException) throw e
        }
    }
}
