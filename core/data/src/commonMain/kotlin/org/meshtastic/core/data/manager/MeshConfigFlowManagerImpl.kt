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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DeviceVersion
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.HandshakeConstants
import org.meshtastic.core.repository.MeshConfigFlowManager
import org.meshtastic.core.repository.MeshConnectionManager
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.NotificationPrefs
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.repository.ServiceStateWriter
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
    private val serviceStateWriter: ServiceStateWriter,
    private val analytics: PlatformAnalytics,
    private val commandSender: CommandSender,
    private val heartbeatSender: DataLayerHeartbeatSender,
    private val notificationPrefs: NotificationPrefs,
    private val radioInterfaceService: RadioInterfaceService,
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
         * together by [buildMyNodeInfo] at Stage 1 completion. Some firmware/network paths can deliver NodeInfo before
         * the Stage 2 request; keep those packets so the later node-list phase can still make progress.
         */
        data class ReceivingConfig(
            val session: RadioSessionContext,
            val rawMyNodeInfo: ProtoMyNodeInfo,
            val metadata: DeviceMetadata? = null,
            val earlyNodes: List<NodeInfo> = emptyList(),
        ) : HandshakeState()

        /**
         * Stage 2: receiving node-info packets from the firmware.
         *
         * [myNodeInfo] was committed at the Stage 1→2 transition. [nodes] accumulates [NodeInfo] packets until
         * `config_complete_id` arrives.
         */
        data class ReceivingNodeInfo(
            val session: RadioSessionContext,
            val myNodeInfo: SharedMyNodeInfo,
            val nodes: List<NodeInfo> = emptyList(),
        ) : HandshakeState()

        /** Both stages finished. The app is fully connected. */
        data class Complete(val session: RadioSessionContext, val myNodeInfo: SharedMyNodeInfo) : HandshakeState()
    }

    private val handshakeState = atomic<HandshakeState>(HandshakeState.Idle)

    private fun runForSession(session: RadioSessionContext, block: () -> Unit): Boolean =
        radioInterfaceService.runIfSessionActive(session, block)

    private suspend fun runWhileForSession(session: RadioSessionContext, block: suspend () -> Unit): Boolean =
        radioInterfaceService.runWhileSessionActive(session, block)

    private fun isActiveSession(session: RadioSessionContext): Boolean = radioInterfaceService.isSessionActive(session)

    private fun HandshakeState.belongsTo(session: RadioSessionContext): Boolean = when (this) {
        HandshakeState.Idle -> false
        is HandshakeState.ReceivingConfig -> this.session == session
        is HandshakeState.ReceivingNodeInfo -> this.session == session
        is HandshakeState.Complete -> this.session == session
    }

    /** Privacy-safe state label for diagnostics; handshake payloads contain transport and device identifiers. */
    private fun HandshakeState.diagnosticName(): String = when (this) {
        HandshakeState.Idle -> "Idle"
        is HandshakeState.ReceivingConfig -> "ReceivingConfig"
        is HandshakeState.ReceivingNodeInfo -> "ReceivingNodeInfo"
        is HandshakeState.Complete -> "Complete"
    }

    override val newNodeCount: Int
        get() =
            when (val state = handshakeState.value) {
                is HandshakeState.ReceivingConfig -> state.earlyNodes.size
                is HandshakeState.ReceivingNodeInfo -> state.nodes.size
                else -> 0
            }

    override fun handleConfigComplete(configCompleteId: Int, session: RadioSessionContext): Boolean {
        var handled = false
        val admitted = runForSession(session) { handled = handleConfigCompleteActive(configCompleteId, session) }
        if (!admitted) {
            Logger.d { "Discarding config_complete from stale transport session gen=${session.generation}" }
        }
        return admitted && handled
    }

    private fun handleConfigCompleteActive(configCompleteId: Int, session: RadioSessionContext): Boolean {
        val state = handshakeState.value
        return when (configCompleteId) {
            HandshakeConstants.CONFIG_NONCE -> {
                if (state !is HandshakeState.ReceivingConfig || !state.belongsTo(session)) {
                    Logger.w { "Ignoring Stage 1 config_complete in state=${state.diagnosticName()}" }
                    false
                } else {
                    handleConfigOnlyComplete(state)
                    true
                }
            }

            HandshakeConstants.NODE_INFO_NONCE -> {
                if (state !is HandshakeState.ReceivingNodeInfo || !state.belongsTo(session)) {
                    Logger.w { "Ignoring Stage 2 config_complete in state=${state.diagnosticName()}" }
                    false
                } else {
                    handleNodeInfoComplete(state)
                    true
                }
            }

            else -> {
                Logger.w { "Config complete id mismatch: $configCompleteId" }
                false
            }
        }
    }

    private fun handleConfigOnlyComplete(state: HandshakeState.ReceivingConfig) {
        val session = state.session
        Logger.i { "Config-only complete (Stage 1)" }

        val finalizedInfo = buildMyNodeInfo(state.rawMyNodeInfo, state.metadata)
        if (finalizedInfo == null) {
            Logger.w { "Stage 1 failed: could not build MyNodeInfo, retrying Stage 1" }
            handshakeState.value = HandshakeState.Idle
            scope.handledLaunch {
                delay(wantConfigDelay)
                runForSession(session) { connectionManager.value.startConfigOnly() }
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

        handshakeState.value =
            HandshakeState.ReceivingNodeInfo(
                session = state.session,
                myNodeInfo = finalizedInfo,
                nodes = state.earlyNodes,
            )
        Logger.i { "myNodeInfo committed" }
        connectionManager.value.onRadioConfigLoaded()
        serviceStateWriter.setConnectionProgress("Loading node list")

        scope.handledLaunch {
            delay(wantConfigDelay)
            val heartbeatSent = runWhileForSession(session) { heartbeatSender.sendHeartbeat("inter-stage") }
            if (!heartbeatSent) return@handledLaunch
            delay(wantConfigDelay)
            runForSession(session) {
                Logger.i { "Requesting NodeInfo (Stage 2)" }
                connectionManager.value.startNodeInfoOnly()
            }
        }
        connectionManager.value.onHandshakeProgress()
    }

    private fun handleNodeInfoComplete(state: HandshakeState.ReceivingNodeInfo) {
        val session = state.session
        Logger.i { "NodeInfo complete (Stage 2)" }

        val info = state.myNodeInfo

        // Transition state immediately (synchronously) to prevent duplicate handling.
        // The async work below rechecks the originating transport session before publishing results.
        // Because nodes is now immutable, no snapshot is needed — state.nodes IS the snapshot.
        // Any stall-guard retry that re-enters handleNodeInfo will see Complete state and be ignored.
        handshakeState.value = HandshakeState.Complete(session = state.session, myNodeInfo = info)

        // Cancel the transport-aware fast-recovery watchdog SYNCHRONOUSLY, before the async DB
        // install work below is launched. The firmware handshake has already completed at this
        // point (NODE_INFO_NONCE received); a slow Room commit on a large mesh would otherwise
        // falsely trip the 12s fast-recovery timeout before onNodeDbReady() gets a chance to
        // cancel it. onNodeDbReady() still performs the same cancel as part of its larger post-
        // NodeDB side-effect set, but it runs only after the DB install block finishes.
        connectionManager.value.onHandshakeComplete()

        scope.handledLaunch { finishNodeInfoInstall(state) }
        // Note: onHandshakeProgress() is intentionally NOT called here. By this point the
        // handshake has reached HandshakeState.Complete and the synchronous onHandshakeComplete()
        // call above has already cancelled the watchdog. Re-arming via onHandshakeProgress()
        // would be both semantically wrong and wasted work. The remaining onHandshakeProgress
        // sites cover all genuine progress.
    }

    private suspend fun finishNodeInfoInstall(state: HandshakeState.ReceivingNodeInfo) {
        val session = state.session
        try {
            val admitted = runWhileForSession(session) { installAndPublishNodeDatabase(state) }
            if (!admitted) Logger.d { "Discarding stale post-handshake install and publication" }
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            val recovered =
                runForSession(session) {
                    Logger.e(e) { "Post-handshake NodeDB install failed; restarting transport to recover" }
                    nodeManager.setNodeDbReady(false)
                    nodeManager.setAllowNodeDbWrites(false)
                    connectionManager.value.recoverPostHandshakeFailure()
                }
            if (!recovered) Logger.d { "Discarding stale post-handshake recovery" }
        }
    }

    @Suppress("ReturnCount")
    private suspend fun installAndPublishNodeDatabase(state: HandshakeState.ReceivingNodeInfo) {
        val session = state.session
        val info = state.myNodeInfo
        val entities = mutableListOf<Node>()
        state.nodes.forEach { nodeInfo ->
            nodeManager.installNodeInfo(nodeInfo)
            if (!isActiveSession(session)) return
            nodeManager.nodeDBbyNodeNum[nodeInfo.num]?.let(entities::add)
                ?: Logger.w { "Node ${nodeInfo.num} missing after installNodeInfo; skipping" }
        }
        if (!isActiveSession(session)) return

        val removedNums = nodeRepository.installConfig(info, entities)
        if (!isActiveSession(session)) return
        if (removedNums.isNotEmpty()) {
            Logger.i { "Config install migrated ${removedNums.size} stale node identit(y/ies)" }
            removedNums.forEach(nodeManager::removeByNodenum)
        }

        val published =
            runForSession(session) {
                nodeManager.setNodeDbReady(true)
                nodeManager.setAllowNodeDbWrites(true)
                serviceStateWriter.setConnectionState(ConnectionState.Connected)
            }
        if (!published) return

        safeCatching { analytics.setDeviceAttributes(info.firmwareVersion ?: "unknown", info.model ?: "unknown") }
            .onFailure { e -> Logger.w(e) { "Failed to set post-handshake analytics attributes" } }
        if (!isActiveSession(session)) return
        safeCatching { connectionManager.value.onNodeDbReady() }
            .onFailure { e -> Logger.e(e) { "Post-connected onNodeDbReady side effects failed" } }
    }

    override fun handleMyInfo(myInfo: ProtoMyNodeInfo, session: RadioSessionContext) {
        // Hex, not utf8: device_id is raw hardware bytes, and a lossy decode could collapse two
        // distinct devices into the same id. Decode before admission, then publish every synchronous handshake
        // mutation under the transport's revocation lock.
        val deviceId = myInfo.device_id.hex().takeIf { it.isNotBlank() }
        var clearGeneration: Long? = null
        val admitted =
            radioInterfaceService.runIfSessionActive(session) {
                Logger.i { "MyNodeInfo received" }
                handshakeState.value = HandshakeState.ReceivingConfig(session = session, rawMyNodeInfo = myInfo)
                nodeManager.setMyDeviceId(deviceId)
                nodeManager.setMyNodeNum(myInfo.my_node_num)
                nodeManager.publishConnectionIdentity(
                    sessionGeneration = session.generation,
                    address = session.address,
                    nodeNum = myInfo.my_node_num,
                    deviceId = deviceId,
                )
                Logger.d {
                    "[DeviceAssociation] fresh-identity node=${myInfo.my_node_num} " +
                        "deviceIdPresent=${deviceId != null}"
                }
                nodeManager.setFirmwareEdition(myInfo.firmware_edition)
                applyEventFirmwareNotificationDefaults(myInfo.firmware_edition)

                // Bump the generation so a pending clear from an interrupted handshake cannot wipe config saved by
                // this newer session. The async clear also rechecks transport authority before touching persistence.
                clearGeneration = handshakeGeneration.incrementAndGet()
                connectionManager.value.onHandshakeProgress()
            }
        if (!admitted) {
            Logger.d { "[DeviceAssociation] discard stale MyNodeInfo gen=${session.generation}" }
            return
        }

        val gen = checkNotNull(clearGeneration)
        // Queue on the serialized session-operation lane before returning to the FIFO frame consumer. Without
        // UNDISPATCHED, a later config frame can queue its persistence first and then be erased by this reset.
        scope.handledLaunch(start = CoroutineStart.UNDISPATCHED) {
            runWhileForSession(session) {
                if (handshakeGeneration.value != gen) return@runWhileForSession
                radioConfigRepository.clearChannelSet()
                if (handshakeGeneration.value != gen) return@runWhileForSession
                radioConfigRepository.clearLocalConfig()
                if (handshakeGeneration.value != gen) return@runWhileForSession
                radioConfigRepository.clearLocalModuleConfig()
                if (handshakeGeneration.value != gen) return@runWhileForSession
                radioConfigRepository.clearDeviceUIConfig()
                if (handshakeGeneration.value != gen) return@runWhileForSession
                radioConfigRepository.clearFileManifest()
                if (handshakeGeneration.value != gen) return@runWhileForSession
                radioConfigRepository.clearLoraRegionPresetMap()
            }
        }
    }

    override fun handleLocalMetadata(metadata: DeviceMetadata, session: RadioSessionContext): Boolean {
        var handled = false
        var metadataNodeNum: Int? = null
        val admitted =
            runForSession(session) {
                Logger.i { "Local Metadata received: ${metadata.firmware_version}" }
                val state = handshakeState.value
                if (state is HandshakeState.ReceivingConfig && state.belongsTo(session)) {
                    handled = true
                    handshakeState.value = state.copy(metadata = metadata)
                    // Persist the metadata immediately, but never let a queued old-session write target the next
                    // session's selected database.
                    if (metadata != DeviceMetadata()) {
                        metadataNodeNum = state.rawMyNodeInfo.my_node_num
                    }
                    connectionManager.value.onHandshakeProgress()
                } else {
                    Logger.w {
                        "Ignoring metadata outside the owning Stage 1 session (state=${state.diagnosticName()})"
                    }
                }
            }
        metadataNodeNum?.let { nodeNum ->
            scope.handledLaunch(start = CoroutineStart.UNDISPATCHED) {
                runWhileForSession(session) { nodeRepository.insertMetadata(nodeNum, metadata) }
            }
        }
        if (!admitted) Logger.d { "Discarding metadata from stale transport session" }
        return admitted && handled
    }

    override fun handleNodeInfo(info: NodeInfo, session: RadioSessionContext): Boolean {
        var handled = false
        val admitted =
            runForSession(session) {
                val state = handshakeState.value
                when (state) {
                    is HandshakeState.ReceivingConfig -> {
                        if (state.belongsTo(session)) {
                            handled = true
                            Logger.d { "Buffering NodeInfo received during Stage 1" }
                            handshakeState.value = state.copy(earlyNodes = state.earlyNodes.withNodeInfo(info))
                            connectionManager.value.onHandshakeProgress()
                        } else {
                            Logger.w { "Ignoring NodeInfo from a session that does not own Stage 1" }
                        }
                    }

                    is HandshakeState.ReceivingNodeInfo -> {
                        if (state.belongsTo(session)) {
                            handled = true
                            handshakeState.value = state.copy(nodes = state.nodes.withNodeInfo(info))
                            connectionManager.value.onHandshakeProgress()
                        } else {
                            Logger.w { "Ignoring NodeInfo from a session that does not own Stage 2" }
                        }
                    }

                    else -> Logger.w { "Ignoring NodeInfo outside active handshake (state=${state.diagnosticName()})" }
                }
            }
        if (!admitted) Logger.d { "Discarding NodeInfo from stale transport session" }
        return admitted && handled
    }

    override fun handleFileInfo(info: FileInfo, session: RadioSessionContext): Boolean {
        val admitted =
            runForSession(session) {
                Logger.d { "FileInfo received: ${info.file_name} (${info.size_bytes} bytes)" }
                connectionManager.value.onHandshakeProgress()
            }
        if (admitted) {
            scope.handledLaunch(start = CoroutineStart.UNDISPATCHED) {
                runWhileForSession(session) { radioConfigRepository.addFileInfo(info) }
            }
        }
        if (!admitted) Logger.d { "Discarding FileInfo from stale transport session" }
        return admitted
    }

    override fun triggerWantConfig(session: RadioSessionContext): Boolean {
        val admitted = runForSession(session) { connectionManager.value.startConfigOnly() }
        if (!admitted) Logger.d { "Discarding reboot handshake trigger from stale transport session" }
        return admitted
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
                // Hex, not utf8: device_id is raw hardware bytes (see setMyDeviceId above).
                deviceId = device_id.hex().ifEmpty { null },
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

private fun List<NodeInfo>.withNodeInfo(info: NodeInfo): List<NodeInfo> {
    val index = indexOfFirst { it.num == info.num }
    return if (index >= 0) {
        toMutableList().apply { this[index] = info }
    } else {
        this + info
    }
}
