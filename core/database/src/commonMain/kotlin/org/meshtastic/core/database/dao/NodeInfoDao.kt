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
package org.meshtastic.core.database.dao

import androidx.room3.Dao
import androidx.room3.MapColumn
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow
import okio.ByteString
import org.meshtastic.core.common.util.crc32
import org.meshtastic.core.database.entity.MetadataEntity
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.database.entity.NodeEntity
import org.meshtastic.core.database.entity.NodeWithRelations
import org.meshtastic.core.database.validDeviceIdOrNull
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.mergePowerChannelLabel
import org.meshtastic.proto.HardwareModel

@Suppress("TooManyFunctions")
@Dao
interface NodeInfoDao {

    companion object {
        const val KEY_SIZE = 32

        /** SQLite has a limit of ~999 bind parameters per query. */
        const val MAX_BIND_PARAMS = 999
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
                // Firmware 2.8+ derives the node number from the public key (num = crc32(key)),
                // so a known key arriving under its canonical num is that node renumbering itself
                // (e.g. after a 2.7→2.8 upgrade), not impersonation — migrate the old identity.
                return if (newNode.hasCanonicalNum()) {
                    migrateNodeIdentity(from = nodeWithSamePK, to = newNode)
                    newNode
                } else {
                    // This is a potential impersonation attempt.
                    nodeWithSamePK
                }
            }
        }
        // If no conflicting public key is found, or if the impersonation check is not active,
        // the new node is considered safe to add.
        return newNode
    }

    /**
     * True when this node's num is the canonical pubkey-derived number introduced by firmware 2.8 (`my_node_num =
     * crc32(public_key)`, see NodeDB::createNewIdentity). A canonical num proves the num/key pairing was not chosen
     * independently of the key, so a same-key-new-num sighting is a renumber rather than a spoof.
     */
    private fun NodeEntity.hasCanonicalNum(): Boolean {
        val key = publicKey ?: user.public_key
        return key.size == KEY_SIZE && key != NodeEntity.ERROR_BYTE_STRING && key.crc32().toInt() == num
    }

    /**
     * Moves a node's identity from the [from] row to the incoming [to] entity after a legitimate renumber (the local
     * device reconciled by [migrateLocalNodeIdentity], or a mesh peer whose new num is its canonical pubkey-derived
     * number). Carries the app-local state the mesh doesn't broadcast, drops the stale row, and re-keys the DM thread
     * so the conversation follows the node. The caller upserts [to] afterwards.
     */
    private suspend fun migrateNodeIdentity(from: NodeEntity, to: NodeEntity) {
        if (to.notes.isBlank()) to.notes = from.notes
        if (to.powerChannelLabels.isEmpty()) to.powerChannelLabels = from.powerChannelLabels
        to.manuallyVerified = to.manuallyVerified || from.manuallyVerified
        to.isFavorite = to.isFavorite || from.isFavorite
        to.isIgnored = to.isIgnored || from.isIgnored
        to.isMuted = to.isMuted || from.isMuted
        deleteNode(from.num)
        deleteMetadata(from.num)
        val oldId = from.user.id.ifEmpty { NodeAddress.numToDefaultId(from.num) }
        val newId = to.user.id.ifEmpty { NodeAddress.numToDefaultId(to.num) }
        if (oldId != newId) {
            remapDmContactKey(oldId, newId)
            remapDmContactSettings(oldId, newId)
            deleteDmContactSettings(oldId)
        }
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
        val existingHasKey = existingKey.size == KEY_SIZE && existingKey != NodeEntity.ERROR_BYTE_STRING

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
     *
     * [trustIncomingKey] skips the mismatch check and accepts a valid incoming key as-is. Set only for the local node
     * during a config install: the connected device is authoritative for its own key over the local link, and an
     * erase-and-reflash legitimately re-keys it (a mismatch there would otherwise poison the local node with
     * [NodeEntity.ERROR_BYTE_STRING] and break PKI traffic until app data is cleared).
     */
    @Suppress("CyclomaticComplexMethod", "MagicNumber")
    private fun handleExistingNodeUpsertValidation(
        existingNode: NodeEntity,
        incomingNode: NodeEntity,
        trustIncomingKey: Boolean = false,
    ): NodeEntity {
        val resolvedNotes = incomingNode.notes.ifBlank { existingNode.notes }
        val resolvedPowerChannelLabels = incomingNode.powerChannelLabels.ifEmpty { existingNode.powerChannelLabels }

        val isPlaceholder = incomingNode.user.hw_model == HardwareModel.UNSET
        val hasExistingUser = existingNode.user.hw_model != HardwareModel.UNSET
        val isDefaultName = incomingNode.user.long_name.matches(Regex("^Meshtastic [0-9a-fA-F]{4}$"))

        if (hasExistingUser && isPlaceholder && isDefaultName) {
            return incomingNode.copy(
                user = existingNode.user,
                publicKey = existingNode.publicKey,
                longName = existingNode.longName,
                shortName = existingNode.shortName,
                manuallyVerified = existingNode.manuallyVerified,
                notes = resolvedNotes,
                powerChannelLabels = resolvedPowerChannelLabels,
            )
        }

        val resolvedKey =
            if (trustIncomingKey && (incomingNode.publicKey?.size ?: 0) == KEY_SIZE) {
                incomingNode.publicKey
            } else {
                resolvePublicKey(existingNode, incomingNode)
            }

        return incomingNode.copy(
            user = incomingNode.user.copy(public_key = resolvedKey ?: ByteString.EMPTY),
            publicKey = resolvedKey,
            notes = resolvedNotes,
            powerChannelLabels = resolvedPowerChannelLabels,
        )
    }

    @Query("SELECT * FROM my_node")
    fun getMyNodeInfo(): Flow<MyNodeEntity?>

    @Upsert suspend fun setMyNodeInfo(myInfo: MyNodeEntity)

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

    /** Snapshot used by DatabaseMerger to carry per-node DeviceMetadata across transports (newest timestamp wins). */
    @Query("SELECT * FROM metadata")
    suspend fun getAllMetadataSnapshot(): List<MetadataEntity>

    @Query("SELECT * FROM nodes WHERE num=:num")
    @Transaction
    suspend fun getNodeByNum(num: Int): NodeWithRelations?

    @Query("SELECT * FROM nodes WHERE num IN (:nodeNums)")
    suspend fun getNodeEntitiesByNums(nodeNums: List<Int>): List<NodeEntity>

    @Query("SELECT * FROM nodes WHERE public_key = :publicKey LIMIT 1")
    suspend fun findNodeByPublicKey(publicKey: ByteString?): NodeEntity?

    @Query("SELECT * FROM nodes WHERE public_key IN (:publicKeys)")
    suspend fun findNodesByPublicKeys(publicKeys: List<ByteString>): List<NodeEntity>

    @Upsert suspend fun doUpsert(node: NodeEntity)

    @Transaction
    suspend fun upsert(node: NodeEntity) {
        val verifiedNode = getVerifiedNodeForUpsert(node)
        doUpsert(verifiedNode)
    }

    @Upsert suspend fun putAll(nodes: List<NodeEntity>)

    @Query("UPDATE nodes SET notes = :notes WHERE num = :num")
    suspend fun setNodeNotes(num: Int, notes: String)

    @Query("UPDATE nodes SET power_channel_labels = :labels WHERE num = :num")
    suspend fun setPowerChannelLabels(num: Int, labels: List<String>)

    /**
     * Sets a single power-channel [label] (0-based [channelIndex]) atomically: the read-modify-write runs inside a
     * transaction that reads the current labels from the DB, so concurrent edits to different channels can't clobber
     * each other via a stale read. Earlier channels keep their slot (blank padding); trailing blanks are dropped.
     *
     * Reads via [getNodeByNum] rather than a scalar `SELECT power_channel_labels`: Room reads a `List<String>` query
     * result as one String per row (returning the raw JSON text), not as the column's converted `List<String>`.
     */
    @Transaction
    suspend fun updatePowerChannelLabel(num: Int, channelIndex: Int, label: String) {
        val existing = getNodeByNum(num)?.node?.powerChannelLabels ?: emptyList()
        setPowerChannelLabels(num, mergePowerChannelLabel(existing, channelIndex, label))
    }

    /**
     * Batch version of [getVerifiedNodeForUpsert]. Pre-fetches all existing nodes and public-key conflicts in two
     * queries instead of N individual queries, then processes each node in memory.
     *
     * [selfNum] is the connected device's node number during a config install; its key updates are trusted (see
     * [handleExistingNodeUpsertValidation]) and a key conflict against it always migrates. Node numbers whose rows were
     * migrated away are appended to [removedNums].
     */
    @Suppress("NestedBlockDepth")
    private suspend fun getVerifiedNodesForUpsert(
        incomingNodes: List<NodeEntity>,
        selfNum: Int? = null,
        removedNums: MutableList<Int> = mutableListOf(),
    ): List<NodeEntity> {
        // Prepare all incoming nodes (populate denormalized fields)
        incomingNodes.forEach { node ->
            node.publicKey = node.user.public_key
            if (node.user.hw_model != HardwareModel.UNSET) {
                node.longName = node.user.long_name
                node.shortName = node.user.short_name
            } else {
                node.longName = null
                node.shortName = null
            }
        }

        // Batch fetch all existing nodes by num (chunked for SQLite bind-param limit)
        val existingNodesMap =
            incomingNodes
                .map { it.num }
                .chunked(MAX_BIND_PARAMS)
                .flatMap { getNodeEntitiesByNums(it) }
                .associateBy { it.num }

        // Partition into updates vs. inserts and resolve existing nodes in-memory
        val result = mutableListOf<NodeEntity>()
        val newNodes = mutableListOf<NodeEntity>()
        for (incoming in incomingNodes) {
            val existing = existingNodesMap[incoming.num]
            if (existing != null) {
                result.add(handleExistingNodeUpsertValidation(existing, incoming, incoming.num == selfNum))
            } else {
                newNodes.add(incoming)
            }
        }

        // Batch validate new nodes' public keys (one query instead of N)
        val publicKeysToCheck = newNodes.mapNotNull { node -> node.publicKey?.takeIf { it.size > 0 } }.distinct()
        val pkConflicts =
            if (publicKeysToCheck.isNotEmpty()) {
                publicKeysToCheck
                    .chunked(MAX_BIND_PARAMS)
                    .flatMap { findNodesByPublicKeys(it) }
                    .associateBy { it.publicKey }
            } else {
                emptyMap()
            }

        for (newNode in newNodes) {
            if ((newNode.publicKey?.size ?: 0) > 0) {
                val conflicting = pkConflicts[newNode.publicKey]
                if (conflicting != null && conflicting.num != newNode.num) {
                    // Same key under a different num. Migrate when this is the connected device itself
                    // (device-authoritative over the local link) or the num is the canonical crc32(key)
                    // number (firmware 2.8 renumber); otherwise keep the existing identity.
                    if (newNode.num == selfNum || newNode.hasCanonicalNum()) {
                        migrateNodeIdentity(from = conflicting, to = newNode)
                        removedNums += conflicting.num
                        result.add(newNode)
                    } else {
                        result.add(conflicting)
                    }
                } else {
                    result.add(newNode)
                }
            } else {
                result.add(newNode)
            }
        }

        // Drop batch entries for identities that were migrated away — a device mid-transition can
        // still list the old num alongside the new one, and re-inserting it would resurrect the ghost.
        result.removeAll { it.num in removedNums }
        return result
    }

    @Query("SELECT * FROM my_node LIMIT 1")
    suspend fun getMyNodeEntity(): MyNodeEntity?

    /** Re-scopes stored messages from one owning node number to another (see `Packet.myNodeNum`). */
    @Query("UPDATE packet SET myNodeNum = :newNum WHERE myNodeNum = :oldNum")
    suspend fun remapPacketOwner(oldNum: Int, newNum: Int)

    /** OR IGNORE: myNodeNum is part of the reactions PK; drop the rare duplicate instead of aborting. */
    @Query("UPDATE OR IGNORE reactions SET myNodeNum = :newNum WHERE myNodeNum = :oldNum")
    suspend fun remapReactionOwner(oldNum: Int, newNum: Int)

    @Query("DELETE FROM reactions WHERE myNodeNum = :oldNum")
    suspend fun deleteReactionsForOwner(oldNum: Int)

    /** Contact keys are `"$channel$userId"`, so a suffix match rewrites just the DM threads of one node. */
    @Query("UPDATE packet SET contact_key = replace(contact_key, :oldId, :newId) WHERE contact_key LIKE '%' || :oldId")
    suspend fun remapDmContactKey(oldId: String, newId: String)

    @Query(
        "UPDATE OR IGNORE contact_settings SET contact_key = replace(contact_key, :oldId, :newId) " +
            "WHERE contact_key LIKE '%' || :oldId",
    )
    suspend fun remapDmContactSettings(oldId: String, newId: String)

    @Query("DELETE FROM contact_settings WHERE contact_key LIKE '%' || :oldId")
    suspend fun deleteDmContactSettings(oldId: String)

    /**
     * Reconciles a change of the connected device's node number before a config install replaces `my_node`.
     *
     * This database was selected by transport address, so a new node number arriving at the same address is, by
     * default, the same physical device under a new identity: a firmware 2.8 upgrade (`my_node_num` became
     * `crc32(public_key)` instead of the MAC-derived number), an erase-and-reflash, a manual key regeneration or import
     * — all of which renumber the device. Message history and app-local node state follow the device.
     *
     * The public key is deliberately NOT used to establish continuity here: keys can be regenerated or imported by the
     * user, and under firmware 2.8 every key change is also a renumber. The only veto is a hardware one — when both
     * sessions reported a factory-burned device id ([MyNodeEntity.deviceId]) and they differ, this is provably
     * different hardware (e.g. a reused TCP address), so the old history stays scoped under the old number instead of
     * leaking to the new device.
     */
    private suspend fun migrateLocalNodeIdentity(previous: MyNodeEntity, mi: MyNodeEntity, incomingSelf: NodeEntity?) {
        val oldNum = previous.myNodeNum
        val newNum = mi.myNodeNum
        val oldDeviceId = validDeviceIdOrNull(previous.deviceId)
        val newDeviceId = validDeviceIdOrNull(mi.deviceId)
        val differentHardware = oldDeviceId != null && newDeviceId != null && oldDeviceId != newDeviceId

        val oldSelf = getNodeByNum(oldNum)?.node
        if (!differentHardware) {
            remapPacketOwner(oldNum, newNum)
            remapReactionOwner(oldNum, newNum)
            deleteReactionsForOwner(oldNum)
        }
        if (!differentHardware && oldSelf != null && incomingSelf != null) {
            migrateNodeIdentity(from = oldSelf, to = incomingSelf)
        } else {
            // Nothing to carry over — just drop the stale self row so it can't shadow the new identity.
            deleteNode(oldNum)
            deleteMetadata(oldNum)
        }
    }

    /**
     * Installs the config download from the connected device, reconciling any node-number change of the device itself
     * (firmware 2.8 renumber, erase-and-reflash, manual re-key) before the new `my_node` row lands.
     *
     * @return node numbers whose rows were removed by identity migration, so callers can evict them from in-memory
     *   caches.
     */
    @Transaction
    suspend fun installConfig(mi: MyNodeEntity, nodes: List<NodeEntity>): List<Int> {
        val removedNums = mutableListOf<Int>()
        val previous = getMyNodeEntity()
        if (previous != null && previous.myNodeNum != mi.myNodeNum) {
            migrateLocalNodeIdentity(previous, mi, nodes.find { it.num == mi.myNodeNum })
            removedNums += previous.myNodeNum
        }
        clearMyNodeInfo()
        setMyNodeInfo(mi)
        putAll(getVerifiedNodesForUpsert(nodes, selfNum = mi.myNodeNum, removedNums = removedNums))
        return removedNums
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
