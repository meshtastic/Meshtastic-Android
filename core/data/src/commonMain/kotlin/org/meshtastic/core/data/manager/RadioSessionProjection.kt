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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import org.koin.core.annotation.Single
import org.meshtastic.core.common.di.ServiceScope
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.model.MeshLog
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.util.decodeOrNull
import org.meshtastic.core.model.util.isLora
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
import org.meshtastic.sdk.NodeField
import org.meshtastic.sdk.RadioSessionEvent
import kotlin.concurrent.Volatile
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
    @Volatile private var lastLocalNodeRefreshMs = 0L

    @Volatile private var lastLocalNodeRefreshGeneration = Long.MIN_VALUE

    /** Installs the handshake snapshot; the caller publishes app readiness only when this returns true. */
    suspend fun installReady(event: RadioSessionEvent.Ready, session: RadioSessionContext): Boolean {
        val myInfo = checkNotNull(event.myInfo)
        val config = event.configBundle
        val installed =
            configFlowManager.value.installRadioSnapshot(
                session,
                RadioHandshakeSnapshot(
                    myInfo = myInfo,
                    metadata = config?.metadata,
                    channels = event.channels.orEmpty(),
                    configs = config?.configs.orEmpty(),
                    moduleConfigs = config?.moduleConfigs.orEmpty(),
                    deviceUiConfig = config?.deviceUIConfig,
                    nodes = event.nodes.values.toList(),
                    files = event.files,
                    regionPresets = event.regionPresets,
                ),
            )
        if (installed) {
            logFromRadio(session, MeshLog.TYPE_MY_INFO, myInfo.toOneLineString()) { it.my_info = myInfo }
            config?.let { logConfigBundle(session, it) }
            logChannels(session, event.channels.orEmpty())
            logNodes(session, event.nodes.values)
        }
        return installed
    }

    /** Projects one post-Ready event of the SDK session that [session] carries. */
    suspend fun project(event: RadioSessionEvent, session: RadioSessionContext) {
        when (event) {
            is RadioSessionEvent.Packet -> runForSession(session, "packet") { handlePacket(event, session) }

            is RadioSessionEvent.NodeChanged ->
                runForSession(session, "node change") {
                    refreshLocalNodeLastHeard(session)
                    applyNodeChange(event.change, session)
                }

            is RadioSessionEvent.ConfigChanged -> {
                configFlowManager.value.installConfigBundle(session, event.bundle)
                logConfigBundle(session, event.bundle)
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
                    eventHandler.value.handle(event.event, session)
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

    fun onDisplayFrame(frame: DisplayMirrorFrame, session: RadioSessionContext) {
        radioInterfaceService.runIfSessionActive(session) { displayMirrorManager.value.handleFrame(frame) }
    }

    fun onDisplayPalette(palette: DisplayPalette, session: RadioSessionContext) {
        radioInterfaceService.runIfSessionActive(session) { displayMirrorManager.value.handlePalette(palette) }
    }

    /**
     * Proves the link is alive on a quiet mesh, where only the SDK heartbeat's queue status crosses it. Throttled so
     * frequent events do not rewrite the local node row on every frame.
     */
    fun refreshLocalNodeLastHeard(session: RadioSessionContext, localNodeNum: Int? = nodeManager.myNodeNum.value) {
        val now = nowMillis
        val sameGeneration = lastLocalNodeRefreshGeneration == session.generation
        if (sameGeneration && now - lastLocalNodeRefreshMs < LOCAL_NODE_REFRESH_INTERVAL_MS) return
        val myNum = localNodeNum ?: return
        nodeManager.updateNodeForSession(myNum, session) { node: Node -> node.copy(lastHeard = nowSeconds.toInt()) }
        lastLocalNodeRefreshGeneration = session.generation
        lastLocalNodeRefreshMs = now
    }

    private fun handlePacket(event: RadioSessionEvent.Packet, session: RadioSessionContext) {
        val myNodeNum = nodeManager.myNodeNum.value ?: return
        val packet =
            event.packet
                .newBuilder()
                .also { wb -> wb.rx_time = event.packet.rxTimeOrNull() ?: nowSeconds.toInt() }
                .build()
        val decoded = packet.decoded ?: return
        val log =
            MeshLog(
                uuid = Uuid.random().toString(),
                message_type = TYPE_PACKET,
                received_date = nowMillis,
                raw_message = packet.toString(),
                fromNum = if (packet.from == myNodeNum) MeshLog.NODE_NUM_LOCAL else packet.from,
                portNum = decoded.portnum.value,
                fromRadio = FromRadio.Builder().also { wb -> wb.packet = packet }.build(),
            )
        val logJob = insertMeshLog(log, session)
        launchSessionBound(session, "mesh-packet emission") { serviceStateWriter.emitMeshPacket(packet) }

        if (packet.from != myNodeNum) refreshLocalNodeLastHeard(session, myNodeNum)
        if (packet.from != 0) {
            nodeManager.updateNodeForSession(packet.from, session, channel = packet.channel) { node ->
                applyPacketLinkMetrics(node, packet)
            }
        }
        if (event.correlatedAdminResponse) applyCorrelatedAdminResponse(packet, myNodeNum, session)
        dataHandler.value.handleReceivedData(packet, myNodeNum, session, log.uuid, logJob)
    }

    /** Link metrics are the app's: the SDK tracks no RSSI or transport, and reports SNR for relayed packets too. */
    private fun applyPacketLinkMetrics(node: Node, packet: MeshPacket): Node {
        val viaMqtt = packet.via_mqtt
        val updateRadioMetrics = packet.hop_start == packet.hop_limit && packet.isLora() && !viaMqtt
        // Stored packets firmware replays after a NodeDB dump are marked LoRa but carry no rx_rssi.
        val heardOverOurRadio = packet.rx_rssi != null && !viaMqtt
        return node.copy(
            heardOnCurrentLora = node.heardOnCurrentLora || heardOverOurRadio,
            lastTransport = packet.transport_mechanism.value,
            snr = if (updateRadioMetrics) packet.snrOrNull() ?: node.snr else node.snr,
            rssi = if (updateRadioMetrics) packet.rx_rssi ?: node.rssi else node.rssi,
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
            is NodeChange.Added -> nodeManager.applySessionNode(change.node, userChanged = true, session)

            is NodeChange.Updated -> {
                val userChanged = NodeField.User in change.changed || NodeField.Name in change.changed
                nodeManager.applySessionNode(change.node, userChanged, session)
            }

            // Only an acknowledged removeNode write removes a node; the handshake never deletes what firmware dropped.
            is NodeChange.Removed -> {
                val nodeNum = change.nodeId.raw
                nodeManager.removeByNodenum(nodeNum)
                launchSessionBound(session, "node removal") { nodeRepository.deleteNode(nodeNum) }
            }

            is NodeChange.Snapshot,
            is NodeChange.WentOffline,
            is NodeChange.CameOnline,
            -> Unit
        }
    }

    private fun logEvent(event: MeshEvent, session: RadioSessionContext) {
        when (event) {
            is MeshEvent.Notification ->
                logFromRadio(session, TYPE_CLIENT_NOTIFICATION, event.notification.toString()) {
                    it.clientNotification = event.notification
                }

            is MeshEvent.FileInfoReceived ->
                logFromRadio(session, TYPE_FILE_INFO, event.info.toString()) { it.fileInfo = event.info }

            is MeshEvent.DeviceRebooted -> logFromRadio(session, TYPE_REBOOTED, event.reason) { it.rebooted = true }

            else -> Unit
        }
    }

    private fun logConfigBundle(session: RadioSessionContext, bundle: ConfigBundle) {
        bundle.configs.forEach { config ->
            logFromRadio(session, TYPE_CONFIG, config.toOneLineString()) { it.config = config }
        }
        bundle.moduleConfigs.forEach { moduleConfig ->
            logFromRadio(session, TYPE_MODULE_CONFIG, moduleConfig.toOneLineString()) { it.moduleConfig = moduleConfig }
        }
    }

    private fun logChannels(session: RadioSessionContext, channels: List<org.meshtastic.proto.Channel>) {
        channels.forEach { channel ->
            logFromRadio(session, TYPE_CHANNEL, channel.toOneLineString()) { it.channel = channel }
        }
    }

    private fun logNodes(session: RadioSessionContext, nodes: Collection<NodeInfo>) {
        nodes.forEach { node -> logFromRadio(session, TYPE_NODE_INFO, node.toPIIString()) { it.node_info = node } }
    }

    /** MeshLog rows keep their persisted [FromRadio] shape, which the debug view and history queries read. */
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
        if (!radioInterfaceService.runWhileSessionActive(session, "SDK $label", block)) {
            Logger.d { "Dropping SDK $label from stale transport session gen=${session.generation}" }
        }
    }

    private fun insertMeshLog(log: MeshLog, session: RadioSessionContext): Job =
        launchSessionBound(session, "mesh-log insert") { meshLogRepository.value.insert(log) }

    private fun launchSessionBound(session: RadioSessionContext, operation: String, block: suspend () -> Unit): Job =
        scope.handledLaunch(start = CoroutineStart.UNDISPATCHED) {
            val admitted = radioInterfaceService.runWithSessionLease(session) { block() }
            if (!admitted) Logger.d { "Skipping $operation from stale transport session gen=${session.generation}" }
        }

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
    }
}
