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

/**
 * Folds the contents of one device database ([source]) into another ([dest]) when both turn out to belong to the same
 * physical node reached over different transports (BLE / TCP / USB). Called once, by [DatabaseManager.associateNode],
 * the first time a secondary transport learns a `myNodeNum` that another transport already claimed.
 *
 * Every per-device table is unified so switching transport is seamless: messages (+FTS), reactions, contact mute/read
 * settings, nodes and their notes, per-node metadata, the audit log (each session's received-packet history — the
 * source of the telemetry timelines, position history, and traceroute results the UI reconstructs), traceroute
 * positions, and discovery sessions. Where the same row exists on both sides the destination is preferred (it will be
 * refreshed by the same radio's re-dump on connect); source-only rows and strictly newer history are brought over.
 * Packets/reactions in [source] and [dest] already share the same `myNodeNum` (same node), so no key remapping is
 * needed there; autoincrement-keyed rows (packets, discovery) are re-inserted with fresh ids to avoid collisions.
 */
object DatabaseMerger {

    suspend fun merge(source: MeshtasticDatabase, dest: MeshtasticDatabase) {
        // All destination writes run in a single transaction so a crash or exception mid-merge rolls
        // back cleanly instead of leaving `dest` half-merged. This also makes a retried merge safe: if
        // associateNode re-runs (e.g. a crash before the address alias is persisted), the rolled-back
        // partial writes never happened, so the fresh-id packet/discovery re-inserts can't duplicate.
        // Reads from `source` use its own connection pool and don't participate in this transaction.
        var packets = 0
        dest.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                packets = mergePackets(source, dest)
                mergeReactions(source, dest)
                mergeContactSettings(source, dest)
                mergeNodes(source, dest)
                mergeMetadata(source, dest)
                // Logs must precede traceroute positions: the latter has a CASCADE foreign key onto log.uuid.
                mergeLogs(source, dest)
                mergeTraceroutePositions(source, dest)
                mergeDiscovery(source, dest)
            }
        }
        Logger.i { "Merged $packets packets across transports into unified node DB" }
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
