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
package org.meshtastic.core.data.manager

import co.touchlab.kermit.Logger
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.collections.immutable.persistentMapOf
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.repository.NeighborInfoHandler
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.ServiceBroadcasts
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.NeighborInfo

@Single
class NeighborInfoHandlerImpl(
    private val nodeManager: NodeManager,
    private val serviceRepository: ServiceRepository,
    private val serviceBroadcasts: ServiceBroadcasts,
    private val nodeRepository: NodeRepository,
) : NeighborInfoHandler {

    private val startTimes = atomic(persistentMapOf<Int, Long>())

    override var lastNeighborInfo: NeighborInfo? = null

    override fun recordStartTime(requestId: Int) {
        startTimes.update { it.put(requestId, nowMillis) }
    }

    override fun handleNeighborInfo(packet: MeshPacket) {
        val payload = packet.decoded?.payload ?: return
        val ni = NeighborInfo.ADAPTER.decode(payload)

        // Store the last neighbor info from our connected radio
        val from = packet.from
        if (from == nodeManager.myNodeNum.value) {
            lastNeighborInfo = ni
            Logger.d { "Stored last neighbor info from connected radio" }
        }

        // Update Node DB
        nodeManager.nodeDBbyNodeNum[from]?.let { serviceBroadcasts.broadcastNodeChange(it) }

        // Format for UI response
        val requestId = packet.decoded?.request_id ?: 0
        val start = startTimes.value[requestId]
        startTimes.update { it.remove(requestId) }

        val neighbors =
            ni.neighbors.joinToString("\n") { n ->
                val user = nodeRepository.getUser(n.node_id)
                val name = "${user.long_name} (${user.short_name})"
                "• $name (SNR: ${n.snr})"
            }

        val fromUser = nodeRepository.getUser(from)
        val formatted = "Neighbors of ${fromUser.long_name}:\n$neighbors"

        val responseText =
            if (start != null) {
                val elapsedMs = nowMillis - start
                val seconds = elapsedMs / MILLIS_PER_SECOND
                Logger.i { "Neighbor info $requestId complete in $seconds s" }
                "$formatted\n\nDuration: ${NumberFormatter.format(seconds, 1)} s"
            } else {
                formatted
            }

        serviceRepository.setNeighborInfoResponse(responseText)
    }

    companion object {
        private const val MILLIS_PER_SECOND = 1000.0
    }
}
