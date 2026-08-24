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
import kotlinx.coroutines.CancellationException
import org.koin.android.annotation.KoinWorker
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.PersistedPacket
import org.meshtastic.core.repository.PersistedPacketId
import org.meshtastic.core.repository.RadioController

@KoinWorker
class SendMessageWorker(
    context: Context,
    params: WorkerParameters,
    private val packetRepository: PacketRepository,
    private val radioController: RadioController,
) : CoroutineWorker(context, params) {

    @Suppress("TooGenericExceptionCaught", "SwallowedException", "ReturnCount")
    override suspend fun doWork(): Result {
        val packetUuid = inputData.getLong(KEY_PACKET_UUID, 0L)
        val myNodeNum = inputData.getInt(KEY_MY_NODE_NUM, 0)
        val persistedId = packetUuid.takeIf { it > 0L }?.let { PersistedPacketId(myNodeNum = myNodeNum, uuid = it) }

        @Suppress("DEPRECATION")
        val legacyPacketId = if (persistedId == null) inputData.getInt(KEY_PACKET_ID, 0) else 0
        if (persistedId == null && legacyPacketId == 0) return Result.failure()

        // Verify we are connected before attempting to send to avoid unnecessary Exception bubbling
        if (radioController.connectionState.value != ConnectionState.Connected) {
            return Result.retry()
        }

        val claimed = claimPacket(persistedId, legacyPacketId) ?: return Result.failure()
        // The DAO returns the pre-transition packet only to the caller that atomically claimed QUEUED -> ENROUTE.
        if (claimed.packet.status != MessageStatus.QUEUED) return Result.success()

        return try {
            radioController.sendMessage(claimed.packet)
            Result.success()
        } catch (e: Exception) {
            packetRepository.rollbackEnroutePacket(claimed.id)
            if (e is CancellationException) throw e
            Result.retry()
        }
    }

    private suspend fun claimPacket(id: PersistedPacketId?, legacyPacketId: Int): PersistedPacket? = if (id != null) {
        packetRepository.claimQueuedPacket(id)
    } else {
        packetRepository.claimQueuedPacketByPacketIdIfUnique(legacyPacketId)
    }

    companion object {
        const val KEY_PACKET_UUID = "packet_uuid"
        const val KEY_MY_NODE_NUM = "my_node_num"

        /** Input key retained only so WorkManager jobs persisted by older app versions can finish after an upgrade. */
        @Deprecated("New work must use KEY_PACKET_UUID and KEY_MY_NODE_NUM")
        const val KEY_PACKET_ID = "packet_id"

        const val WORK_NAME_PREFIX = "send_message_uuid_"
    }
}
