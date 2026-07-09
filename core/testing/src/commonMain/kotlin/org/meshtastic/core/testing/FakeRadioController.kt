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

import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Position
import org.meshtastic.core.repository.AdminEditScope
import org.meshtastic.core.repository.RadioController
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.Config
import org.meshtastic.proto.HamParameters
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.User

/**
 * A test double for [RadioController] that provides a no-op implementation and tracks calls for assertions in tests.
 */
@Suppress("TooManyFunctions", "EmptyFunctionBlock")
class FakeRadioController :
    BaseFake(),
    RadioController {

    /** Canonical app-level connection state, mirroring [ServiceRepository][connectionState] semantics. */
    private val _connectionState = mutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _clientNotification = mutableStateFlow<ClientNotification?>(null)
    override val clientNotification: StateFlow<ClientNotification?> = _clientNotification

    val sentPackets = mutableListOf<DataPacket>()
    val favoritedNodes = mutableListOf<Int>()
    val sentSharedContacts = mutableListOf<Int>()

    /** Every [setLocalConfig] call, in order — lets tests assert e.g. that a scan restored the home LoRa preset. */
    val localConfigs = mutableListOf<Config>()
    val lastLocalConfig: Config?
        get() = localConfigs.lastOrNull()

    /** Every [setLocalChannel] call, in order. */
    val localChannels = mutableListOf<Channel>()

    var throwOnSend: Boolean = false

    /**
     * When set, a channel write throws once [localChannels] has reached this many entries — simulates a mid-write
     * failure.
     */
    var failChannelWriteAfter: Int? = null
    var lastSetDeviceAddress: String? = null
    var lastSetOwnerUser: User? = null
    var editSettingsCalled = false
    var startProvideLocationCalled = false
    var stopProvideLocationCalled = false

    init {
        registerResetAction {
            sentPackets.clear()
            favoritedNodes.clear()
            sentSharedContacts.clear()
            localConfigs.clear()
            localChannels.clear()
            throwOnSend = false
            failChannelWriteAfter = null
            lastSetDeviceAddress = null
            lastSetOwnerUser = null
            editSettingsCalled = false
            startProvideLocationCalled = false
            stopProvideLocationCalled = false
        }
    }

    override suspend fun sendMessage(packet: DataPacket) {
        if (throwOnSend) error("Fake send failure")
        sentPackets.add(packet)
    }

    override fun clearClientNotification() {
        _clientNotification.value = null
    }

    override suspend fun setFavorite(nodeNum: Int, favorite: Boolean) {
        if (favorite) favoritedNodes.add(nodeNum) else favoritedNodes.remove(nodeNum)
    }

    override suspend fun sendSharedContact(nodeNum: Int): Boolean {
        sentSharedContacts.add(nodeNum)
        return true
    }

    override suspend fun setIgnored(nodeNum: Int, ignored: Boolean) {}

    override suspend fun toggleMuted(nodeNum: Int) {}

    override suspend fun sendReaction(emoji: String, replyId: Int, contactKey: String) {}

    override suspend fun importContact(contact: org.meshtastic.proto.SharedContact) {}

    override suspend fun refreshMetadata(destNum: Int) {}

    override suspend fun setLocalConfig(config: Config) {
        localConfigs.add(config)
    }

    override suspend fun setLocalChannel(channel: Channel) {
        localChannels.add(channel)
    }

    override suspend fun setOwner(destNum: Int, user: User, packetId: Int) {
        lastSetOwnerUser = user
    }

    override suspend fun setHamMode(destNum: Int, hamParameters: HamParameters, packetId: Int) {}

    override suspend fun setConfig(destNum: Int, config: Config, packetId: Int) {
        localConfigs.add(config)
    }

    override suspend fun setModuleConfig(destNum: Int, config: ModuleConfig, packetId: Int) {}

    override suspend fun setRemoteChannel(destNum: Int, channel: Channel, packetId: Int) {
        failChannelWriteAfter?.let { if (localChannels.size >= it) error("Fake channel write failure") }
        localChannels.add(channel)
    }

    override suspend fun setFixedPosition(destNum: Int, position: Position) {}

    override suspend fun setRingtone(destNum: Int, ringtone: String) {}

    override suspend fun setCannedMessages(destNum: Int, messages: String) {}

    override suspend fun setTime(destNum: Int, packetId: Int) {}

    override suspend fun getOwner(destNum: Int, packetId: Int) {}

    override suspend fun getConfig(destNum: Int, configType: Int, packetId: Int) {}

    override suspend fun getModuleConfig(destNum: Int, moduleConfigType: Int, packetId: Int) {}

    override suspend fun getChannel(destNum: Int, index: Int, packetId: Int) {}

    override suspend fun getRingtone(destNum: Int, packetId: Int) {}

    override suspend fun getCannedMessages(destNum: Int, packetId: Int) {}

    override suspend fun getDeviceConnectionStatus(destNum: Int, packetId: Int) {}

    override suspend fun reboot(destNum: Int, packetId: Int) {}

    override suspend fun rebootToDfu(nodeNum: Int) {}

    override suspend fun requestRebootOta(requestId: Int, destNum: Int, mode: Int, hash: ByteArray?) {}

    override suspend fun shutdown(destNum: Int, packetId: Int) {}

    override suspend fun factoryReset(destNum: Int, packetId: Int) {}

    override suspend fun nodedbReset(destNum: Int, packetId: Int, preserveFavorites: Boolean) {}

    override suspend fun removeByNodenum(packetId: Int, nodeNum: Int) {}

    override suspend fun requestPosition(destNum: Int, currentPosition: Position) {}

    override suspend fun requestUserInfo(destNum: Int) {}

    override suspend fun requestTraceroute(requestId: Int, destNum: Int) {}

    override suspend fun requestTelemetry(requestId: Int, destNum: Int, typeValue: Int) {}

    override suspend fun requestNeighborInfo(requestId: Int, destNum: Int) {}

    override suspend fun editSettings(destNum: Int, block: suspend AdminEditScope.() -> Unit) {
        editSettingsCalled = true
        val scope =
            object : AdminEditScope {
                override suspend fun setOwner(user: User) = setOwner(destNum, user, generatePacketId())

                override suspend fun setConfig(config: Config) = setConfig(destNum, config, generatePacketId())

                override suspend fun setModuleConfig(config: ModuleConfig) =
                    setModuleConfig(destNum, config, generatePacketId())

                override suspend fun setChannel(channel: Channel) =
                    setRemoteChannel(destNum, channel, generatePacketId())

                override suspend fun setFixedPosition(position: Position) =
                    this@FakeRadioController.setFixedPosition(destNum, position)
            }
        scope.block()
    }

    override suspend fun editLocalSettings(block: suspend AdminEditScope.() -> Unit) = editSettings(0, block)

    override fun generatePacketId(): Int = 1

    override fun startProvideLocation() {
        startProvideLocationCalled = true
    }

    override fun stopProvideLocation() {
        stopProvideLocationCalled = true
    }

    override suspend fun setDeviceAddress(address: String) {
        lastSetDeviceAddress = address
    }

    // --- Helper methods for testing ---

    fun setConnectionState(state: ConnectionState) {
        _connectionState.value = state
    }

    fun setClientNotification(notification: ClientNotification?) {
        _clientNotification.value = notification
    }
}
