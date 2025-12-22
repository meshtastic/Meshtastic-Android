/*
 * Copyright (c) 2025 Meshtastic LLC
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

package com.geeksville.mesh.service

import com.geeksville.mesh.concurrent.handledLaunch
import com.geeksville.mesh.util.ignoreException
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.meshtastic.core.data.repository.PacketRepository
import org.meshtastic.core.database.entity.ReactionEntity
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.service.ServiceAction
import org.meshtastic.proto.Portnums
import org.meshtastic.proto.user
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeshActionHandler
@Inject
constructor(
    private val nodeManager: MeshNodeManager,
    private val commandSender: MeshCommandSender,
    private val packetRepository: Lazy<PacketRepository>,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun onServiceAction(action: ServiceAction) {
        ignoreException {
            val myNodeNum = nodeManager.myNodeNum ?: return@ignoreException
            when (action) {
                is ServiceAction.Favorite -> {
                    val node = action.node
                    commandSender.sendAdmin(myNodeNum) {
                        if (node.isFavorite) removeFavoriteNode = node.num else setFavoriteNode = node.num
                    }
                    nodeManager.updateNodeInfo(node.num) { it.isFavorite = !node.isFavorite }
                }
                is ServiceAction.Ignore -> {
                    val node = action.node
                    commandSender.sendAdmin(myNodeNum) {
                        if (node.isIgnored) removeIgnoredNode = node.num else setIgnoredNode = node.num
                    }
                    nodeManager.updateNodeInfo(node.num) { it.isIgnored = !node.isIgnored }
                }
                is ServiceAction.Reaction -> {
                    val channel = action.contactKey[0].digitToInt()
                    val destId = action.contactKey.substring(1)
                    val dataPacket =
                        org.meshtastic.core.model.DataPacket(
                            to = destId,
                            dataType = Portnums.PortNum.TEXT_MESSAGE_APP_VALUE,
                            bytes = action.emoji.encodeToByteArray(),
                            channel = channel,
                            replyId = action.replyId,
                            wantAck = false,
                        )
                    commandSender.sendData(dataPacket)
                    rememberReaction(action)
                }
                is ServiceAction.ImportContact -> {
                    val verifiedContact = action.contact.toBuilder().setManuallyVerified(true).build()
                    commandSender.sendAdmin(myNodeNum) { addContact = verifiedContact }
                    nodeManager.handleReceivedUser(
                        verifiedContact.nodeNum,
                        verifiedContact.user,
                        manuallyVerified = true,
                    )
                }
                is ServiceAction.SendContact -> {
                    commandSender.sendAdmin(myNodeNum) { addContact = action.contact }
                }
                is ServiceAction.GetDeviceMetadata -> {
                    commandSender.sendAdmin(action.destNum, wantResponse = true) { getDeviceMetadataRequest = true }
                }
            }
        }
    }

    private fun rememberReaction(action: ServiceAction.Reaction) {
        scope.handledLaunch {
            val reaction =
                ReactionEntity(
                    replyId = action.replyId,
                    userId = DataPacket.ID_LOCAL,
                    emoji = action.emoji,
                    timestamp = System.currentTimeMillis(),
                    snr = 0f,
                    rssi = 0,
                    hopsAway = 0,
                )
            packetRepository.get().insertReaction(reaction)
        }
    }

    fun handleSetOwner(u: org.meshtastic.core.model.MeshUser, myNodeNum: Int) {
        commandSender.sendAdmin(myNodeNum) {
            setOwner = user {
                id = u.id
                longName = u.longName
                shortName = u.shortName
                isLicensed = u.isLicensed
            }
        }
        nodeManager.handleReceivedUser(
            myNodeNum,
            user {
                id = u.id
                longName = u.longName
                shortName = u.shortName
                isLicensed = u.isLicensed
            },
        )
    }
}
