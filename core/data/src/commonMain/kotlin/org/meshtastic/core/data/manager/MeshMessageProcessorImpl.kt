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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.clampTimestampToNow
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.model.MeshLog
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.util.isLora
import org.meshtastic.core.model.util.toOneLineString
import org.meshtastic.core.model.util.toPIIString
import org.meshtastic.core.repository.FromRadioPacketHandler
import org.meshtastic.core.repository.MeshDataHandler
import org.meshtastic.core.repository.MeshLogRepository
import org.meshtastic.core.repository.MeshMessageProcessor
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.repository.ReceivedRadioFrame
import org.meshtastic.core.repository.ServiceStateWriter
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.LogRecord
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import kotlin.concurrent.Volatile
import kotlin.uuid.Uuid

/** Implementation of [MeshMessageProcessor] that handles raw radio messages and prepares mesh packets for routing. */
@Suppress("TooManyFunctions")
@Single
class MeshMessageProcessorImpl(
    private val nodeManager: NodeManager,
    private val serviceStateWriter: ServiceStateWriter,
    private val meshLogRepository: Lazy<MeshLogRepository>,
    private val dataHandler: Lazy<MeshDataHandler>,
    private val fromRadioDispatcher: FromRadioPacketHandler,
    private val radioInterfaceService: RadioInterfaceService,
    @Named("ServiceScope") private val scope: CoroutineScope,
) : MeshMessageProcessor {

    /**
     * Epoch-millisecond timestamp of the last local-node `lastHeard` DB write. Used to throttle updates to at most once
     * per [LOCAL_NODE_REFRESH_INTERVAL_MS] so that high-frequency FromRadio variants (log records, queue status) don't
     * flood the DB.
     */
    @Volatile private var lastLocalNodeRefreshMs = 0L

    @Volatile private var lastLocalNodeRefreshGeneration = Long.MIN_VALUE

    private data class BufferedMeshPacket(val packet: MeshPacket, val session: RadioSessionContext)

    private val earlyMutex = Mutex()
    private val earlyFlushMutex = Mutex()
    private val earlyReceivedPackets = ArrayDeque<BufferedMeshPacket>()
    private val maxEarlyPacketBuffer = 10240

    override suspend fun clearEarlyPackets() {
        earlyFlushMutex.withLock { earlyMutex.withLock { earlyReceivedPackets.clear() } }
    }

    init {
        // Flush buffered packets only once BOTH the node DB is ready AND our own node number is known. Processing a
        // received packet while myNodeNum is still null would key a local packet under its raw from_num instead of
        // NODE_NUM_LOCAL (see [handleReceivedMeshPacket] / [processReceivedMeshPacket]), orphaning it from per-node
        // queries. Emit the (ready, myNodeNum) pair — not the derived boolean — so distinctUntilChanged re-fires on
        // every underlying state change (including a reconnect where myNodeNum transitions null -> value) rather than
        // collapsing distinct states that happen to map to the same boolean.
        combine(nodeManager.isNodeDbReady, nodeManager.myNodeNum) { ready, myNodeNum -> ready to myNodeNum }
            .distinctUntilChanged()
            .onEach { (ready, myNodeNum) ->
                if (ready && myNodeNum != null) {
                    flushEarlyReceivedPackets("ready")
                }
            }
            .launchIn(scope)
    }

    override suspend fun handleFromRadio(frame: ReceivedRadioFrame, myNodeNum: Int?) {
        if (!radioInterfaceService.isSessionActive(frame.session)) {
            Logger.d { "Dropping decoded work from stale transport session gen=${frame.session.generation}" }
            return
        }
        val bytes = frame.payload.toByteArray()
        val proto =
            safeCatching { FromRadio.ADAPTER.decode(bytes) }
                .getOrElse { primaryException ->
                    safeCatching { FromRadio(log_record = LogRecord.ADAPTER.decode(bytes)) }
                        .getOrElse {
                            Logger.e(primaryException) {
                                "Failed to parse radio packet (len=${bytes.size}). Not a valid FromRadio or LogRecord."
                            }
                            return
                        }
                }
        processFromRadio(proto, myNodeNum, frame.session)
    }

    private suspend fun processFromRadio(proto: FromRadio, myNodeNum: Int?, session: RadioSessionContext) {
        val admitted =
            radioInterfaceService.runWhileSessionActive(session) {
                safeCatching {
                    // Audit log every incoming variant without allowing delayed work to cross a session boundary.
                    logVariant(proto, session)

                    val packet = proto.packet
                    if (packet != null) {
                        handleReceivedMeshPacket(packet, myNodeNum, session)
                    } else {
                        // Packets refresh the local node in processReceivedMeshPacket; other variants need the
                        // heartbeat.
                        refreshLocalNodeLastHeard(session)
                        fromRadioDispatcher.handleFromRadio(proto, session)
                    }
                }
                    .onFailure {
                        Logger.e(it) { "Dropped a FromRadio after a handler error; receive pipeline kept alive" }
                    }
            }
        if (!admitted) {
            Logger.d { "Dropping FromRadio from stale transport session gen=${session.generation}" }
        }
    }

    private fun logVariant(proto: FromRadio, session: RadioSessionContext) {
        val (type, message) =
            when {
                proto.log_record != null -> "LogRecord" to proto.log_record.toString()
                proto.rebooted != null -> "Rebooted" to proto.rebooted.toString()
                proto.xmodemPacket != null -> "XmodemPacket" to proto.xmodemPacket.toString()
                proto.deviceuiConfig != null -> "DeviceUIConfig" to proto.deviceuiConfig.toString()
                proto.fileInfo != null -> "FileInfo" to proto.fileInfo.toString()
                proto.my_info != null -> "MyInfo" to proto.my_info!!.toOneLineString()
                proto.node_info != null -> "NodeInfo" to proto.node_info!!.toPIIString()
                proto.config != null -> "Config" to proto.config!!.toOneLineString()
                proto.moduleConfig != null -> "ModuleConfig" to proto.moduleConfig!!.toOneLineString()
                proto.channel != null -> "Channel" to proto.channel!!.toOneLineString()
                proto.clientNotification != null -> "ClientNotification" to proto.clientNotification.toString()
                else -> return
            }

        insertMeshLog(
            MeshLog(
                uuid = Uuid.random().toString(),
                message_type = type,
                received_date = nowMillis,
                raw_message = message,
                fromRadio = proto,
            ),
            session,
        )
    }

    /** Test seam for packet-only fixtures with explicit transport authority. */
    internal suspend fun handleReceivedMeshPacket(packet: MeshPacket, myNodeNum: Int?, session: RadioSessionContext) {
        val rxTime =
            if (packet.rx_time == 0) {
                nowSeconds.toInt()
            } else {
                packet.rx_time
            }
        val preparedPacket = packet.copy(rx_time = rxTime)

        // Require myNodeNum to be known before storing: processReceivedMeshPacket only keys a local packet under
        // NODE_NUM_LOCAL when packet.from == myNodeNum. If myNodeNum is still null (early in a (re)connect, before
        // MyNodeInfo resolves), a local packet would be stored under its raw from_num and become invisible to
        // per-node chart queries while still appearing in the unfiltered Debug log. Buffer until both are ready;
        // the init combine flushes the buffer once myNodeNum resolves.
        if (!radioInterfaceService.isSessionActive(session)) {
            Logger.d { "Dropping mesh packet from stale transport session gen=${session.generation}" }
            return
        }

        if (nodeManager.isNodeDbReady.value && myNodeNum != null) {
            processReceivedMeshPacket(preparedPacket, myNodeNum, session)
        } else {
            // Production callers already hold the session lease in processFromRadio. Enqueue directly so packet FIFO
            // does not depend on the ordering of separately launched mutex waiters.
            enqueueBufferedPacket(BufferedMeshPacket(preparedPacket, session))
            if (nodeManager.isNodeDbReady.value && nodeManager.myNodeNum.value != null) {
                flushEarlyReceivedPackets("enqueue-ready")
            }
        }
    }

    private suspend fun enqueueBufferedPacket(buffered: BufferedMeshPacket) = earlyMutex.withLock {
        discardBufferedPacketsFromOtherSessions(buffered.session)
        val queueSize = earlyReceivedPackets.size
        if (queueSize >= maxEarlyPacketBuffer) {
            Logger.w { "Early packet buffer full ($queueSize), dropping oldest packet" }
            earlyReceivedPackets.removeFirstOrNull()
        }
        earlyReceivedPackets.addLast(buffered)
    }

    private fun discardBufferedPacketsFromOtherSessions(activeSession: RadioSessionContext) {
        val removed = earlyReceivedPackets.removeAll { queued -> queued.session != activeSession }
        if (removed) Logger.d { "Discarded buffered packets from an earlier transport generation" }
    }

    private fun flushEarlyReceivedPackets(reason: String) {
        scope.launch { earlyFlushMutex.withLock { replayEarlyReceivedPackets(reason) } }
    }

    private suspend fun replayEarlyReceivedPackets(reason: String) {
        var replayed = 0
        while (nodeManager.isNodeDbReady.value) {
            val myNodeNum = nodeManager.myNodeNum.value ?: return
            val buffered = earlyMutex.withLock { earlyReceivedPackets.removeFirstOrNull() } ?: break

            // Readiness can regress while a queued flush waits behind an earlier replay. Put the packet back at the
            // front instead of keying it against a database that is being cleared or an unresolved local node number.
            if (!isReplayReady(myNodeNum)) {
                earlyMutex.withLock { earlyReceivedPackets.addFirst(buffered) }
                return
            }

            if (processBufferedPacket(buffered, myNodeNum)) replayed += 1
        }
        if (replayed > 0) Logger.d { "replayEarlyPackets reason=$reason count=$replayed" }
    }

    private fun isReplayReady(myNodeNum: Int): Boolean =
        nodeManager.isNodeDbReady.value && nodeManager.myNodeNum.value == myNodeNum

    private suspend fun processBufferedPacket(buffered: BufferedMeshPacket, myNodeNum: Int): Boolean {
        val admitted =
            radioInterfaceService.runWhileSessionActive(buffered.session) {
                safeCatching { processReceivedMeshPacket(buffered.packet, myNodeNum, buffered.session) }
                    .onFailure {
                        Logger.e(it) { "Dropped a buffered early packet after a handler error; replay continued" }
                    }
            }
        if (!admitted) {
            Logger.d { "Dropping buffered packet from stale transport session gen=${buffered.session.generation}" }
        }
        return admitted
    }

    @Suppress("LongMethod")
    private suspend fun processReceivedMeshPacket(packet: MeshPacket, myNodeNum: Int, session: RadioSessionContext) {
        val decoded = packet.decoded ?: return
        val log =
            MeshLog(
                uuid = Uuid.random().toString(),
                message_type = "Packet",
                received_date = nowMillis,
                raw_message = packet.toString(),
                fromNum = if (packet.from == myNodeNum) MeshLog.NODE_NUM_LOCAL else packet.from,
                portNum = decoded.portnum.value,
                fromRadio = FromRadio(packet = packet),
            )
        val logJob = insertMeshLog(log, session)

        launchSessionBound(session, "mesh-packet emission") { serviceStateWriter.emitMeshPacket(packet) }

        val from = packet.from
        if (from == myNodeNum) {
            persistNodeUpdate(
                myNodeNum,
                channel = packet.channel,
                operation = "local sender-node packet update",
            ) { node ->
                applySenderPacketUpdate(node, packet, decoded).copy(lastHeard = nowSeconds.toInt())
            }
        } else {
            persistNodeUpdate(myNodeNum, operation = "local-node packet refresh") { node: Node ->
                node.copy(lastHeard = nowSeconds.toInt())
            }
            persistNodeUpdate(from, channel = packet.channel, operation = "sender-node packet update") { node ->
                applySenderPacketUpdate(node, packet, decoded)
            }
        }

        dataHandler.value.handleReceivedData(packet, myNodeNum, session, log.uuid, logJob)
    }

    private fun applySenderPacketUpdate(node: Node, packet: MeshPacket, decoded: org.meshtastic.proto.Data): Node {
        val viaMqtt = packet.via_mqtt == true
        val isDirect = packet.hop_start == packet.hop_limit
        val updateRadioMetrics = isDirect && packet.isLora() && !viaMqtt
        val hopsAway =
            when {
                decoded.portnum == PortNum.RANGE_TEST_APP -> 0
                viaMqtt -> -1
                packet.hop_start == 0 && (decoded.bitfield ?: 0) == 0 -> -1
                packet.hop_limit > packet.hop_start -> -1
                else -> packet.hop_start - packet.hop_limit
            }
        return node.copy(
            lastHeard = clampTimestampToNow(packet.rx_time),
            viaMqtt = viaMqtt,
            lastTransport = packet.transport_mechanism.value,
            snr = if (updateRadioMetrics) packet.rx_snr else node.snr,
            rssi = if (updateRadioMetrics) packet.rx_rssi else node.rssi,
            hopsAway = hopsAway,
        )
    }

    /**
     * Refreshes the local node's [Node.lastHeard] to prove the radio link is alive.
     *
     * Without this, [lastHeard] is only set when a [MeshPacket] arrives from another node (see
     * [processReceivedMeshPacket]). On a quiet mesh the heartbeat cycle still exchanges data with the firmware (ToRadio
     * heartbeat → FromRadio queueStatus every 30 s), but that data never touched [lastHeard], causing the local node to
     * appear stale in the UI even though the connection is healthy.
     *
     * To avoid flooding the DB on high-frequency variants (log records arrive many times per second when debug logging
     * is enabled), writes are throttled to at most once per [LOCAL_NODE_REFRESH_INTERVAL_MS].
     */
    private suspend fun refreshLocalNodeLastHeard(session: RadioSessionContext) {
        val now = nowMillis
        val sameGeneration = lastLocalNodeRefreshGeneration == session.generation
        if (sameGeneration && now - lastLocalNodeRefreshMs < LOCAL_NODE_REFRESH_INTERVAL_MS) return

        val myNum = nodeManager.myNodeNum.value ?: return
        val persisted =
            persistNodeUpdate(myNum, operation = "local-node link refresh") { node: Node ->
                node.copy(lastHeard = nowSeconds.toInt())
            }
        if (persisted) {
            lastLocalNodeRefreshGeneration = session.generation
            lastLocalNodeRefreshMs = now
        }
    }

    private suspend fun persistNodeUpdate(
        nodeNum: Int,
        channel: Int = 0,
        operation: String,
        transform: (Node) -> Node,
    ): Boolean = safeCatching { nodeManager.updateNodeAndPersist(nodeNum, channel, transform) }
        .onFailure { Logger.e(it) { "Failed $operation; packet processing continued" } }
        .isSuccess

    private fun insertMeshLog(log: MeshLog, session: RadioSessionContext): Job =
        launchSessionBound(session, "mesh-log insert") { meshLogRepository.value.insert(log) }

    private fun launchSessionBound(session: RadioSessionContext, operation: String, block: suspend () -> Unit): Job =
        scope.handledLaunch(start = CoroutineStart.UNDISPATCHED) {
            val admitted = radioInterfaceService.runWithSessionLease(session) { block() }
            if (!admitted) {
                Logger.d { "Skipping $operation from stale transport session gen=${session.generation}" }
            }
        }

    companion object {
        /**
         * Minimum interval between local-node `lastHeard` DB writes, in milliseconds. Aligned with the heartbeat
         * interval (30 s) so that one write per heartbeat cycle keeps the node fresh without unnecessary DB churn.
         */
        private const val LOCAL_NODE_REFRESH_INTERVAL_MS = 30_000L
    }
}
