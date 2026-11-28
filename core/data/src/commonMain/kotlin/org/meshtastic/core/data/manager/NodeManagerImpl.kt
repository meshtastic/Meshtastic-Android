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
import kotlinx.atomicfu.getAndUpdate
import kotlinx.atomicfu.update
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.ByteString
import org.koin.core.annotation.Single
import org.meshtastic.core.common.di.ServiceScope
import org.meshtastic.core.common.util.clampTimestampToNow
import org.meshtastic.core.common.util.crc32
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.model.Capabilities
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.util.NodeIdLookup
import org.meshtastic.core.model.util.TimeConstants
import org.meshtastic.core.repository.ConnectionIdentity
import org.meshtastic.core.repository.MeshNotificationManager
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.getStringSuspend
import org.meshtastic.core.resources.new_node_seen
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.FirmwareEdition
import org.meshtastic.proto.Paxcount
import org.meshtastic.proto.StatusMessage
import org.meshtastic.proto.User
import org.meshtastic.sdk.NodeField
import kotlinx.coroutines.flow.update as updateStateFlow
import org.meshtastic.proto.NodeInfo as ProtoNodeInfo
import org.meshtastic.proto.Position as ProtoPosition

/** Short one-way diagnostic value that cannot disclose a raw public-key prefix. */
private const val KEY_FINGERPRINT_HEX_LENGTH = 8

internal fun publicKeyLogFingerprint(key: ByteString): String = key.sha256().hex().take(KEY_FINGERPRINT_HEX_LENGTH)

