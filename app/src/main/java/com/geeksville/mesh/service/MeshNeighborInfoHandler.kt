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
package com.geeksville.mesh.service

import co.touchlab.kermit.Logger
import com.meshtastic.core.strings.getString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.unknown_username
import org.meshtastic.proto.MeshProtos
import org.meshtastic.proto.MeshProtos.MeshPacket
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeshNeighborInfoHandler
@Inject
constructor(
    private val nodeManager: MeshNodeManager,
    private val serviceRepository: ServiceRepository,
    private val commandSender: MeshCommandSender,
    private val serviceBroadcasts: MeshServiceBroadcasts,
) {
    private var scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start(scope: CoroutineScope) {
        this.scope = scope
    }

    fun handleNeighborInfo(packet: MeshPacket) {
        val ni = MeshProtos.NeighborInfo.parseFrom(packet.decoded.payload)

        // Store the last neighbor info from our connected radio
        if (packet.from == nodeManager.myNodeNum) {
            commandSender.lastNeighborInfo = ni
            Logger.d { "Stored last neighbor info from connected radio" }
        }

        // Update Node DB
        nodeManager.nodeDBbyNodeNum[packet.from]?.let { serviceBroadcasts.broadcastNodeChange(it.toNodeInfo()) }

        // Format for UI response
        val requestId = packet.decoded.requestId
        val start = commandSender.neighborInfoStartTimes.remove(requestId)

        // Only show response if packet is addressed to us and we sent a request in the last 3 minutes
        val myNodeNum = nodeManager.myNodeNum
        val isAddressedToUs = packet.to == myNodeNum
        val isRecentRequest = start != null && (System.currentTimeMillis() - start) < NEIGHBOR_RQ_COOLDOWN

        if (isAddressedToUs && isRecentRequest) {
            val neighbors =
                ni.neighborsList.joinToString("\n") { n ->
                    val node = nodeManager.nodeDBbyNodeNum[n.nodeId]
                    val name =
                        node?.let { "${it.longName} (${it.shortName})" } ?: getString(Res.string.unknown_username)
                    "• $name (SNR: ${n.snr})"
                }

            val formatted =
                "Neighbors of ${nodeManager.nodeDBbyNodeNum[packet.from]?.longName ?: "Unknown"}:\n$neighbors"

            val responseText =
                if (start != null) {
                    val elapsedMs = System.currentTimeMillis() - start
                    val seconds = elapsedMs / MILLIS_PER_SECOND
                    Logger.i { "Neighbor info $requestId complete in $seconds s" }
                    String.format(Locale.US, "%s\n\nDuration: %.1f s", formatted, seconds)
                } else {
                    formatted
                }

            serviceRepository.setNeighborInfoResponse(responseText)
        } else {
            Logger.d {
                "Neighbor info response filtered: isAddressedToUs=$isAddressedToUs, isRecentRequest=$isRecentRequest"
            }
        }
    }

    companion object {
        private const val MILLIS_PER_SECOND = 1000.0

        // Only show neighbor info responses for requests sent in the last 3 minutes
        private const val NEIGHBOR_RQ_COOLDOWN = 3 * 60 * 1000L // 3 minutes in milliseconds
    }
}
