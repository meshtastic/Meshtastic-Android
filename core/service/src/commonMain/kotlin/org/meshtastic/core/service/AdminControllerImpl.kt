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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.model.Position
import org.meshtastic.core.repository.AdminController
import org.meshtastic.core.repository.AdminEditScope
import org.meshtastic.core.repository.AwaitedSendStatus
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.ConnectionStateProvider
import org.meshtastic.core.repository.EditSettingsTransactionException
import org.meshtastic.core.repository.LocalNodeUnavailableException
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.toFixedPositionAdminMessage
import org.meshtastic.core.repository.toFixedPositionProto
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Channel
import org.meshtastic.proto.Config
import org.meshtastic.proto.HamParameters
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.OTAMode
import org.meshtastic.proto.User
import kotlin.time.Duration.Companion.seconds

/**
 * Additional window for observing local transport departure after a dispatched commit loses its response. The
 * surrounding NonCancellable finalization can take longer because [CommandSender.sendAdminAwaitResult] first waits
 * behind older FIFO entries and then owns its radio-response window.
 */
internal val COMMIT_DEPARTURE_TIMEOUT = 5.seconds

internal fun editSettingsBoundaryFailureMessage(boundary: String): String =
    "Device rejected or timed out while sending edit-settings $boundary"

internal fun editSettingsCommitFailureMessage(status: AwaitedSendStatus, dispatched: Boolean): String =
    "Device rejected or timed out while sending edit-settings commit (status=$status, dispatched=$dispatched)"

/**
 * [AdminController] implementation: local/remote configuration, channels, owner, device lifecycle, and the
 * [editSettings] transaction.
 *
 * Focused collaborator of [RadioControllerImpl]. Builds [AdminMessage] protos directly and delegates to [CommandSender]
 * for transport, mirroring the SDK's `AdminApiImpl` pattern. Standalone writes may update local caches optimistically;
 * transactional writes stage their local projections until commit acceptance so failed edits cannot publish settings
 * the device did not commit. The device remains the source of truth and re-sends its full config on every connection.
 */
