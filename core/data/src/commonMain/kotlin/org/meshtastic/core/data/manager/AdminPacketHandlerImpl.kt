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
package org.meshtastic.core.data.manager

import co.touchlab.kermit.Logger
import org.koin.core.annotation.Single
import org.meshtastic.core.repository.AdminPacketHandler
import org.meshtastic.core.repository.MeshConfigFlowManager
import org.meshtastic.core.repository.MeshConfigHandler
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.SessionManager
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.MeshPacket

/**
 * Implementation of [AdminPacketHandler] that processes admin messages, including session passkeys, device/module
 * configuration, and metadata.
 */
@Single
class AdminPacketHandlerImpl(
    private val nodeManager: NodeManager,
    private val configHandler: Lazy<MeshConfigHandler>,
    private val configFlowManager: Lazy<MeshConfigFlowManager>,
    private val sessionManager: SessionManager,
) : AdminPacketHandler {

    override fun handleAdminMessage(packet: MeshPacket, myNodeNum: Int) {
        val payload = packet.decoded?.payload ?: return
        val u = AdminMessage.ADAPTER.decode(payload)
        Logger.d { "Admin message from=${packet.from} fields=${u.summarize()}" }
        // Firmware embeds the session_passkey in every admin response. A missing (default-empty)
        // field must not reset stored state, so only record refreshes when bytes arrived.
        val incomingPasskey = u.session_passkey
        if (incomingPasskey.size > 0) {
            sessionManager.recordSession(packet.from, incomingPasskey)
        }

        val fromNum = packet.from
        u.get_module_config_response?.let {
            if (fromNum == myNodeNum) {
                configHandler.value.handleModuleConfig(it)
            } else {
                it.statusmessage?.node_status?.let { nodeManager.updateNodeStatus(fromNum, it) }
            }
        }

        if (fromNum == myNodeNum) {
            u.get_config_response?.let { configHandler.value.handleDeviceConfig(it) }
            u.get_channel_response?.let { configHandler.value.handleChannel(it) }
        }

        u.get_device_metadata_response?.let {
            if (fromNum == myNodeNum) {
                configFlowManager.value.handleLocalMetadata(it)
            } else {
                nodeManager.insertMetadata(fromNum, it)
            }
        }
    }
}

/** Returns a short summary of the non-null admin message fields for logging. */
private fun AdminMessage.summarize(): String = buildList {
    get_config_response?.let { add("get_config_response") }
    get_module_config_response?.let { add("get_module_config_response") }
    get_channel_response?.let { add("get_channel_response") }
    get_device_metadata_response?.let { add("get_device_metadata_response") }
    if (session_passkey.size > 0) add("session_passkey")
}
    .joinToString()
    .ifEmpty { "empty" }
