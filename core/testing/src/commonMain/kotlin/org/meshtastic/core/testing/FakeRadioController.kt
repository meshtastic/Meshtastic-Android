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
import org.meshtastic.core.model.DeviceAdminEdit
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceConnectionStatus
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
    private val _connectionState = mutableStateFlow<ConnectionState>(ConnectionState.Connected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _clientNotification = mutableStateFlow<ClientNotification?>(null)
    override val clientNotification: StateFlow<ClientNotification?> = _clientNotification

    val sentPackets = mutableListOf<DataPacket>()
    val favoritedNodes = mutableListOf<Int>()
    val sentSharedContacts = mutableListOf<Int>()
    var throwOnSend: Boolean = false
    var lastSetDeviceAddress: String? = null
    var lastStoreForwardHistoryRequest: Pair<Int?, Int?>? = null
    var editSettingsCalled = false
    var startProvideLocationCalled = false
    var stopProvideLocationCalled = false

    init {
        registerResetAction {
            sentPackets.clear()
            favoritedNodes.clear()
            sentSharedContacts.clear()
            throwOnSend = false
            lastSetDeviceAddress = null
            lastStoreForwardHistoryRequest = null
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

    override suspend fun favoriteNode(nodeNum: Int) {
        favoritedNodes.add(nodeNum)
    }

    override suspend fun sendSharedContact(nodeNum: Int): Boolean {
        sentSharedContacts.add(nodeNum)
        return true
    }

    override suspend fun setLocalConfig(config: Config) {}

    override suspend fun setLocalChannel(channel: Channel) {}

    override suspend fun setOwner(destNum: Int, user: User) {}

    override suspend fun setConfig(destNum: Int, config: Config) {}

    override suspend fun setModuleConfig(destNum: Int, config: ModuleConfig) {}

    override suspend fun setRemoteChannel(destNum: Int, channel: Channel) {}

    override suspend fun setFixedPosition(destNum: Int, position: Position) {}

    override suspend fun setRingtone(destNum: Int, ringtone: String) {}

    override suspend fun setCannedMessages(destNum: Int, messages: String) {}

    override suspend fun getOwner(destNum: Int): User = User()

    override suspend fun getConfig(destNum: Int, configType: Int): Config = Config()

    override suspend fun getModuleConfig(destNum: Int, moduleConfigType: Int): ModuleConfig = ModuleConfig()

    override suspend fun getChannel(destNum: Int, index: Int): Channel = Channel()

    override suspend fun listChannels(destNum: Int): List<Channel> = emptyList()

    override suspend fun getRingtone(destNum: Int): String = ""

    override suspend fun getCannedMessages(destNum: Int): String = ""

    override suspend fun getDeviceConnectionStatus(destNum: Int): DeviceConnectionStatus =
        DeviceConnectionStatus()

    override suspend fun reboot(destNum: Int) {}

    override suspend fun rebootToDfu(nodeNum: Int) {}

    override suspend fun requestRebootOta(destNum: Int, mode: Int, hash: ByteArray?) {}

    override suspend fun shutdown(destNum: Int) {}

    override suspend fun factoryReset(destNum: Int) {}

    override suspend fun nodedbReset(destNum: Int, preserveFavorites: Boolean) {}

    override suspend fun removeByNodenum(nodeNum: Int) {}

    override suspend fun requestPosition(destNum: Int, currentPosition: Position) {}

    override suspend fun requestUserInfo(destNum: Int) {}

    override suspend fun requestTraceroute(destNum: Int) {}

    override suspend fun requestTelemetry(destNum: Int, type: TelemetryType) {}

    override suspend fun requestNeighborInfo(destNum: Int) {}

    override suspend fun requestStoreForwardHistory(since: Int?, serverNodeNum: Int?): Boolean {
        lastStoreForwardHistoryRequest = since to serverNodeNum
        return true
    }

    override suspend fun editSettings(destNum: Int, block: suspend DeviceAdminEdit.() -> Unit) {
        editSettingsCalled = true
        val edit = object : DeviceAdminEdit {
            override suspend fun setConfig(config: Config) {}
            override suspend fun setModuleConfig(config: ModuleConfig) {}
            override suspend fun setOwner(user: User) {}
            override suspend fun setChannel(channel: Channel) {}
        }
        block(edit)
    }

    override fun startProvideLocation() {
        startProvideLocationCalled = true
    }

    override fun stopProvideLocation() {
        stopProvideLocationCalled = true
    }

    override fun setDeviceAddress(address: String) {
        lastSetDeviceAddress = address
    }

    // --- Helper methods for testing ---

    fun setConnectionState(state: ConnectionState) {
        _connectionState.value = state
    }
}
