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
package org.meshtastic.core.testing

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.repository.AdminEditScope
import org.meshtastic.core.repository.ConnectionStateHolder
import org.meshtastic.core.repository.EditSettingsTransactionException
import org.meshtastic.core.repository.NodeRequest
import org.meshtastic.core.repository.PacketQueueRejectedException
import org.meshtastic.core.repository.RadioController
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceConnectionStatus
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.HamParameters
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.SharedContact
import org.meshtastic.proto.User
import org.meshtastic.sdk.AdminResult
import org.meshtastic.sdk.ConnectionState

/**
 * A test double for [RadioController] that provides a no-op implementation and tracks calls for assertions in tests.
 */
@Suppress("TooManyFunctions", "EmptyFunctionBlock")
class FakeRadioController :
    BaseFake(),
    RadioController {

    sealed interface SettingsOperation {
        data class SetConfig(val config: Config) : SettingsOperation

        data class SetChannel(val channel: Channel) : SettingsOperation
    }

    /** Canonical app-level connection state, mirroring [ServiceRepository][connectionState] semantics. */
    private val connectionStateHolder = ConnectionStateHolder()
    override val connectionLifecycle = connectionStateHolder.connectionLifecycle
    override val connectionState = connectionStateHolder.connectionState

    private val _clientNotification = mutableStateFlow<ClientNotification?>(null)
    override val clientNotification: StateFlow<ClientNotification?> = _clientNotification

    val sentPackets = mutableListOf<DataPacket>()
    val favoritedNodes = mutableListOf<Int>()
    val sentSharedContacts = mutableListOf<Int>()

    /** One local or admin configuration write. Local writes have no destination. */
    data class ConfigWrite(val destination: Int?, val config: Config)

    /** One module configuration write. Local writes have no destination. */
    data class ModuleConfigWrite(val destination: Int?, val config: ModuleConfig)

    /** One local or admin channel write. Local writes have no destination. */
    data class ChannelWrite(val destination: Int?, val channel: Channel)

    /** One owner write. Local writes have no destination. */
    data class OwnerWrite(val destination: Int?, val user: User)

    /** Every local or admin configuration write, preserving destination and call order together. */
    val configWrites = mutableListOf<ConfigWrite>()

    /** Local configuration payloads in call order. Prefer [configWrites] when destination identity matters. */
    val localConfigs: List<Config>
        get() = configWrites.filter { it.destination == null }.map(ConfigWrite::config)

    val lastLocalConfig: Config?
        get() = configWrites.lastOrNull { it.destination == null }?.config

    /** Every local or admin channel write, preserving destination and call order together. */
    val channelWrites = mutableListOf<ChannelWrite>()

    /** Local channel payloads in call order. Prefer [channelWrites] when destination identity matters. */
    val localChannels: List<Channel>
        get() = channelWrites.filter { it.destination == null }.map(ChannelWrite::channel)

    /** Every config and channel write, in their shared call order. */
    val settingsOperations = mutableListOf<SettingsOperation>()

    /** Every [setFixedPosition] call, in order. */
    val fixedPositions = mutableListOf<Position>()

    /** Every module configuration write, preserving destination and call order together. */
    val moduleConfigWrites = mutableListOf<ModuleConfigWrite>()

    /** Module configuration payloads in call order. Prefer [moduleConfigWrites] when destination identity matters. */
    val allModuleConfigs: List<ModuleConfig>
        get() = moduleConfigWrites.map(ModuleConfigWrite::config)

    /** Local module configuration payloads in call order. Prefer [moduleConfigWrites] when destination matters. */
    val localModuleConfigs: List<ModuleConfig>
        get() = moduleConfigWrites.filter { it.destination == null }.map(ModuleConfigWrite::config)

    /** Destination node for every module configuration write, in order. Local writes have no destination. */
    val moduleConfigDestinations: List<Int?>
        get() = moduleConfigWrites.map(ModuleConfigWrite::destination)

    /** Every local or admin owner write, preserving destination and call order together. */
    val ownerWrites = mutableListOf<OwnerWrite>()

    /**
     * One admin request as it reached the controller: which request, for which node, carrying what, under which packet
     * ID. [packetId] is null for requests whose controller method takes none.
     */
    data class AdminRequest(val kind: String, val destNum: Int, val payload: Any?, val packetId: Int?)

    /** Every admin get/set request in call order, including those with no dedicated list above. */
    val adminRequests = mutableListOf<AdminRequest>()

    /** The value [generatePacketId] returns. */
    var nextPacketId: Int = 1

    /** Destination node for every edit transaction, in order. Local edits use the fake's sentinel value of zero. */
    val editSettingsDestinations = mutableListOf<Int>()

    /** High-level admin operation order, including edit transaction boundaries. */
    val adminOperations = mutableListOf<String>()

    /** When true, an edit transaction fails before the begin boundary is recorded. */
    var failEditSettingsBegin: Boolean = false

    /** Test hook invoked after an edit transaction commits. */
    var onEditSettingsCommitted: suspend () -> Unit = {}

    /** Test hook invoked before a fixed-position write is recorded. */
    var onSetFixedPosition: suspend (Int, Position) -> Unit = { _, _ -> }

    /** Test hook invoked after a standalone module-config write. */
    var onStandaloneModuleConfig: suspend (ModuleConfig) -> Unit = {}

    /** Test hook invoked after a standalone general-config write. */
    var onStandaloneConfig: suspend (Config) -> Unit = {}

    var throwOnSend: Boolean = false

    /** Deterministic suspension/fault hook invoked before a packet is recorded as sent. */
    var onSendMessage: suspend (DataPacket) -> Unit = {}

    /** When true, [setLocalConfig] throws — simulates the radio link dropping mid config write. */
    var throwOnSetLocalConfig: Boolean = false

    /** Number of upcoming local-config writes to reject through the production queue-admission failure contract. */
    var rejectLocalConfigWritesRemaining: Int = 0

    /** Number of upcoming local-channel writes to reject through the production queue-admission failure contract. */
    var rejectLocalChannelWritesRemaining: Int = 0

    /** Failure thrown by [requestNeighborInfo], when set. */
    var requestNeighborInfoFailure: Exception? = null

    /** Failure thrown by [setFavorite], when set. */
    var setFavoriteFailure: Exception? = null

    /** Failure thrown by [sendSharedContact], when set. */
    var sendSharedContactFailure: Exception? = null

    /** Destinations of [requestNeighborInfo] calls, in order. */
    val neighborInfoRequests = mutableListOf<Int>()

    /**
     * When set, a channel write throws after this many total local/admin [channelWrites] — simulates mid-write failure.
     */
    var failChannelWriteAfter: Int? = null
    var lastSetDeviceAddress: String? = null
    var lastSetOwnerUser: User? = null
    var editSettingsCalled = false
    var startProvideLocationCalled = false
    var stopProvideLocationCalled = false
    var gattCacheInvalidationRequested = false
        private set

    private val _sessionGeneration = mutableStateFlow(0L)
    override val sessionGeneration: StateFlow<Long> = _sessionGeneration

    private val deviceSwitchMutex = Mutex()

    /** Test hook that can suspend while a conditional local-configuration restore owns device selection. */
    var beforeRestoreLocalConfiguration: suspend () -> Unit = {}

    /** Device identity currently owned by the conditional restore seam. */
    var selectedDeviceAddress: String? = null

    /** Deterministic test hook invoked inside every [requestRebootOta] call with the request parameters. */
    var onRequestRebootOta: suspend (destNum: Int, mode: Int, hash: ByteArray?) -> Unit = { _, _, _ -> }

    init {
        registerResetAction {
            connectionStateHolder.reset()
            sentPackets.clear()
            favoritedNodes.clear()
            sentSharedContacts.clear()
            factoryResetCalls.clear()
            displayMirrorCalls.clear()
            displayFrameRequests = 0
            inputEvents.clear()
            configWrites.clear()
            channelWrites.clear()
            settingsOperations.clear()
            fixedPositions.clear()
            moduleConfigWrites.clear()
            ownerWrites.clear()
            adminRequests.clear()
            nextPacketId = 1
            editSettingsDestinations.clear()
            adminOperations.clear()
            failEditSettingsBegin = false
            onEditSettingsCommitted = {}
            onSetFixedPosition = { _, _ -> }
            onStandaloneModuleConfig = {}
            onStandaloneConfig = {}
            throwOnSend = false
            onSendMessage = {}
            throwOnSetLocalConfig = false
            rejectLocalConfigWritesRemaining = 0
            rejectLocalChannelWritesRemaining = 0
            requestNeighborInfoFailure = null
            setFavoriteFailure = null
            sendSharedContactFailure = null
            neighborInfoRequests.clear()
            failChannelWriteAfter = null
            lastSetDeviceAddress = null
            lastSetOwnerUser = null
            editSettingsCalled = false
            startProvideLocationCalled = false
            stopProvideLocationCalled = false
            onRequestRebootOta = { _, _, _ -> }
            gattCacheInvalidationRequested = false
            beforeRestoreLocalConfiguration = {}
            selectedDeviceAddress = null
            _sessionGeneration.value = 0L
        }
    }

    override suspend fun sendMessage(packet: DataPacket) {
        onSendMessage(packet)
        if (throwOnSend) error("Fake send failure")
        sentPackets.add(packet)
    }

    override fun clearClientNotification() {
        _clientNotification.value = null
    }

    override suspend fun setFavorite(nodeNum: Int, favorite: Boolean) {
        setFavoriteFailure?.let { throw it }
        if (favorite) favoritedNodes.add(nodeNum) else favoritedNodes.remove(nodeNum)
    }

    override suspend fun sendSharedContact(nodeNum: Int): Boolean {
        sendSharedContactFailure?.let { throw it }
        sentSharedContacts.add(nodeNum)
        return true
    }

    override suspend fun setIgnored(nodeNum: Int, ignored: Boolean) {}

    override suspend fun toggleMuted(nodeNum: Int) {}

    override suspend fun sendReaction(emoji: String, replyId: Int, contactKey: String) {}

    override suspend fun importContact(contact: org.meshtastic.proto.SharedContact) {}

    override suspend fun refreshMetadata(destNum: Int): NodeRequest = NodeRequest.NONE

    override fun newRemoteShellSessionId(): Int? = null

    override suspend fun sendRemoteShell(destNum: Int, frame: org.meshtastic.proto.RemoteShell) {}

    val displayMirrorCalls = mutableListOf<Boolean>()
    var displayFrameRequests = 0
    val inputEvents = mutableListOf<Int>()

    override fun setDisplayMirror(enabled: Boolean) {
        displayMirrorCalls += enabled
    }

    override fun requestDisplayFrame() {
        displayFrameRequests++
    }

    override fun sendInputEvent(eventCode: Int, kbChar: Int, touchX: Int, touchY: Int) {
        inputEvents += eventCode
    }

    override suspend fun setLocalConfig(config: Config) {
        if (throwOnSetLocalConfig) error("Fake local config write failure")
        if (rejectLocalConfigWritesRemaining > 0) {
            rejectLocalConfigWritesRemaining--
            throw PacketQueueRejectedException("Local config")
        }
        configWrites.add(ConfigWrite(destination = null, config = config))
        settingsOperations.add(SettingsOperation.SetConfig(config))
    }

    override suspend fun setLocalChannel(channel: Channel) {
        if (rejectLocalChannelWritesRemaining > 0) {
            rejectLocalChannelWritesRemaining--
            throw PacketQueueRejectedException("Local channel")
        }
        failChannelWriteAfter?.let { if (channelWrites.size >= it) error("Fake channel write failure") }
        channelWrites.add(ChannelWrite(destination = null, channel = channel))
        settingsOperations.add(SettingsOperation.SetChannel(channel))
    }

    override suspend fun restoreLocalConfiguration(
        expectedDeviceAddress: String?,
        config: Config,
        primaryChannel: Channel?,
    ): Boolean = deviceSwitchMutex.withLock {
        if (expectedDeviceAddress == null || selectedDeviceAddress != expectedDeviceAddress) return@withLock false
        beforeRestoreLocalConfiguration()
        primaryChannel?.let { channel -> setLocalChannel(channel) }
        setLocalConfig(config)
        true
    }

    override suspend fun setOwner(destNum: Int, user: User): AdminResult<Unit> {
        lastSetOwnerUser = user
        ownerWrites.add(OwnerWrite(destination = destNum.takeUnless { it == 0 }, user = user))
        adminOperations.add("owner")
        recordAdminRequest("setOwner", destNum, user, packetId = null)
        return writeResult
    }

    override suspend fun setHamMode(destNum: Int, hamParameters: HamParameters): AdminResult<Unit> {
        recordAdminRequest("setHamMode", destNum, hamParameters, packetId = null)
        return writeResult
    }

    override suspend fun setConfig(destNum: Int, config: Config): AdminResult<Unit> {
        recordAdminRequest("setConfig", destNum, config, packetId = null)
        recordConfigWrite(destNum, config, invokeStandaloneHook = true)
        return writeResult
    }

    override suspend fun setModuleConfig(destNum: Int, config: ModuleConfig): AdminResult<Unit> {
        recordAdminRequest("setModuleConfig", destNum, config, packetId = null)
        recordModuleConfigWrite(destNum, config, invokeStandaloneHook = true)
        return writeResult
    }

    private fun recordAdminRequest(kind: String, destNum: Int, payload: Any?, packetId: Int?) {
        adminRequests.add(AdminRequest(kind, destNum, payload, packetId))
    }

    /** What every admin write answers. */
    var writeResult: AdminResult<Unit> = AdminResult.Success(Unit)

    /** Answers for reads, keyed by request kind (`getOwner`, `getConfig`, ...); a read without one times out. */
    val readResults = mutableMapOf<String, AdminResult<*>>()

    @Suppress("UNCHECKED_CAST")
    private fun <T> read(kind: String, destNum: Int, payload: Any?): AdminResult<T> {
        recordAdminRequest(kind, destNum, payload, packetId = null)
        return (readResults[kind] ?: AdminResult.Timeout) as AdminResult<T>
    }

    private suspend fun recordConfigWrite(destNum: Int, config: Config, invokeStandaloneHook: Boolean) {
        configWrites.add(ConfigWrite(destination = destNum.takeUnless { it == 0 }, config = config))
        settingsOperations.add(SettingsOperation.SetConfig(config))
        adminOperations.add("config:update")
        if (invokeStandaloneHook) onStandaloneConfig(config)
    }

    private suspend fun recordModuleConfigWrite(destNum: Int, config: ModuleConfig, invokeStandaloneHook: Boolean) {
        moduleConfigWrites.add(ModuleConfigWrite(destination = destNum.takeUnless { it == 0 }, config = config))
        adminOperations.add("module:update")
        if (invokeStandaloneHook) onStandaloneModuleConfig(config)
    }

    override suspend fun setRemoteChannel(destNum: Int, channel: Channel): AdminResult<Unit> {
        failChannelWriteAfter?.let { if (channelWrites.size >= it) error("Fake channel write failure") }
        channelWrites.add(ChannelWrite(destination = destNum.takeUnless { it == 0 }, channel = channel))
        settingsOperations.add(SettingsOperation.SetChannel(channel))
        adminOperations.add("channel:${channel.index}")
        recordAdminRequest("setRemoteChannel", destNum, channel, packetId = null)
        return writeResult
    }

    override suspend fun setFixedPosition(destNum: Int, position: Position) {
        recordAdminRequest("setFixedPosition", destNum, position, packetId = null)
        onSetFixedPosition(destNum, position)
        fixedPositions.add(position)
        adminOperations.add("fixed-position")
    }

    override suspend fun setRingtone(destNum: Int, ringtone: String) {
        recordAdminRequest("setRingtone", destNum, ringtone, packetId = null)
    }

    override suspend fun playRingtone(destNum: Int) {
        recordAdminRequest("playRingtone", destNum, payload = null, packetId = null)
    }

    override suspend fun setCannedMessages(destNum: Int, messages: String) {
        recordAdminRequest("setCannedMessages", destNum, messages, packetId = null)
    }

    override suspend fun setTime(destNum: Int): AdminResult<Unit> = writeResult

    /** What the remote node-DB writes return, standing in for the remote node's acknowledgement. */
    var remoteNodeDbAck: Boolean = true

    /** Answers one remote node-DB write by its recorded kind, in place of [remoteNodeDbAck]. */
    var remoteNodeDbAckFor: (suspend (kind: String) -> Boolean)? = null

    private suspend fun remoteAck(kind: String): Boolean = remoteNodeDbAckFor?.invoke(kind) ?: remoteNodeDbAck

    override suspend fun addRemoteContact(destNum: Int, contact: SharedContact): Boolean {
        recordAdminRequest("addRemoteContact", destNum, contact, packetId = null)
        return remoteAck("addRemoteContact")
    }

    override suspend fun setRemoteFavorite(destNum: Int, nodeNum: Int, favorite: Boolean): Boolean {
        val kind = if (favorite) "setRemoteFavorite" else "removeRemoteFavorite"
        recordAdminRequest(kind, destNum, nodeNum, packetId = null)
        return remoteAck(kind)
    }

    override suspend fun setRemoteIgnored(destNum: Int, nodeNum: Int, ignored: Boolean): Boolean {
        val kind = if (ignored) "setRemoteIgnored" else "removeRemoteIgnored"
        recordAdminRequest(kind, destNum, nodeNum, packetId = null)
        return remoteAck(kind)
    }

    override suspend fun removeRemoteNode(destNum: Int, nodeNum: Int): Boolean {
        recordAdminRequest("removeRemoteNode", destNum, nodeNum, packetId = null)
        return remoteAck("removeRemoteNode")
    }

    override suspend fun getOwner(destNum: Int): AdminResult<User> = read("getOwner", destNum, payload = null)

    override suspend fun getDeviceMetadata(destNum: Int): AdminResult<DeviceMetadata> =
        read("getDeviceMetadata", destNum, payload = null)

    override suspend fun getConfig(destNum: Int, configType: Int): AdminResult<Config> =
        read("getConfig", destNum, configType)

    override suspend fun getModuleConfig(destNum: Int, moduleConfigType: Int): AdminResult<ModuleConfig> =
        read("getModuleConfig", destNum, moduleConfigType)

    override suspend fun getChannel(destNum: Int, index: Int): AdminResult<Channel> = read("getChannel", destNum, index)

    override suspend fun getRingtone(destNum: Int): AdminResult<String> = read("getRingtone", destNum, payload = null)

    override suspend fun getCannedMessages(destNum: Int): AdminResult<String> =
        read("getCannedMessages", destNum, payload = null)

    override suspend fun getDeviceConnectionStatus(destNum: Int): AdminResult<DeviceConnectionStatus> =
        read("getDeviceConnectionStatus", destNum, payload = null)

    override suspend fun reboot(destNum: Int): AdminResult<Unit> = writeResult

    override suspend fun rebootToDfu(nodeNum: Int): AdminResult<Unit> = writeResult

    override suspend fun requestRebootOta(destNum: Int, mode: Int, hash: ByteArray?) {
        onRequestRebootOta(destNum, mode, hash)
    }

    override suspend fun shutdown(destNum: Int): AdminResult<Unit> = writeResult

    /** Destination node numbers of [factoryReset] calls, for tests asserting a reset was (or was not) sent. */
    val factoryResetCalls = mutableListOf<Int>()

    override suspend fun factoryReset(destNum: Int): AdminResult<Unit> {
        factoryResetCalls += destNum
        return writeResult
    }

    override suspend fun nodedbReset(destNum: Int, preserveFavorites: Boolean): AdminResult<Unit> = writeResult

    override suspend fun removeByNodenum(nodeNum: Int) {}

    override suspend fun requestPosition(destNum: Int, currentPosition: Position): NodeRequest = NodeRequest.NONE

    override suspend fun requestUserInfo(destNum: Int): NodeRequest = NodeRequest.NONE

    override suspend fun requestTraceroute(destNum: Int): NodeRequest = NodeRequest.NONE

    override suspend fun requestTelemetry(destNum: Int, type: TelemetryType): NodeRequest = NodeRequest.NONE

    override suspend fun requestNeighborInfo(destNum: Int): NodeRequest {
        neighborInfoRequests += destNum
        requestNeighborInfoFailure?.let { throw it }
        return NodeRequest.NONE
    }

    private fun Throwable.containsCauseIdentity(expected: Throwable): Boolean {
        val visited = mutableListOf<Throwable>()
        var current: Throwable? = this
        while (current != null && visited.none { it === current }) {
            if (current === expected) break
            visited += current
            current = current.cause
        }
        return current === expected
    }

    @Suppress("SuspendFunSwallowedCancellation") // mirrors production: the failure is held until the commit
    override suspend fun editSettings(destNum: Int, block: suspend AdminEditScope.() -> Unit) {
        editSettingsDestinations.add(destNum)
        editSettingsCalled = true
        if (failEditSettingsBegin) {
            throw EditSettingsTransactionException("Device rejected or timed out while sending edit-settings begin")
        }
        adminOperations.add("begin")
        val scope =
            object : AdminEditScope {
                override suspend fun setOwner(user: User) = this@FakeRadioController.setOwner(destNum, user).let {}

                override suspend fun setConfig(config: Config) =
                    recordConfigWrite(destNum, config, invokeStandaloneHook = false)

                override suspend fun setModuleConfig(config: ModuleConfig) =
                    recordModuleConfigWrite(destNum, config, invokeStandaloneHook = false)

                override suspend fun setChannel(channel: Channel) = setRemoteChannel(destNum, channel).let {}

                override suspend fun setFixedPosition(position: Position) =
                    this@FakeRadioController.setFixedPosition(destNum, position)
            }
        // Production has no firmware abort boundary: once begin is accepted, commit must still close the edit session
        // after a block failure. Preserve that failure while attempting the same non-cancellable commit boundary here.
        val blockResult = runCatching { scope.block() }
        val commitResult = runCatching {
            kotlinx.coroutines.withContext(NonCancellable) {
                adminOperations.add("commit")
                onEditSettingsCommitted()
            }
        }

        blockResult.exceptionOrNull()?.let { blockFailure ->
            commitResult
                .exceptionOrNull()
                ?.takeUnless { it.containsCauseIdentity(blockFailure) }
                ?.let(blockFailure::addSuppressed)
            throw blockFailure
        }
        commitResult.getOrThrow()
    }

    override suspend fun editLocalSettings(block: suspend AdminEditScope.() -> Unit) = editSettings(0, block)

    override fun generatePacketId(): Int = nextPacketId

    override fun startProvideLocation() {
        startProvideLocationCalled = true
    }

    override fun stopProvideLocation() {
        stopProvideLocationCalled = true
    }

    override suspend fun setDeviceAddress(address: String) = deviceSwitchMutex.withLock {
        selectedDeviceAddress = address
        lastSetDeviceAddress = address
    }

    override fun requestGattCacheInvalidationOnNextConnect() {
        gattCacheInvalidationRequested = true
    }

    // --- Helper methods for testing ---

    fun setConnectionState(state: ConnectionState) = connectionStateHolder.setConnectionState(state)

    fun setSessionGeneration(generation: Long) {
        _sessionGeneration.value = generation
    }

    fun setClientNotification(notification: ClientNotification?) {
        _clientNotification.value = notification
    }
}
