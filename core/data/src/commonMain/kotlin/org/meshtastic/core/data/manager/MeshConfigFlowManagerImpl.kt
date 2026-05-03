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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DeviceVersion
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.HandshakeConstants
import org.meshtastic.core.repository.MeshConfigFlowManager
import org.meshtastic.core.repository.MeshConnectionManager
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.NotificationPrefs
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.ServiceBroadcasts
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.FileInfo
import org.meshtastic.proto.FirmwareEdition
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.NodeInfo
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
    private val heartbeatSender: DataLayerHeartbeatSender,
    private val notificationPrefs: NotificationPrefs,
    @Named("ServiceScope") private val scope: CoroutineScope,
) : MeshConfigFlowManager {
    private val wantConfigDelay = 100L

    /** Monotonically increasing generation so async clears from a stale handshake are discarded. */
    private val handshakeGeneration = atomic(0L)

    /**
     * Type-safe handshake state machine. Each state carries exactly the data that is valid during that phase,
     * eliminating the possibility of accessing stale or uninitialized fields.
     *
     * Guards [handleConfigComplete] so that duplicate or out-of-order `config_complete_id` signals from the firmware
     * cannot trigger the wrong stage handler or drive the state machine backward.
     */
    private sealed class HandshakeState {
        /** No handshake in progress. */
        data object Idle : HandshakeState()

        /**
         * Stage 1: receiving device config, module config, channels, and metadata.
         *
         * [rawMyNodeInfo] arrives first (my_info packet); [metadata] may arrive shortly after. Both are consumed
         * together by [buildMyNodeInfo] at Stage 1 completion.
         */
        data class ReceivingConfig(val rawMyNodeInfo: ProtoMyNodeInfo, val metadata: DeviceMetadata? = null) :
            HandshakeState()

        /**
         * Stage 2: receiving node-info packets from the firmware.
         *
         * [myNodeInfo] was committed at the Stage 1→2 transition. [nodes] accumulates [NodeInfo] packets until
         * `config_complete_id` arrives.
         */
        data class ReceivingNodeInfo(val myNodeInfo: SharedMyNodeInfo, val nodes: List<NodeInfo> = emptyList()) :
            HandshakeState()

        /** Both stages finished. The app is fully connected. */
        data class Complete(val myNodeInfo: SharedMyNodeInfo) : HandshakeState()
    }

    private var handshakeState: HandshakeState = HandshakeState.Idle

    override val newNodeCount: Int
        get() = (handshakeState as? HandshakeState.ReceivingNodeInfo)?.nodes?.size ?: 0

    override fun handleConfigComplete(configCompleteId: Int) {
        val state = handshakeState
        when (configCompleteId) {
            HandshakeConstants.CONFIG_NONCE -> {
                if (state !is HandshakeState.ReceivingConfig) {
                    Logger.w { "Ignoring Stage 1 config_complete in state=$state" }
                    return
                }
                handleConfigOnlyComplete(state)
            }

            HandshakeConstants.NODE_INFO_NONCE -> {
                if (state !is HandshakeState.ReceivingNodeInfo) {
                    Logger.w { "Ignoring Stage 2 config_complete in state=$state" }
                    return
                }
                handleNodeInfoComplete(state)
            }

            else -> Logger.w { "Config complete id mismatch: $configCompleteId" }
        }
    }

    private fun handleConfigOnlyComplete(state: HandshakeState.ReceivingConfig) {
        Logger.i { "Config-only complete (Stage 1)" }

        val finalizedInfo = buildMyNodeInfo(state.rawMyNodeInfo, state.metadata)
        if (finalizedInfo == null) {
            Logger.w { "Stage 1 failed: could not build MyNodeInfo, retrying Stage 1" }
            handshakeState = HandshakeState.Idle
            scope.handledLaunch {
                delay(wantConfigDelay)
                connectionManager.value.startConfigOnly()
            }
            return
        }

        // Warn if firmware is below the absolute minimum supported version.
        // The UI layer already enforces this via FirmwareVersionCheck, so we just log here
        // for diagnostics rather than hard-disconnecting.
        finalizedInfo.firmwareVersion?.let { fwVersion ->
            if (DeviceVersion(fwVersion) < DeviceVersion(DeviceVersion.ABS_MIN_FW_VERSION)) {
                Logger.w {
                    "Firmware $fwVersion is below minimum ${DeviceVersion.ABS_MIN_FW_VERSION} — " +
                        "protocol incompatibilities may occur"
                }
            }
        }

        handshakeState = HandshakeState.ReceivingNodeInfo(myNodeInfo = finalizedInfo)
        Logger.i { "myNodeInfo committed (nodeNum=${finalizedInfo.myNodeNum})" }
        connectionManager.value.onRadioConfigLoaded()

        scope.handledLaunch {
            delay(wantConfigDelay)
            heartbeatSender.sendHeartbeat("inter-stage")
            delay(wantConfigDelay)
            Logger.i { "Requesting NodeInfo (Stage 2)" }
            connectionManager.value.startNodeInfoOnly()
        }
    }

    private fun handleNodeInfoComplete(state: HandshakeState.ReceivingNodeInfo) {
        Logger.i { "NodeInfo complete (Stage 2)" }

        val info = state.myNodeInfo

        // Transition state immediately (synchronously) to prevent duplicate handling.
        // The async work below (DB writes, broadcasts) proceeds without the guard.
        // Because nodes is now immutable, no snapshot is needed — state.nodes IS the snapshot.
        // Any stall-guard retry that re-enters handleNodeInfo will see Complete state and be ignored.
        handshakeState = HandshakeState.Complete(myNodeInfo = info)

        val entities =
            state.nodes.mapNotNull { nodeInfo ->
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

        // Transition to Stage 1, discarding any stale data from a prior interrupted handshake.
        handshakeState = HandshakeState.ReceivingConfig(rawMyNodeInfo = myInfo)
        nodeManager.setMyNodeNum(myInfo.my_node_num)
        nodeManager.setFirmwareEdition(myInfo.firmware_edition)
        applyEventFirmwareNotificationDefaults(myInfo.firmware_edition)

        // Bump the generation so that a pending clear from a prior (interrupted) handshake
        // will see a stale snapshot and skip its writes, preventing it from wiping config
        // that was saved by this (newer) handshake's incoming packets.
        val gen = handshakeGeneration.incrementAndGet()

        // Clear persisted radio config so the new handshake starts from a clean slate.
        // DataStore serializes its own writes, so the clear will precede subsequent
        // setLocalConfig / updateChannelSettings calls dispatched by later packets in this
        // session (handleFromRadio processes packets sequentially, so later dispatches always
        // occur after this one returns).
        scope.handledLaunch {
            if (handshakeGeneration.value != gen) return@handledLaunch // Stale handshake; skip.
            radioConfigRepository.clearChannelSet()
            radioConfigRepository.clearLocalConfig()
            radioConfigRepository.clearLocalModuleConfig()
            radioConfigRepository.clearDeviceUIConfig()
            radioConfigRepository.clearFileManifest()
        }
    }

    override fun handleLocalMetadata(metadata: DeviceMetadata) {
        Logger.i { "Local Metadata received: ${metadata.firmware_version}" }
        val state = handshakeState
        if (state is HandshakeState.ReceivingConfig) {
            handshakeState = state.copy(metadata = metadata)
            // Persist the metadata immediately — buildMyNodeInfo() reads it at Stage 1 complete,
            // but the DB write does not need to wait until then.
            if (metadata != DeviceMetadata()) {
                scope.handledLaunch { nodeRepository.insertMetadata(state.rawMyNodeInfo.my_node_num, metadata) }
            }
        } else {
            Logger.w { "Ignoring metadata outside Stage 1 (state=$state)" }
        }
    }

    override fun handleNodeInfo(info: NodeInfo) {
        val state = handshakeState
        if (state is HandshakeState.ReceivingNodeInfo) {
            handshakeState = state.copy(nodes = state.nodes + info)
        } else {
            Logger.w { "Ignoring NodeInfo outside Stage 2 (state=$state)" }
        }
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

    private fun applyEventFirmwareNotificationDefaults(edition: FirmwareEdition) {
        if (edition != FirmwareEdition.VANILLA) {
            if (!notificationPrefs.nodeEventsAutoDisabledForEvent.value) {
                notificationPrefs.setNodeEventsEnabled(false)
                notificationPrefs.setNodeEventsAutoDisabledForEvent(true)
            }
        } else {
            if (notificationPrefs.nodeEventsAutoDisabledForEvent.value) {
                notificationPrefs.setNodeEventsEnabled(true)
                notificationPrefs.setNodeEventsAutoDisabledForEvent(false)
            }
        }
    }
}
