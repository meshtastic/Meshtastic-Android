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
package org.meshtastic.desktop.radio

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.repository.MessageQueue
import org.meshtastic.core.repository.PacketRepository

/**
 * Desktop implementation of [MessageQueue].
 *
 * Unlike Android which uses WorkManager to ensure delivery across app lifecycles, Desktop immediately delegates to the
 * active controller to send the message.
 */
class DesktopMessageQueue(
    private val packetRepository: PacketRepository,
    private val radioController: RadioController,
) : MessageQueue {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun enqueue(packetId: Int) {
        scope.launch {
            if (packetId == 0) return@launch

            // Verify we are connected before attempting to send to avoid unnecessary Exception bubbling
            if (radioController.connectionState.value != ConnectionState.Connected) {
                // In a real desktop environment, we might want a background loop to retry queued messages.
                // For now, it will retry when connection is re-established (handled by
                // MeshConnectionManager.onRadioConfigLoaded).
                return@launch
            }

            val packetData =
                packetRepository.getPacketByPacketId(packetId)
                    ?: return@launch // Packet no longer exists in DB? Do not retry.

            try {
                radioController.sendMessage(packetData)
                packetRepository.updateMessageStatus(packetData, MessageStatus.ENROUTE)
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Logger.w(e) { "Failed to send packet ${packetData.id}, re-queuing" }
                packetRepository.updateMessageStatus(packetData, MessageStatus.QUEUED)
            }
        }
    }
}