/** Implementation of [NodeManager] that maintains an in-memory database of the mesh. */
@Suppress("LongParameterList", "TooManyFunctions")
@Single(binds = [NodeManager::class, NodeIdLookup::class])
class NodeManagerImpl(
    private val nodeRepository: NodeRepository,
    private val serviceNotifications: MeshNotificationManager,
    private val radioInterfaceService: RadioInterfaceService,
    private val scope: ServiceScope,
) : NodeManager {

    // Fixed stripes bound mutex lifetime while preserving same-node persistence ordering. Hash collisions only reduce
    // parallelism; they never weaken ordering for a node.
    private val nodePersistenceLanes = List(NODE_PERSISTENCE_LANE_COUNT) { Mutex() }

    private fun persistenceLane(nodeNum: Int): Mutex =
        nodePersistenceLanes[(nodeNum.toLong() and Int.MAX_VALUE.toLong()).toInt() % nodePersistenceLanes.size]

    // A persist not yet in its lane writes the latest node once it gets there, so one per node and session is enough.
    // Each is session work that teardown joins, so a slow database must not collect one per packet.
    private val pendingPersists = atomic(emptyMap<Pair<Int, RadioSessionContext?>, Any>())

    private fun schedulePersistence(nodeNum: Int, session: RadioSessionContext?) {
        val key = nodeNum to session
        val token = Any()
        var claimed = false
        pendingPersists.update { pending ->
            claimed = key !in pending
            if (claimed) pending + (key to token) else pending
        }
        if (!claimed) return
        val release = { pendingPersists.update { pending -> if (pending[key] === token) pending - key else pending } }
        radioInterfaceService
            .launchSessionWork(scope, session) { persistLatestNode(nodeNum, onLaneEntered = release) }
            .invokeOnCompletion { release() }
    }

    /**
     * Resolves a validated public-key correlation hint from a stored [Node], preferring [Node.publicKey] and falling
     * back to [User.public_key] only when the primary field is not itself a valid hint.
     */
    internal fun resolveNodePublicKeyHint(node: Node): ByteString? = nodePublicKeyHint(node)

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
        /** Numbers the user removed locally; the radio still holds them until the removeNode ack. */
        val pendingRemovals: PersistentSet<Int> = persistentSetOf(),
    ) {
        fun isRetiredAbsent(nodeNum: Int): Boolean = nodeNum in retiredNodeNums && nodeNum !in index.byNum

        fun isSuppressed(nodeNum: Int): Boolean = isRetiredAbsent(nodeNum) || nodeNum in pendingRemovals
    }

    private val nodeState = atomic(NodeState())

    /** The favorite, ignored and muted flags the SDK last reported per node in this session. */
    private val reportedFlags = atomic(persistentMapOf<Int, ReportedFlags>())

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

    // A StateFlow rather than a plain field: setFirmwareVersion, clear, loadCachedNodeDB and the install paths run
    // on different coroutines, so reads need atomic visibility, and the UI needs to observe it to suppress the
    // unheard consumers until the database normalization has landed.
    override val reportsHeardOnCurrentLora = MutableStateFlow(false)

    override fun setFirmwareVersion(version: String?, session: RadioSessionContext?) {
        val supported = Capabilities(version).supportsHeardOnCurrentLora
        reportsHeardOnCurrentLora.value = supported
        // Normalize in the database, not just in nodeState: the node list renders from the repository flows, which
        // read rows directly and would otherwise still see a false written by a radio whose firmware could report it.
        // Bound to the originating session's scope: a delayed write from a superseded session would otherwise resolve
        // the next session's database and clear flags that session had legitimately set.
        if (!supported) {
            radioInterfaceService.launchSessionWork(scope, session) { nodeRepository.markAllHeardOnCurrentLora() }
        }
    }

    companion object {
        private const val NODE_PERSISTENCE_LANE_COUNT = 64
        private const val MQTT_NAME_SUFFIX = " (MQTT)"

        /** `precision_bits` value used by firmware for an un-degraded (full 32-bit) coordinate. */
        private const val FULL_POSITION_PRECISION_BITS = 32

        /** Total bits in the `latitude_i` / `longitude_i` fixed-point representation. */
        private const val POSITION_COORDINATE_BITS = 32

        /**
         * Reproduces the firmware's coarsening of a coordinate to [bits] of precision: mask off the low bits, then
         * re-center the value inside the remaining box. Mirrors `Router::sendLocal` / `PositionModule` behaviour so we
         * can tell whether a coarse report describes the box that already contains a coordinate we hold.
         */
        internal fun coarsenCoordinate(degI: Int, bits: Int): Int =
            (degI and (-1 shl (POSITION_COORDINATE_BITS - bits))) + (1 shl (POSITION_COORDINATE_BITS - 1 - bits))

        /**
         * Returns [incoming] with the coordinates of [stored] restored when [incoming] is merely a precision-degraded
         * restatement of what we already know (see [isRedundantCoarsePosition]); otherwise [incoming] unchanged.
         */
        internal fun preservingKnownPrecision(incoming: ProtoPosition, stored: ProtoPosition): ProtoPosition =
            if (isRedundantCoarsePosition(incoming, stored)) {
                incoming
                    .newBuilder()
                    .also { wb ->
                        wb.latitude_i = stored.latitude_i
                        wb.longitude_i = stored.longitude_i
                        wb.precision_bits = stored.precision_bits
                    }
                    .build()
            } else {
                incoming
            }

        /**
         * True when [incoming] is a precision-degraded report that carries no new information over [stored].
         *
         * Nodes on a channel with `position_precision` set broadcast their location coarsened to the box centre. When
         * we already hold a more precise coordinate for that node (e.g. a fixed position we just wrote, or an earlier
         * full-precision report) and that coordinate falls inside the same box, adopting the coarse value would make
         * the position visibly "drift" away from the real one (issue #6360). Only positions from a *different* box
         * indicate the node actually moved, and those are accepted normally.
         */
        internal fun isRedundantCoarsePosition(incoming: ProtoPosition, stored: ProtoPosition): Boolean {
            val incomingBits = incoming.precision_bits
            val storedLatI = stored.latitude_i ?: 0
            val storedLonI = stored.longitude_i ?: 0
            // `0` means the stored coordinate was never degraded, so treat it as full precision.
            val storedBits = stored.precision_bits.takeIf { it > 0 } ?: FULL_POSITION_PRECISION_BITS

            val isDegradedReport = incomingBits > 0 && incomingBits < FULL_POSITION_PRECISION_BITS
            val haveMorePreciseCoordinate = (storedLatI != 0 || storedLonI != 0) && storedBits > incomingBits

            return isDegradedReport &&
                haveMorePreciseCoordinate &&
                coarsenCoordinate(storedLatI, incomingBits) == (incoming.latitude_i ?: 0) &&
                coarsenCoordinate(storedLonI, incomingBits) == (incoming.longitude_i ?: 0)
        }

        /**
         * Ceiling on entries in the in-memory node index. See [NodeIndex.evictedToFit].
         *
         * An order of magnitude above the largest NodeDB firmware exposes (`MAX_NUM_NODES` is 100–250 by target), so a
         * legitimately busy mesh never reaches it and only sustained novel-`from` traffic does.
         */
        const val MAX_IN_MEMORY_NODES = 2_000

        /**
         * Size the in-memory node index is cut back to once it passes [MAX_IN_MEMORY_NODES], so a flood pays for one
         * eviction pass per 200 novel nodes rather than per node. A cached NodeDB over the cap loads at this size.
         */
        const val IN_MEMORY_NODES_LOW_WATER = MAX_IN_MEMORY_NODES * 9 / 10
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
            // Cached rows may carry a flag written by a different radio. The connected device's NodeInfo dump
            // repopulates real values when it reports them, so "heard" is the only safe resting state here.
            val snapshot =
                nodeRepository.getNodeDbSnapshot().mapValues { (_, node) ->
                    if (reportsHeardOnCurrentLora.value) node else node.copy(heardOnCurrentLora = true)
                }
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
                        state
                            .copy(
                                index = NodeIndex.fromByNum(filteredSnapshot),
                                localNodeNum = state.localNodeNum ?: persistedLocalNum,
                            )
                            .withBoundedIndex()
                    } else {
                        // Live state changed during the load window. Merge: current nodes (with their fresher
                        // fields) win over snapshot rows at the same num; snapshot rows only fill slots that the
                        // live index does not already carry. Live local-node info and retirement state remain
                        // authoritative; a missing local number receives the same persisted fallback as a direct load.
                        val liveByNum = state.index.byNum
                        val merged = filteredSnapshot.toMutableMap()
                        liveByNum.forEach { (num, node) -> merged[num] = node }
                        state
                            .copy(
                                index = NodeIndex.fromByNum(merged),
                                localNodeNum = state.localNodeNum ?: persistedLocalNum,
                            )
                            .withBoundedIndex()
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
        reportedFlags.value = persistentMapOf()
        isNodeDbReady.value = false
        allowNodeDbWrites.value = false
        myNodeNum.value = null
        myDeviceId.value = null
        firmwareEdition.value = null
        reportsHeardOnCurrentLora.value = false
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
            minAppVersion = mi.minAppVersion,
            hasWifi = mi.hasWifi,
            deviceId = mi.deviceId ?: myNode?.user?.id,
        )
    }

    override fun getMyId(): String {
        val num = nodeState.value.localNodeNum ?: nodeRepository.myNodeInfo.value?.myNodeNum ?: return ""
        return nodeState.value.index.byNum[num]?.user?.id ?: ""
    }

    override fun removeByNodenum(nodeNum: Int) {
        nodeState.update { state ->
            state.copy(
                index = state.index.remove(nodeNum),
                pendingRemovals = state.pendingRemovals.adding(nodeNum),
                revision = state.revision + 1,
            )
        }
        serviceNotifications.cancelLowBatteryNotification(nodeNum)
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
            committedHints = removedNums.associateWith { num ->
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
            serviceNotifications.cancelNewNodeNotification(num)
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

    /**
     * Applies [MAX_IN_MEMORY_NODES] to this state's index, cutting it back to [IN_MEMORY_NODES_LOW_WATER] once it is
     * over, and protecting the local node and [alsoKeep].
     *
     * EVERY path that installs an index must go through this. There are three — the ordinary [updateNodeState] reducer,
     * the [handleReceivedUser] commit, and the [loadCachedNodeDB] snapshot install — and bounding only the first left
     * the NodeInfo path, which is the one an unauthenticated peer drives most directly, completely unbounded.
     *
     * Returns the receiver unchanged when nothing needs evicting, so a CAS on the result stays cheap.
     */
    private fun NodeState.withBoundedIndex(alsoKeep: Int? = null): NodeState {
        val bounded =
            index.evictedToFit(
                MAX_IN_MEMORY_NODES,
                IN_MEMORY_NODES_LOW_WATER,
                keep = setOfNotNull(alsoKeep, localNodeNum),
            )
        return if (bounded === index) this else copy(index = bounded)
    }

    private data class NodeStateChange(val previous: Node, val next: Node)

    private fun updateNodeState(nodeNum: Int, channel: Int, transform: (Node) -> Node): NodeStateChange? {
        var change: NodeStateChange? = null
        nodeState.update { state ->
            change = null
            if (state.isSuppressed(nodeNum)) return@update state
            val current = state.index.byNum[nodeNum] ?: createDefaultNode(nodeNum, channel)
            val next = transform(current)
            change = NodeStateChange(previous = current, next = next)
            state
                .copy(index = state.index.put(nodeNum, next), revision = state.revision + 1)
                .withBoundedIndex(alsoKeep = nodeNum)
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
        if (change != null && shouldPersist(change.next)) schedulePersistence(nodeNum, session)
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

    /** [transform] may run more than once under compare-and-set contention, so it must be side-effect free. */
    private suspend fun updateNodeAndPersist(nodeNum: Int, transform: (Node) -> Node) {
        val result = updateNodeState(nodeNum, channel = 0, transform)?.next ?: return
        if (shouldPersist(result)) persistLatestNode(nodeNum)
    }

    /** Serializes persistence per node and reads the latest in-memory value inside that lane. */
    private suspend fun persistLatestNode(nodeNum: Int, onLaneEntered: () -> Unit = {}) =
        persistenceLane(nodeNum).withLock {
            onLaneEntered()
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
        // Pin admission and session ownership to packet arrival. A packet that loses a node-state CAS must not become a
        // live discovery after initial sync, and a packet from a cleared session must never retry into the new one.
        val allowNotificationAtArrival = isNodeDbReady.value
        val arrivalSessionGeneration = sessionGeneration.value
        while (sessionGeneration.value == arrivalSessionGeneration) {
            val before = nodeState.value
            if (fromNum in before.pendingRemovals) {
                Logger.d { "[NodeIdentity] user from=$fromNum discarded while its removal awaits the radio" }
                return
            }
            val transition =
                reduceReceivedUser(
                    before.index,
                    fromNum,
                    p,
                    channel,
                    manuallyVerified,
                    before.localNodeNum,
                    before.isRetiredAbsent(fromNum),
                    allowNotificationAtArrival = allowNotificationAtArrival,
                    retiredKeyHint = before.retiredKeyHints[fromNum],
                )
            receivedUserReductionHook?.invoke()
            if (sessionGeneration.value != arrivalSessionGeneration) break
            val after =
                before
                    .copy(
                        index = transition.after,
                        retiredNodeNums =
                        transition.unretireNodeNum?.let { before.retiredNodeNums.removing(it) }
                            ?: before.retiredNodeNums,
                        retiredKeyHints =
                        transition.unretireNodeNum?.let { before.retiredKeyHints.removing(it) }
                            ?: before.retiredKeyHints,
                        revision = before.revision + 1,
                    )
                    .withBoundedIndex(alsoKeep = fromNum)
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
        Logger.d { "[NodeIdentity] user from=$fromNum discarded after session reset" }
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
            val rawPosTime = if (p.time != 0) p.time else (defaultTime / TimeConstants.MS_PER_SEC).toInt()
            val posTime = clampTimestampToNow(rawPosTime)
            val newLastHeard = maxOf(node.lastHeard, posTime)

            val newPos =
                when {
                    isZeroPos ->
                        p.newBuilder()
                            .also { wb ->
                                wb.time = posTime
                                wb.latitude_i = node.position.latitude_i
                                wb.longitude_i = node.position.longitude_i
                                wb.altitude = p.altitude ?: node.position.altitude
                                wb.sats_in_view = p.sats_in_view
                            }
                            .build()

                    else ->
                        preservingKnownPrecision(p.newBuilder().also { wb -> wb.time = posTime }.build(), node.position)
                }

            node.copy(position = newPos, lastHeard = newLastHeard)
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

    override suspend fun updateNodeStatusAndPersist(nodeNum: Int, status: String?) {
        updateNodeAndPersist(nodeNum) { node -> applyNodeStatus(node, status) }
    }

    private fun applyNodeStatus(node: Node, status: String?): Node =
        node.copy(nodeStatus = status?.takeIf { it.isNotEmpty() })

    override fun installNodeInfo(info: ProtoNodeInfo) {
        val reportsHeard = reportsHeardOnCurrentLora.value
        cancelLowBatteryIfUnfavorited(info)
        recordReportedFlags(info)
        // The handshake snapshot is firmware's word on the node, so it ends a removal still waiting for its ack.
        nodeState.update { state ->
            if (info.num in state.pendingRemovals) {
                state.copy(pendingRemovals = state.pendingRemovals.removing(info.num))
            } else {
                state
            }
        }
        // Stage-2 configuration installation persists the complete node snapshot through installConfig.
        updateNodeState(info.num, channel = 0) { node -> applyNodeInfo(node, info, reportsHeard) }
    }

    override fun applySessionNode(info: ProtoNodeInfo, changed: Set<NodeField>?, session: RadioSessionContext) {
        // The SDK still holds a node the user removed until the ack, so only a later add brings it back.
        if (changed != null && info.num in nodeState.value.pendingRemovals) return
        if (changed == null) nodeState.update { it.copy(pendingRemovals = it.pendingRemovals.removing(info.num)) }
        val user = info.user
        val held = nodeState.value.index.byNum[info.num]
        val heldUnknown = held?.isUnknownUser != false
        // The SDK builds a node it first hears in a packet with default flags, channel 0 and no signing bit, as
        // firmware does after evicting it; only firmware's NodeDB sets those, so a node the app holds keeps its own.
        val keepLocalState = changed == null && held != null && !info.carriesFirmwareFlags()
        val userChanged = changed == null || NodeField.User in changed || NodeField.Name in changed
        if (user != null && (userChanged || heldUnknown)) {
            handleReceivedUser(info.num, sanitizeReceivedUser(user, info.via_mqtt), info.channel, session = session)
        }
        val flags = reportedFlagChanges(info, changed).takeUnless { keepLocalState } ?: ReportedFlagChanges()
        if (flags.favorite == false) cancelLowBatteryIfUnfavorited(info)
        val reportsHeard = reportsHeardOnCurrentLora.value
        updateNodeAndSchedulePersistence(info.num, info.channel, session) { node ->
            applySessionNodeFields(node, info, changed, flags, reportsHeard, keepLocalState)
        }
    }

    private fun ProtoNodeInfo.carriesFirmwareFlags(): Boolean =
        is_favorite || is_ignored || is_muted || is_key_manually_verified || has_xeddsa_signed

    override fun applySessionNodeRemoved(nodeNum: Int) {
        nodeState.update { state ->
            state.copy(
                index = state.index.remove(nodeNum),
                pendingRemovals = state.pendingRemovals.removing(nodeNum),
                revision = state.revision + 1,
            )
        }
        reportedFlags.update { it.removing(nodeNum) }
        serviceNotifications.cancelLowBatteryNotification(nodeNum)
    }

    override fun cancelPendingRemoval(nodeNum: Int) {
        nodeState.update { state -> state.copy(pendingRemovals = state.pendingRemovals.removing(nodeNum)) }
    }

    /**
     * Each live update carries the SDK's flags, which trail a local optimistic write until its admin ack. A flag is
     * applied only when an [NodeField.Other] change moved it from the value the SDK last reported, or when the node is
     * new to the session.
     */
    private fun reportedFlagChanges(info: ProtoNodeInfo, changed: Set<NodeField>?): ReportedFlagChanges {
        val reported = ReportedFlags(info.is_favorite, info.is_ignored, info.is_muted)
        val previous = recordReportedFlags(info)
        return when {
            changed == null || previous == null ->
                ReportedFlagChanges(reported.favorite, reported.ignored, reported.muted)

            NodeField.Other !in changed -> ReportedFlagChanges()

            else ->
                ReportedFlagChanges(
                    favorite = reported.favorite.takeIf { it != previous.favorite },
                    ignored = reported.ignored.takeIf { it != previous.ignored },
                    muted = reported.muted.takeIf { it != previous.muted },
                )
        }
    }

    /** Stores what the SDK now reports for [info] and returns what it reported before. */
    private fun recordReportedFlags(info: ProtoNodeInfo): ReportedFlags? {
        val reported = ReportedFlags(info.is_favorite, info.is_ignored, info.is_muted)
        return reportedFlags.getAndUpdate { it.putting(info.num, reported) }[info.num]
    }

    /**
     * The packet projection owns the link metrics and hop count, which the SDK computes differently or not at all, and
     * a live update never clears a heard-on-current-LoRa flag the packets set. Position and channel move only when the
     * change says they did, so a stale SDK copy cannot overwrite a fixed position the app just wrote.
     */
    private fun applySessionNodeFields(
        node: Node,
        info: ProtoNodeInfo,
        changed: Set<NodeField>?,
        flags: ReportedFlagChanges,
        reportsHeard: Boolean,
        keepLocalState: Boolean,
    ): Node {
        val position =
            info.position
                ?.takeIf { changed == null || NodeField.Position in changed }
                ?.let { incoming ->
                    // An untimed fix takes the packet's receive time, which the SDK put in last_heard.
                    val time = incoming.time.takeIf { it != 0 } ?: info.last_heard
                    val timed = incoming.newBuilder().also { wb -> wb.time = clampTimestampToNow(time) }.build()
                    preservingKnownPrecision(timed, node.position)
                } ?: node.position
        return node.copy(
            position = position,
            channel =
            if (!keepLocalState && (changed == null || NodeField.Other in changed)) info.channel else node.channel,
            heardOnCurrentLora = node.heardOnCurrentLora || !reportsHeard || info.heard_on_current_lora,
            lastHeard = maxOf(node.lastHeard, clampTimestampToNow(info.last_heard)),
            deviceMetrics = info.device_metrics ?: node.deviceMetrics,
            viaMqtt = info.via_mqtt,
            isFavorite = flags.favorite ?: node.isFavorite,
            isIgnored = flags.ignored ?: node.isIgnored,
            isMuted = flags.muted ?: node.isMuted,
            // A live merge never learns the signing bit, so only an add carrying firmware's word may clear it.
            signsPackets =
            if (changed == null && !keepLocalState) {
                info.has_xeddsa_signed
            } else {
                node.signsPackets || info.has_xeddsa_signed
            },
            manuallyVerified = node.manuallyVerified || info.is_key_manually_verified,
        )
    }

    private data class ReportedFlags(val favorite: Boolean, val ignored: Boolean, val muted: Boolean)

    private data class ReportedFlagChanges(
        val favorite: Boolean? = null,
        val ignored: Boolean? = null,
        val muted: Boolean? = null,
    )

    /** Only favorites are warned about, and one unfavorited from another client arrives here, not via setFavorite. */
    private fun cancelLowBatteryIfUnfavorited(info: ProtoNodeInfo) {
        val state = nodeState.value
        if (info.is_favorite || info.num == state.localNodeNum) return
        if (state.index.byNum[info.num]?.isFavorite == true) serviceNotifications.cancelLowBatteryNotification(info.num)
    }

    private fun applyNodeInfo(node: Node, info: ProtoNodeInfo, reportsHeard: Boolean): Node {
        var next = node
        val user = info.user
        if (user != null && !shouldPreserveExistingUser(node.user, user)) {
            val newUser = sanitizeReceivedUser(user, info.via_mqtt)
            next = next.copy(user = newUser, publicKey = newUser.public_key)
        }
        info.position?.let { position ->
            // The NodeDB snapshot from the connected device carries the coarsened position for nodes on a
            // precision-limited channel; do not let it clobber a more precise coordinate we already hold (#6360).
            val timed = position.newBuilder().also { wb -> wb.time = clampTimestampToNow(position.time) }.build()
            next = next.copy(position = preservingKnownPrecision(timed, next.position))
        }
        // Firmware that predates the field never sends it, and a proto3 bool decodes as false - which would mark
        // every node unheard. Normalize rather than skip: a value persisted by a previous radio must not survive
        // into a session whose firmware cannot report it.
        next = next.copy(heardOnCurrentLora = !reportsHeard || info.heard_on_current_lora)
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
            manuallyVerified = next.manuallyVerified || info.is_key_manually_verified,
        )
    }

    /** A licensed (ham) operator may not encrypt, so its key is dropped; an MQTT-heard name is marked as such. */
    private fun sanitizeReceivedUser(user: User, viaMqtt: Boolean): User {
        val unlicensedKey =
            if (user.is_licensed == true) {
                user.newBuilder().also { wb -> wb.public_key = ByteString.EMPTY }.build()
            } else {
                user
            }
        return if (viaMqtt && !unlicensedKey.long_name.endsWith(MQTT_NAME_SUFFIX)) {
            unlicensedKey
                .newBuilder()
                .also { wb -> wb.long_name = "${unlicensedKey.long_name}$MQTT_NAME_SUFFIX" }
                .build()
        } else {
            unlicensedKey
        }
    }

    override fun insertMetadata(nodeNum: Int, metadata: DeviceMetadata, session: RadioSessionContext?) {
        if (nodeState.value.isRetiredAbsent(nodeNum)) return
        radioInterfaceService.launchSessionWork(scope, session) {
            if (!nodeState.value.isRetiredAbsent(nodeNum)) nodeRepository.insertMetadata(nodeNum, metadata)
        }
    }

    /** Applies ordinary same-number persistence and notification side effects once after the reducer CAS commits. */
    private fun applyReceivedUserEffects(transition: ReceivedUserTransition, session: RadioSessionContext?) {
        transition.upsertNode?.let { node -> if (shouldPersist(node)) schedulePersistence(node.num, session) }
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
                serviceNotifications.showNewNodeSeenNotification(node, title)
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

    /** Test seam invoked after reduction but before CAS to deterministically race a concurrent node-state update. */
    internal var receivedUserReductionHook: (() -> Unit)? = null

    override fun toNodeID(nodeNum: Int): String = if (nodeNum == NodeAddress.NODENUM_BROADCAST) {
        NodeAddress.ID_BROADCAST
    } else {
        nodeState.value.index.byNum[nodeNum]?.user?.id ?: NodeAddress.numToDefaultId(nodeNum)
    }
}
