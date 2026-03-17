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
package org.meshtastic.core.service.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.koin.android.annotation.KoinWorker
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.repository.PacketRepository

@KoinWorker
class SendMessageWorker(
    context: Context,
    params: WorkerParameters,
    private val packetRepository: PacketRepository,
    private val radioController: RadioController,
) : CoroutineWorker(context, params) {

    @Suppress("TooGenericExceptionCaught", "SwallowedException", "ReturnCount")
    override suspend fun doWork(): Result {
        val packetId = inputData.getInt(KEY_PACKET_ID, 0)
        if (packetId == 0) return Result.failure()

        // Verify we are connected before attempting to send to avoid unnecessary Exception bubbling
        if (radioController.connectionState.value != ConnectionState.Connected) {
            return Result.retry()
        }

        val packetData =
            packetRepository.getPacketByPacketId(packetId)
                ?: return Result.failure() // Packet no longer exists in DB? Do not retry.

        return try {
            radioController.sendMessage(packetData)
            packetRepository.updateMessageStatus(packetData, MessageStatus.ENROUTE)
            Result.success()
        } catch (e: Exception) {
            packetRepository.updateMessageStatus(packetData, MessageStatus.QUEUED)
            Result.retry()
        }
    }

    companion object {
        const val KEY_PACKET_ID = "packet_id"
        const val WORK_NAME_PREFIX = "send_message_"
    }
}
