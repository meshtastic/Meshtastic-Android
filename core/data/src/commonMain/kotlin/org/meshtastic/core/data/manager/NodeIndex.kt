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

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import okio.ByteString
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.User

/**
 * Resolves a validated public-key correlation hint from a raw [ByteString]. Returns null unless the key is exactly
 * [Node.PUBLIC_KEY_SIZE] bytes and is not [Node.ERROR_BYTE_STRING]. The hint is used only for in-memory correlation; it
 * is not a trusted hardware identity and never authorizes durable node migration or deletion.
 */
internal fun resolveValidatedPublicKeyHint(key: ByteString?): ByteString? {
    if (key == null || key.size != Node.PUBLIC_KEY_SIZE || key == Node.ERROR_BYTE_STRING) return null
    return key
}

/** Single source of truth for stored-node key precedence: the primary field wins over the embedded [User] field. */
internal fun nodePublicKeyHint(node: Node): ByteString? =
    resolveValidatedPublicKeyHint(node.publicKey) ?: resolveValidatedPublicKeyHint(node.user.public_key)

internal val DEFAULT_NODE_NAME_REGEX = Regex("^Meshtastic [0-9a-fA-F]{4}$")

// byNum is the canonical in-memory store. Candidate-number reverse sets make normal put/remove and keyed packet
// handling proportional to the affected ID/key candidates rather than the entire mesh. byId stores the selected
// deterministic representative for O(1) user-ID lookup.
internal data class NodeIndex(
    val byNum: PersistentMap<Int, Node> = persistentMapOf(),
    val byId: PersistentMap<String, Node> = persistentMapOf(),
    val candidateNumsById: PersistentMap<String, PersistentSet<Int>> = persistentMapOf(),
    val candidateNumsByPublicKey: PersistentMap<ByteString, PersistentSet<Int>> = persistentMapOf(),
) {
    fun put(num: Int, node: Node, preferredNum: Int? = null): NodeIndex {
        val previous = byNum[num]
        val nextByNum = byNum.putting(num, node)
        var nextCandidatesById = candidateNumsById
        var nextCandidatesByPublicKey = candidateNumsByPublicKey

        previous
            ?.user
            ?.id
            ?.takeIf { it.isNotEmpty() }
            ?.let { id -> nextCandidatesById = nextCandidatesById.removingCandidate(id, num) }
        previous
            ?.let { nodePublicKeyHint(it) }
            ?.let { key -> nextCandidatesByPublicKey = nextCandidatesByPublicKey.removingCandidate(key, num) }
        node.user.id
            .takeIf { it.isNotEmpty() }
            ?.let { id -> nextCandidatesById = nextCandidatesById.addingCandidate(id, num) }
        nodePublicKeyHint(node)?.let { key ->
            nextCandidatesByPublicKey = nextCandidatesByPublicKey.addingCandidate(key, num)
        }

        var nextById = byId
        val affectedIds = setOfNotNull(previous?.user?.id, node.user.id).filter { it.isNotEmpty() }
        for (id in affectedIds) {
            val representative = chooseRepresentativeNum(nextCandidatesById[id].orEmpty(), nextByNum, preferredNum)
            nextById =
                if (representative == null) {
                    nextById.removing(id)
                } else {
                    nextById.putting(id, checkNotNull(nextByNum[representative]))
                }
        }
        return NodeIndex(nextByNum, nextById, nextCandidatesById, nextCandidatesByPublicKey)
    }

    /**
     * Once the index exceeds [maxNodes] entries, shrinks it to [lowWater] entries, never removing anything in
     * [keep]. At or under [maxNodes] it returns the receiver unchanged.
     *
     * `packet.from` is untrusted and unthrottled, so without this every novel value allocates a permanent [Node]
     * and the index grows until the process dies. Only `core:data` reads this index — for correlation,
     * notifications, channel lookup and staging DB writes — and every one of those readers already tolerates a
     * miss, so dropping a cold entry degrades correlation rather than losing user-visible data. The node list and
     * map read Room, not this index, so eviction is not visible in the UI.
     *
     * Eviction order is least-valuable-first: bare-packet placeholders before nodes that have sent a real NodeInfo,
     * and within each group the least recently heard. Nodes the user has marked (favourite, ignored, verified) are
     * never evicted, since that is user data rather than observed mesh state.
     *
     * The cap is therefore best-effort rather than absolute: if protected entries alone exceed [maxNodes] the index
     * stays above it. That is deliberate — these marks are set only by the local user, so no remote party can
     * inflate them, and silently discarding user data to satisfy a memory bound would be the worse trade.
     *
     * Evicting down to [lowWater] rather than to [maxNodes] amortizes the sort: under a flood of novel numbers it
     * runs once per `maxNodes - lowWater` inserts instead of on every one. Protected entries above [lowWater]
     * shrink that batch, and once they fill [maxNodes] every insert past it pays for the filter and sort again.
     */
    fun evictedToFit(maxNodes: Int, lowWater: Int, keep: Set<Int>): NodeIndex {
        require(lowWater < maxNodes) { "lowWater $lowWater must be below maxNodes $maxNodes" }
        if (byNum.size <= maxNodes) return this
        val evictable =
            byNum.values
                .filterNot { it.num in keep || it.isFavorite || it.isIgnored || it.manuallyVerified }
                // Placeholders first, then oldest-heard, then node num so the outcome is deterministic.
                .sortedWith(
                    compareByDescending<Node> { isDefaultIdentityPlaceholder(it) }
                        .thenBy { it.lastHeard }
                        .thenBy { it.num },
                )
        val toDrop = evictable.take(byNum.size - lowWater)
        return toDrop.fold(this) { index, node -> index.remove(node.num) }
    }

    /**
     * Removes [num] from both indices. When the removed node's user ID was the [byId] representative and another
     * surviving node shares that ID, [preferredNum] wins when present; otherwise deterministic ordering selects the
     * replacement.
     */
    fun remove(num: Int, preferredNum: Int? = null): NodeIndex {
        val previous = byNum[num] ?: return this
        val newByNum = byNum.removing(num)
        var newCandidatesById = candidateNumsById
        var newCandidatesByPublicKey = candidateNumsByPublicKey
        previous.user.id
            .takeIf { it.isNotEmpty() }
            ?.let { id -> newCandidatesById = newCandidatesById.removingCandidate(id, num) }
        nodePublicKeyHint(previous)?.let { key ->
            newCandidatesByPublicKey = newCandidatesByPublicKey.removingCandidate(key, num)
        }
        var newById = byId
        if (previous.user.id.isNotEmpty()) {
            val survivor =
                chooseRepresentativeNum(newCandidatesById[previous.user.id].orEmpty(), newByNum, preferredNum)
            newById =
                if (survivor != null) {
                    newById.putting(previous.user.id, checkNotNull(newByNum[survivor]))
                } else {
                    newById.removing(previous.user.id)
                }
        }
        return NodeIndex(newByNum, newById, newCandidatesById, newCandidatesByPublicKey)
    }

    companion object {
        internal fun isDefaultIdentityPlaceholder(node: Node): Boolean = nodePublicKeyHint(node) == null &&
            node.user.hw_model == HardwareModel.UNSET &&
            node.user.id == NodeAddress.numToDefaultId(node.num) &&
            node.user.long_name.matches(DEFAULT_NODE_NAME_REGEX)

        /**
         * Selects a deterministic representative number from [candidates] sharing one user ID. If [preferredNum] is
         * present it wins; otherwise an established key/hardware identity wins, then the lower node number.
         */
        internal fun chooseRepresentativeNum(
            candidates: Set<Int>,
            nodes: Map<Int, Node>,
            preferredNum: Int?,
        ): Int? {
            if (preferredNum != null && preferredNum in candidates) return preferredNum
            return candidates.minWithOrNull(
                compareByDescending<Int> { num ->
                    nodes[num]?.let { node ->
                        nodePublicKeyHint(node) != null || node.user.hw_model != HardwareModel.UNSET
                    } == true
                }
                    .thenBy { it },
            )
        }

        fun fromByNum(nodes: Map<Int, Node>): NodeIndex {
            var result = NodeIndex()
            nodes.forEach { (num, node) -> result = result.put(num, node) }
            return result
        }

        private fun <K> PersistentMap<K, PersistentSet<Int>>.addingCandidate(
            key: K,
            num: Int,
        ): PersistentMap<K, PersistentSet<Int>> = putting(key, (this[key] ?: persistentSetOf()).adding(num))

        private fun <K> PersistentMap<K, PersistentSet<Int>>.removingCandidate(
            key: K,
            num: Int,
        ): PersistentMap<K, PersistentSet<Int>> {
            val remaining = this[key]?.removing(num) ?: return this
            return if (remaining.isEmpty()) removing(key) else putting(key, remaining)
        }
    }
}
