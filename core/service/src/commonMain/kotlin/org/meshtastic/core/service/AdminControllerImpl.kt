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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.firstOrNull
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.model.Position
import org.meshtastic.core.repository.AdminController
import org.meshtastic.core.repository.AdminEditScope
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Channel
import org.meshtastic.proto.Config
import org.meshtastic.proto.HamParameters
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.OTAMode
import org.meshtastic.proto.User

/**
 * [AdminController] implementation: local/remote configuration, channels, owner, device lifecycle, and the
 * [editSettings] transaction.
 *
 * Focused collaborator of [RadioControllerImpl]. Builds [AdminMessage] protos directly and delegates to [CommandSender]
 * for transport, mirroring the SDK's `AdminApiImpl` pattern. Config/channel writes use fire-and-forget optimistic local
 * persistence ([handledLaunch]): the device is the source of truth and re-sends its full config on every connection, so
 * persistence is a cache optimization, not a correctness requirement.
 */
@Suppress("TooManyFunctions")
internal class AdminControllerImpl(
    private val commandSender: CommandSender,
    private val nodeManager: NodeManager,
    private val radioConfigRepository: RadioConfigRepository,
    private val scope: CoroutineScope,
) : AdminController {

    private val myNodeNum: Int
        get() = nodeManager.myNodeNum.value ?: 0

    // ── Owner ───────────────────────────────────────────────────────────────

    override suspend fun setOwner(destNum: Int, user: User, packetId: Int) {
        commandSender.sendAdmin(destNum, packetId) { AdminMessage(set_owner = user) }
        nodeManager.handleReceivedUser(destNum, user)
    }

    override suspend fun getOwner(destNum: Int, packetId: Int) {
        commandSender.sendAdmin(destNum, packetId, wantResponse = true) { AdminMessage(get_owner_request = true) }
    }

    override suspend fun setHamMode(destNum: Int, hamParameters: HamParameters, packetId: Int) {
        if (destNum != nodeManager.myNodeNum.value) {
            Logger.w { "Ignoring setHamMode for node $destNum — ham onboarding targets the local node only" }
            return
        }
        // Firmware applies tx_power/frequency to the LoRa config verbatim, so echo the node's current
        // values to keep a re-send (e.g. a callsign edit while already licensed) from wiping overrides.
        val lora = radioConfigRepository.localConfigFlow.firstOrNull()?.lora ?: Config.LoRaConfig()
        val params = hamParameters.copy(tx_power = lora.tx_power, frequency = lora.override_frequency)
        commandSender.sendAdmin(destNum, packetId) { AdminMessage(set_ham_mode = params) }
        val currentUser = nodeManager.nodeDBbyNodeNum[destNum]?.user ?: User()
        nodeManager.handleReceivedUser(
            destNum,
            currentUser.copy(
                long_name = hamParameters.call_sign,
                short_name = hamParameters.short_name,
                is_licensed = true,
            ),
        )
    }

    // ── Configuration ─────────────────────────────────────────────────────────

    override suspend fun setLocalConfig(config: Config) {
        commandSender.sendAdmin(myNodeNum) { AdminMessage(set_config = config) }
        scope.handledLaunch { radioConfigRepository.setLocalConfig(config) }
    }

    override suspend fun setConfig(destNum: Int, config: Config, packetId: Int) {
        commandSender.sendAdmin(destNum, packetId) { AdminMessage(set_config = config) }
        if (destNum == nodeManager.myNodeNum.value) {
            scope.handledLaunch { radioConfigRepository.setLocalConfig(config) }
        }
    }

    override suspend fun getConfig(destNum: Int, configType: Int, packetId: Int) {
        commandSender.sendAdmin(destNum, packetId, wantResponse = true) {
            if (configType == AdminMessage.ConfigType.SESSIONKEY_CONFIG.value) {
                AdminMessage(get_device_metadata_request = true)
            } else {
                AdminMessage(get_config_request = AdminMessage.ConfigType.fromValue(configType))
            }
        }
    }

    override suspend fun setModuleConfig(destNum: Int, config: ModuleConfig, packetId: Int) {
        commandSender.sendAdmin(destNum, packetId) { AdminMessage(set_module_config = config) }
        if (destNum == nodeManager.myNodeNum.value) {
            config.statusmessage?.let { sm -> nodeManager.updateNodeStatus(destNum, sm.node_status) }
            scope.handledLaunch { radioConfigRepository.setLocalModuleConfig(config) }
        }
    }

    override suspend fun getModuleConfig(destNum: Int, moduleConfigType: Int, packetId: Int) {
        commandSender.sendAdmin(destNum, packetId, wantResponse = true) {
            AdminMessage(get_module_config_request = AdminMessage.ModuleConfigType.fromValue(moduleConfigType))
        }
    }

    // ── Channels ────────────────────────────────────────────────────────────

    override suspend fun setLocalChannel(channel: Channel) {
        commandSender.sendAdmin(myNodeNum) { AdminMessage(set_channel = channel) }
        scope.handledLaunch { radioConfigRepository.updateChannelSettings(channel) }
    }

    override suspend fun setRemoteChannel(destNum: Int, channel: Channel, packetId: Int) {
        commandSender.sendAdmin(destNum, packetId) { AdminMessage(set_channel = channel) }
        if (destNum == nodeManager.myNodeNum.value) {
            scope.handledLaunch { radioConfigRepository.updateChannelSettings(channel) }
        }
    }

    override suspend fun getChannel(destNum: Int, index: Int, packetId: Int) {
        commandSender.sendAdmin(destNum, packetId, wantResponse = true) {
            AdminMessage(get_channel_request = index + 1)
        }
    }

    // ── Ringtone & Canned Messages ─────────────────────────────────────────

    override suspend fun setRingtone(destNum: Int, ringtone: String) {
        commandSender.sendAdmin(destNum) { AdminMessage(set_ringtone_message = ringtone) }
    }

    override suspend fun getRingtone(destNum: Int, packetId: Int) {
        commandSender.sendAdmin(destNum, packetId, wantResponse = true) { AdminMessage(get_ringtone_request = true) }
    }

    override suspend fun setCannedMessages(destNum: Int, messages: String) {
        commandSender.sendAdmin(destNum) { AdminMessage(set_canned_message_module_messages = messages) }
    }

    override suspend fun setTime(destNum: Int, packetId: Int) {
        Logger.i { "Set time requested for node $destNum" }
        // Resolve the timestamp at send time so the value is as fresh as possible when it leaves the phone.
        commandSender.sendAdmin(destNum, packetId) { AdminMessage(set_time_only = nowSeconds.toInt()) }
    }

    override suspend fun getCannedMessages(destNum: Int, packetId: Int) {
        commandSender.sendAdmin(destNum, packetId, wantResponse = true) {
            AdminMessage(get_canned_message_module_messages_request = true)
        }
    }

    // ── Position ────────────────────────────────────────────────────────────

    override suspend fun setFixedPosition(destNum: Int, position: Position) {
        commandSender.setFixedPosition(destNum, position)
    }

    // ── Device Status & Lifecycle ───────────────────────────────────────────

    override suspend fun getDeviceConnectionStatus(destNum: Int, packetId: Int) {
        commandSender.sendAdmin(destNum, packetId, wantResponse = true) {
            AdminMessage(get_device_connection_status_request = true)
        }
    }

    override suspend fun reboot(destNum: Int, packetId: Int) {
        Logger.i { "Reboot requested for node $destNum" }
        commandSender.sendAdmin(destNum, packetId) { AdminMessage(reboot_seconds = DEFAULT_DELAY_SECONDS) }
    }

    override suspend fun rebootToDfu(nodeNum: Int) {
        commandSender.sendAdmin(nodeNum) { AdminMessage(enter_dfu_mode_request = true) }
    }

    override suspend fun requestRebootOta(requestId: Int, destNum: Int, mode: Int, hash: ByteArray?) {
        val otaMode = OTAMode.fromValue(mode) ?: OTAMode.NO_REBOOT_OTA
        val otaEvent =
            AdminMessage.OTAEvent(reboot_ota_mode = otaMode, ota_hash = hash?.toByteString() ?: ByteString.EMPTY)
        commandSender.sendAdmin(destNum, requestId) { AdminMessage(ota_request = otaEvent) }
    }

    override suspend fun shutdown(destNum: Int, packetId: Int) {
        commandSender.sendAdmin(destNum, packetId) { AdminMessage(shutdown_seconds = DEFAULT_DELAY_SECONDS) }
    }

    override suspend fun factoryReset(destNum: Int, packetId: Int) {
        Logger.i { "Factory reset requested for node $destNum" }
        commandSender.sendAdmin(destNum, packetId) { AdminMessage(factory_reset_device = 1) }
    }

    override suspend fun nodedbReset(destNum: Int, packetId: Int, preserveFavorites: Boolean) {
        commandSender.sendAdmin(destNum, packetId) { AdminMessage(nodedb_reset = preserveFavorites) }
    }

    // ── Edit Settings (transactional) ───────────────────────────────────────

    override suspend fun editSettings(destNum: Int, block: suspend AdminEditScope.() -> Unit) {
        commandSender.sendAdmin(destNum) { AdminMessage(begin_edit_settings = true) }
        EditSettingsSession(destNum).block()
        commandSender.sendAdmin(destNum) { AdminMessage(commit_edit_settings = true) }
    }

    override suspend fun editLocalSettings(block: suspend AdminEditScope.() -> Unit) = editSettings(myNodeNum, block)

    /** Binds the [AdminEditScope] operations to a fixed destination, delegating to this controller's set* methods. */
    private inner class EditSettingsSession(private val destNum: Int) : AdminEditScope {
        override suspend fun setOwner(user: User) = setOwner(destNum, user, commandSender.generatePacketId())

        override suspend fun setConfig(config: Config) = setConfig(destNum, config, commandSender.generatePacketId())

        override suspend fun setModuleConfig(config: ModuleConfig) =
            setModuleConfig(destNum, config, commandSender.generatePacketId())

        // Unlike the one-shot setRemoteChannel, a transactional channel write does NOT mirror to the local cache per
        // slot: importChannelSet owns the cache and writes it once after commit (replaceAllSettings), so an import
        // interrupted before commit leaves the local channel cache untouched. (Firmware still writes each set_channel
        // into its in-memory channel table on arrival; only disk persist/reload/reboot is deferred to commit.)
        override suspend fun setChannel(channel: Channel) =
            commandSender.sendAdmin(destNum) { AdminMessage(set_channel = channel) }

        override suspend fun setFixedPosition(position: Position) =
            this@AdminControllerImpl.setFixedPosition(destNum, position)
    }

    private companion object {
        private const val DEFAULT_DELAY_SECONDS = 5
    }
}
