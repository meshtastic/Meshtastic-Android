package org.meshtastic.feature.messaging.domain.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.meshtastic.core.data.repository.PacketRepository
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.RadioController

@HiltWorker
class SendMessageWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val packetRepository: PacketRepository,
    private val radioController: RadioController
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val packetId = inputData.getInt(KEY_PACKET_ID, 0)
        if (packetId == 0) return Result.failure()

        // Verify we are connected before attempting to send to avoid unnecessary Exception bubbling
        if (radioController.connectionState.value != ConnectionState.Connected) {
            return Result.retry()
        }

        val packetEntity = packetRepository.getPacketByPacketId(packetId)
            ?: return Result.failure() // Packet no longer exists in DB? Do not retry.

        val packetData = packetEntity.packet.data

        return try {
            radioController.sendMessage(packetData)
            packetRepository.updateMessageStatus(packetData, MessageStatus.ENROUTE)
            Result.success()
        } catch (e: Exception) {
            packetRepository.updateMessageStatus(packetData, MessageStatus.ERROR)
            Result.retry()
        }
    }

    companion object {
        const val KEY_PACKET_ID = "packet_id"
        const val WORK_NAME_PREFIX = "send_message_"
    }
}
