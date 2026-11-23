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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.model.HamName
import org.meshtastic.core.model.Position
import org.meshtastic.core.repository.AdminController
import org.meshtastic.core.repository.AdminEditScope
import org.meshtastic.core.repository.EditSettingsTransactionException
import org.meshtastic.core.repository.LocalNodeUnavailableException
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.PacketQueueRejectedException
import org.meshtastic.core.repository.REMOTE_READ_TIMEOUT
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.toFixedPositionProto
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Channel
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceConnectionStatus
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.HamParameters
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.OTAMode
import org.meshtastic.proto.Routing
import org.meshtastic.proto.User
import org.meshtastic.sdk.AdminApi
import org.meshtastic.sdk.AdminEdit
import org.meshtastic.sdk.AdminResult
import org.meshtastic.sdk.ChannelIndex
import org.meshtastic.sdk.NodeId
import org.meshtastic.sdk.onSuccess
import kotlin.time.Duration.Companion.seconds

internal fun editSettingsFailureMessage(result: AdminResult<*>): String =
    "Device rejected or did not answer the edit-settings transaction ($result)"

/**
 * [AdminController] implementation: local/remote configuration, channels, owner, device lifecycle, and the
 * [editSettings] transaction.
 *
 * Focused collaborator of [RadioControllerImpl]. Every command runs on the active session's SDK admin API. An
 * acknowledged write to the local node reaches the app's caches through the SDK session events (`ConfigChanged`,
 * `ChannelsChanged`, `NodeChanged`), so this class projects only what the SDK does not report: a remote node's owner,
 * ham mode, and fixed positions. The [editSettings] transaction stages those projections until the SDK reports the
 * commit applied, so a failed edit cannot publish settings the device did not commit.
 */
