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

    private fun handleExistingNodeUpsertValidation(existingNode: NodeEntity, incomingNode: NodeEntity): NodeEntity {
        val isPlaceholder = incomingNode.user.hw_model == HardwareModel.UNSET
        val hasExistingUser = existingNode.user.hw_model != HardwareModel.UNSET
        val isDefaultName = incomingNode.user.long_name?.matches(Regex("^Meshtastic [0-9a-fA-F]{4}$")) == true

        val shouldPreserve = hasExistingUser && isPlaceholder && isDefaultName

        if (shouldPreserve) {
            // Preserve existing name and user info, but update metadata like lastHeard, SNR, and position.
            val resolvedNotes = if (incomingNode.notes.isBlank()) existingNode.notes else incomingNode.notes
            return existingNode.copy(
                lastHeard = incomingNode.lastHeard,
                snr = incomingNode.snr,
                rssi = incomingNode.rssi,
                position = incomingNode.position,
                hopsAway = incomingNode.hopsAway,
                deviceTelemetry = incomingNode.deviceTelemetry,
                environmentTelemetry = incomingNode.environmentTelemetry,
                powerTelemetry = incomingNode.powerTelemetry,
                paxcounter = incomingNode.paxcounter,
                channel = incomingNode.channel,
                viaMqtt = incomingNode.viaMqtt,
                isFavorite = incomingNode.isFavorite,
                isIgnored = incomingNode.isIgnored,
                isMuted = incomingNode.isMuted,
                notes = resolvedNotes,
            )
        }

        // A public key is considered matching if the incoming key equals the existing key,
        // OR if the existing key is empty (allowing a new key to be set or an update to proceed).
        val existingResolvedKey = existingNode.publicKey ?: existingNode.user.public_key
        val isPublicKeyMatchingOrExistingIsEmpty = existingResolvedKey == incomingNode.publicKey || !existingNode.hasPKC

        val resolvedNotes = if (incomingNode.notes.isBlank()) existingNode.notes else incomingNode.notes

        return if (isPublicKeyMatchingOrExistingIsEmpty) {
            // Keys match or existing key was empty: trust the incoming node data completely.
            // This allows for legitimate updates to user info and other fields.
            incomingNode.copy(notes = resolvedNotes)
        } else {
            // Public key mismatch: This could be a factory reset or a hardware ID collision.
            // We allow the name and user info to update, but we clear the public key
            // to indicate that this node is no longer "verified" against the previous key.
            incomingNode.copy(
                user = incomingNode.user.copy(public_key = NodeEntity.ERROR_BYTE_STRING),
                publicKey = NodeEntity.ERROR_BYTE_STRING,
                notes = resolvedNotes,
            )
        }
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
