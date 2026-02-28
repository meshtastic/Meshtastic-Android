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
package org.meshtastic.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import okio.ByteString
import org.meshtastic.core.database.entity.MetadataEntity
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.database.entity.NodeEntity
import org.meshtastic.core.database.entity.NodeWithRelations
import org.meshtastic.proto.HardwareModel

@Suppress("TooManyFunctions")
@Dao
interface NodeInfoDao {

    companion object {
        const val KEY_SIZE = 32
    }

    /**
     * Verifies a [NodeEntity] before an upsert operation. It handles populating the publicKey for lazy migration,
     * checks for public key conflicts with new nodes, and manages updates to existing nodes, particularly in cases of
     * public key mismatches to prevent potential impersonation or data corruption.
     *
     * @param incomingNode The node entity to be verified.
     * @return A [NodeEntity] that is safe to upsert, or null if the upsert should be aborted (e.g., due to an
     *   impersonation attempt, though this logic is currently commented out).
     */
    private suspend fun getVerifiedNodeForUpsert(incomingNode: NodeEntity): NodeEntity {
        // Populate the NodeEntity.publicKey field from the User.publicKey for consistency
        // and to support lazy migration.
        incomingNode.publicKey = incomingNode.user.public_key

        // Populate denormalized name columns from the User protobuf for search functionality
        // Only populate if the user is not a placeholder (hwModel != UNSET); otherwise keep them null
        if (incomingNode.user.hw_model != HardwareModel.UNSET) {
            incomingNode.longName = incomingNode.user.long_name
            incomingNode.shortName = incomingNode.user.short_name
        } else {
            incomingNode.longName = null
            incomingNode.shortName = null
        }

        val existingNodeEntity = getNodeByNum(incomingNode.num)?.node

        return if (existingNodeEntity == null) {
            handleNewNodeUpsertValidation(incomingNode)
        } else {
            handleExistingNodeUpsertValidation(existingNodeEntity, incomingNode)
        }
    }

