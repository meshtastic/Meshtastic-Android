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
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.ByteString
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.clampTimestampToNow
import org.meshtastic.core.common.util.crc32
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.util.NodeIdLookup
import org.meshtastic.core.repository.ConnectionIdentity
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.Notification
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.getStringSuspend
import org.meshtastic.core.resources.new_node_seen
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.FirmwareEdition
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.Paxcount
import org.meshtastic.proto.StatusMessage
import org.meshtastic.proto.Telemetry
import org.meshtastic.proto.User
import kotlinx.coroutines.flow.update as updateStateFlow
import org.meshtastic.proto.NodeInfo as ProtoNodeInfo
import org.meshtastic.proto.Position as ProtoPosition

/**
 * Resolves a validated public-key correlation hint from a raw [ByteString]. Returns null unless the key is exactly
 * [Node.PUBLIC_KEY_SIZE] bytes and is not [Node.ERROR_BYTE_STRING]. The hint is used only for in-memory correlation; it
 * is not a trusted hardware identity and never authorizes durable node migration or deletion.
 */
private fun resolveValidatedPublicKeyHint(key: ByteString?): ByteString? {
    if (key == null || key.size != Node.PUBLIC_KEY_SIZE || key == Node.ERROR_BYTE_STRING) return null
    return key
}

/** Single source of truth for stored-node key precedence: the primary field wins over the embedded [User] field. */
private fun nodePublicKeyHint(node: Node): ByteString? =
    resolveValidatedPublicKeyHint(node.publicKey) ?: resolveValidatedPublicKeyHint(node.user.public_key)

/** Short one-way diagnostic value that cannot disclose a raw public-key prefix. */
private const val KEY_FINGERPRINT_HEX_LENGTH = 8

internal fun publicKeyLogFingerprint(key: ByteString): String = key.sha256().hex().take(KEY_FINGERPRINT_HEX_LENGTH)

private val DEFAULT_NODE_NAME_REGEX = Regex("^Meshtastic [0-9a-fA-F]{4}$")

