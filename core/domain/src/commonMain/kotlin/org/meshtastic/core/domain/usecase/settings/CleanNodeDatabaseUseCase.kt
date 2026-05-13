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
package org.meshtastic.core.domain.usecase.settings

import org.koin.core.annotation.Single
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.repository.NodeRepository
import kotlin.time.Duration.Companion.days

/** Use case for cleaning up nodes from the database. */
@Single
open class CleanNodeDatabaseUseCase
constructor(
    private val nodeRepository: NodeRepository,
    private val radioController: RadioController,
) {
    /** Identifies nodes that match the cleanup criteria. */
    open suspend fun getNodesToClean(
        olderThanDays: Float,
        onlyUnknownNodes: Boolean,
        currentTimeSeconds: Long,
    ): List<Node> {
        val sevenDaysAgoSeconds = currentTimeSeconds - 7.days.inWholeSeconds
        val olderThanTimestamp = currentTimeSeconds - olderThanDays.toInt().days.inWholeSeconds

        val nodesToConsider =
            if (onlyUnknownNodes) {
                val olderNodes = nodeRepository.getNodesOlderThan(olderThanTimestamp.toInt())
                val unknownNodes = nodeRepository.getUnknownNodes()
                olderNodes.filter { itNode -> unknownNodes.any { it.num == itNode.num } }
            } else {
                nodeRepository.getNodesOlderThan(olderThanTimestamp.toInt())
            }

        return nodesToConsider.filterNot { node ->
            (node.hasPKC && node.lastHeard >= sevenDaysAgoSeconds) || node.isIgnored || node.isFavorite
        }
    }

    /** Performs the cleanup of specified nodes. */
    open suspend fun cleanNodes(nodeNums: List<Int>) {
        if (nodeNums.isEmpty()) return

        nodeRepository.deleteNodes(nodeNums)
        for (nodeNum in nodeNums) {
            val packetId = radioController.getPacketId()
            radioController.removeByNodenum(packetId, nodeNum)
        }
    }
}