    /** Validates a new node before it is inserted into the database. */
    private suspend fun handleNewNodeUpsertValidation(newNode: NodeEntity): NodeEntity {
        // Check if the new node's public key (if present and not empty)
        // is already claimed by another existing node.
        if ((newNode.publicKey?.size ?: 0) > 0) {
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

    /**
     * Resolves the public key for an existing node during an update.
     *
     * This function implements safety checks to prevent public key conflicts (PKC) and ensure robust handling of key
     * updates.
     *
     * @param existingNode The current state of the node in the database.
     * @param incomingNode The new node data being upserted.
     * @return The resolved [ByteString] for the public key:
     * - [NodeEntity.ERROR_BYTE_STRING]: If there is a mismatch between a valid existing key and a new incoming key.
     * - `incomingNode.publicKey`: If the incoming key is new, matches the existing one, or if recovering from an error
     *   state.
     * - `existingNode.publicKey`: If the incoming update has no key, or if the user is licensed but already has a valid
     *   key (prevents wiping).
     * - [ByteString.EMPTY]: If the user is licensed and didn't previously have a key (or if key is explicitly cleared).
     */
    private fun resolvePublicKey(existingNode: NodeEntity, incomingNode: NodeEntity): ByteString? {
        val existingKey = existingNode.publicKey ?: existingNode.user.public_key
        val incomingKey = incomingNode.publicKey

        val incomingHasKey = (incomingKey?.size ?: 0) == KEY_SIZE
        val existingHasKey = (existingKey?.size ?: 0) == KEY_SIZE && existingKey != NodeEntity.ERROR_BYTE_STRING

        return when {
            incomingHasKey -> {
                if (existingHasKey && incomingKey != existingKey) {
                    // Actual mismatch between two non-empty keys
                    NodeEntity.ERROR_BYTE_STRING
                } else {
                    // New key, same key, or recovery from Error state
                    incomingKey
                }
            }
            existingHasKey -> existingKey
            incomingNode.user.is_licensed -> ByteString.EMPTY
            else -> existingKey
        }
    }

    /**
     * Handles the validation logic when upserting an existing node.
     *
     * It distinguishes between two scenarios:
     * 1. **Preservation**: If the incoming update is a placeholder (unset HW model) with a default name, and the
     *    existing node has full user info, we preserve the existing identity (user, keys, names, verification) while
     *    updating telemetry and status fields from the incoming packet.
     * 2. **Update**: If it's a normal update, we validate the public key using [resolvePublicKey] to prevent conflicts
     *    or accidental key wiping, and then update the node.
     */
    @Suppress("CyclomaticComplexMethod", "MagicNumber")
    private fun handleExistingNodeUpsertValidation(existingNode: NodeEntity, incomingNode: NodeEntity): NodeEntity {
        val resolvedNotes = incomingNode.notes.ifBlank { existingNode.notes }

        val isPlaceholder = incomingNode.user.hw_model == HardwareModel.UNSET
        val hasExistingUser = existingNode.user.hw_model != HardwareModel.UNSET
        val isDefaultName = incomingNode.user.long_name?.matches(Regex("^Meshtastic [0-9a-fA-F]{4}$")) == true

        if (hasExistingUser && isPlaceholder && isDefaultName) {
            return incomingNode.copy(
                user = existingNode.user,
                publicKey = existingNode.publicKey,
                longName = existingNode.longName,
                shortName = existingNode.shortName,
                manuallyVerified = existingNode.manuallyVerified,
                notes = resolvedNotes,
            )
        }

        val resolvedKey = resolvePublicKey(existingNode, incomingNode)

        return incomingNode.copy(
            user = incomingNode.user.copy(public_key = resolvedKey ?: ByteString.EMPTY),
            publicKey = resolvedKey,
            notes = resolvedNotes,
        )
    }

    @Query("SELECT * FROM my_node")
    fun getMyNodeInfo(): Flow<MyNodeEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setMyNodeInfo(myInfo: MyNodeEntity)

    @Query("DELETE FROM my_node")
    suspend fun clearMyNodeInfo()

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
    suspend fun clearNodeInfo(preserveFavorites: Boolean) {
        if (preserveFavorites) {
            deleteNonFavoriteNodes()
        } else {
            deleteAllNodes()
        }
    }

    @Query("DELETE FROM nodes WHERE is_favorite = 0")
    suspend fun deleteNonFavoriteNodes()

    @Query("DELETE FROM nodes")
    suspend fun deleteAllNodes()

    @Query("DELETE FROM nodes WHERE num=:num")
    suspend fun deleteNode(num: Int)

    @Query("DELETE FROM nodes WHERE num IN (:nodeNums)")
    suspend fun deleteNodes(nodeNums: List<Int>)

    @Query("SELECT * FROM nodes WHERE last_heard < :lastHeard")
    suspend fun getNodesOlderThan(lastHeard: Int): List<NodeEntity>

    @Query("SELECT * FROM nodes WHERE short_name IS NULL")
    suspend fun getUnknownNodes(): List<NodeEntity>

    @Upsert suspend fun upsert(meta: MetadataEntity)

    @Query("DELETE FROM metadata WHERE num=:num")
    suspend fun deleteMetadata(num: Int)

    @Query("SELECT * FROM nodes WHERE num=:num")
    @Transaction
    suspend fun getNodeByNum(num: Int): NodeWithRelations?

    @Query("SELECT * FROM nodes WHERE public_key = :publicKey LIMIT 1")
    suspend fun findNodeByPublicKey(publicKey: ByteString?): NodeEntity?

    @Upsert suspend fun doUpsert(node: NodeEntity)

    suspend fun upsert(node: NodeEntity) {
        val verifiedNode = getVerifiedNodeForUpsert(node)
        doUpsert(verifiedNode)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAll(nodes: List<NodeEntity>)

    @Query("UPDATE nodes SET notes = :notes WHERE num = :num")
    suspend fun setNodeNotes(num: Int, notes: String)

    @Transaction
    suspend fun installConfig(mi: MyNodeEntity, nodes: List<NodeEntity>) {
        clearMyNodeInfo()
        setMyNodeInfo(mi)
        putAll(nodes.map { getVerifiedNodeForUpsert(it) })
    }

    /**
     * Backfills longName and shortName columns from the user protobuf for nodes where these columns are NULL. This
     * ensures search functionality works for all nodes. Skips placeholder/default users (hwModel == UNSET).
     */
    @Transaction
    suspend fun backfillDenormalizedNames() {
        val nodes = getAllNodesSnapshot()
        val nodesToUpdate =
            nodes
                .filter { node ->
                    // Only backfill if columns are NULL AND the user is not a placeholder (hwModel != UNSET)
                    (node.longName == null || node.shortName == null) && node.user.hw_model != HardwareModel.UNSET
                }
                .map { node -> node.copy(longName = node.user.long_name, shortName = node.user.short_name) }
        if (nodesToUpdate.isNotEmpty()) {
            putAll(nodesToUpdate)
        }
    }

    @Query("SELECT * FROM nodes")
    suspend fun getAllNodesSnapshot(): List<NodeEntity>
}