@Suppress("TooManyFunctions")
internal class AdminControllerImpl(
    private val nodeManager: NodeManager,
    private val radioConfigRepository: RadioConfigRepository,
    private val radio: RadioInterfaceService,
    private val sessionAdmin: SessionAdmin,
    private val heardOnCurrentLoraRefresher: HeardOnCurrentLoraRefresher,
) : AdminController {

    private fun isLocal(destNum: Int): Boolean = destNum == nodeManager.myNodeNum.value

    /** Runs [command] on [destNum] through the session's SDK admin API; a remote read waits [REMOTE_READ_TIMEOUT]. */
    private suspend fun <T> onNode(
        destNum: Int,
        read: Boolean = false,
        command: suspend AdminApi.() -> AdminResult<T>,
    ): AdminResult<T> {
        val local = isLocal(destNum)
        return sessionAdmin.run {
            val target =
                when {
                    local -> this
                    read -> forNode(NodeId(destNum)).withRpcTimeout(REMOTE_READ_TIMEOUT)
                    else -> forNode(NodeId(destNum))
                }
            target.command()
        }
    }

    /** Starts [command] on [destNum] without waiting for its answer; see [SessionAdmin.launch]. */
    private fun launchOnNode(operation: String, destNum: Int, command: suspend AdminApi.() -> AdminResult<*>) {
        val local = isLocal(destNum)
        sessionAdmin.launch(operation) { (if (local) this else forNode(NodeId(destNum))).command() }
    }

    // ── Owner ───────────────────────────────────────────────────────────────

    override suspend fun setOwner(destNum: Int, user: User): AdminResult<Unit> {
        val local = isLocal(destNum)
        return onNode(destNum) { setOwner(user) }
            .onSuccess { if (!local) nodeManager.handleReceivedUser(destNum, user) }
    }

    override suspend fun getOwner(destNum: Int): AdminResult<User> = onNode(destNum, read = true) { getOwner() }

    override suspend fun setHamMode(destNum: Int, hamParameters: HamParameters): AdminResult<Unit> {
        if (!isLocal(destNum)) {
            Logger.w { "Ignoring setHamMode for node $destNum — ham onboarding targets the local node only" }
            return AdminResult.Failed(Routing.Error.BAD_REQUEST)
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
        return onNode(destNum) { setHamMode(params) }
            .onSuccess {
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
    }

    // ── Configuration ─────────────────────────────────────────────────────────

    override suspend fun setLocalConfig(config: Config) {
        val movesSlot = heardOnCurrentLoraRefresher.movesSlot(config)
        sessionAdmin.launch(
            "Set config",
            onResult = { result ->
                if (movesSlot && result is AdminResult.Success) heardOnCurrentLoraRefresher.afterAcknowledgedWrite()
            },
        ) {
            setConfig(config)
        }
    }

    override fun setDisplayMirror(enabled: Boolean) {
        launchLocal("Display mirror") { setDisplayMirror(enabled) }
    }

    override fun requestDisplayFrame() {
        launchLocal("Display frame") { requestDisplayFrame() }
    }

    /** Starts a local-only [command]; with no radio session there is no display to drive, so it is dropped. */
    private fun launchLocal(operation: String, command: suspend AdminApi.() -> AdminResult<*>) {
        try {
            sessionAdmin.launch(operation, command = command)
        } catch (e: PacketQueueRejectedException) {
            Logger.d(e) { "$operation dropped without a radio session" }
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
        try {
            sessionAdmin.launch("Input event") { sendInputEvent(inputEvent) }
        } catch (e: PacketQueueRejectedException) {
            Logger.d(e) { "Input event dropped without a radio session" }
        }
    }

    override suspend fun setConfig(destNum: Int, config: Config): AdminResult<Unit> {
        if (!isLocal(destNum)) return onNode(destNum) { setConfig(config) }
        val movesSlot = heardOnCurrentLoraRefresher.movesSlot(config)
        return onNode(destNum) { setConfig(config) }
            .onSuccess { if (movesSlot) heardOnCurrentLoraRefresher.afterAcknowledgedWrite() }
    }

    override suspend fun getConfig(destNum: Int, configType: Int): AdminResult<Config> {
        val type = AdminMessage.ConfigType.fromValue(configType) ?: return AdminResult.Failed(Routing.Error.BAD_REQUEST)
        return onNode(destNum, read = true) { getConfig(type) }
    }

    override suspend fun setModuleConfig(destNum: Int, config: ModuleConfig): AdminResult<Unit> =
        onNode(destNum) { setModuleConfig(config) }

    override suspend fun getModuleConfig(destNum: Int, moduleConfigType: Int): AdminResult<ModuleConfig> {
        val type =
            AdminMessage.ModuleConfigType.fromValue(moduleConfigType)
                ?: return AdminResult.Failed(Routing.Error.BAD_REQUEST)
        return onNode(destNum, read = true) { getModuleConfig(type) }
    }

    // ── Channels ────────────────────────────────────────────────────────────

    override suspend fun setLocalChannel(channel: Channel) {
        val movesSlot = heardOnCurrentLoraRefresher.movesSlot(channel)
        sessionAdmin.launch(
            "Set channel",
            onResult = { result ->
                if (movesSlot && result is AdminResult.Success) heardOnCurrentLoraRefresher.afterAcknowledgedWrite()
            },
        ) {
            setChannel(channel)
        }
    }

    override suspend fun setRemoteChannel(destNum: Int, channel: Channel): AdminResult<Unit> {
        if (!isLocal(destNum)) return onNode(destNum) { setChannel(channel) }
        val movesSlot = heardOnCurrentLoraRefresher.movesSlot(channel)
        return onNode(destNum) { setChannel(channel) }
            .onSuccess { if (movesSlot) heardOnCurrentLoraRefresher.afterAcknowledgedWrite() }
    }

    override suspend fun getChannel(destNum: Int, index: Int): AdminResult<Channel> =
        onNode(destNum, read = true) { getChannel(ChannelIndex(index)) }

    // ── Ringtone & Canned Messages ─────────────────────────────────────────

    override suspend fun setRingtone(destNum: Int, ringtone: String) {
        launchOnNode("Set ringtone", destNum) { setRingtone(ringtone) }
    }

    override suspend fun playRingtone(destNum: Int) {
        launchOnNode("Play ringtone", destNum) { playRingtone() }
    }

    override suspend fun getRingtone(destNum: Int): AdminResult<String> = onNode(destNum, read = true) { getRingtone() }

    override suspend fun setCannedMessages(destNum: Int, messages: String) {
        launchOnNode("Set canned messages", destNum) { setCannedMessages(messages) }
    }

    override suspend fun setTime(destNum: Int): AdminResult<Unit> {
        Logger.i { "Set time requested for node $destNum" }
        return onNode(destNum) { setTime() }
    }

    override suspend fun getCannedMessages(destNum: Int): AdminResult<String> =
        onNode(destNum, read = true) { getCannedMessages() }

    // ── Position ────────────────────────────────────────────────────────────

    override suspend fun setFixedPosition(destNum: Int, position: Position) {
        if (position.isFixedPositionRemoval()) {
            launchOnNode("Remove fixed position", destNum) { removeFixedPosition() }
            return
        }
        val myNum = nodeManager.myNodeNum.value ?: throw LocalNodeUnavailableException("Fixed position")
        val protoPosition = position.toFixedPositionProto()
        launchOnNode("Set fixed position", destNum) { setFixedPosition(protoPosition) }
        nodeManager.handleReceivedPosition(destNum, myNum, protoPosition, nowMillis)
    }

    // ── Device Status & Lifecycle ───────────────────────────────────────────

    override suspend fun getDeviceMetadata(destNum: Int): AdminResult<DeviceMetadata> =
        onNode(destNum, read = true) { getDeviceMetadata() }

    override suspend fun getDeviceConnectionStatus(destNum: Int): AdminResult<DeviceConnectionStatus> =
        onNode(destNum, read = true) { getDeviceConnectionStatus() }

    override suspend fun reboot(destNum: Int): AdminResult<Unit> {
        Logger.i { "Reboot requested for node $destNum" }
        return onNode(destNum) { reboot(DEFAULT_DELAY_SECONDS.seconds) }
    }

    override suspend fun rebootToDfu(nodeNum: Int): AdminResult<Unit> = onNode(nodeNum) { enterDfuMode() }

    override suspend fun requestRebootOta(destNum: Int, mode: Int, hash: ByteArray?) {
        val otaMode = OTAMode.fromValue(mode) ?: OTAMode.NO_REBOOT_OTA
        val otaEvent =
            AdminMessage.OTAEvent.Builder()
                .also { wb ->
                    wb.reboot_ota_mode = otaMode
                    wb.ota_hash = hash?.toByteString() ?: ByteString.EMPTY
                }
                .build()
        launchOnNode("OTA request", destNum) { otaRequest(otaEvent) }
    }

    override suspend fun shutdown(destNum: Int): AdminResult<Unit> =
        onNode(destNum) { shutdown(DEFAULT_DELAY_SECONDS.seconds) }

    override suspend fun factoryReset(destNum: Int): AdminResult<Unit> {
        Logger.i { "Factory reset requested for node $destNum" }
        return onNode(destNum) { factoryReset(preserveBleBonds = false) }
    }

    override suspend fun nodedbReset(destNum: Int, preserveFavorites: Boolean): AdminResult<Unit> =
        onNode(destNum) { nodeDbReset(preserveFavorites) }

    // ── Edit Settings (transactional) ───────────────────────────────────────

    /**
     * Runs [block] in the active session's SDK `editSettings`, addressed with `forNode` unless [destNum] is the local
     * node. The SDK owns begin, commit and the commit-on-cancellation rule, and reports a local commit's config and
     * owner as session events. The projections the SDK does not report are applied only once it reports the commit
     * applied, and only while the same session still owns the database; a local BLE commit drops the link, and the next
     * session's config download carries the committed values instead.
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

    /** Forwards [AdminEditScope] writes to the SDK transaction and stages the projections the SDK does not report. */
    private inner class EditSettingsScope(
        private val edit: AdminEdit,
        private val destNum: Int,
        private val isLocalDestination: Boolean,
        private val projections: StagedProjections,
    ) : AdminEditScope {
        override suspend fun setOwner(user: User) {
            edit.setOwner(user)
            if (!isLocalDestination) projections.stage { nodeManager.handleReceivedUser(destNum, user) }
        }

        override suspend fun setConfig(config: Config) = edit.setConfig(config)

        override suspend fun setModuleConfig(config: ModuleConfig) = edit.setModuleConfig(config)

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
