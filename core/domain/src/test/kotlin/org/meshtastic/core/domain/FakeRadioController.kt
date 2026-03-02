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
package org.meshtastic.core.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.RadioController
import org.meshtastic.proto.ClientNotification

class FakeRadioController : RadioController {

    // Mutable state flows so we can manipulate them in our tests
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Connected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _clientNotification = MutableStateFlow<ClientNotification?>(null)
    override val clientNotification: StateFlow<ClientNotification?> = _clientNotification

    // Track sent packets to assert in tests
    val sentPackets = mutableListOf<DataPacket>()
    val favoritedNodes = mutableListOf<Int>()
    val sentSharedContacts = mutableListOf<Int>()

    override suspend fun sendMessage(packet: DataPacket) {
        sentPackets.add(packet)
    }

    override fun clearClientNotification() {
        _clientNotification.value = null
    }

    override suspend fun favoriteNode(nodeNum: Int) {
        favoritedNodes.add(nodeNum)
    }

    override suspend fun sendSharedContact(nodeNum: Int) {
        sentSharedContacts.add(nodeNum)
    }

    override suspend fun setOwner(destNum: Int, user: org.meshtastic.proto.User, packetId: Int) {}

    override suspend fun setConfig(destNum: Int, config: org.meshtastic.proto.Config, packetId: Int) {}

    override suspend fun setModuleConfig(destNum: Int, config: org.meshtastic.proto.ModuleConfig, packetId: Int) {}

    override suspend fun setRemoteChannel(destNum: Int, channel: org.meshtastic.proto.Channel, packetId: Int) {}

    override suspend fun setFixedPosition(destNum: Int, position: org.meshtastic.core.model.Position) {}

    override suspend fun setRingtone(destNum: Int, ringtone: String) {}

    override suspend fun setCannedMessages(destNum: Int, messages: String) {}

    override suspend fun getOwner(destNum: Int, packetId: Int) {}

    override suspend fun getConfig(destNum: Int, configType: Int, packetId: Int) {}

    override suspend fun getModuleConfig(destNum: Int, moduleConfigType: Int, packetId: Int) {}

    override suspend fun getChannel(destNum: Int, index: Int, packetId: Int) {}

    override suspend fun getRingtone(destNum: Int, packetId: Int) {}

    override suspend fun getCannedMessages(destNum: Int, packetId: Int) {}

    override suspend fun getDeviceConnectionStatus(destNum: Int, packetId: Int) {}

    override suspend fun reboot(destNum: Int, packetId: Int) {}

    override suspend fun shutdown(destNum: Int, packetId: Int) {}

    override suspend fun factoryReset(destNum: Int, packetId: Int) {}

    override suspend fun nodedbReset(destNum: Int, packetId: Int, preserveFavorites: Boolean) {}

    override suspend fun removeByNodenum(packetId: Int, nodeNum: Int) {}

    override suspend fun beginEditSettings(destNum: Int) {}

    override suspend fun commitEditSettings(destNum: Int) {}

    override fun getPacketId(): Int = 1

    override fun startProvideLocation() {}

    override fun stopProvideLocation() {}

    // --- Helper methods for testing ---

    fun setConnectionState(state: ConnectionState) {
        _connectionState.value = state
    }
}