@Suppress("TooManyFunctions")
internal class AdminControllerImpl(
    private val commandSender: CommandSender,
    private val nodeManager: NodeManager,
    private val radioConfigRepository: RadioConfigRepository,
    private val connectionStateProvider: ConnectionStateProvider,
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
        val isLocalDestination = destNum == nodeManager.myNodeNum.value
        requireBeginBoundaryAccepted(destNum)

        // Firmware has no abort boundary. Preserve any block failure, including cancellation, only long enough to
        // attempt the commit that closes the accepted session; the original failure is restored below. Local cache
        // projections are staged by the session and become visible only after both the block and commit succeed.
        val session = EditSettingsSession(destNum, isLocalDestination)
        val blockResult = runCatching { session.block() }
        val commitResult = runCatching {
            withContext(NonCancellable) { requireCommitBoundaryAccepted(destNum, isLocalDestination) }
        }

        blockResult.exceptionOrNull()?.let { blockFailure ->
            // Coroutine stack-trace recovery may rebuild the same exception across the NonCancellable boundary.
            commitResult
                .exceptionOrNull()
                ?.takeUnless { it.containsCauseIdentity(blockFailure) }
                ?.let(blockFailure::addSuppressed)
            throw blockFailure
        }
        commitResult.getOrThrow()
        session.applyStagedProjections()
    }

    private fun Throwable.containsCauseIdentity(expected: Throwable): Boolean {
        val visited = mutableListOf<Throwable>()
        var current: Throwable? = this
        var found = false
        while (current != null) {
            val candidate = current
            if (visited.any { it === candidate }) {
                current = null
            } else {
                found = candidate === expected
                visited += candidate
                current = candidate.cause.takeUnless { found }
            }
        }
        return found
    }

    override suspend fun editLocalSettings(block: suspend AdminEditScope.() -> Unit) {
        val localNodeNum = nodeManager.myNodeNum.value ?: throw LocalNodeUnavailableException("Edit settings")
        editSettings(localNodeNum, block)
    }

    /** Binds [AdminEditScope] writes to one destination and stages optimistic projections until commit acceptance. */
    private inner class EditSettingsSession(private val destNum: Int, private val isLocalDestination: Boolean) :
        AdminEditScope {
        private val projectionLock = SynchronizedObject()
        private val stagedProjections = mutableListOf<suspend () -> Unit>()

        override suspend fun setOwner(user: User) {
            commandSender.sendAdmin(destNum, commandSender.generatePacketId()) { AdminMessage(set_owner = user) }
            stageProjection { nodeManager.handleReceivedUser(destNum, user) }
        }

        override suspend fun setConfig(config: Config) {
            commandSender.sendAdmin(destNum, commandSender.generatePacketId()) { AdminMessage(set_config = config) }
            if (isLocalDestination) stageProjection { radioConfigRepository.setLocalConfig(config) }
        }

        override suspend fun setModuleConfig(config: ModuleConfig) {
            commandSender.sendAdmin(destNum, commandSender.generatePacketId()) {
                AdminMessage(set_module_config = config)
            }
            if (isLocalDestination) {
                stageProjection {
                    config.statusmessage?.let { status -> nodeManager.updateNodeStatus(destNum, status.node_status) }
                    radioConfigRepository.setLocalModuleConfig(config)
                }
            }
        }

        // Unlike one-shot writes, a transaction can replace several slots. This scope cannot infer the complete target
        // set, so the operation adding channel writes must reconcile that set after the transaction; mirroring slots
        // here could expose a partial cache if a later write or commit fails.
        override suspend fun setChannel(channel: Channel) =
            commandSender.sendAdmin(destNum, commandSender.generatePacketId()) { AdminMessage(set_channel = channel) }

        override suspend fun setFixedPosition(position: Position) {
            val removesFixedPosition = position.isFixedPositionRemoval()
            val projectionNodeNum =
                if (removesFixedPosition) {
                    null
                } else {
                    nodeManager.myNodeNum.value ?: throw LocalNodeUnavailableException("Fixed position")
                }
            commandSender.sendAdmin(destNum, commandSender.generatePacketId()) {
                position.toFixedPositionAdminMessage()
            }
            if (projectionNodeNum != null) {
                val protoPosition = position.toFixedPositionProto()
                stageProjection {
                    nodeManager.handleReceivedPosition(destNum, projectionNodeNum, protoPosition, nowMillis)
                }
            }
        }

        private fun stageProjection(block: suspend () -> Unit) {
            synchronized(projectionLock) { stagedProjections += block }
        }

        suspend fun applyStagedProjections() = withContext(NonCancellable) {
            val projections =
                synchronized(projectionLock) { stagedProjections.toList().also { stagedProjections.clear() } }
            projections.forEach { projection -> applyProjection(projection) }
        }

        @Suppress("TooGenericExceptionCaught")
        private suspend fun applyProjection(projection: suspend () -> Unit) {
            try {
                projection()
            } catch (e: Exception) {
                Logger.w(e) { "Local edit-settings projection failed after device commit" }
            }
        }
    }

    /** Requires the begin boundary to be admitted before any transactional settings writes are issued. */
    private suspend fun requireBeginBoundaryAccepted(destNum: Int) {
        if (!commandSender.sendAdminAwait(destNum) { AdminMessage(begin_edit_settings = true) }) {
            throw EditSettingsTransactionException(editSettingsBoundaryFailureMessage("begin"))
        }
    }

    /**
     * Applies commit back-pressure. Ordinary writes are already queued FIFO by [CommandSender], so waiting for the
     * commit's queue acceptance keeps the caller suspended until the sender has processed every preceding write. This
     * prevents a rebooting transport write from overtaking an uncommitted settings transaction.
     */
    private suspend fun requireCommitBoundaryAccepted(destNum: Int, isLocalDestination: Boolean) {
        val result = commandSender.sendAdminAwaitResult(destNum) { AdminMessage(commit_edit_settings = true) }
        val uncertainAfterDispatch =
            isLocalDestination &&
                result.dispatched &&
                result.departureEpochAtDispatch != null &&
                (result.status == AwaitedSendStatus.TRANSPORT_STOPPED || result.status == AwaitedSendStatus.TIMED_OUT)
        val departedLifecycle =
            if (uncertainAfterDispatch) {
                withTimeoutOrNull(COMMIT_DEPARTURE_TIMEOUT) {
                    connectionStateProvider.connectionLifecycle.first {
                        it.epochs.departures > checkNotNull(result.departureEpochAtDispatch)
                    }
                }
            } else {
                null
            }
        // A dispatched local commit is durable once a post-dispatch departure is observed. Firmware may move the
        // connection through Disconnected, Connecting, or DeviceSleep while rebooting.
        val committedBeforeLocalDeparture = uncertainAfterDispatch && departedLifecycle != null

        if (!result.accepted && !committedBeforeLocalDeparture) {
            throw EditSettingsTransactionException(editSettingsCommitFailureMessage(result.status, result.dispatched))
        }
        if (committedBeforeLocalDeparture) {
            Logger.i { "Edit-settings commit dispatched before expected local transport departure" }
        }
    }

    private companion object {
        private const val DEFAULT_DELAY_SECONDS = 5
    }
}
