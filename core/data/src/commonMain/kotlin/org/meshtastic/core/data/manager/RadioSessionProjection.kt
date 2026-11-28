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
import kotlinx.coroutines.Job
import org.koin.core.annotation.Single
import org.meshtastic.core.common.di.ServiceScope
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.model.MeshLog
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.util.decodeOrNull
import org.meshtastic.core.model.util.isLora
import org.meshtastic.core.model.util.isNodeDbReplay
import org.meshtastic.core.model.util.rxTimeOrNull
import org.meshtastic.core.model.util.snrOrNull
import org.meshtastic.core.model.util.toOneLineString
import org.meshtastic.core.model.util.toPIIString
import org.meshtastic.core.repository.DisplayMirrorManager
import org.meshtastic.core.repository.MeshDataHandler
import org.meshtastic.core.repository.MeshLogRepository
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.repository.ServiceStateWriter
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Data
import org.meshtastic.proto.DisplayPalette
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.LogRecord
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.NodeInfo
import org.meshtastic.proto.PortNum
import org.meshtastic.sdk.ConfigBundle
import org.meshtastic.sdk.DisplayMirrorFrame
import org.meshtastic.sdk.MeshEvent
import org.meshtastic.sdk.NodeChange
import org.meshtastic.sdk.RadioSessionEvent
import kotlin.uuid.Uuid

/**
 * Writes one app radio session's typed SDK session events into Room, the repositories and the app's packet consumers.
 * The SDK owns protocol state, NodeDB, config and channels; this keeps what the app adds on top: durable history, link
 * metrics the SDK does not track, notifications and the MeshLog audit rows.
 */
