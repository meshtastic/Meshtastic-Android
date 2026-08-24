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
package org.meshtastic.core.database

import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import co.touchlab.kermit.Logger
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.database.entity.MergeMarkerEntity

/**
 * Folds the contents of one device database ([source]) into another ([dest]) when both turn out to belong to the same
 * physical node reached over different transports (BLE / TCP / USB). Called once, by [DatabaseManager.associateDevice],
 * the first time a secondary transport learns a `myNodeNum` that another transport already claimed.
 *
 * Every per-device table is unified so switching transport is seamless: messages (+FTS), reactions, contact mute/read
 * settings, nodes and their notes, per-node metadata, quick-chat actions, the audit log (each session's received-packet
 * history — the source of the telemetry timelines, position history, and traceroute results the UI reconstructs),
 * traceroute positions, and discovery sessions. Where the same row exists on both sides the destination is preferred
 * (it will be refreshed by the same radio's re-dump on connect); source-only rows and strictly newer history are
 * brought over. Packets/reactions in [source] and [dest] already share the same `myNodeNum` (same node), so no key
 * remapping is needed there; autoincrement-keyed rows (packets, discovery) are re-inserted with fresh ids to avoid
 * collisions.
 */
internal class StaleAssociationException : Exception("Transport session no longer authorizes database association")

object DatabaseMerger {

