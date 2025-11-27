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

package org.meshtastic.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.google.protobuf.ByteString
import kotlinx.coroutines.flow.Flow
import org.meshtastic.core.database.entity.MetadataEntity
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.database.entity.NodeEntity
import org.meshtastic.core.database.entity.NodeWithRelations
import org.meshtastic.proto.MeshProtos

@Suppress("TooManyFunctions")
@Dao
interface NodeInfoDao {

    /**
     * Verifies a [NodeEntity] before an upsert operation. It handles populating the publicKey for lazy migration,
     * checks for public key conflicts with new nodes, and manages updates to existing nodes, particularly in cases of
     * public key mismatches to prevent potential impersonation or data corruption.
     *
     * @param incomingNode The node entity to be verified.
     * @return A [NodeEntity] that is safe to upsert, or null if the upsert should be aborted (e.g., due to an
     *   impersonation attempt, though this logic is currently commented out).
     */
    private fun getVerifiedNodeForUpsert(incomingNode: NodeEntity): NodeEntity {
        // Populate the NodeEntity.publicKey field from the User.publicKey for consistency
        // and to support lazy migration.
        incomingNode.publicKey = incomingNode.user.publicKey

        // Populate denormalized name columns from the User protobuf for search functionality
        // Only populate if the user is not a placeholder (hwModel != UNSET)
        if (incomingNode.user.hwModel != MeshProtos.HardwareModel.UNSET) {
            incomingNode.longName = incomingNode.user.longName
            incomingNode.shortName = incomingNode.user.shortName
        }

        val existingNodeEntity = getNodeByNum(incomingNode.num)?.node

        return if (existingNodeEntity == null) {
            handleNewNodeUpsertValidation(incomingNode)
        } else {
            handleExistingNodeUpsertValidation(existingNodeEntity, incomingNode)
        }
    }

    /** Validates a new node before it is inserted into the database. */
    private fun handleNewNodeUpsertValidation(newNode: NodeEntity): NodeEntity {
        // Check if the new node's public key (if present and not empty)
        // is already claimed by another existing node.
        if (newNode.publicKey?.isEmpty == false) {
            val nodeWithSamePK = findNodeByPublicKey(newNode.publicKey)
            if (nodeWithSamePK != null && nodeWithSamePK.num != newNode.num) {
                // This is a potential impersonation attempt.
                return nodeWithSamePK
            }
        }
        // If no conflicting public key is found, or if the impersonation check is not active,
        // the new node is considered safe to add.
        return newNode
    }

    private fun handleExistingNodeUpsertValidation(existingNode: NodeEntity, incomingNode: NodeEntity): NodeEntity {
        // A public key is considered matching if the incoming key equals the existing key,
        // OR if the existing key is empty (allowing a new key to be set or an update to proceed).
        val isPublicKeyMatchingOrExistingIsEmpty =
            existingNode.user.publicKey == incomingNode.publicKey || existingNode.user.publicKey.isEmpty

        return if (isPublicKeyMatchingOrExistingIsEmpty) {
            // Keys match or existing key was empty: trust the incoming node data completely.
            // This allows for legitimate updates to user info and other fields.
            val resolvedNotes = if (incomingNode.notes.isBlank()) existingNode.notes else incomingNode.notes
            incomingNode.copy(notes = resolvedNotes)
        } else {
            existingNode.copy(
                lastHeard = incomingNode.lastHeard,
                snr = incomingNode.snr,
                position = incomingNode.position,
                // Preserve the existing user object, but update its internal public key to EMPTY
                // to reflect the conflict state.
                user = existingNode.user.toBuilder().setPublicKey(ByteString.EMPTY).build(),
                publicKey = ByteString.EMPTY,
                notes = existingNode.notes,
            )
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
        """,
    )
    @Transaction
    fun nodeDBbyNum(): Flow<
        Map<
            @MapColumn(columnName = "num")
            Int,
            NodeWithRelations,
            >,
        >

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
    """,
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
    fun clearNodeInfo(preserveFavorites: Boolean) {
        if (preserveFavorites) {
            deleteNonFavoriteNodes()
        } else {
            deleteAllNodes()
        }
    }

    @Query("DELETE FROM nodes WHERE is_favorite = 0")
    fun deleteNonFavoriteNodes()

    @Query("DELETE FROM nodes")
    fun deleteAllNodes()

    @Query("DELETE FROM nodes WHERE num=:num")
    fun deleteNode(num: Int)

    @Query("DELETE FROM nodes WHERE num IN (:nodeNums)")
    fun deleteNodes(nodeNums: List<Int>)

    @Query("SELECT * FROM nodes WHERE last_heard < :lastHeard")
    fun getNodesOlderThan(lastHeard: Int): List<NodeEntity>

    @Query("SELECT * FROM nodes WHERE short_name IS NULL")
    fun getUnknownNodes(): List<NodeEntity>

    @Upsert fun upsert(meta: MetadataEntity)

    @Query("DELETE FROM metadata WHERE num=:num")
    fun deleteMetadata(num: Int)

    @Query("SELECT * FROM nodes WHERE num=:num")
    @Transaction
    fun getNodeByNum(num: Int): NodeWithRelations?

    @Query("SELECT * FROM nodes WHERE public_key = :publicKey LIMIT 1")
    fun findNodeByPublicKey(publicKey: ByteString?): NodeEntity?

    @Upsert fun doUpsert(node: NodeEntity)

    fun upsert(node: NodeEntity) {
        val verifiedNode = getVerifiedNodeForUpsert(node)
        doUpsert(verifiedNode)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putAll(nodes: List<NodeEntity>)

    @Query("UPDATE nodes SET notes = :notes WHERE num = :num")
    fun setNodeNotes(num: Int, notes: String)

    @Transaction
    fun installConfig(mi: MyNodeEntity, nodes: List<NodeEntity>) {
        clearMyNodeInfo()
        setMyNodeInfo(mi)
        putAll(nodes.map { getVerifiedNodeForUpsert(it) })
    }

    /**
     * Backfills longName and shortName columns from the user protobuf for nodes where these columns are NULL. This
     * ensures search functionality works for all nodes. Skips placeholder/default users (hwModel == UNSET).
     */
    @Transaction
    fun backfillDenormalizedNames() {
        val nodes = getAllNodesSnapshot()
        val nodesToUpdate =
            nodes
                .filter { node ->
                    // Only backfill if columns are NULL AND the user is not a placeholder (hwModel != UNSET)
                    (node.longName == null || node.shortName == null) &&
                        node.user.hwModel != MeshProtos.HardwareModel.UNSET
                }
                .map { node -> node.copy(longName = node.user.longName, shortName = node.user.shortName) }
        if (nodesToUpdate.isNotEmpty()) {
            putAll(nodesToUpdate)
        }
    }

    @Query("SELECT * FROM nodes")
    fun getAllNodesSnapshot(): List<NodeEntity>
}
