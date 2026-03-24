/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.RadioController
import org.meshtastic.proto.ClientNotification

/**
 * A test double for [RadioController] that provides a no-op implementation and tracks calls for assertions in tests.
 *
 * Use this in place of mocking the entire RadioController interface when you need fine-grained control over connection
 * state and packet tracking.
 *
 * Example:
 * ```kotlin
 * val radioController = FakeRadioController()
 * radioController.setConnectionState(ConnectionState.Connected)
 * // ... perform test ...
 * assertEquals(1, radioController.sentPackets.size)
 * ```
 */
@Suppress("TooManyFunctions", "EmptyFunctionBlock")
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
    var throwOnSend: Boolean = false
    var lastSetDeviceAddress: String? = null

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

    override suspend fun sendSharedContact(nodeNum: Int) {
        sentSharedContacts.add(nodeNum)
    }

    override suspend fun setLocalConfig(config: org.meshtastic.proto.Config) {}

    override suspend fun setLocalChannel(channel: org.meshtastic.proto.Channel) {}

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

    override suspend fun rebootToDfu(nodeNum: Int) {}

    override suspend fun requestRebootOta(requestId: Int, destNum: Int, mode: Int, hash: ByteArray?) {}

    override suspend fun shutdown(destNum: Int, packetId: Int) {}

    override suspend fun factoryReset(destNum: Int, packetId: Int) {}

    override suspend fun nodedbReset(destNum: Int, packetId: Int, preserveFavorites: Boolean) {}

    override suspend fun removeByNodenum(packetId: Int, nodeNum: Int) {}

    override suspend fun requestPosition(destNum: Int, currentPosition: org.meshtastic.core.model.Position) {}

    override suspend fun requestUserInfo(destNum: Int) {}

    override suspend fun requestTraceroute(requestId: Int, destNum: Int) {}

    override suspend fun requestTelemetry(requestId: Int, destNum: Int, typeValue: Int) {}

    override suspend fun requestNeighborInfo(requestId: Int, destNum: Int) {}

    override suspend fun beginEditSettings(destNum: Int) {}

    override suspend fun commitEditSettings(destNum: Int) {}

    override fun getPacketId(): Int = 1

    override fun startProvideLocation() {}

    override fun stopProvideLocation() {}

    override fun setDeviceAddress(address: String) {
        lastSetDeviceAddress = address
    }

    // --- Helper methods for testing ---

    fun setConnectionState(state: ConnectionState) {
        _connectionState.value = state
    }
}
