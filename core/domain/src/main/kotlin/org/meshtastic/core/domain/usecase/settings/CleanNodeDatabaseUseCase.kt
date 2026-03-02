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
package org.meshtastic.core.domain.usecase.settings

import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.RadioController
import javax.inject.Inject
import kotlin.time.Duration.Companion.days

/**
 * Use case for cleaning up nodes from the database.
 */
class CleanNodeDatabaseUseCase @Inject constructor(
    private val nodeRepository: NodeRepository,
    private val radioController: RadioController,
) {
    /**
     * Identifies nodes that match the cleanup criteria.
     */
    suspend fun getNodesToClean(
        olderThanDays: Float,
        onlyUnknownNodes: Boolean,
        currentTimeSeconds: Long,
    ): List<Node> {
        val sevenDaysAgoSeconds = currentTimeSeconds - 7.days.inWholeSeconds
        val olderThanTimestamp = currentTimeSeconds - olderThanDays.toInt().days.inWholeSeconds

        val nodesToConsider = if (onlyUnknownNodes) {
            val olderNodes = nodeRepository.getNodesOlderThan(olderThanTimestamp.toInt())
            val unknownNodes = nodeRepository.getUnknownNodes()
            olderNodes.filter { itNode -> unknownNodes.any { it.num == itNode.num } }
        } else {
            nodeRepository.getNodesOlderThan(olderThanTimestamp.toInt())
        }

        return nodesToConsider
            .filterNot { node ->
                (node.hasPKC && node.lastHeard >= sevenDaysAgoSeconds) ||
                node.isIgnored ||
                node.isFavorite
            }
            .map { it.toModel() }
    }

    /**
     * Performs the cleanup of specified nodes.
     */
    suspend fun cleanNodes(nodeNums: List<Int>) {
        if (nodeNums.isEmpty()) return
        
        nodeRepository.deleteNodes(nodeNums)
        val packetId = radioController.getPacketId()
        for (nodeNum in nodeNums) {
            radioController.removeByNodenum(packetId, nodeNum)
        }
    }
}
