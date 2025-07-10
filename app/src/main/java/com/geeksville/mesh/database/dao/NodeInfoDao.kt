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

package com.geeksville.mesh.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.geeksville.mesh.android.BuildUtils.warn
import com.geeksville.mesh.database.entity.MetadataEntity
import com.geeksville.mesh.database.entity.MyNodeEntity
import com.geeksville.mesh.database.entity.NodeEntity
import com.geeksville.mesh.database.entity.NodeWithRelations
import com.google.protobuf.ByteString
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions")
@Dao
interface NodeInfoDao {

    // Helper function to contain all validation logic
    private fun getVerifiedNodeForUpsert(node: NodeEntity): NodeEntity? {
        // Populate the new publicKey field for lazy migration
        node.publicKey = node.user.publicKey

        val existingNode = getNodeByNum(node.num)?.node

        return if (existingNode == null) {
            // This is a new node. We must check if its public key is already claimed by another node.
            if (node.publicKey != null && node.publicKey?.isEmpty == false) {
                val nodeWithSamePK = findNodeByPublicKey(node.publicKey)
                if (nodeWithSamePK != null && nodeWithSamePK.num != node.num) {
                    // This is the impersonation attempt we want to block.
                    @Suppress("MaxLineLength")
                    warn("NodeInfoDao: Blocking new node #${node.num} because its public key is already used by #${nodeWithSamePK.num}.")
                    return null // ABORT
                }
            }
            // If we're here, the new node is safe to add.
             node
        } else {
            // This is an update to an existing node.
            val keyMatch =
                existingNode.user.publicKey == node.user.publicKey || existingNode.user.publicKey.isEmpty
             if (keyMatch) {
                // Keys match, trust the incoming node completely.
                // This allows for legit nodeId changes etc.
                node
            } else {
                // Keys do NOT match. This is a potential attack.
                // Log it, and create a NEW entity based on the EXISTING trusted one,
                // only updating dynamic data and setting the public key to EMPTY to signal a conflict.
                @Suppress("MaxLineLength")
                warn("NodeInfoDao: Received packet for #${node.num} with non-matching public key. Identity data ignored, key set to EMPTY.")
                existingNode.copy(
                    lastHeard = node.lastHeard,
                    snr = node.snr,
                    position = node.position,
                    user = existingNode.user.toBuilder().setPublicKey(ByteString.EMPTY).build(),
                    publicKey = ByteString.EMPTY
                )
            }
        }
    }

    @Query("SELECT * FROM my_node")
    fun getMyNodeInfo(): Flow<MyNodeEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun setMyNodeInfo(myInfo: MyNodeEntity)

    @Query("DELETE FROM my_node")
    fun clearMyNodeInfo()

    @Query(
        """
        SELECT * FROM nodes
        ORDER BY CASE
            WHEN num = (SELECT myNodeNum FROM my_node LIMIT 1) THEN 0
            ELSE 1
        END,
        last_heard DESC
        """
    )
    @Transaction
    fun nodeDBbyNum(): Flow<Map<@MapColumn(columnName = "num") Int, NodeWithRelations>>

    @Query(
        """
    WITH OurNode AS (
        SELECT latitude, longitude
        FROM nodes
        WHERE num = (SELECT myNodeNum FROM my_node LIMIT 1)
    )
    SELECT * FROM nodes
    WHERE (:includeUnknown = 1 OR short_name IS NOT NULL)
        AND (:filter = ''
            OR (long_name LIKE '%' || :filter || '%'
            OR short_name LIKE '%' || :filter || '%'
            OR printf('!%08x', CASE WHEN num < 0 THEN num + 4294967296 ELSE num END) LIKE '%' || :filter || '%'
            OR CAST(CASE WHEN num < 0 THEN num + 4294967296 ELSE num END AS TEXT) LIKE '%' || :filter || '%'))
        AND (:lastHeardMin = -1 OR last_heard >= :lastHeardMin)
        AND (:hopsAwayMax = -1 OR (hops_away <= :hopsAwayMax AND hops_away >= 0) OR num = (SELECT myNodeNum FROM my_node LIMIT 1))
    ORDER BY CASE
        WHEN num = (SELECT myNodeNum FROM my_node LIMIT 1) THEN 0
        ELSE 1
    END,
    CASE
        WHEN :sort = 'last_heard' THEN last_heard * -1
        WHEN :sort = 'alpha' THEN UPPER(long_name) 
        WHEN :sort = 'distance' THEN
            CASE
                WHEN latitude IS NULL OR longitude IS NULL OR
                    (latitude = 0.0 AND longitude = 0.0) THEN 999999999
                ELSE
                    (latitude - (SELECT latitude FROM OurNode)) *
                    (latitude - (SELECT latitude FROM OurNode)) +
                    (longitude - (SELECT longitude FROM OurNode)) *
                    (longitude - (SELECT longitude FROM OurNode))
            END
        WHEN :sort = 'hops_away' THEN
            CASE
                WHEN hops_away = -1 THEN 999999999
                ELSE hops_away
            END
        WHEN :sort = 'channel' THEN channel
        WHEN :sort = 'via_mqtt' THEN via_mqtt
        WHEN :sort = 'via_favorite' THEN is_favorite * -1
        ELSE 0
    END ASC,
    last_heard DESC
    """
    )
    @Transaction
    fun getNodes(
        sort: String,
        filter: String,
        includeUnknown: Boolean,
        hopsAwayMax: Int,
        lastHeardMin: Int,
    ): Flow<List<NodeWithRelations>>

    @Transaction
    fun upsert(node: NodeEntity) {
        getVerifiedNodeForUpsert(node)?.let { doUpsert(it) }
    }

    @Suppress("NestedBlockDepth")
    @Transaction
    fun putAll(nodes: List<NodeEntity>) {
        val safeNodes = nodes.mapNotNull { getVerifiedNodeForUpsert(it) }
        if (safeNodes.isNotEmpty()) {
            doPutAll(safeNodes)
        }
    }

    @Query("DELETE FROM nodes")
    fun clearNodeInfo()

    @Query("DELETE FROM nodes WHERE num=:num")
    fun deleteNode(num: Int)

    @Upsert
    fun upsert(meta: MetadataEntity)

    @Query("DELETE FROM metadata WHERE num=:num")
    fun deleteMetadata(num: Int)

    @Query("SELECT * FROM nodes WHERE num=:num")
    @Transaction
    fun getNodeByNum(num: Int): NodeWithRelations?

    @Query("SELECT * FROM nodes WHERE public_key = :publicKey LIMIT 1")
    fun findNodeByPublicKey(publicKey: ByteString?): NodeEntity?

    @Upsert
    fun doUpsert(node: NodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun doPutAll(nodes: List<NodeEntity>)
}
