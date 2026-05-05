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
package org.meshtastic.core.data.manager

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.repository.MeshDataHandler
import org.meshtastic.core.repository.MeshServiceNotifications
import org.meshtastic.core.repository.MessageFilter
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.Notification
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.critical_alert
import org.meshtastic.core.resources.getStringSuspend
import org.meshtastic.core.resources.unknown_username
import org.meshtastic.core.resources.waypoint_received
import org.meshtastic.proto.PortNum

/**
 * SDK-era implementation of [MeshDataHandler] focused on message persistence and notifications.
 *
 * The full packet-routing logic (handleReceivedData) is no longer needed — the SDK's packet flow
 * is consumed directly by VMs and SdkStateBridge. This class retains only [rememberDataPacket]
 * which is called by [StoreForwardPacketHandlerImpl] to persist forwarded messages.
 */
@Single
class MessagePersistenceHandler(
    private val nodeRepository: NodeRepository,
    private val packetRepository: Lazy<PacketRepository>,
    private val notificationManager: NotificationManager,
    private val serviceNotifications: MeshServiceNotifications,
    private val radioConfigRepository: RadioConfigRepository,
    private val messageFilter: MessageFilter,
    @Named("ServiceScope") private val scope: CoroutineScope,
) : MeshDataHandler {

    private val rememberDataType =
        setOf(
            PortNum.TEXT_MESSAGE_APP.value,
            PortNum.ALERT_APP.value,
            PortNum.WAYPOINT_APP.value,
            PortNum.NODE_STATUS_APP.value,
        )

    override fun handleReceivedData(
        packet: org.meshtastic.proto.MeshPacket,
        myNodeNum: Int,
        logUuid: String?,
        logInsertJob: kotlinx.coroutines.Job?,
    ) {
        // No-op: Incoming packet routing is handled by SdkStateBridge / VM packet observers.
        // This method exists only to satisfy the MeshDataHandler interface contract.
    }

    override fun rememberDataPacket(dataPacket: DataPacket, myNodeNum: Int, updateNotification: Boolean) {
        if (dataPacket.dataType !in rememberDataType) return
        val fromLocal =
            dataPacket.from == DataPacket.ID_LOCAL || dataPacket.from == DataPacket.nodeNumToDefaultId(myNodeNum)
        val toBroadcast = dataPacket.to == DataPacket.ID_BROADCAST
        val contactId = if (fromLocal || toBroadcast) dataPacket.to else dataPacket.from

        val contactKey = "${dataPacket.channel}$contactId"

        scope.handledLaunch {
            packetRepository.value.apply {
                val existingPackets = findPacketsWithId(dataPacket.id)
                if (existingPackets.isNotEmpty()) {
                    Logger.d {
                        "Skipping duplicate packet: packetId=${dataPacket.id} from=${dataPacket.from} " +
                            "to=${dataPacket.to} contactKey=$contactKey" +
                            " (already have ${existingPackets.size} packet(s))"
                    }
                    return@handledLaunch
                }

                val isFiltered = shouldFilterMessage(dataPacket, contactKey)

                insert(
                    dataPacket,
                    myNodeNum,
                    contactKey,
                    nowMillis,
                    read = fromLocal || isFiltered,
                    filtered = isFiltered,
                )
                if (!isFiltered) {
                    handlePacketNotification(dataPacket, contactKey, updateNotification)
                }
            }
        }
    }

    @Suppress("ReturnCount")
    private suspend fun PacketRepository.shouldFilterMessage(dataPacket: DataPacket, contactKey: String): Boolean {
        val isIgnored = nodeRepository.nodeDBbyID[dataPacket.from]?.isIgnored == true
        if (isIgnored) return true

        if (dataPacket.dataType != PortNum.TEXT_MESSAGE_APP.value) return false
        val isFilteringDisabled = getContactSettings(contactKey).filteringDisabled
        return messageFilter.shouldFilter(dataPacket.text.orEmpty(), isFilteringDisabled)
    }

    private suspend fun handlePacketNotification(
        dataPacket: DataPacket,
        contactKey: String,
        updateNotification: Boolean,
    ) {
        val conversationMuted = packetRepository.value.getContactSettings(contactKey).isMuted
        val nodeMuted = nodeRepository.nodeDBbyID[dataPacket.from]?.isMuted == true
        val isSilent = conversationMuted || nodeMuted
        if (dataPacket.dataType == PortNum.ALERT_APP.value && !isSilent) {
            scope.launch {
                notificationManager.dispatch(
                    Notification(
                        title = getSenderName(dataPacket),
                        message = dataPacket.alert ?: getStringSuspend(Res.string.critical_alert),
                        category = Notification.Category.Alert,
                        contactKey = contactKey,
                    ),
                )
            }
        } else if (updateNotification && !isSilent) {
            scope.handledLaunch { updateNotification(contactKey, dataPacket, isSilent) }
        }
    }

    private suspend fun getSenderName(packet: DataPacket): String {
        if (packet.from == DataPacket.ID_LOCAL) {
            val myId = nodeRepository.getMyId()
            return nodeRepository.nodeDBbyID[myId]?.user?.long_name ?: getStringSuspend(Res.string.unknown_username)
        }
        return nodeRepository.nodeDBbyID[packet.from]?.user?.long_name ?: getStringSuspend(Res.string.unknown_username)
    }

    private suspend fun updateNotification(contactKey: String, dataPacket: DataPacket, isSilent: Boolean) {
        when (dataPacket.dataType) {
            PortNum.TEXT_MESSAGE_APP.value -> {
                val message = dataPacket.text!!
                val channelName =
                    if (dataPacket.to == DataPacket.ID_BROADCAST) {
                        radioConfigRepository.channelSetFlow.first().settings.getOrNull(dataPacket.channel)?.name
                    } else {
                        null
                    }
                serviceNotifications.updateMessageNotification(
                    contactKey,
                    getSenderName(dataPacket),
                    message,
                    dataPacket.to == DataPacket.ID_BROADCAST,
                    channelName,
                    isSilent,
                )
            }

            PortNum.WAYPOINT_APP.value -> {
                val message = getStringSuspend(Res.string.waypoint_received, dataPacket.waypoint!!.name)
                notificationManager.dispatch(
                    Notification(
                        title = getSenderName(dataPacket),
                        message = message,
                        category = Notification.Category.Message,
                        contactKey = contactKey,
                        isSilent = isSilent,
                    ),
                )
            }

            else -> return
        }
    }
}
