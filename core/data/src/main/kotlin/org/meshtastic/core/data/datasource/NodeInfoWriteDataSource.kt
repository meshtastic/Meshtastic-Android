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

package org.meshtastic.core.data.datasource

import org.meshtastic.core.database.entity.MetadataEntity
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.database.entity.NodeEntity

interface NodeInfoWriteDataSource {
    suspend fun upsert(node: NodeEntity)

    suspend fun installConfig(mi: MyNodeEntity, nodes: List<NodeEntity>)

    suspend fun clearNodeDB(preserveFavorites: Boolean)

    suspend fun deleteNode(num: Int)

    suspend fun deleteNodes(nodeNums: List<Int>)

    suspend fun deleteMetadata(num: Int)

    suspend fun upsert(metadata: MetadataEntity)

    suspend fun setNodeNotes(num: Int, notes: String)

    suspend fun backfillDenormalizedNames()
}
