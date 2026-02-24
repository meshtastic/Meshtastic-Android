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

import co.touchlab.kermit.Logger
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.Capabilities
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.prefs.homoglyph.HomoglyphPrefs
import org.meshtastic.core.service.ServiceAction
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.feature.messaging.HomoglyphCharacterStringTransformer
import org.meshtastic.proto.Config
import org.meshtastic.proto.SharedContact
import javax.inject.Inject

@Suppress("TooGenericExceptionCaught")
class SendMessageUseCase
@Inject
constructor(
    private val nodeRepository: NodeRepository,
    private val serviceRepository: ServiceRepository,
    private val homoglyphEncodingPrefs: HomoglyphPrefs,
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

        val packet = DataPacket(dest, channel ?: 0, finalMessageText, replyId).apply { from = fromId }

        try {
            serviceRepository.meshService?.send(packet)
        } catch (ex: Exception) {
            Logger.e(ex) { "Failed to send data packet" }
        }
    }

    private suspend fun favoriteNode(node: Node) {
        try {
            serviceRepository.onServiceAction(ServiceAction.Favorite(node))
        } catch (ex: Exception) {
            Logger.e(ex) { "Favorite node error" }
        }
    }

    private suspend fun sendSharedContact(node: Node) {
        try {
            val contact =
                SharedContact(node_num = node.num, user = node.user, manually_verified = node.manuallyVerified)
            serviceRepository.onServiceAction(ServiceAction.SendContact(contact = contact))
        } catch (ex: Exception) {
            Logger.e(ex) { "Send shared contact error" }
        }
    }
}