/** Implementation of [NodeManager] that maintains an in-memory database of the mesh. */
@Suppress("LongParameterList", "TooManyFunctions", "CyclomaticComplexMethod", "LargeClass")
@Single(binds = [NodeManager::class, NodeIdLookup::class])
class NodeManagerImpl(
    private val nodeRepository: NodeRepository,
    private val notificationManager: NotificationManager,
    private val radioInterfaceService: RadioInterfaceService,
    @Named("ServiceScope") private val scope: CoroutineScope,
) : NodeManager {

    // Fixed stripes bound mutex lifetime while preserving same-node persistence ordering. Hash collisions only reduce
    // parallelism; they never weaken ordering for a node.
    private val nodePersistenceLanes = List(NODE_PERSISTENCE_LANE_COUNT) { Mutex() }

    private fun persistenceLane(nodeNum: Int): Mutex =
        nodePersistenceLanes[(nodeNum.toLong() and Int.MAX_VALUE.toLong()).toInt() % nodePersistenceLanes.size]

    /**
     * Resolves a validated public-key correlation hint from a stored [Node], preferring [Node.publicKey] and falling
     * back to [User.public_key] only when the primary field is not itself a valid hint.
     */
    internal fun resolveNodePublicKeyHint(node: Node): ByteString? = nodePublicKeyHint(node)

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

    private data class NodeState(
        val index: NodeIndex = NodeIndex(),
        val localNodeNum: Int? = null,
        val retiredNodeNums: PersistentSet<Int> = persistentSetOf(),
        /**
         * Validated public-key hint for each retired number, captured before index removal. Used to suppress same-key
         * replays even when no canonical candidate currently holds the key in the in-memory index.
         */
        val retiredKeyHints: PersistentMap<Int, ByteString> = persistentMapOf(),
        /**
         * Monotonic counter bumped on every live mutation to [index] or [localNodeNum]. Captured by [loadCachedNodeDB]
         * before its suspending snapshot read so the commit step can detect concurrent live mutations and merge instead
         * of overwriting them.
         */
        val revision: Long = 0L,
    ) {
        fun isRetiredAbsent(nodeNum: Int): Boolean = nodeNum in retiredNodeNums && nodeNum !in index.byNum
    }

    private val nodeState = atomic(NodeState())

    /**
     * Session generation, bumped by [clear]. Captured by [loadCachedNodeDB] before its suspending snapshot read so a
     * load launched in one session can be discarded completely if [clear] started a new session before the load
     * committed.
     */
    private val sessionGeneration = atomic(0L)

    override val nodeDBbyNodeNum: Map<Int, Node>
        get() = nodeState.value.index.byNum

    override fun getNodeById(id: String): Node? = nodeState.value.index.byId[id]

    override val isNodeDbReady = MutableStateFlow(false)
    override val allowNodeDbWrites = MutableStateFlow(false)

    override fun setNodeDbReady(ready: Boolean) {
        isNodeDbReady.value = ready
    }

    override fun setAllowNodeDbWrites(allowed: Boolean) {
        allowNodeDbWrites.value = allowed
    }

    override val myNodeNum = MutableStateFlow<Int?>(null)

    override fun setMyNodeNum(num: Int?) {
        nodeState.update { it.copy(localNodeNum = num, revision = it.revision + 1) }
        myNodeNum.value = num
    }

    override val myDeviceId = MutableStateFlow<String?>(null)

    override fun setMyDeviceId(id: String?) {
        myDeviceId.value = id
    }

    private val _connectionIdentity = MutableStateFlow<ConnectionIdentity?>(null)
    override val connectionIdentity: StateFlow<ConnectionIdentity?> = _connectionIdentity

    override fun clearConnectionIdentity() {
        _connectionIdentity.value = null
    }

    override fun clearStaleConnectionIdentity(activeSessionGeneration: Long) {
        _connectionIdentity.updateStateFlow { identity ->
            identity?.takeIf { it.sessionGeneration == activeSessionGeneration }
        }
    }

    override fun publishConnectionIdentity(sessionGeneration: Long, address: String, nodeNum: Int, deviceId: String?) {
        _connectionIdentity.value = ConnectionIdentity(sessionGeneration, address, nodeNum, deviceId)
    }

    override val firmwareEdition = MutableStateFlow<FirmwareEdition?>(null)

    override fun setFirmwareEdition(edition: FirmwareEdition?) {
        firmwareEdition.value = edition
    }

    companion object {
        private const val NODE_PERSISTENCE_LANE_COUNT = 64
        private const val TIME_MS_TO_S = 1000L
        private const val GENERATED_NODE_NAME_SUFFIX_LENGTH = 4
    }

    override fun loadCachedNodeDB() {
        // NOTE: this method intentionally does NOT touch _connectionIdentity. The connection-session identity is only
        // populated by a fresh MyNodeInfo handshake; restoring it from cache would re-introduce a stale-identity
        // association across transport switches.
        scope.handledLaunch {
            // Capture the session generation and live revision BEFORE the suspending DB read. After the read returns
            // we re-check both: a generation mismatch means clear() started a new session while we were reading, so
            // the result is discarded completely; a revision mismatch means a live packet mutated the index while we
            // were reading, so we merge instead of overwriting (otherwise a node received during the load window would
            // be silently dropped).
            val generation = sessionGeneration.value
            val revisionAtStart = nodeState.value.revision
            // Read from the CURRENTLY SELECTED database through DatabaseProvider.withReadDb, not from the
            // process-wide nodeDBbyNum StateFlow. The StateFlow is a stateIn cache over SharingStarted.Eagerly and
            // can briefly retain the PREVIOUS database's map after a currentDb switch, which would resurrect retired
            // or stale rows on the new session.
            val snapshot = nodeRepository.getNodeDbSnapshot()
            val persistedLocalNum = nodeRepository.myNodeInfo.value?.myNodeNum
            nodeState.update { state ->
                if (generation != sessionGeneration.value) {
                    // Session was reset while the load was in flight; drop the result entirely.
                    state
                } else {
                    val retiredNums = state.retiredNodeNums
                    // Retirement always wins: rows for currently retired numbers never re-enter the live index from
                    // the snapshot, even if the cache still holds them.
                    val filteredSnapshot = snapshot.filterKeys { it !in retiredNums }
                    if (state.revision == revisionAtStart) {
                        // No live mutation since capture: install the filtered snapshot directly. Preserve any
                        // local-node number learned during the session; fall back to the persisted value only when
                        // we have none yet.
                        state.copy(
                            index = NodeIndex.fromByNum(filteredSnapshot),
                            localNodeNum = state.localNodeNum ?: persistedLocalNum,
                        )
                    } else {
                        // Live state changed during the load window. Merge: current nodes (with their fresher
                        // fields) win over snapshot rows at the same num; snapshot rows only fill slots that the
                        // live index does not already carry. Live local-node info and retirement state remain
                        // authoritative; a missing local number receives the same persisted fallback as a direct load.
                        val liveByNum = state.index.byNum
                        val merged = filteredSnapshot.toMutableMap()
                        liveByNum.forEach { (num, node) -> merged[num] = node }
                        state.copy(
                            index = NodeIndex.fromByNum(merged),
                            localNodeNum = state.localNodeNum ?: persistedLocalNum,
                        )
                    }
                }
            }
            // Keep the published myNodeNum consistent with whatever state survived the commit above. If the load was
            // discarded, the published value is whatever clear()/setMyNodeNum() left behind.
            myNodeNum.value = nodeState.value.localNodeNum
        }
    }

    override fun clear() {
        // Bump the session generation FIRST so any in-flight loadCachedNodeDB whose suspend read returns after this
        // point sees a mismatch and discards its result instead of installing stale state into the freshly cleared
        // index.
        sessionGeneration.incrementAndGet()
        nodeState.value = NodeState()
        isNodeDbReady.value = false
        allowNodeDbWrites.value = false
        myNodeNum.value = null
        myDeviceId.value = null
        firmwareEdition.value = null
        _connectionIdentity.value = null
    }

    override fun getMyNodeInfo(): MyNodeInfo? {
        val mi = nodeRepository.myNodeInfo.value ?: return null
        val myNode = nodeState.value.index.byNum[mi.myNodeNum]
        return MyNodeInfo(
            myNodeNum = mi.myNodeNum,
            hasGPS = (myNode?.position?.latitude_i ?: 0) != 0,
            model = mi.model ?: myNode?.user?.hw_model?.name,
            firmwareVersion = mi.firmwareVersion,
            couldUpdate = mi.couldUpdate,
            shouldUpdate = mi.shouldUpdate,
            currentPacketId = mi.currentPacketId,
            messageTimeoutMsec = mi.messageTimeoutMsec,
            minAppVersion = mi.minAppVersion,
            maxChannels = mi.maxChannels,
            hasWifi = mi.hasWifi,
            channelUtilization = 0f,
            airUtilTx = 0f,
            deviceId = mi.deviceId ?: myNode?.user?.id,
        )
    }

    override fun getMyId(): String {
        val num = nodeState.value.localNodeNum ?: nodeRepository.myNodeInfo.value?.myNodeNum ?: return ""
        return nodeState.value.index.byNum[num]?.user?.id ?: ""
    }

    override fun removeByNodenum(nodeNum: Int) {
        nodeState.update { state -> state.copy(index = state.index.remove(nodeNum), revision = state.revision + 1) }
    }

    override fun applyTrustedIdentityMigrations(removedNums: Collection<Int>) {
        if (removedNums.isEmpty()) return
        var committedHints: Map<Int, ByteString?> = emptyMap()
        var committedPresentNums: Set<Int> = emptySet()
        nodeState.update { state ->
            // Capture validated public-key hints from the current index state before removal. A number that is already
            // absent (e.g. re-applying the same migration after a previous retirement) has no current node to capture
            // a key from — its previously captured hint MUST be preserved so same-key replay stays suppressed and a
            // later genuinely different-keyed device can still claim the slot under the intended contract.
            committedPresentNums = removedNums.filterTo(mutableSetOf()) { it in state.index.byNum }
            committedHints =
                removedNums.associateWith { num ->
                    state.index.byNum[num]?.let(::resolveNodePublicKeyHint) ?: state.retiredKeyHints[num]
                }
            state.copy(
                index = removedNums.fold(state.index) { index, nodeNum -> index.remove(nodeNum) },
                retiredNodeNums = state.retiredNodeNums.addingAll(removedNums),
                // Only ADD hints here; never remove a prior hint for an absent number. A subsequent migration that
                // re-applies the same retirement must preserve the original hint so same-key replay stays suppressed
                // and a later genuinely different-keyed device can still claim the slot.
                retiredKeyHints =
                state.retiredKeyHints.puttingAll(
                    committedHints.mapNotNull { (num, key) -> key?.let { num to it } }.toMap(),
                ),
                revision = state.revision + 1,
            )
        }
        // Commit retirement first. A dispatch already in progress will fail its final state revalidation; one that
        // completed before the commit is removed by this cancellation. Side effects run once, outside the CAS loop.
        removedNums.forEach { num ->
            notificationManager.cancel(num)
            val keyDescription =
                committedHints[num]?.let(::publicKeyLogFingerprint)
                    ?: if (num in committedPresentNums) "none" else "absent"
            Logger.d {
                "[NodeIdentity] trusted-migration removed=$num" + " key=$keyDescription notificationCancelled=true"
            }
        }
    }

    internal fun getOrCreateNode(n: Int, channel: Int = 0): Node =
        nodeState.value.index.byNum[n] ?: createDefaultNode(n, channel)

    private data class NodeStateChange(val previous: Node, val next: Node)

    private fun updateNodeState(nodeNum: Int, channel: Int, transform: (Node) -> Node): NodeStateChange? {
        var change: NodeStateChange? = null
        nodeState.update { state ->
            change = null
            if (state.isRetiredAbsent(nodeNum)) return@update state
            val current = state.index.byNum[nodeNum] ?: createDefaultNode(nodeNum, channel)
            val next = transform(current)
            change = NodeStateChange(previous = current, next = next)
            state.copy(index = state.index.put(nodeNum, next), revision = state.revision + 1)
        }
        return change
    }

    private fun shouldPersist(node: Node): Boolean =
        node.user.id.isNotEmpty() && isNodeDbReady.value && allowNodeDbWrites.value

    private fun updateNodeAndSchedulePersistence(
        nodeNum: Int,
        channel: Int,
        session: RadioSessionContext? = null,
        transform: (Node) -> Node,
    ): NodeStateChange? = updateNodeState(nodeNum, channel, transform).also { change ->
        if (change != null && shouldPersist(change.next)) {
            radioInterfaceService.launchSessionWork(scope, session) { persistLatestNode(nodeNum) }
        }
    }

    override fun updateNode(nodeNum: Int, channel: Int, transform: (Node) -> Node) {
        updateNodeAndSchedulePersistence(nodeNum, channel, transform = transform)
    }

    override fun updateNodeForSession(
        nodeNum: Int,
        session: RadioSessionContext,
        channel: Int,
        transform: (Node) -> Node,
    ) {
        updateNodeAndSchedulePersistence(nodeNum, channel, session, transform)
    }

    override suspend fun updateNodeAndPersist(nodeNum: Int, channel: Int, transform: (Node) -> Node) {
        val result = updateNodeState(nodeNum, channel, transform)?.next ?: return
        if (shouldPersist(result)) persistLatestNode(nodeNum)
    }

    /** Serializes persistence per node and reads the latest in-memory value inside that lane. */
    private suspend fun persistLatestNode(nodeNum: Int) = persistenceLane(nodeNum).withLock {
        val latest = nodeState.value.index.byNum[nodeNum] ?: return@withLock
        if (shouldPersist(latest)) nodeRepository.upsert(latest)
    }

    override fun handleReceivedUser(
        fromNum: Int,
        p: User,
        channel: Int,
        manuallyVerified: Boolean,
        session: RadioSessionContext?,
    ) {
        while (true) {
            val before = nodeState.value
            val transition =
                reduceReceivedUser(
                    before.index,
                    fromNum,
                    p,
                    channel,
                    manuallyVerified,
                    before.localNodeNum,
                    before.isRetiredAbsent(fromNum),
                    retiredKeyHint = before.retiredKeyHints[fromNum],
                )
            receivedUserReductionHook?.invoke()
            val after =
                before.copy(
                    index = transition.after,
                    retiredNodeNums =
                    transition.unretireNodeNum?.let { before.retiredNodeNums.removing(it) }
                        ?: before.retiredNodeNums,
                    retiredKeyHints =
                    transition.unretireNodeNum?.let { before.retiredKeyHints.removing(it) }
                        ?: before.retiredKeyHints,
                    revision = before.revision + 1,
                )
            if (nodeState.compareAndSet(before, after)) {
                Logger.d {
                    val keyStr = resolveValidatedPublicKeyHint(p.public_key)?.let(::publicKeyLogFingerprint) ?: "none"
                    val canonicalNum = resolveValidatedPublicKeyHint(p.public_key)?.let { it.crc32().toInt() }
                    "[NodeIdentity] user from=$fromNum local=${before.localNodeNum}" +
                        " retired=${before.isRetiredAbsent(fromNum)}" +
                        " key=$keyStr canonical=$canonicalNum" +
                        " decision=${transition.decision} notify=${transition.notifyNode != null}"
                }
                applyReceivedUserEffects(transition, session)
                return
            }
        }
    }

    override fun handleReceivedPosition(
        fromNum: Int,
        myNodeNum: Int,
        p: ProtoPosition,
        defaultTime: Long,
        session: RadioSessionContext?,
    ) {
        val isZeroPos = (p.latitude_i ?: 0) == 0 && (p.longitude_i ?: 0) == 0
        @Suppress("ComplexCondition")
        if (myNodeNum == fromNum && isZeroPos && p.sats_in_view == 0 && p.time == 0) {
            Logger.d { "Ignoring empty position update for the local node" }
            return
        }

        updateNodeAndSchedulePersistence(fromNum, channel = 0, session = session) { node ->
            val rawPosTime = if (p.time != 0) p.time else (defaultTime / TIME_MS_TO_S).toInt()
            val posTime = clampTimestampToNow(rawPosTime)
            val newLastHeard = maxOf(node.lastHeard, posTime)

            val newPos =
                if (isZeroPos) {
                    p.copy(
                        time = posTime,
                        latitude_i = node.position.latitude_i,
                        longitude_i = node.position.longitude_i,
                        altitude = p.altitude ?: node.position.altitude,
                        sats_in_view = p.sats_in_view,
                    )
                } else {
                    p.copy(time = posTime)
                }

            node.copy(position = newPos, lastHeard = newLastHeard)
        }
    }

    override fun handleReceivedTelemetry(fromNum: Int, telemetry: Telemetry) {
        updateNode(fromNum) { node ->
            var nextNode = node
            telemetry.device_metrics?.let { nextNode = nextNode.copy(deviceMetrics = it) }
            telemetry.environment_metrics?.let { nextNode = nextNode.copy(environmentMetrics = it) }
            telemetry.power_metrics?.let { nextNode = nextNode.copy(powerMetrics = it) }
            telemetry.air_quality_metrics?.let { nextNode = nextNode.copy(airQualityMetrics = it) }
            val telemetryTime = if (telemetry.time != 0) telemetry.time else node.lastHeard
            val newLastHeard = clampTimestampToNow(maxOf(node.lastHeard, telemetryTime))
            nextNode.copy(lastHeard = newLastHeard)
        }
    }

    override fun handleReceivedPaxcounter(fromNum: Int, p: Paxcount, session: RadioSessionContext?) {
        updateNodeAndSchedulePersistence(fromNum, channel = 0, session = session) { it.copy(paxcounter = p) }
    }

    override fun handleReceivedNodeStatus(fromNum: Int, s: StatusMessage, session: RadioSessionContext?) {
        updateNodeAndSchedulePersistence(fromNum, channel = 0, session = session) { node ->
            applyNodeStatus(node, s.status)
        }
    }

    override fun updateNodeStatus(nodeNum: Int, status: String?) {
        updateNode(nodeNum) { node -> applyNodeStatus(node, status) }
    }

    override suspend fun updateNodeStatusAndPersist(nodeNum: Int, status: String?) {
        updateNodeAndPersist(nodeNum) { node -> applyNodeStatus(node, status) }
    }

    private fun applyNodeStatus(node: Node, status: String?): Node =
        node.copy(nodeStatus = status?.takeIf { it.isNotEmpty() })

    override fun installNodeInfo(info: ProtoNodeInfo) {
        // Stage-2 configuration installation persists the complete node snapshot through installConfig.
        updateNodeState(info.num, channel = 0) { node -> applyNodeInfo(node, info) }
    }

    override suspend fun installNodeInfoAndPersist(info: ProtoNodeInfo) {
        updateNodeAndPersist(info.num) { node -> applyNodeInfo(node, info) }
    }

    private fun applyNodeInfo(node: Node, info: ProtoNodeInfo): Node {
        var next = node
        val user = info.user
        if (user != null && !shouldPreserveExistingUser(node.user, user)) {
            var newUser = user.let { if (it.is_licensed == true) it.copy(public_key = ByteString.EMPTY) else it }
            if (info.via_mqtt && !newUser.long_name.endsWith(" (MQTT)")) {
                newUser = newUser.copy(long_name = "${newUser.long_name} (MQTT)")
            }
            next = next.copy(user = newUser, publicKey = newUser.public_key)
        }
        info.position?.let { position ->
            next = next.copy(position = position.copy(time = clampTimestampToNow(position.time)))
        }
        return next.copy(
            lastHeard = clampTimestampToNow(info.last_heard),
            deviceMetrics = info.device_metrics ?: next.deviceMetrics,
            channel = info.channel,
            viaMqtt = info.via_mqtt,
            hopsAway = info.hops_away ?: -1,
            isFavorite = info.is_favorite,
            isIgnored = info.is_ignored,
            isMuted = info.is_muted,
            signsPackets = info.has_xeddsa_signed,
        )
    }

    override fun insertMetadata(nodeNum: Int, metadata: DeviceMetadata, session: RadioSessionContext?) {
        if (nodeState.value.isRetiredAbsent(nodeNum)) return
        radioInterfaceService.launchSessionWork(scope, session) {
            if (!nodeState.value.isRetiredAbsent(nodeNum)) nodeRepository.insertMetadata(nodeNum, metadata)
        }
    }

    private fun shouldPreserveExistingUser(existing: User, incoming: User): Boolean {
        val isDefaultName = incoming.long_name.matches(DEFAULT_NODE_NAME_REGEX)
        val isDefaultHwModel = incoming.hw_model == HardwareModel.UNSET
        val hasCustomIdentity =
            existing.id != incoming.id ||
                existing.long_name != incoming.long_name ||
                existing.short_name != incoming.short_name
        val hasExistingUser =
            existing.id.isNotEmpty() && (existing.hw_model != HardwareModel.UNSET || hasCustomIdentity)
        return hasExistingUser && isDefaultName && isDefaultHwModel
    }

    /**
     * Classification of one received-user reduction. Returned by [reduceReceivedUser] so callers can emit a single
     * identity-routing log line per committed transition.
     */
    private enum class ReceivedUserDecision {
        LOCAL_UPDATE,
        KNOWN_SAME_NUMBER,
        GENUINE_NEW_NODE,
        RETIRED_NO_KEY_SUPPRESSED,
        RETIRED_WITHOUT_HINT_SUPPRESSED,
        RETIRED_SAME_KEY_SUPPRESSED,
        RETIRED_KEY_ALREADY_REPRESENTED,
        RETIRED_DIFFERENT_KEY_REACTIVATED,
        CANONICAL_DUPLICATE_RECONCILED,
        STALE_PRESENTATION_REMOVED,
        AMBIGUOUS_DUPLICATE_UPDATED,
        IDENTITY_CONFLICT_IGNORED,
    }

    /**
     * Result of classifying one [User] packet against an in-memory [NodeIndex] snapshot. The reducer is pure: it only
     * reads [before] and the packet fields, and produces the next index plus the side effects to apply once the CAS
     * commits. Safe to re-evaluate on every retry.
     */
    private data class ReceivedUserTransition(
        val after: NodeIndex,
        /** Ordinary same-number upsert to apply after CAS; duplicate correlation never supplies this value. */
        val upsertNode: Node?,
        /** Node to fire a "new node seen" notification for (null if not a genuine new node). */
        val notifyNode: Node?,
        /** Retired number to reactivate after a validated, genuinely new identity claims the vacant slot. */
        val unretireNodeNum: Int? = null,
        val decision: ReceivedUserDecision,
    )

    /**
     * Pure reducer that classifies an incoming [User] packet against the [before] snapshot and returns the next index
     * plus queued side effects. No logging, no DB calls, no coroutine launches — deterministic and safe to re-run on
     * CAS retry.
     *
     * A validated public key is only an in-memory correlation hint. It can choose/suppress duplicate presentations but
     * cannot authorize repository deletion, metadata removal, DM remapping, or durable node-number migration. Those
     * operations remain exclusively in the trusted config-install DAO path.
     */
    @Suppress("CyclomaticComplexMethod", "ReturnCount", "LongMethod")
    private fun reduceReceivedUser(
        before: NodeIndex,
        fromNum: Int,
        p: User,
        channel: Int,
        manuallyVerified: Boolean,
        myNum: Int?,
        retiredAbsent: Boolean,
        retiredKeyHint: ByteString? = null,
    ): ReceivedUserTransition {
        val resolvedKey = resolveValidatedPublicKeyHint(p.public_key)
        val existing = before.byNum[fromNum]

        if (retiredAbsent) {
            // Suppress same-key replay even when canonical representative hasn't yet appeared in
            // the in-memory index. The retiredKeyHint was captured from the node as it was retired.
            if (resolvedKey != null && retiredKeyHint != null && resolvedKey == retiredKeyHint) {
                return ReceivedUserTransition(
                    after = before,
                    upsertNode = null,
                    notifyNode = null,
                    decision = ReceivedUserDecision.RETIRED_SAME_KEY_SUPPRESSED,
                )
            }
            // A packet with no valid key is suppressed for a retired number.
            if (resolvedKey == null) {
                return ReceivedUserTransition(
                    after = before,
                    upsertNode = null,
                    notifyNode = null,
                    decision = ReceivedUserDecision.RETIRED_NO_KEY_SUPPRESSED,
                )
            }
            // A retired slot without a trusted old-key hint has no evidence that a different-looking packet is a
            // legitimate reuse rather than the stale identity. Keep it retired for this process session.
            if (retiredKeyHint == null) {
                return ReceivedUserTransition(
                    after = before,
                    upsertNode = null,
                    notifyNode = null,
                    decision = ReceivedUserDecision.RETIRED_WITHOUT_HINT_SUPPRESSED,
                )
            }
            // Key is already represented elsewhere — stale replay, suppress.
            val keyAlreadyRepresented =
                resolvedKey.let { key -> before.candidateNumsByPublicKey[key].orEmpty().isNotEmpty() }
            if (keyAlreadyRepresented) {
                return ReceivedUserTransition(
                    after = before,
                    upsertNode = null,
                    notifyNode = null,
                    decision = ReceivedUserDecision.RETIRED_KEY_ALREADY_REPRESENTED,
                )
            }
            // Different valid, unrepresented key — legitimate number reuse.
            return sameNumberUserTransition(
                before = before,
                fromNum = fromNum,
                existing = createDefaultNode(fromNum, channel),
                incoming = p,
                channel = channel,
                manuallyVerified = manuallyVerified,
                persist = true,
                allowNotification = true,
                unretireNodeNum = fromNum,
            )
                .copy(decision = ReceivedUserDecision.RETIRED_DIFFERENT_KEY_REACTIVATED)
        }

        if (fromNum == myNum) {
            // Local-link data may persist an ordinary update at its current number. Duplicate cleanup is still
            // in-memory only; trusted installConfig migration owns durable renumbering and app-local state transfer.
            val otherSameKeyNums =
                resolvedKey
                    ?.let { key -> before.candidateNumsByPublicKey[key].orEmpty().filter { it != fromNum } }
                    .orEmpty()
            val afterRemovals = otherSameKeyNums.fold(before) { idx, num -> idx.remove(num) }
            val localNode = afterRemovals.byNum[fromNum] ?: createDefaultNode(fromNum, channel)
            val transformed = transformUserNode(localNode, p, channel, manuallyVerified)
            return ReceivedUserTransition(
                after = afterRemovals.put(fromNum, transformed, preferredNum = fromNum),
                upsertNode = transformed,
                notifyNode = null,
                decision = ReceivedUserDecision.LOCAL_UPDATE,
            )
        }

        if (resolvedKey != null) {
            val canonicalNum = resolvedKey.crc32().toInt()
            val existingKey = existing?.let(::resolveNodePublicKeyHint)
            val otherSameKeyNums = before.candidateNumsByPublicKey[resolvedKey].orEmpty().filter { it != fromNum }

            // Fast path: a known same-number presentation needs no candidate reconciliation when it is already at the
            // canonical number or has no duplicate. A canonical duplicate remains in-memory-only because an ordinary
            // DAO upsert could remap the row through same-key verification.
            if (existingKey == resolvedKey && (fromNum == canonicalNum || otherSameKeyNums.isEmpty())) {
                return sameNumberUserTransition(
                    before,
                    fromNum,
                    checkNotNull(existing),
                    p,
                    channel,
                    manuallyVerified,
                    persist = otherSameKeyNums.isEmpty(),
                    allowNotification = false,
                )
            }

            if (fromNum == canonicalNum && otherSameKeyNums.isNotEmpty()) {
                val slotAcceptsCanonical =
                    existing == null || existingKey == resolvedKey || NodeIndex.isDefaultIdentityPlaceholder(existing)
                if (!slotAcceptsCanonical) {
                    return ReceivedUserTransition(
                        after = before,
                        upsertNode = null,
                        notifyNode = null,
                        decision = ReceivedUserDecision.IDENTITY_CONFLICT_IGNORED,
                    )
                }

                // Canonical presentation may replace its own default placeholder while preserving all non-user fields.
                // Same-key noncanonical representatives are removed from memory only.
                val afterRemovals = otherSameKeyNums.fold(before) { index, num -> index.remove(num) }
                val baseNode = afterRemovals.byNum[fromNum] ?: createDefaultNode(fromNum, channel)
                val transformed =
                    transformUserNode(baseNode, p, channel, manuallyVerified).let { updated ->
                        if (existing != null && NodeIndex.isDefaultIdentityPlaceholder(existing)) {
                            updated.copy(channel = existing.channel)
                        } else {
                            updated
                        }
                    }
                return ReceivedUserTransition(
                    after = afterRemovals.put(fromNum, transformed, preferredNum = fromNum),
                    upsertNode = null,
                    notifyNode = null,
                    decision = ReceivedUserDecision.CANONICAL_DUPLICATE_RECONCILED,
                )
            }

            if (canonicalNum in otherSameKeyNums) {
                // A stale remote presentation yields only when its own slot already carries the same validated hint.
                // An absent slot, no-key telemetry/position placeholder, or different identity is preserved.
                val after =
                    if (existingKey == resolvedKey) before.remove(fromNum, preferredNum = canonicalNum) else before
                return ReceivedUserTransition(
                    after = after,
                    upsertNode = null,
                    notifyNode = null,
                    decision = ReceivedUserDecision.STALE_PRESENTATION_REMOVED,
                )
            }

            if (otherSameKeyNums.isNotEmpty()) {
                // Direction is ambiguous among noncanonical presentations. Accept an empty/default fromNum slot so a
                // legitimate pre-2.8 or fixed-number peer can move between noncanonical numbers without becoming
                // locked out behind a telemetry/position placeholder. Preserve established different-key identities.
                val slotAcceptsAmbiguous =
                    existing == null || existingKey == resolvedKey || NodeIndex.isDefaultIdentityPlaceholder(existing)
                if (!slotAcceptsAmbiguous) {
                    return ReceivedUserTransition(
                        after = before,
                        upsertNode = null,
                        notifyNode = null,
                        decision = ReceivedUserDecision.IDENTITY_CONFLICT_IGNORED,
                    )
                }

                // Public keys are broadcast, unauthenticated correlation hints. Installing the presentation here is
                // deliberately in-memory-only: it must never authorize persistence, migration, or deletion. Trusted
                // config installation remains the sole authority for durable identity changes.
                val baseNode = existing ?: createDefaultNode(fromNum, channel)
                val transformed = transformUserNode(baseNode, p, channel, manuallyVerified)
                return ReceivedUserTransition(
                    after = before.put(fromNum, transformed, preferredNum = fromNum),
                    upsertNode = null,
                    notifyNode = null,
                    decision = ReceivedUserDecision.AMBIGUOUS_DUPLICATE_UPDATED,
                )
            }
        }

        return sameNumberUserTransition(
            before,
            fromNum,
            existing ?: createDefaultNode(fromNum, channel),
            p,
            channel,
            manuallyVerified,
            persist = true,
            allowNotification = true,
        )
    }

    private fun sameNumberUserTransition(
        before: NodeIndex,
        fromNum: Int,
        existing: Node,
        incoming: User,
        channel: Int,
        manuallyVerified: Boolean,
        persist: Boolean,
        allowNotification: Boolean,
        unretireNodeNum: Int? = null,
    ): ReceivedUserTransition {
        val isNewNode = existing.isUnknownUser && incoming.hw_model != HardwareModel.UNSET
        val shouldPreserve = shouldPreserveExistingUser(existing.user, incoming)
        val transformed = transformUserNode(existing, incoming, channel, manuallyVerified)
        val notify = if (allowNotification && isNewNode && !shouldPreserve) transformed else null
        val decision =
            when {
                allowNotification && isNewNode && !shouldPreserve -> ReceivedUserDecision.GENUINE_NEW_NODE
                else -> ReceivedUserDecision.KNOWN_SAME_NUMBER
            }
        return ReceivedUserTransition(
            after = before.put(fromNum, transformed),
            upsertNode = transformed.takeIf { persist },
            notifyNode = notify,
            unretireNodeNum = unretireNodeNum,
            decision = decision,
        )
    }

    /** Pure factory for a placeholder [Node] at [num]. Kept side-effect-free so the reducer can call it safely. */
    private fun createDefaultNode(num: Int, channel: Int): Node {
        val userId = NodeAddress.numToDefaultId(num)
        return Node(
            num = num,
            user =
            User(
                id = userId,
                long_name = "Meshtastic ${userId.takeLast(GENERATED_NODE_NAME_SUFFIX_LENGTH)}",
                short_name = userId.takeLast(GENERATED_NODE_NAME_SUFFIX_LENGTH),
                hw_model = HardwareModel.UNSET,
            ),
            channel = channel,
        )
    }

    /**
     * Pure transform of an existing [node] under an incoming [User] packet (extracted from the legacy updateNode path).
     */
    private fun transformUserNode(node: Node, p: User, channel: Int, manuallyVerified: Boolean): Node {
        val shouldPreserve = shouldPreserveExistingUser(node.user, p)
        return if (shouldPreserve) {
            node.copy(channel = channel, manuallyVerified = manuallyVerified)
        } else {
            val incomingKey = resolveValidatedPublicKeyHint(p.public_key)
            val sanitizedUser = if (incomingKey == null) p.copy(public_key = ByteString.EMPTY) else p
            // Prefer node.publicKey when valid (the authoritative stored key); fall back to node.user.public_key.
            val existingKey = resolveNodePublicKeyHint(node)
            val keyMatch = existingKey == null || existingKey == incomingKey
            val newUser = if (keyMatch) sanitizedUser else sanitizedUser.copy(public_key = ByteString.EMPTY)
            node.copy(
                user = newUser,
                publicKey = newUser.public_key,
                channel = channel,
                manuallyVerified = manuallyVerified,
            )
        }
    }

    /** Applies ordinary same-number persistence and notification side effects once after the reducer CAS commits. */
    private fun applyReceivedUserEffects(transition: ReceivedUserTransition, session: RadioSessionContext?) {
        transition.upsertNode?.let { node ->
            if (shouldPersist(node)) {
                radioInterfaceService.launchSessionWork(scope, session) { persistLatestNode(node.num) }
            }
        }
        transition.notifyNode?.let { node ->
            radioInterfaceService.launchSessionWork(scope, session) {
                // Resolve the display title before validation so the suspending compose-resources call does
                // not create a window where state can change after identity checks pass.
                val title = notificationTitleFormatter(node.user.short_name)

                // Revalidate immediately before dispatch: the number may have been retired, cleared, or
                // replaced by a different identity between the reducer CAS and this coroutine dispatch.
                val currentState = nodeState.value
                if (currentState.isRetiredAbsent(node.num)) {
                    Logger.d { "[NodeIdentity] notification-skip num=${node.num} reason=retired" }
                    return@launchSessionWork
                }
                val currentNode = currentState.index.byNum[node.num]
                if (currentNode == null) {
                    Logger.d { "[NodeIdentity] notification-skip num=${node.num} reason=absent" }
                    return@launchSessionWork
                }
                if (currentState.localNodeNum == node.num) {
                    Logger.d { "[NodeIdentity] notification-skip num=${node.num} reason=local-node" }
                    return@launchSessionWork
                }
                // Identity comparison: prefer validated public-key equality; fall back to user.id.
                // Reject if one snapshot has a key and the other does not — that is an identity change.
                val nodeKey = resolveNodePublicKeyHint(node)
                val currentKey = resolveNodePublicKeyHint(currentNode)
                val sameIdentity =
                    when {
                        nodeKey != null && currentKey != null -> nodeKey == currentKey

                        nodeKey == null && currentKey == null ->
                            node.user.id.isNotEmpty() &&
                                currentNode.user.id.isNotEmpty() &&
                                node.user.id == currentNode.user.id

                        else -> false
                    }
                if (!sameIdentity) {
                    Logger.d { "[NodeIdentity] notification-skip num=${node.num} reason=identity-changed" }
                    return@launchSessionWork
                }
                Logger.d { "[NodeIdentity] notification-dispatch num=${node.num}" }
                notificationManager.dispatch(
                    Notification(
                        title = title,
                        message = node.user.long_name,
                        category = Notification.Category.NodeEvent,
                        id = node.num,
                        deepLinkUri = "meshtastic://meshtastic/nodes/${node.num}",
                    ),
                )
            }
        }
    }

    /**
     * Test seam over the notification title; production resolves compose-resources lazily inside the dispatch
     * coroutine.
     */
    internal var notificationTitleFormatter: suspend (String) -> String = { shortName ->
        getStringSuspend(Res.string.new_node_seen, shortName)
    }

    /** Test seam invoked after reduction but before CAS to deterministically race a local-node-number update. */
    internal var receivedUserReductionHook: (() -> Unit)? = null

    override fun toNodeID(nodeNum: Int): String = if (nodeNum == NodeAddress.NODENUM_BROADCAST) {
        NodeAddress.ID_BROADCAST
    } else {
        nodeState.value.index.byNum[nodeNum]?.user?.id ?: NodeAddress.numToDefaultId(nodeNum)
    }
}
