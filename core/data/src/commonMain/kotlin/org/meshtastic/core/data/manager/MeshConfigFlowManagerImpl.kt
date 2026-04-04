/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import okio.IOException
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.common.util.ioDispatcher
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.HandshakeConstants
import org.meshtastic.core.repository.MeshConfigFlowManager
import org.meshtastic.core.repository.MeshConnectionManager
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketHandler
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.ServiceBroadcasts
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.FileInfo
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.Heartbeat
import org.meshtastic.proto.NodeInfo
import org.meshtastic.proto.ToRadio
import org.meshtastic.core.model.MyNodeInfo as SharedMyNodeInfo
import org.meshtastic.proto.MyNodeInfo as ProtoMyNodeInfo

@Suppress("LongParameterList", "TooManyFunctions")
@Single
class MeshConfigFlowManagerImpl(
    private val nodeManager: NodeManager,
    private val connectionManager: Lazy<MeshConnectionManager>,
    private val nodeRepository: NodeRepository,
    private val radioConfigRepository: RadioConfigRepository,
    private val serviceRepository: ServiceRepository,
    private val serviceBroadcasts: ServiceBroadcasts,
    private val analytics: PlatformAnalytics,
    private val commandSender: CommandSender,
    private val packetHandler: PacketHandler,
) : MeshConfigFlowManager {
    private var scope: CoroutineScope = CoroutineScope(ioDispatcher + SupervisorJob())
    private val wantConfigDelay = 100L

    override fun start(scope: CoroutineScope) {
        this.scope = scope
    }

    /**
     * Tracks which phase of the two-stage handshake we are currently in.
     *
     * Guards [handleConfigComplete] so that duplicate or out-of-order `config_complete_id` signals from the firmware
     * cannot trigger the wrong stage handler or drive the state machine backward.
     */
    private enum class HandshakePhase {
        IDLE,
        STAGE_1,
        STAGE_2,
        COMPLETE,
    }

    private var handshakePhase = HandshakePhase.IDLE

    private val newNodes = mutableListOf<NodeInfo>()
    override val newNodeCount: Int
        get() = newNodes.size

    // The two inputs to MyNodeInfo construction. Both are set during Stage 1 and consumed
    // together at Stage 1 complete by buildMyNodeInfo(). rawMyNodeInfo arrives first
    // (my_info packet); metadata arrives shortly after (metadata packet). Neither is needed
    // outside Stage 1, so we hold the raw protos rather than rebuilding an intermediate
    // SharedMyNodeInfo every time one of them changes.
    private var rawMyNodeInfo: ProtoMyNodeInfo? = null
    private var lastMetadata: DeviceMetadata? = null

    // Committed at Stage 1 complete, consumed at Stage 2 complete.
    private var myNodeInfo: SharedMyNodeInfo? = null

    override fun handleConfigComplete(configCompleteId: Int) {
        when (configCompleteId) {
            HandshakeConstants.CONFIG_NONCE -> {
                if (handshakePhase != HandshakePhase.STAGE_1) {
                    Logger.w { "Ignoring Stage 1 config_complete in phase=$handshakePhase" }
                    return
                }
                handleConfigOnlyComplete()
            }
            HandshakeConstants.NODE_INFO_NONCE -> {
                if (handshakePhase != HandshakePhase.STAGE_2) {
                    Logger.w { "Ignoring Stage 2 config_complete in phase=$handshakePhase" }
                    return
                }
                handleNodeInfoComplete()
            }
            else -> Logger.w { "Config complete id mismatch: $configCompleteId" }
        }
    }

    private fun handleConfigOnlyComplete() {
        Logger.i { "Config-only complete (Stage 1)" }

        val raw = rawMyNodeInfo
        if (raw == null) {
            // my_info is the very first packet the firmware sends; if it never arrived
            // something is fundamentally wrong. Proceeding to Stage 2 would leave the app
            // Connected but with no node info. Retry Stage 1 instead.
            Logger.e { "Stage 1 failed: my_info never received. Retrying Stage 1." }
            handshakePhase = HandshakePhase.IDLE
            scope.handledLaunch {
                delay(wantConfigDelay)
                connectionManager.value.startConfigOnly()
            }
            return
        }

        val finalizedInfo = buildMyNodeInfo(raw, lastMetadata)
        if (finalizedInfo == null) {
            Logger.e { "Stage 1 failed: could not build MyNodeInfo. Retrying Stage 1." }
            handshakePhase = HandshakePhase.IDLE
            scope.handledLaunch {
                delay(wantConfigDelay)
                connectionManager.value.startConfigOnly()
            }
            return
        }

        myNodeInfo = finalizedInfo
        handshakePhase = HandshakePhase.STAGE_2
        Logger.i { "myNodeInfo committed (nodeNum=${finalizedInfo.myNodeNum})" }
        connectionManager.value.onRadioConfigLoaded()

        scope.handledLaunch {
            delay(wantConfigDelay)
            sendHeartbeat()
            delay(wantConfigDelay)
            Logger.i { "Requesting NodeInfo (Stage 2)" }
            connectionManager.value.startNodeInfoOnly()
        }
    }

    private fun sendHeartbeat() {
        try {
            packetHandler.sendToRadio(ToRadio(heartbeat = Heartbeat()))
            Logger.d { "Heartbeat sent between nonce stages" }
        } catch (ex: IOException) {
            Logger.w(ex) { "Failed to send heartbeat; proceeding with node-info stage" }
        }
    }

    private fun handleNodeInfoComplete() {
        Logger.i { "NodeInfo complete (Stage 2)" }

        val info = myNodeInfo
        if (info == null) {
            Logger.e { "handleNodeInfoComplete: myNodeInfo is NULL — cannot complete handshake" }
            // Stay in STAGE_2 so a firmware retry can still be accepted. Do NOT
            // transition to COMPLETE or Connected without a valid node identity.
            return
        }

        // Transition phase immediately (synchronously) to prevent duplicate handling.
        // The async work below (DB writes, broadcasts) proceeds without the guard.
        handshakePhase = HandshakePhase.COMPLETE

        // Snapshot and clear immediately so that a concurrent stall-guard retry (which
        // resends want_config_id and causes the firmware to restart the node_info burst)
        // starts accumulating into a fresh list rather than doubling this batch.
        val nodesToProcess = newNodes.toList()
        newNodes.clear()

        val entities =
            nodesToProcess.mapNotNull { nodeInfo ->
                nodeManager.installNodeInfo(nodeInfo, withBroadcast = false)
                nodeManager.nodeDBbyNodeNum[nodeInfo.num]
                    ?: run {
                        Logger.w { "Node ${nodeInfo.num} missing from DB after installNodeInfo; skipping" }
                        null
                    }
            }

        scope.handledLaunch {
            nodeRepository.installConfig(info, entities)
            analytics.setDeviceAttributes(info.firmwareVersion ?: "unknown", info.model ?: "unknown")
            nodeManager.setNodeDbReady(true)
            nodeManager.setAllowNodeDbWrites(true)
            serviceRepository.setConnectionState(ConnectionState.Connected)
            serviceBroadcasts.broadcastConnection()
            connectionManager.value.onNodeDbReady()
        }
    }

    override fun handleMyInfo(myInfo: ProtoMyNodeInfo) {
        Logger.i { "MyNodeInfo received: ${myInfo.my_node_num}" }

        // Reset all per-handshake state so stale data from a prior interrupted handshake
        // cannot carry over into this one. newNodes may hold partial Stage 2 data if the
        // previous connection dropped mid-way through Stage 2.
        newNodes.clear()
        rawMyNodeInfo = null
        lastMetadata = null
        myNodeInfo = null
        handshakePhase = HandshakePhase.STAGE_1

        rawMyNodeInfo = myInfo
        nodeManager.myNodeNum = myInfo.my_node_num

        // Clear persisted radio config so the new handshake starts from a clean slate.
        // DataStore serializes its own writes, so the clear will precede subsequent
        // setLocalConfig / updateChannelSettings calls dispatched by later packets in this
        // session (handleFromRadio processes packets sequentially, so later dispatches always
        // occur after this one returns).
        scope.handledLaunch {
            radioConfigRepository.clearChannelSet()
            radioConfigRepository.clearLocalConfig()
            radioConfigRepository.clearLocalModuleConfig()
            radioConfigRepository.clearDeviceUIConfig()
            radioConfigRepository.clearFileManifest()
        }
    }

    override fun handleLocalMetadata(metadata: DeviceMetadata) {
        Logger.i { "Local Metadata received: ${metadata.firmware_version}" }
        lastMetadata = metadata
        // Persist the metadata immediately — buildMyNodeInfo() reads it at Stage 1 complete,
        // but the DB write does not need to wait until then.
        rawMyNodeInfo?.let { raw ->
            if (metadata != DeviceMetadata()) {
                scope.handledLaunch { nodeRepository.insertMetadata(raw.my_node_num, metadata) }
            }
        }
    }

    override fun handleNodeInfo(info: NodeInfo) {
        newNodes.add(info)
    }

    override fun handleFileInfo(info: FileInfo) {
        Logger.d { "FileInfo received: ${info.file_name} (${info.size_bytes} bytes)" }
        scope.handledLaunch { radioConfigRepository.addFileInfo(info) }
    }

    override fun triggerWantConfig() {
        connectionManager.value.startConfigOnly()
    }

    /**
     * Builds a [SharedMyNodeInfo] from the raw proto and optional firmware metadata. Pure function — no side effects.
     * Returns null only if construction throws.
     */
    private fun buildMyNodeInfo(raw: ProtoMyNodeInfo, metadata: DeviceMetadata?): SharedMyNodeInfo? = try {
        with(raw) {
            SharedMyNodeInfo(
                myNodeNum = my_node_num,
                hasGPS = false,
                model =
                when (val hwModel = metadata?.hw_model) {
                    null,
                    HardwareModel.UNSET,
                    -> null
                    else -> hwModel.name.replace('_', '-').replace('p', '.').lowercase()
                },
                firmwareVersion = metadata?.firmware_version?.takeIf { it.isNotBlank() },
                couldUpdate = false,
                shouldUpdate = false,
                currentPacketId = commandSender.getCurrentPacketId() and 0xffffffffL,
                messageTimeoutMsec = 300000,
                minAppVersion = min_app_version,
                maxChannels = 8,
                hasWifi = metadata?.hasWifi == true,
                channelUtilization = 0f,
                airUtilTx = 0f,
                deviceId = device_id.utf8(),
                pioEnv = pio_env.ifEmpty { null },
            )
        }
    } catch (@Suppress("TooGenericExceptionCaught") ex: Exception) {
        Logger.e(ex) { "Failed to build MyNodeInfo" }
        null
    }
}
