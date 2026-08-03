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
package org.meshtastic.core.takserver

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.TakPrefs
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Instant

/**
 * Publishes Meshtastic node-database entries to connected TAK clients as CoT contacts.
 *
 * This is the mesh -> ATAK visualization path, and it is entirely local: synthesized node CoT travels app -> TAK client
 * and never crosses the mesh. Mesh-originated TAKPacket traffic is [TAKMeshIntegration]'s job; the two are independent.
 *
 * Opt-in via [TakPrefs.isMeshToCotEnabled] and additionally gated on the TAK server running, because the owning
 * [TAKMeshIntegration] is itself only started while the server is enabled.
 */
@OptIn(ExperimentalAtomicApi::class)
class MeshToCotBroadcaster(
    private val takServerManager: TAKServerManager,
    private val nodeRepository: NodeRepository,
    private val takPrefs: TakPrefs,
    private val dispatchers: CoroutineDispatchers,
) {
    private val isRunning = AtomicBoolean(false)

    @Volatile private var job: Job? = null

    // Last CoT sent per node number, time-normalized (see normalizeForDedup) so an unchanged node
    // is not re-sent on every unrelated nodeDB emission. Guarded by sentMutex.
    private val lastSent = mutableMapOf<Int, CoTMessage>()
    private val sentMutex = Mutex()

    fun start(scope: CoroutineScope) {
        // CAS, not a job-null check: two concurrent start() calls must not both launch, and a
        // dead job left by a cancelled scope must not block every future start().
        if (!isRunning.compareAndSet(expectedValue = false, newValue = true)) return
        job =
            scope.launch(dispatchers.default) {
                try {
                    takPrefs.isMeshToCotEnabled.collectLatest { enabled ->
                        if (!enabled) return@collectLatest
                        Logger.i { "Mesh-to-CoT enabled — publishing node contacts to TAK clients" }
                        runEnabled()
                    }
                } finally {
                    // Owning scope cancelled without stop(): release the guard so a later start()
                    // on a fresh scope isn't refused forever.
                    isRunning.store(false)
                }
            }
    }

    fun stop() {
        if (!isRunning.compareAndSet(expectedValue = true, newValue = false)) return
        // lastSent is deliberately NOT cleared here: cancel() doesn't join, so a still-finishing
        // publish() on another thread may hold sentMutex, and clearing unsynchronized would race
        // it. runEnabled() drops the state on the next enable instead.
        job?.cancel()
        job = null
        Logger.i { "Mesh-to-CoT stopped" }
    }

    private suspend fun runEnabled() = coroutineScope {
        // Fresh dedup state per enable cycle, so nothing carried over from a previous run (or a
        // previous device) suppresses the initial publish.
        clearSent()

        // A newly attached client has none of our prior broadcasts, so drop the dedup state
        // and replay every eligible node. Uses the live snapshot rather than the nodeDBbyNum
        // cache, which can briefly hold the previous transport's map after a device switch.
        launch {
            takServerManager.clientConnected.collect {
                clearSent()
                publish(nodeRepository.getNodeDbSnapshot().values, reason = "client connected")
            }
        }

        // Stationary nodes never change, so dedup alone would let their ATAK markers expire at
        // MESH_NODE_STALE_MINUTES. Re-send everything well inside that window.
        launch {
            while (true) {
                delay(MESH_TO_COT_REFRESH_INTERVAL_MS)
                clearSent()
                publish(nodeRepository.getNodeDbSnapshot().values, reason = "periodic refresh")
            }
        }

        launch { nodeRepository.nodeDBbyNum.collect { publish(it.values, reason = "node update") } }
    }

    private suspend fun publish(nodes: Collection<Node>, reason: String) {
        // Without a connected client every broadcast() would land in TAKServerManager's 50-entry
        // offline queue and evict genuine mesh CoT. Nothing is lost by skipping: connecting a
        // client triggers a full replay. Re-checked per node because a client can disconnect
        // mid-replay, and the inter-message spacing makes a large replay take whole seconds.
        val ourNodeNum = nodeRepository.myNodeInfo.value?.myNodeNum
        val eligible = nodes.filter { it.isEligibleForCot(ourNodeNum) }
        var sent = 0
        for (node in eligible) {
            if (takServerManager.connectionCount.value <= 0) return
            val cot = node.toCoTMessage()
            if (!admit(node.num, cot)) continue
            takServerManager.broadcast(cot)
            sent++
            delay(MESH_TO_COT_BROADCAST_SPACING_MS)
        }
        if (sent > 0) {
            Logger.i { "Mesh-to-CoT: sent $sent of ${eligible.size} eligible node(s) to TAK clients ($reason)" }
        }
    }

    /** Records [cot] as the latest for [nodeNum], returning false when an identical event was already sent. */
    private suspend fun admit(nodeNum: Int, cot: CoTMessage): Boolean = sentMutex.withLock {
        val normalized = normalizeForDedup(cot)
        if (lastSent[nodeNum] == normalized) return false
        lastSent[nodeNum] = normalized
        true
    }

    private suspend fun clearSent() = sentMutex.withLock { lastSent.clear() }

    private companion object {
        /**
         * Strips the timestamps so two events describing the same node state compare equal. Every other CoT field
         * participates, so any change to position, callsign, battery, or telemetry re-broadcasts.
         */
        fun normalizeForDedup(cot: CoTMessage): CoTMessage =
            cot.copy(time = Instant.DISTANT_PAST, start = Instant.DISTANT_PAST, stale = Instant.DISTANT_PAST)
    }
}
