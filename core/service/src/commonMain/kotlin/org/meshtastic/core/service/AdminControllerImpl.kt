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
package org.meshtastic.core.service

import co.touchlab.kermit.Logger
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.model.HamName
import org.meshtastic.core.model.Position
import org.meshtastic.core.repository.AdminController
import org.meshtastic.core.repository.AdminEditScope
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.EditSettingsTransactionException
import org.meshtastic.core.repository.LocalNodeUnavailableException
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.toFixedPositionProto
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Channel
import org.meshtastic.proto.Config
import org.meshtastic.proto.HamParameters
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.OTAMode
import org.meshtastic.proto.User
import org.meshtastic.sdk.AdminEdit
import org.meshtastic.sdk.AdminResult
import org.meshtastic.sdk.NodeId

internal fun editSettingsFailureMessage(result: AdminResult<*>): String =
    "Device rejected or did not answer the edit-settings transaction ($result)"

/**
 * [AdminController] implementation: local/remote configuration, channels, owner, device lifecycle, and the
 * [editSettings] transaction.
 *
 * Focused collaborator of [RadioControllerImpl]. Standalone writes build [AdminMessage] protos for [CommandSender] and
 * may update local caches optimistically. The [editSettings] transaction runs on the active session's SDK admin API and
 * stages its local projections until the SDK reports the commit applied, so failed edits cannot publish settings the
 * device did not commit. The device remains the source of truth and re-sends its full config on every connection.
 */
