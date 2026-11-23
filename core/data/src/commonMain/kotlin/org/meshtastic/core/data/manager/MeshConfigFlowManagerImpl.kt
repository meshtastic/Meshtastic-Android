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
import kotlinx.coroutines.CoroutineStart
import org.koin.core.annotation.Single
import org.meshtastic.core.common.di.ServiceScope
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.MeshConfigFlowManager
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.NotificationPrefs
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.repository.ServiceStateWriter
import org.meshtastic.proto.Channel
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.FileInfo
import org.meshtastic.proto.FirmwareEdition
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.NodeInfo
import org.meshtastic.sdk.ConfigBundle
import org.meshtastic.sdk.RadioSessionEvent
import org.meshtastic.core.model.MyNodeInfo as SharedMyNodeInfo
import org.meshtastic.proto.MyNodeInfo as ProtoMyNodeInfo

@Suppress("LongParameterList", "TooManyFunctions")
@Single
class MeshConfigFlowManagerImpl(
    private val nodeManager: NodeManager,
    private val nodeRepository: NodeRepository,
    private val radioConfigRepository: RadioConfigRepository,
    private val serviceStateWriter: ServiceStateWriter,
    private val analytics: PlatformAnalytics,
    private val notificationPrefs: NotificationPrefs,
    private val radioInterfaceService: RadioInterfaceService,
    private val scope: ServiceScope,
) : MeshConfigFlowManager {
    private data class InstalledSnapshot(val session: RadioSessionContext, val myNode: SharedMyNodeInfo)

    private val installedSnapshot = atomic<InstalledSnapshot?>(null)

    private fun runForSession(session: RadioSessionContext, block: () -> Unit): Boolean =
        radioInterfaceService.runIfSessionActive(session, block)

    private suspend fun runWhileForSession(
        session: RadioSessionContext,
        label: String,
        block: suspend () -> Unit,
    ): Boolean = radioInterfaceService.runWhileSessionActive(session, label, block)

    private fun isActiveSession(session: RadioSessionContext): Boolean = radioInterfaceService.isSessionActive(session)

    /**
     * Installs the SDK handshake snapshot under the app's current session lease. The session waits for this call to
     * return true before delivering retained packets.
     */
    internal suspend fun installRadioSnapshot(session: RadioSessionContext, ready: RadioSessionEvent.Ready): Boolean {
        val myInfo = checkNotNull(ready.myInfo)
        val bundle = ready.configBundle
        val metadata = bundle?.metadata
        val info = buildMyNodeInfo(myInfo, metadata) ?: return false
        var installed = false
        runWhileForSession(session, "radio snapshot install") {
            val admitted =
                runForSession(session) {
                    nodeManager.setNodeDbReady(false)
                    nodeManager.setAllowNodeDbWrites(false)
                    publishMyNodeIdentity(myInfo, session)
                    nodeManager.setFirmwareVersion(metadata?.firmware_version, session)
                }
            if (!admitted) return@runWhileForSession

            clearHandshakeConfig { isActiveSession(session) }
            if (!isActiveSession(session)) return@runWhileForSession
            val moduleConfigs = bundle?.moduleConfigs.orEmpty()
            with(radioConfigRepository) {
                ready.channels.orEmpty().forEach { updateChannelSettings(it) }
                bundle?.configs.orEmpty().forEach { setLocalConfig(it) }
                moduleConfigs.forEach { setLocalModuleConfig(it) }
                bundle?.deviceUIConfig?.let { setDeviceUIConfig(it) }
                ready.files.forEach { addFileInfo(it) }
                ready.regionPresets?.let { setLoraRegionPresetMap(it) }
            }
            metadata?.let { nodeRepository.insertMetadata(info.myNodeNum, it) }
            // Before the node install, which persists the local node with its status.
            moduleConfigs
                .firstNotNullOfOrNull { it.statusmessage }
                ?.let { status -> nodeManager.updateNodeStatusAndPersist(info.myNodeNum, status.node_status) }
            installed =
                installNodeDatabase(session, info, ready.nodes.values.toList(), isRefresh = false) &&
                runForSession(session) { installedSnapshot.value = InstalledSnapshot(session, info) }
        }
        return installed
    }

    /** Applies a complete nodes-only SDK download under the same session lease as the initial snapshot. */
    internal suspend fun installRefreshedNodes(session: RadioSessionContext, nodes: List<NodeInfo>) {
        runWhileForSession(session, "SDK NodeDB refresh") {
            val installed = installedSnapshot.value ?: return@runWhileForSession
            if (installed.session == session) installNodeDatabase(session, installed.myNode, nodes, isRefresh = true)
        }
    }

    /** Writes the whole SDK config bundle after an acknowledged write or a change from another client. */
    internal suspend fun installConfigBundle(session: RadioSessionContext, bundle: ConfigBundle) {
        runWhileForSession(session, "SDK config change") {
            with(radioConfigRepository) {
                bundle.configs.forEach { setLocalConfig(it) }
                bundle.moduleConfigs.forEach { setLocalModuleConfig(it) }
                bundle.deviceUIConfig?.let { setDeviceUIConfig(it) }
                bundle.regionPresets?.let { setLoraRegionPresetMap(it) }
            }
            val myNodeNum = installedSnapshot.value?.takeIf { it.session == session }?.myNode?.myNodeNum
            if (myNodeNum != null) {
                nodeRepository.insertMetadata(myNodeNum, bundle.metadata)
                bundle.moduleConfigs
                    .firstNotNullOfOrNull { it.statusmessage }
                    ?.let { status ->
                        nodeManager.updateNodeStatusAndPersist(myNodeNum, status.node_status)
                    }
            }
        }
    }

    /** Writes the whole SDK channel list after an acknowledged channel write or a change from another client. */
    internal suspend fun installChannels(session: RadioSessionContext, channels: List<Channel>) {
        runWhileForSession(session, "SDK channel change") {
            channels.sortedBy { it.index }.forEach { radioConfigRepository.updateChannelSettings(it) }
        }
    }

    @Suppress("ReturnCount")
    private suspend fun installNodeDatabase(
        session: RadioSessionContext,
        info: SharedMyNodeInfo,
        nodes: List<NodeInfo>,
        isRefresh: Boolean,
    ): Boolean {
        // Re-key conversations against the installed channel and LoRa settings before publishing readiness.
        safeCatching { radioConfigRepository.reconcileConversations() }
            .onFailure { e -> Logger.e(e) { "Conversation reconciliation failed after handshake" } }
        if (!isActiveSession(session)) return false

        val entities = mutableListOf<Node>()
        nodes.forEach { nodeInfo ->
            nodeManager.installNodeInfo(nodeInfo)
            if (!isActiveSession(session)) return false
            nodeManager.nodeDBbyNodeNum[nodeInfo.num]?.let(entities::add)
                ?: Logger.w { "Node ${nodeInfo.num} missing after installNodeInfo; skipping" }
        }
        if (!isActiveSession(session)) return false

        val removedNums = nodeRepository.installConfig(info, entities)
        if (!isActiveSession(session)) return false
        if (removedNums.isNotEmpty()) {
            Logger.i { "Config install migrated ${removedNums.size} stale node identit(y/ies)" }
            nodeManager.applyTrustedIdentityMigrations(removedNums)
        }

        if (isRefresh) return true

        val published =
            runForSession(session) {
                nodeManager.setNodeDbReady(true)
                nodeManager.setAllowNodeDbWrites(true)
                serviceStateWriter.setConnectionState(ConnectionState.Connected)
            }
        if (!published) return false

        safeCatching { analytics.setDeviceAttributes(info.firmwareVersion ?: "unknown", info.model ?: "unknown") }
            .onFailure { e -> Logger.w(e) { "Failed to set post-handshake analytics attributes" } }
        return isActiveSession(session)
    }

    private fun publishMyNodeIdentity(myInfo: ProtoMyNodeInfo, session: RadioSessionContext) {
        // The hardware device ID is raw bytes. Hex preserves it for database association.
        val deviceId = myInfo.device_id.hex().takeIf { it.isNotBlank() }
        nodeManager.setMyDeviceId(deviceId)
        nodeManager.setMyNodeNum(myInfo.my_node_num)
        nodeManager.publishConnectionIdentity(
            sessionGeneration = session.generation,
            address = session.address,
            nodeNum = myInfo.my_node_num,
            deviceId = deviceId,
        )
        Logger.d {
            "[DeviceAssociation] fresh-identity node=${myInfo.my_node_num} " + "deviceIdPresent=${deviceId != null}"
        }
        nodeManager.setFirmwareEdition(myInfo.firmware_edition)
        applyEventFirmwareNotificationDefaults(myInfo.firmware_edition)
    }

    @Suppress("ReturnCount")
    private suspend fun clearHandshakeConfig(isCurrent: () -> Boolean) {
        if (!isCurrent()) return
        radioConfigRepository.clearChannelSet()
        if (!isCurrent()) return
        radioConfigRepository.clearLocalConfig()
        if (!isCurrent()) return
        radioConfigRepository.clearLocalModuleConfig()
        if (!isCurrent()) return
        radioConfigRepository.clearDeviceUIConfig()
        if (!isCurrent()) return
        radioConfigRepository.clearFileManifest()
        if (!isCurrent()) return
        radioConfigRepository.clearLoraRegionPresetMap()
    }

    override fun handleFileInfo(info: FileInfo, session: RadioSessionContext): Boolean {
        val admitted =
            runForSession(session) {
                Logger.d { "FileInfo received: ${info.file_name} (${info.size_bytes} bytes)" }
            }
        if (admitted) {
            scope.handledLaunch(start = CoroutineStart.UNDISPATCHED) {
                runWhileForSession(session, "fileInfo persist") { radioConfigRepository.addFileInfo(info) }
            }
        }
        if (!admitted) Logger.d { "Discarding FileInfo from stale transport session" }
        return admitted
    }

    override fun refreshNodeInfo(): Boolean {
        val session = radioInterfaceService.activeSession.value ?: return false
        return radioInterfaceService.requestNodeDbRefresh(session)
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
                // Nothing reads this column; the SDK client numbers packets.
                currentPacketId = 0L,
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

    // Whether to silence new-node notifications is a read-decide-write over two preferences, so it lives behind one
    // atomic prefs operation rather than being assembled from setter calls here.
    private fun applyEventFirmwareNotificationDefaults(edition: FirmwareEdition) {
        notificationPrefs.applyEventFirmwareNodeEventDefault(isEventFirmware = edition != FirmwareEdition.VANILLA)
    }
}