    /**
     * Folds [source] into [dest], skipping the work if [sourceName] was already merged into [dest]. [sourceName] is the
     * source database's file name — the stable key under which the merge is recorded (see [MergeMarkerEntity]).
     *
     * The caller owns the transport-session lifecycle lease through this method's return. That lease makes transaction
     * commit and session rollover mutually exclusive; [isAssociationActive] remains a defensive check for authority
     * lost before the merge entered its commit phase.
     */
    suspend fun merge(
        source: MeshtasticDatabase,
        dest: MeshtasticDatabase,
        sourceName: String,
        isAssociationActive: () -> Boolean = { true },
    ) {
        // All destination writes run in a single transaction so a crash or exception mid-merge rolls
        // back cleanly instead of leaving `dest` half-merged. The merge marker is written inside that
        // same transaction, so it commits atomically with the copied rows: on a retried merge (e.g. a
        // crash after commit but before associateDevice persisted the address alias) the marker is already
        // present and the whole merge is skipped, so the fresh-id packet/discovery re-inserts — which are
        // NOT idempotent on their own — can never duplicate. Reads from `source` use its own connection
        // pool and don't participate in this transaction.
        var packets = 0
        var skipped = false
        dest.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                ensureAssociationActive(isAssociationActive)
                if (dest.mergeMarkerDao().isMerged(sourceName)) {
                    ensureAssociationActive(isAssociationActive)
                    skipped = true
                    return@immediateTransaction
                }
                packets = mergePackets(source, dest)
                mergeReactions(source, dest)
                mergeContactSettings(source, dest)
                mergeNodes(source, dest)
                mergeMetadata(source, dest)
                mergeQuickChat(source, dest)
                // Logs must precede traceroute positions: the latter has a CASCADE foreign key onto log.uuid.
                mergeLogs(source, dest)
                mergeTraceroutePositions(source, dest)
                mergeDiscovery(source, dest)
                dest.mergeMarkerDao().insertMarker(MergeMarkerEntity(sourceDbName = sourceName, mergedAt = nowMillis))
                // Keep the defensive authority check as the final transaction statement. The caller-held lifecycle
                // lease prevents rollover until commit returns; this check still rolls back if authority was lost
                // before
                // the transaction entered the lease-protected commit phase.
                ensureAssociationActive(isAssociationActive)
            }
        }
        if (skipped) {
            Logger.i { "Source ${anonymizeDbName(sourceName)} already merged; skipped duplicate merge" }
        } else {
            Logger.i { "Merged $packets packets across transports into unified node DB" }
        }
    }

    private fun ensureAssociationActive(isAssociationActive: () -> Boolean) {
        if (!isAssociationActive()) throw StaleAssociationException()
    }

    /**
     * Message history is the primary payload. `uuid` is per-DB autoincrement, so re-insert with uuid = 0 to mint a
     * fresh id and avoid collisions; rebuild the FTS index once at the end so copied messages are searchable.
     */
    private suspend fun mergePackets(source: MeshtasticDatabase, dest: MeshtasticDatabase): Int {
        val packets = source.packetDao().getAllPacketsSnapshot()
        packets.forEach { dest.packetDao().insertPacketForMerge(it.copy(uuid = 0L)) }
        if (packets.isNotEmpty()) dest.packetDao().rebuildFtsIndex()
        return packets.size
    }

    /** Composite PK (myNodeNum, reply_id, user_id, emoji) makes IGNORE a natural dedupe. */
    private suspend fun mergeReactions(source: MeshtasticDatabase, dest: MeshtasticDatabase) {
        dest.packetDao().insertReactionsIgnore(source.packetDao().getAllReactionsSnapshot())
    }

    /** Keep the destination's settings on conflict (IGNORE); only bring over contacts it has never seen. */
    private suspend fun mergeContactSettings(source: MeshtasticDatabase, dest: MeshtasticDatabase) {
        dest.packetDao().insertContactSettingsIgnore(source.packetDao().getAllContactSettingsSnapshot())
    }

    /**
     * Bring over every node the destination has never seen (so its last-known identity/telemetry/position shows
     * immediately, including offline nodes the radio may not re-report). For overlapping nodes, keep the destination's
     * row — the same radio re-dumps it on connect — but fill in a user note where the destination has none, never
     * clobbering one the user already wrote on the canonical DB.
     */
    private suspend fun mergeNodes(source: MeshtasticDatabase, dest: MeshtasticDatabase) {
        val destNums = dest.nodeInfoDao().getAllNodesSnapshot().mapTo(mutableSetOf()) { it.num }
        source.nodeInfoDao().getAllNodesSnapshot().forEach { node ->
            when {
                node.num !in destNums -> dest.nodeInfoDao().upsert(node)

                node.notes.isNotBlank() -> {
                    val existing = dest.nodeInfoDao().getNodeByNum(node.num)
                    if (existing != null && existing.node.notes.isBlank()) {
                        dest.nodeInfoDao().setNodeNotes(node.num, node.notes)
                    }
                }
            }
        }
    }

    /** Per-node DeviceMetadata: bring source rows the dest lacks or that are strictly newer (by timestamp). */
    private suspend fun mergeMetadata(source: MeshtasticDatabase, dest: MeshtasticDatabase) {
        val destByNum = dest.nodeInfoDao().getAllMetadataSnapshot().associateBy { it.num }
        source.nodeInfoDao().getAllMetadataSnapshot().forEach { meta ->
            val existing = destByNum[meta.num]
            if (existing == null || meta.timestamp > existing.timestamp) dest.nodeInfoDao().upsert(meta)
        }
    }

    /**
     * User-authored quick-chat buttons. `uuid`/`position` are per-DB, so append the source's actions after the
     * destination's last one with fresh ids, skipping any the destination already has (same name + message + mode).
     * Without this the source's buttons are lost outright when [DatabaseManager] retires it.
     */
    private suspend fun mergeQuickChat(source: MeshtasticDatabase, dest: MeshtasticDatabase) {
        val destActions = dest.quickChatActionDao().getAllSnapshot()
        val existing = destActions.mapTo(mutableSetOf()) { Triple(it.name, it.message, it.mode) }
        var nextPosition = (destActions.maxOfOrNull { it.position } ?: -1) + 1
        source.quickChatActionDao().getAllSnapshot().forEach { action ->
            if (existing.add(Triple(action.name, action.message, action.mode))) {
                dest.quickChatActionDao().upsert(action.copy(uuid = 0L, position = nextPosition))
                nextPosition++
            }
        }
    }

    /**
     * The audit log holds each transport session's received-packet history — the source of the telemetry timelines,
     * position history, and node-info history the UI reconstructs per node/port. This is the only place that time
     * series diverges between transports (the current node snapshot is re-dumped identically by the same radio), so
     * merging it is what actually unifies "metrics/telemetry/positions". uuid is a unique string, so IGNORE is safe.
     */
    private suspend fun mergeLogs(source: MeshtasticDatabase, dest: MeshtasticDatabase) {
        dest.meshLogDao().insertIgnore(source.meshLogDao().getAllLogsSnapshot())
    }

    /**
     * Traceroute-derived positions, keyed to their log entry (CASCADE FK) — merge after [mergeLogs] so the parent log
     * rows already exist in [dest].
     */
    private suspend fun mergeTraceroutePositions(source: MeshtasticDatabase, dest: MeshtasticDatabase) {
        dest.tracerouteNodePositionDao().insertAll(source.tracerouteNodePositionDao().getAllSnapshot())
    }

    /**
     * Discovery sessions are distinct events per transport session, so append them all (no dedupe). The three tables
     * form an autoincrement-keyed FK chain (session → preset result → discovered node), so re-insert each level with a
     * fresh id and rewire the child's foreign key to the parent's newly-assigned id.
     */
    private suspend fun mergeDiscovery(source: MeshtasticDatabase, dest: MeshtasticDatabase) {
        val src = source.discoveryDao()
        val dst = dest.discoveryDao()
        src.getAllSessionsSnapshot().forEach { session ->
            val newSessionId = dst.insertSession(session.copy(id = 0))
            src.getPresetResults(session.id).forEach { preset ->
                val newPresetId = dst.insertPresetResult(preset.copy(id = 0, sessionId = newSessionId))
                val nodes = src.getDiscoveredNodes(preset.id).map { it.copy(id = 0, presetResultId = newPresetId) }
                if (nodes.isNotEmpty()) dst.insertDiscoveredNodes(nodes)
            }
        }
    }
}
