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
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.collections.immutable.persistentMapOf
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.repository.NeighborInfoHandler
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.NeighborInfo
import org.meshtastic.sdk.NeighborInfo as SdkNeighborInfo

@Single
class NeighborInfoHandlerImpl(
    private val nodeRepository: NodeRepository,
    private val serviceRepository: ServiceRepository,
) : NeighborInfoHandler {

    private val startTimes = atomic(persistentMapOf<Int, Long>())

    override var lastNeighborInfo: NeighborInfo? = null

    override fun recordStartTime(requestId: Int) {
        startTimes.update { it.put(requestId, nowMillis) }
    }

    override fun handleNeighborInfo(packet: MeshPacket) {
        val payload = packet.decoded?.payload ?: return
        val ni = NeighborInfo.ADAPTER.decode(payload)

        val from = packet.from
        if (from == nodeRepository.myNodeNum.value) {
            lastNeighborInfo = ni
            Logger.d { "Stored last neighbor info from connected radio" }
        }

        val requestId = packet.decoded?.request_id ?: 0
        val start = startTimes.value[requestId]
        startTimes.update { it.remove(requestId) }

        val formatted =
            SdkNeighborInfo
                .fromProto(
                    reportingNode = from,
                    neighborNodeIds = ni.neighbors.map { it.node_id },
                    snrValues = ni.neighbors.map { it.snr },
                ).format { nodeId ->
                    val user = nodeRepository.getUser(nodeId.raw)
                    "${user.long_name} (${user.short_name})"
                }

        val responseText =
            if (start != null) {
                val elapsedMs = nowMillis - start
                val seconds = elapsedMs / MILLIS_PER_SECOND
                Logger.i { "Neighbor info $requestId complete in $seconds s" }
                "$formatted\nDuration: ${NumberFormatter.format(seconds, 1)} s"
            } else {
                formatted
            }

        serviceRepository.setNeighborInfoResponse(responseText)
    }

    companion object {
        private const val MILLIS_PER_SECOND = 1000.0
    }
}