@Suppress("LongParameterList", "TooManyFunctions")
@Single
class RadioSessionProjection(
    private val nodeManager: NodeManager,
    private val nodeRepository: NodeRepository,
    private val serviceStateWriter: ServiceStateWriter,
    private val meshLogRepository: Lazy<MeshLogRepository>,
    private val dataHandler: Lazy<MeshDataHandler>,
    private val configFlowManager: Lazy<MeshConfigFlowManagerImpl>,
    private val eventHandler: Lazy<RadioSessionEventHandler>,
    private val displayMirrorManager: Lazy<DisplayMirrorManager>,
    private val radioInterfaceService: RadioInterfaceService,
    private val scope: ServiceScope,
) {
    private data class LocalRefresh(val generation: Long, val atMillis: Long)

    private val lastLocalRefresh = atomic<LocalRefresh?>(null)

    /** The config last written to the MeshLog, so a change logs only the sections it moved. */
    private val loggedConfig = atomic<ConfigBundle?>(null)

    /**
     * Installs the handshake snapshot; the caller publishes app readiness only when this returns true. Returns false
     * when the session closed first or the snapshot carries no my_info.
     */
    suspend fun installReady(event: RadioSessionEvent.Ready, session: RadioSessionContext): Boolean {
        val myInfo = event.myInfo
        if (myInfo == null) {
            Logger.w { "SDK Ready carried no my_info; snapshot not installed for app session ${session.generation}" }
            return false
        }
        val config = event.configBundle
        val installed = configFlowManager.value.installRadioSnapshot(session, event)
        if (installed) {
            logFromRadio(session, MeshLog.TYPE_MY_INFO, myInfo.toOneLineString()) { it.my_info = myInfo }
            loggedConfig.value = null
            config?.let { logConfigChanges(session, it) }
            logChannels(session, event.channels.orEmpty())
            logNodes(session, event.nodes.values)
        }
        return installed
    }

    /**
     * Projects one post-Ready event of the SDK session that [session] carries. A handler failure drops that event only:
     * one malformed payload from any node on the mesh must not end the session.
     */
    suspend fun project(event: RadioSessionEvent, session: RadioSessionContext) {
        safeCatching { projectEvent(event, session) }
            .onFailure { failure ->
                Logger.e(failure) { "Dropped an SDK ${event::class.simpleName} after a handler error; session kept" }
            }
    }

    private suspend fun projectEvent(event: RadioSessionEvent, session: RadioSessionContext) {
        when (event) {
            is RadioSessionEvent.Packet -> runForSession(session, "packet") { handlePacket(event, session) }

            is RadioSessionEvent.NodeChanged ->
                runForSession(session, "node change") {
                    refreshLocalNodeLastHeard(session)
                    applyNodeChange(event.change, session)
                }

            is RadioSessionEvent.ConfigChanged -> {
                configFlowManager.value.installConfigBundle(session, event.bundle)
                logConfigChanges(session, event.bundle)
            }

            is RadioSessionEvent.ChannelsChanged -> {
                configFlowManager.value.installChannels(session, event.channels)
                logChannels(session, event.channels)
            }

            is RadioSessionEvent.NodeDbRefreshed -> {
                configFlowManager.value.installRefreshedNodes(session, event.nodes.values.toList())
                logNodes(session, event.nodes.values)
            }

            is RadioSessionEvent.Event ->
                runForSession(session, "event") {
                    refreshLocalNodeLastHeard(session)
                    logEvent(event.event, session)
                    when (val meshEvent = event.event) {
                        is MeshEvent.RoutingReport -> dataHandler.value.handleRoutingReport(meshEvent, session)
                        else -> eventHandler.value.handle(meshEvent, session)
                    }
                }

            is RadioSessionEvent.Lockdown,
            is RadioSessionEvent.Ready,
            is RadioSessionEvent.Ended,
            -> Unit
        }
    }

    fun onDeviceLog(record: LogRecord, session: RadioSessionContext) {
        logFromRadio(session, TYPE_LOG_RECORD, record.toString()) { it.log_record = record }
    }

    /**
     * Logs a packet the SDK queued for the radio as an outgoing row under the local node, with the ID and fields the
     * SDK wrote: the debug panel, the local position log and the request logs with their reply matching read these
     * rows.
     */
    fun onOutboundPacket(packet: MeshPacket, session: RadioSessionContext) {
        val decoded = packet.decoded ?: return
        val loggedPacket = packet.redactedForLog()
        insertMeshLog(
            MeshLog(
                uuid = Uuid.random().toString(),
                message_type = TYPE_PACKET,
                received_date = nowMillis,
                raw_message = loggedPacket.toString(),
                fromNum = MeshLog.NODE_NUM_LOCAL,
                portNum = decoded.portnum.value,
                fromRadio = FromRadio.Builder().also { wb -> wb.packet = loggedPacket }.build(),
            ),
            session,
        )
    }

    fun onDisplayFrame(frame: DisplayMirrorFrame, session: RadioSessionContext) {
        if (radioInterfaceService.isSessionActive(session)) displayMirrorManager.value.handleFrame(frame)
    }

    fun onDisplayPalette(palette: DisplayPalette, session: RadioSessionContext) {
        if (radioInterfaceService.isSessionActive(session)) displayMirrorManager.value.handlePalette(palette)
    }

    /**
     * Proves the link is alive on a quiet mesh, where only the SDK heartbeat's queue status crosses it. Throttled so
     * frequent events do not rewrite the local node row on every frame.
     */
    fun refreshLocalNodeLastHeard(session: RadioSessionContext, localNodeNum: Int? = nodeManager.myNodeNum.value) {
        val myNum = localNodeNum ?: return
        val now = nowMillis
        val previous = lastLocalRefresh.value
        val due =
            previous == null ||
                previous.generation != session.generation ||
                now - previous.atMillis >= LOCAL_NODE_REFRESH_INTERVAL_MS
        // The side-flow heartbeat and the event collector both call this; only the caller that claims the slot writes.
        if (!due || !lastLocalRefresh.compareAndSet(previous, LocalRefresh(session.generation, now))) return
        nodeManager.updateNodeForSession(myNum, session) { node: Node -> node.copy(lastHeard = nowSeconds.toInt()) }
    }

    private fun handlePacket(event: RadioSessionEvent.Packet, session: RadioSessionContext) {
        radioInterfaceService.reportReceiveActivity()
        val myNodeNum = nodeManager.myNodeNum.value ?: return
        // A node DB replay keeps its absent rx_time; the phone's clock would make a never-heard node heard now.
        val packet =
            event.packet
                .newBuilder()
                .also { wb ->
                    wb.rx_time =
                        event.packet.rxTimeOrNull() ?: nowSeconds.toInt().takeUnless { event.packet.isNodeDbReplay() }
                }
                .build()
        val decoded = packet.decoded ?: return
        val loggedPacket = packet.redactedForLog()
        val log =
            MeshLog(
                uuid = Uuid.random().toString(),
                message_type = TYPE_PACKET,
                received_date = nowMillis,
                raw_message = loggedPacket.toString(),
                fromNum = if (packet.from == myNodeNum) MeshLog.NODE_NUM_LOCAL else packet.from,
                portNum = decoded.portnum.value,
                fromRadio = FromRadio.Builder().also { wb -> wb.packet = loggedPacket }.build(),
            )
        val logJob = insertMeshLog(log, session)
        radioInterfaceService.launchSessionWork(scope, session) { serviceStateWriter.emitMeshPacket(packet) }

        if (packet.from != myNodeNum) refreshLocalNodeLastHeard(session, myNodeNum)
        if (packet.from != 0) {
            nodeManager.updateNodeForSession(packet.from, session, channel = packet.channel) { node ->
                applyPacketLinkMetrics(node, packet, decoded)
            }
        }
        if (event.correlatedAdminResponse) applyCorrelatedAdminResponse(packet, myNodeNum, session)
        dataHandler.value.handleReceivedData(packet, myNodeNum, session, log.uuid, logJob)
    }

    /**
     * Link metrics and the hop count are the app's: the SDK tracks no RSSI or transport, reports SNR for relayed
     * packets too, and keeps a stale hop count where the app shows the hops as unknown.
     */
    private fun applyPacketLinkMetrics(node: Node, packet: MeshPacket, decoded: Data): Node {
        val viaMqtt = packet.via_mqtt
        // A node DB replay's rx_snr is the radio's stored reading, measured earlier and maybe on other settings.
        val updateRadioMetrics =
            packet.hop_start == packet.hop_limit && packet.isLora() && !viaMqtt && !packet.isNodeDbReplay()
        val heardOverOurRadio = packet.rx_rssi != null && !viaMqtt
        // Range test packets go out with zero hops, so the sender is heard directly whatever hop_start says.
        val hopsAway =
            when {
                decoded.portnum == PortNum.RANGE_TEST_APP -> 0
                viaMqtt -> HOPS_UNKNOWN
                packet.hop_start == 0 && decoded.bitfield == null -> HOPS_UNKNOWN
                packet.hop_limit > packet.hop_start -> HOPS_UNKNOWN
                else -> packet.hop_start - packet.hop_limit
            }
        return node.copy(
            heardOnCurrentLora = node.heardOnCurrentLora || heardOverOurRadio,
            lastTransport = packet.transport_mechanism.value,
            snr = if (updateRadioMetrics) packet.snrOrNull() ?: node.snr else node.snr,
            rssi = if (updateRadioMetrics) packet.rx_rssi ?: node.rssi else node.rssi,
            hopsAway = hopsAway,
        )
    }

    /** Local config and channel answers arrive as their own change events; metadata and remote status do not. */
    private fun applyCorrelatedAdminResponse(packet: MeshPacket, myNodeNum: Int, session: RadioSessionContext) {
        val admin =
            packet.decoded
                ?.takeIf { it.portnum == PortNum.ADMIN_APP }
                ?.let { AdminMessage.ADAPTER.decodeOrNull(it.payload, Logger) } ?: return
        val fromNum = packet.from.takeIf { it != 0 } ?: myNodeNum
        admin.get_device_metadata_response?.let { nodeManager.insertMetadata(fromNum, it, session) }
        val remoteStatus = admin.get_module_config_response?.statusmessage?.takeIf { fromNum != myNodeNum }
        remoteStatus?.let { status ->
            nodeManager.updateNodeForSession(fromNum, session) { node ->
                node.copy(nodeStatus = status.node_status.takeIf(String::isNotEmpty))
            }
        }
    }

    private fun applyNodeChange(change: NodeChange, session: RadioSessionContext) {
        when (change) {
            is NodeChange.Added -> nodeManager.applySessionNode(change.node, changed = null, session)

            is NodeChange.Updated -> nodeManager.applySessionNode(change.node, change.changed, session)

            // Only an acknowledged removeNode write removes a node; the handshake never deletes what firmware dropped.
            is NodeChange.Removed -> {
                val nodeNum = change.nodeId.raw
                nodeManager.applySessionNodeRemoved(nodeNum)
                radioInterfaceService.launchSessionWork(scope, session) { nodeRepository.deleteNode(nodeNum) }
            }

            is NodeChange.Snapshot,
            is NodeChange.WentOffline,
            is NodeChange.CameOnline,
            -> Unit
        }
    }

    private fun logEvent(event: MeshEvent, session: RadioSessionContext) {
        when (event) {
            is MeshEvent.Notification -> {
                val notification = event.notification.redactedForLog()
                logFromRadio(session, TYPE_CLIENT_NOTIFICATION, notification.toString()) {
                    it.clientNotification = notification
                }
            }

            is MeshEvent.FileInfoReceived ->
                logFromRadio(session, TYPE_FILE_INFO, event.info.toString()) { it.fileInfo = event.info }

            is MeshEvent.DeviceRebooted -> logFromRadio(session, TYPE_REBOOTED, event.reason) { it.rebooted = true }

            is MeshEvent.XmodemPacket ->
                logFromRadio(session, TYPE_XMODEM, event.packet.toString()) { it.xmodemPacket = event.packet }

            else -> Unit
        }
    }

    /** Logs the sections of [bundle] that differ from the last logged bundle; after Ready, that is all of them. */
    private fun logConfigChanges(session: RadioSessionContext, bundle: ConfigBundle) {
        val previous = loggedConfig.getAndSet(bundle)
        (bundle.configs - previous?.configs.orEmpty().toSet()).forEach { changed ->
            val config = changed.redactedForLog()
            logFromRadio(session, TYPE_CONFIG, config.toOneLineString()) { it.config = config }
        }
        (bundle.moduleConfigs - previous?.moduleConfigs.orEmpty().toSet()).forEach { changed ->
            val moduleConfig = changed.redactedForLog()
            logFromRadio(session, TYPE_MODULE_CONFIG, moduleConfig.toOneLineString()) { it.moduleConfig = moduleConfig }
        }
        bundle.deviceUIConfig
            ?.takeIf { it != previous?.deviceUIConfig }
            ?.redactedForLog()
            ?.let { ui ->
                logFromRadio(session, TYPE_DEVICE_UI_CONFIG, ui.toString()) { it.deviceuiConfig = ui }
            }
    }

    private fun logChannels(session: RadioSessionContext, channels: List<org.meshtastic.proto.Channel>) {
        channels
            .map { it.redactedForLog() }
            .forEach { channel ->
                logFromRadio(session, TYPE_CHANNEL, channel.toOneLineString()) { it.channel = channel }
            }
    }

    private fun logNodes(session: RadioSessionContext, nodes: Collection<NodeInfo>) {
        nodes.forEach { node -> logFromRadio(session, TYPE_NODE_INFO, node.toPIIString()) { it.node_info = node } }
    }

    /**
     * MeshLog rows keep their persisted [FromRadio] shape, which the debug view and history queries read. A
     * credential-bearing message arrives as its `redactedForLog` copy, in both [message] and the frame.
     */
    private fun logFromRadio(
        session: RadioSessionContext,
        type: String,
        message: String,
        fill: (FromRadio.Builder) -> Unit,
    ): Job = insertMeshLog(
        MeshLog(
            uuid = Uuid.random().toString(),
            message_type = type,
            received_date = nowMillis,
            raw_message = message,
            fromRadio = FromRadio.Builder().also(fill).build(),
        ),
        session,
    )

    private suspend fun runForSession(session: RadioSessionContext, label: String, block: suspend () -> Unit) {
        if (!radioInterfaceService.isSessionActive(session)) {
            Logger.d { "Dropping SDK $label from stale transport session gen=${session.generation}" }
            return
        }
        withHandlerTimeout("SDK $label", block)
    }

    private fun insertMeshLog(log: MeshLog, session: RadioSessionContext): Job =
        radioInterfaceService.launchSessionWork(scope, session) { meshLogRepository.value.insert(log) }

    private companion object {
        /** One local-node write per SDK heartbeat interval keeps the node fresh without database churn. */
        const val LOCAL_NODE_REFRESH_INTERVAL_MS = 30_000L

        const val TYPE_PACKET = "Packet"
        const val TYPE_LOG_RECORD = "LogRecord"
        const val TYPE_CONFIG = "Config"
        const val TYPE_MODULE_CONFIG = "ModuleConfig"
        const val TYPE_CHANNEL = "Channel"
        const val TYPE_NODE_INFO = "NodeInfo"
        const val TYPE_CLIENT_NOTIFICATION = "ClientNotification"
        const val TYPE_FILE_INFO = "FileInfo"
        const val TYPE_REBOOTED = "Rebooted"
        const val TYPE_XMODEM = "XmodemPacket"
        const val TYPE_DEVICE_UI_CONFIG = "DeviceUIConfig"

        /** The app's hop count for a sender it cannot place: MQTT, a pre-2.3 header or a corrupt one. */
        const val HOPS_UNKNOWN = -1
    }
}
