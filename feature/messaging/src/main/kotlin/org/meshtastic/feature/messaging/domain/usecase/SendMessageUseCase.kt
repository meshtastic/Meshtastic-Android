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
package org.meshtastic.feature.messaging.domain.usecase

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import co.touchlab.kermit.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.data.repository.PacketRepository
import org.meshtastic.core.database.entity.Packet
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.Capabilities
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.prefs.homoglyph.HomoglyphPrefs
import org.meshtastic.feature.messaging.HomoglyphCharacterStringTransformer
import org.meshtastic.feature.messaging.domain.worker.SendMessageWorker
import org.meshtastic.proto.Config
import javax.inject.Inject
import kotlin.math.abs
import kotlin.random.Random

@Suppress("TooGenericExceptionCaught")
class SendMessageUseCase
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val nodeRepository: NodeRepository,
    private val packetRepository: PacketRepository,
    private val radioController: RadioController,
    private val homoglyphEncodingPrefs: HomoglyphPrefs,
    private val workManager: WorkManager,
) {

    @Suppress("NestedBlockDepth", "LongMethod", "CyclomaticComplexMethod")
    suspend operator fun invoke(
        text: String,
        contactKey: String = "0${DataPacket.ID_BROADCAST}",
        replyId: Int? = null,
    ) {
        val channel = contactKey[0].digitToIntOrNull()
        val dest = if (channel != null) contactKey.substring(1) else contactKey

        val ourNode = nodeRepository.ourNodeInfo.value
        val fromId = ourNode?.user?.id ?: DataPacket.ID_LOCAL

        // logic for direct messages
        if (channel == null) {
            val destNode = nodeRepository.getNode(dest)
            val fwVersion = ourNode?.metadata?.firmware_version
            val isClientBase = ourNode?.user?.role == Config.DeviceConfig.Role.CLIENT_BASE
            val capabilities = Capabilities(fwVersion)

            if (capabilities.canSendVerifiedContacts) {
                sendSharedContact(destNode)
            } else {
                if (!destNode.isFavorite && !isClientBase) {
                    favoriteNode(destNode)
                }
            }
        }

        // Apply homoglyph encoding
        val finalMessageText =
            if (homoglyphEncodingPrefs.homoglyphEncodingEnabled) {
                HomoglyphCharacterStringTransformer.optimizeUtf8StringWithHomoglyphs(text)
            } else {
                text
            }

        val packetId = abs(Random.nextInt())
        
        val packet = DataPacket(dest, channel ?: 0, finalMessageText, replyId).apply { 
            from = fromId
            id = packetId
            status = MessageStatus.QUEUED
        }

        val packetToSave = Packet(
            uuid = 0L,
            myNodeNum = ourNode?.num ?: 0,
            packetId = packetId,
            port_num = packet.dataType,
            contact_key = contactKey,
            received_time = nowMillis,
            read = true, 
            data = packet,
            snr = packet.snr,
            rssi = packet.rssi,
            hopsAway = packet.hopsAway,
            filtered = false,
        )

        try {
            // Write to the DB to immediately reflect the queued state on the UI
            packetRepository.insert(packetToSave)

            // Enqueue the durable WorkManager worker
            val workRequest = OneTimeWorkRequestBuilder<SendMessageWorker>()
                .setInputData(workDataOf(SendMessageWorker.KEY_PACKET_ID to packetId))
                .build()

            workManager.enqueueUniqueWork(
                "${SendMessageWorker.WORK_NAME_PREFIX}${packetId}", 
                ExistingWorkPolicy.REPLACE, 
                workRequest
            )
        } catch (ex: Exception) {
            Logger.e(ex) { "Failed to enqueue WorkManager packet" }
        }
    }

    private suspend fun favoriteNode(node: Node) {
        try {
            radioController.favoriteNode(node.num)
        } catch (ex: Exception) {
            Logger.e(ex) { "Favorite node error" }
        }
    }

    private suspend fun sendSharedContact(node: Node) {
        try {
            radioController.sendSharedContact(node.num)
        } catch (ex: Exception) {
            Logger.e(ex) { "Send shared contact error" }
        }
    }
}
