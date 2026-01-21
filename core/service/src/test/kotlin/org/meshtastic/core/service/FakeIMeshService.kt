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
package org.meshtastic.core.service

import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MeshUser
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.NodeInfo
import org.meshtastic.core.model.Position

/**
 * A fake implementation of [IMeshService] for testing purposes. This also serves as a contract verification: if the
 * AIDL changes, this class will fail to compile.
 */
@Suppress("TooManyFunctions", "EmptyFunctionBlock")
open class FakeIMeshService : IMeshService.Stub() {
    override fun subscribeReceiver(packageName: String?, receiverName: String?) {}

    override fun setOwner(user: MeshUser?) {}

    override fun setRemoteOwner(requestId: Int, destNum: Int, payload: ByteArray?) {}

    override fun getRemoteOwner(requestId: Int, destNum: Int) {}

    override fun getMyId(): String = "fake_id"

    override fun getPacketId(): Int = 1234

    override fun send(packet: DataPacket?) {}

    override fun getNodes(): List<NodeInfo> = emptyList()

    override fun getConfig(): ByteArray = byteArrayOf()

    override fun setConfig(payload: ByteArray?) {}

    override fun setRemoteConfig(requestId: Int, destNum: Int, payload: ByteArray?) {}

    override fun getRemoteConfig(requestId: Int, destNum: Int, configTypeValue: Int) {}

    override fun setModuleConfig(requestId: Int, destNum: Int, payload: ByteArray?) {}

    override fun getModuleConfig(requestId: Int, destNum: Int, moduleConfigTypeValue: Int) {}

    override fun setRingtone(destNum: Int, ringtone: String?) {}

    override fun getRingtone(requestId: Int, destNum: Int) {}

    override fun setCannedMessages(destNum: Int, messages: String?) {}

    override fun getCannedMessages(requestId: Int, destNum: Int) {}

    override fun setChannel(payload: ByteArray?) {}

    override fun setRemoteChannel(requestId: Int, destNum: Int, payload: ByteArray?) {}

    override fun getRemoteChannel(requestId: Int, destNum: Int, channelIndex: Int) {}

    override fun beginEditSettings(destNum: Int) {}

    override fun commitEditSettings(destNum: Int) {}

    override fun removeByNodenum(requestID: Int, nodeNum: Int) {}

    override fun requestPosition(destNum: Int, position: Position?) {}

    override fun setFixedPosition(destNum: Int, position: Position?) {}

    override fun requestTraceroute(requestId: Int, destNum: Int) {}

    override fun requestNeighborInfo(requestId: Int, destNum: Int) {}

    override fun requestShutdown(requestId: Int, destNum: Int) {}

    override fun requestReboot(requestId: Int, destNum: Int) {}

    override fun requestFactoryReset(requestId: Int, destNum: Int) {}

    override fun rebootToDfu(destNum: Int) {}

    override fun requestNodedbReset(requestId: Int, destNum: Int, preserveFavorites: Boolean) {}

    override fun getChannelSet(): ByteArray = byteArrayOf()

    override fun connectionState(): String = "CONNECTED"

    override fun setDeviceAddress(deviceAddr: String?): Boolean = true

    override fun getMyNodeInfo(): MyNodeInfo? = null

    override fun startFirmwareUpdate() {}

    override fun getUpdateStatus(): Int = 0

    override fun startProvideLocation() {}

    override fun stopProvideLocation() {}

    override fun requestUserInfo(destNum: Int) {}

    override fun getDeviceConnectionStatus(requestId: Int, destNum: Int) {}

    override fun requestTelemetry(requestId: Int, destNum: Int, type: Int) {}

    override fun requestRebootOta(requestId: Int, destNum: Int, mode: Int, hash: ByteArray?) {}
}