@Suppress("TooManyFunctions")
internal class AdminControllerImpl(
    private val commandSender: CommandSender,
    private val nodeManager: NodeManager,
    private val radioConfigRepository: RadioConfigRepository,
    private val radio: RadioInterfaceService,
    private val heardOnCurrentLoraRefresher: HeardOnCurrentLoraRefresher,
    private val scope: CoroutineScope,
) : AdminController {

    private val myNodeNum: Int
        get() = nodeManager.myNodeNum.value ?: 0

    // ── Owner ───────────────────────────────────────────────────────────────

    override suspend fun setOwner(destNum: Int, user: User, packetId: Int) {
        commandSender.sendAdmin(destNum, packetId) { AdminMessage.Builder().also { wb -> wb.set_owner = user }.build() }
        nodeManager.handleReceivedUser(destNum, user)
    }

    override suspend fun getOwner(destNum: Int, packetId: Int) {
        commandSender.sendAdmin(destNum, packetId, wantResponse = true) {
            AdminMessage.Builder().also { wb -> wb.get_owner_request = true }.build()
        }
    }

    override suspend fun setHamMode(destNum: Int, hamParameters: HamParameters, packetId: Int) {
        if (destNum != nodeManager.myNodeNum.value) {
            Logger.w { "Ignoring setHamMode for node $destNum — ham onboarding targets the local node only" }
            return
        }
        // Firmware applies tx_power/frequency to the LoRa config verbatim, so echo the node's current
        // values to keep a re-send (e.g. a callsign edit while already licensed) from wiping overrides.
        val lora = radioConfigRepository.localConfigFlow.firstOrNull()?.lora ?: Config.LoRaConfig.Builder().build()
        val params =
            hamParameters
                .newBuilder()
                .also { wb ->
                    wb.tx_power = lora.tx_power
                    wb.frequency = lora.override_frequency
                }
                .build()
        commandSender.sendAdmin(destNum, packetId) {
            AdminMessage.Builder().also { wb -> wb.set_ham_mode = params }.build()
        }
        val currentUser = nodeManager.nodeDBbyNodeNum[destNum]?.user ?: User.Builder().build()
        nodeManager.handleReceivedUser(
            destNum,
            currentUser
                .newBuilder()
                .also { wb ->
                    wb.long_name = HamName.compose(hamParameters.call_sign, hamParameters.long_name)
                    wb.short_name = hamParameters.short_name
                    wb.is_licensed = true
                }
                .build(),
        )
    }

    // ── Configuration ─────────────────────────────────────────────────────────

    override suspend fun setLocalConfig(config: Config) {
        val packetId = commandSender.generatePacketId()
        heardOnCurrentLoraRefresher.beforeLocalWrite(config, packetId)
        commandSender.sendAdmin(myNodeNum, packetId) {
            AdminMessage.Builder().also { wb -> wb.set_config = config }.build()
        }
        scope.handledLaunch { radioConfigRepository.setLocalConfig(config) }
    }

    override fun setDisplayMirror(enabled: Boolean) {
        commandSender.sendAdminImmediate(myNodeNum) {
            AdminMessage.Builder().also { wb -> wb.set_display_mirror = enabled }.build()
        }
    }

    override fun requestDisplayFrame() {
        commandSender.sendAdminImmediate(myNodeNum) {
            AdminMessage.Builder().also { wb -> wb.get_display_frame_request = true }.build()
        }
    }

    override fun sendInputEvent(eventCode: Int, kbChar: Int, touchX: Int, touchY: Int) {
        val inputEvent =
            AdminMessage.InputEvent.Builder()
                .also { wb ->
                    wb.event_code = eventCode
                    wb.kb_char = kbChar
                    wb.touch_x = touchX
                    wb.touch_y = touchY
                }
                .build()
        commandSender.sendAdminImmediate(myNodeNum) {
            AdminMessage.Builder().also { wb -> wb.send_input_event = inputEvent }.build()
        }
    }

    override suspend fun setConfig(destNum: Int, config: Config, packetId: Int) {
        val isLocal = destNum == nodeManager.myNodeNum.value
        if (isLocal) heardOnCurrentLoraRefresher.beforeLocalWrite(config, packetId)
        commandSender.sendAdmin(destNum, packetId) {
            AdminMessage.Builder().also { wb -> wb.set_config = config }.build()
        }
        if (isLocal) {
            scope.handledLaunch { radioConfigRepository.setLocalConfig(config) }
        }
    }

    override suspend fun getConfig(destNum: Int, configType: Int, packetId: Int) {
        commandSender.sendAdmin(destNum, packetId, wantResponse = true) {
            if (configType == AdminMessage.ConfigType.SESSIONKEY_CONFIG.value) {
                AdminMessage.Builder().also { wb -> wb.get_device_metadata_request = true }.build()
            } else {
                AdminMessage.Builder()
                    .also { wb -> wb.get_config_request = AdminMessage.ConfigType.fromValue(configType) }
                    .build()
            }
        }
    }

    override suspend fun setModuleConfig(destNum: Int, config: ModuleConfig, packetId: Int) {
        commandSender.sendAdmin(destNum, packetId) {
            AdminMessage.Builder().also { wb -> wb.set_module_config = config }.build()
        }
        if (destNum == nodeManager.myNodeNum.value) {
            config.statusmessage?.let { sm -> nodeManager.updateNodeStatus(destNum, sm.node_status) }
            scope.handledLaunch { radioConfigRepository.setLocalModuleConfig(config) }
        }
    }

    override suspend fun getModuleConfig(destNum: Int, moduleConfigType: Int, packetId: Int) {
        commandSender.sendAdmin(destNum, packetId, wantResponse = true) {
            AdminMessage.Builder()
                .also { wb -> wb.get_module_config_request = AdminMessage.ModuleConfigType.fromValue(moduleConfigType) }
                .build()
        }
    }

    // ── Channels ────────────────────────────────────────────────────────────

    override suspend fun setLocalChannel(channel: Channel) {
        val packetId = commandSender.generatePacketId()
        heardOnCurrentLoraRefresher.beforeLocalWrite(channel, packetId)
        commandSender.sendAdmin(myNodeNum, packetId) {
            AdminMessage.Builder().also { wb -> wb.set_channel = channel }.build()
        }
        scope.handledLaunch { radioConfigRepository.updateChannelSettings(channel) }
    }

    override suspend fun setRemoteChannel(destNum: Int, channel: Channel, packetId: Int) {
        val isLocal = destNum == nodeManager.myNodeNum.value
        if (isLocal) heardOnCurrentLoraRefresher.beforeLocalWrite(channel, packetId)
        commandSender.sendAdmin(destNum, packetId) {
            AdminMessage.Builder().also { wb -> wb.set_channel = channel }.build()
        }
        if (isLocal) {
            scope.handledLaunch { radioConfigRepository.updateChannelSettings(channel) }
        }
    }

    override suspend fun getChannel(destNum: Int, index: Int, packetId: Int) {
        commandSender.sendAdmin(destNum, packetId, wantResponse = true) {
            AdminMessage.Builder().also { wb -> wb.get_channel_request = index + 1 }.build()
        }
    }

    // ── Ringtone & Canned Messages ─────────────────────────────────────────

    override suspend fun setRingtone(destNum: Int, ringtone: String) {
        commandSender.sendAdmin(destNum) {
            AdminMessage.Builder().also { wb -> wb.set_ringtone_message = ringtone }.build()
        }
    }

    override suspend fun playRingtone(destNum: Int) {
        commandSender.sendAdmin(destNum) {
            AdminMessage.Builder().also { wb -> wb.play_ringtone_request = true }.build()
        }
    }

    override suspend fun getRingtone(destNum: Int, packetId: Int) {
        commandSender.sendAdmin(destNum, packetId, wantResponse = true) {
            AdminMessage.Builder().also { wb -> wb.get_ringtone_request = true }.build()
        }
    }

    override suspend fun setCannedMessages(destNum: Int, messages: String) {
        commandSender.sendAdmin(destNum) {
            AdminMessage.Builder().also { wb -> wb.set_canned_message_module_messages = messages }.build()
        }
    }

    override suspend fun setTime(destNum: Int, packetId: Int) {
        Logger.i { "Set time requested for node $destNum" }
        // Resolve the timestamp at send time so the value is as fresh as possible when it leaves the phone.
        commandSender.sendAdmin(destNum, packetId) {
            AdminMessage.Builder().also { wb -> wb.set_time_only = nowSeconds.toInt() }.build()
        }
    }

    override suspend fun getCannedMessages(destNum: Int, packetId: Int) {
        commandSender.sendAdmin(destNum, packetId, wantResponse = true) {
            AdminMessage.Builder().also { wb -> wb.get_canned_message_module_messages_request = true }.build()
        }
    }

    // ── Position ────────────────────────────────────────────────────────────

    override suspend fun setFixedPosition(destNum: Int, position: Position) {
        commandSender.setFixedPosition(destNum, position)
    }

    // ── Device Status & Lifecycle ───────────────────────────────────────────

    override suspend fun getDeviceConnectionStatus(destNum: Int, packetId: Int) {
        commandSender.sendAdmin(destNum, packetId, wantResponse = true) {
            AdminMessage.Builder().also { wb -> wb.get_device_connection_status_request = true }.build()
        }
    }

    override suspend fun reboot(destNum: Int, packetId: Int) {
        Logger.i { "Reboot requested for node $destNum" }
        commandSender.sendAdmin(destNum, packetId) {
            AdminMessage.Builder().also { wb -> wb.reboot_seconds = DEFAULT_DELAY_SECONDS }.build()
        }
    }

    override suspend fun rebootToDfu(nodeNum: Int, packetId: Int) {
        commandSender.sendAdmin(nodeNum, packetId) {
            AdminMessage.Builder().also { wb -> wb.enter_dfu_mode_request = true }.build()
        }
    }

    override suspend fun requestRebootOta(requestId: Int, destNum: Int, mode: Int, hash: ByteArray?) {
        val otaMode = OTAMode.fromValue(mode) ?: OTAMode.NO_REBOOT_OTA
        val otaEvent =
            AdminMessage.OTAEvent.Builder()
                .also { wb ->
                    wb.reboot_ota_mode = otaMode
                    wb.ota_hash = hash?.toByteString() ?: ByteString.EMPTY
                }
                .build()
        commandSender.sendAdmin(destNum, requestId) {
            AdminMessage.Builder().also { wb -> wb.ota_request = otaEvent }.build()
        }
    }

    override suspend fun shutdown(destNum: Int, packetId: Int) {
        commandSender.sendAdmin(destNum, packetId) {
            AdminMessage.Builder().also { wb -> wb.shutdown_seconds = DEFAULT_DELAY_SECONDS }.build()
        }
    }

    override suspend fun factoryReset(destNum: Int, packetId: Int) {
        Logger.i { "Factory reset requested for node $destNum" }
        commandSender.sendAdmin(destNum, packetId) {
            AdminMessage.Builder().also { wb -> wb.factory_reset_device = 1 }.build()
        }
    }

    override suspend fun nodedbReset(destNum: Int, packetId: Int, preserveFavorites: Boolean) {
        commandSender.sendAdmin(destNum, packetId) {
            AdminMessage.Builder().also { wb -> wb.nodedb_reset = preserveFavorites }.build()
        }
    }

    // ── Edit Settings (transactional) ───────────────────────────────────────

    /**
     * Runs [block] in the active session's SDK `editSettings`, addressed with `forNode` unless [destNum] is the local
     * node. The SDK owns begin, commit and the commit-on-cancellation rule. Staged projections are applied only once it
     * reports the commit applied, and only while the same session still owns the database; a local BLE commit drops the
     * link, and the next session's config download carries the committed values instead.
     */
    override suspend fun editSettings(destNum: Int, block: suspend AdminEditScope.() -> Unit) {
        val isLocalDestination = destNum == nodeManager.myNodeNum.value
        val session = radio.activeSession.value
        val sessionAdmin =
            session?.let(radio::adminForSession)
                ?: throw EditSettingsTransactionException(editSettingsFailureMessage(AdminResult.NodeUnreachable))
        val admin = if (isLocalDestination) sessionAdmin else sessionAdmin.forNode(NodeId(destNum))

        val projections = StagedProjections()
        val result = admin.editSettings { EditSettingsScope(this, destNum, isLocalDestination, projections).block() }
        if (result !is AdminResult.Success) throw EditSettingsTransactionException(editSettingsFailureMessage(result))
        radio.runWithSessionLease(session) { projections.apply() }
    }

    override suspend fun editLocalSettings(block: suspend AdminEditScope.() -> Unit) {
        val localNodeNum = nodeManager.myNodeNum.value ?: throw LocalNodeUnavailableException("Edit settings")
        editSettings(localNodeNum, block)
    }

    /** Forwards [AdminEditScope] writes to the SDK transaction and stages their local projections. */
    private inner class EditSettingsScope(
        private val edit: AdminEdit,
        private val destNum: Int,
        private val isLocalDestination: Boolean,
        private val projections: StagedProjections,
    ) : AdminEditScope {
        override suspend fun setOwner(user: User) {
            edit.setOwner(user)
            projections.stage { nodeManager.handleReceivedUser(destNum, user) }
        }

        override suspend fun setConfig(config: Config) {
            edit.setConfig(config)
            if (isLocalDestination) projections.stage { radioConfigRepository.setLocalConfig(config) }
        }

        override suspend fun setModuleConfig(config: ModuleConfig) {
            edit.setModuleConfig(config)
            if (isLocalDestination) {
                projections.stage {
                    config.statusmessage?.let { status -> nodeManager.updateNodeStatus(destNum, status.node_status) }
                    radioConfigRepository.setLocalModuleConfig(config)
                }
            }
        }

        // Unlike one-shot writes, a transaction can replace several slots. This scope cannot infer the complete target
        // set, so the operation adding channel writes must reconcile that set after the transaction; mirroring slots
        // here could expose a partial cache if a later write or commit fails.
        override suspend fun setChannel(channel: Channel) = edit.setChannel(channel)

        override suspend fun setFixedPosition(position: Position) {
            if (position.isFixedPositionRemoval()) {
                edit.removeFixedPosition()
                return
            }
            val projectionNodeNum = nodeManager.myNodeNum.value ?: throw LocalNodeUnavailableException("Fixed position")
            val protoPosition = position.toFixedPositionProto()
            edit.setFixedPosition(protoPosition)
            projections.stage {
                nodeManager.handleReceivedPosition(destNum, projectionNodeNum, protoPosition, nowMillis)
            }
        }
    }

    private class StagedProjections {
        private val lock = SynchronizedObject()
        private val staged = mutableListOf<suspend () -> Unit>()

        fun stage(projection: suspend () -> Unit) {
            synchronized(lock) { staged += projection }
        }

        suspend fun apply() = withContext(NonCancellable) {
            synchronized(lock) { staged.toList().also { staged.clear() } }.forEach { applyProjection(it) }
        }

        // Only called under apply's NonCancellable, so no cancellation of its own reaches the catch.
        @Suppress("TooGenericExceptionCaught", "SuspendFunSwallowedCancellation")
        private suspend fun applyProjection(projection: suspend () -> Unit) {
            try {
                projection()
            } catch (e: Exception) {
                Logger.w(e) { "Local edit-settings projection failed after device commit" }
            }
        }
    }

    private companion object {
        private const val DEFAULT_DELAY_SECONDS = 5
    }
}
