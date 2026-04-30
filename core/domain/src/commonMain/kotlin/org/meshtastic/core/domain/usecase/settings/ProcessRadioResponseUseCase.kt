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
package org.meshtastic.core.domain.usecase.settings

import co.touchlab.kermit.Logger
import org.koin.core.annotation.Single
import org.meshtastic.core.model.getStringResFrom
import org.meshtastic.core.resources.UiText
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Channel
import org.meshtastic.proto.Data
import org.meshtastic.proto.DeviceConnectionStatus
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Routing
import org.meshtastic.proto.User

/** Sealed class representing the result of processing a radio response packet. */
sealed class RadioResponseResult {
    data class Metadata(val metadata: DeviceMetadata) : RadioResponseResult()

    data class ChannelResponse(val channel: Channel) : RadioResponseResult()

    data class Owner(val user: User) : RadioResponseResult()

    data class ConfigResponse(val config: org.meshtastic.proto.Config) : RadioResponseResult()

    data class ModuleConfigResponse(val config: org.meshtastic.proto.ModuleConfig) : RadioResponseResult()

    data class CannedMessages(val messages: String) : RadioResponseResult()

    data class Ringtone(val ringtone: String) : RadioResponseResult()

    data class ConnectionStatus(val status: DeviceConnectionStatus) : RadioResponseResult()

    data class Error(val message: UiText) : RadioResponseResult()

    data object Success : RadioResponseResult()
}

/** Use case for processing incoming [MeshPacket]s that are responses to admin requests. */
@Single
open class ProcessRadioResponseUseCase {
    /**
     * Decodes and processes the provided [packet].
     *
     * @param packet The mesh packet received from the radio.
     * @param destNum The node number that the response is expected from.
     * @param requestIds The set of active request IDs.
     * @return A [RadioResponseResult] if the packet matches a request, or null otherwise.
     */
    @Suppress("CyclomaticComplexMethod", "NestedBlockDepth")
    open operator fun invoke(packet: MeshPacket, destNum: Int, requestIds: Set<Int>): RadioResponseResult? {
        val data = packet.decoded
        if (data == null || data.request_id !in requestIds) {
            return null
        }

        return when (data.portnum) {
            PortNum.ROUTING_APP -> processRoutingResponse(packet, data, destNum)
            PortNum.ADMIN_APP -> processAdminResponse(packet, data, destNum)
            else -> null
        }
    }

    private fun processRoutingResponse(packet: MeshPacket, data: Data, destNum: Int): RadioResponseResult? {
        val parsed = Routing.ADAPTER.decode(data.payload)
        return when {
            parsed.error_reason != Routing.Error.NONE ->
                RadioResponseResult.Error(UiText.Resource(getStringResFrom(parsed.error_reason?.value ?: 0)))

            packet.from == destNum -> RadioResponseResult.Success

            else -> null
        }
    }

    private fun processAdminResponse(packet: MeshPacket, data: Data, destNum: Int): RadioResponseResult {
        if (destNum != packet.from) {
            return RadioResponseResult.Error(
                UiText.DynamicString("Unexpected sender: ${packet.from.toUInt()} instead of ${destNum.toUInt()}."),
            )
        }

        val parsed = AdminMessage.ADAPTER.decode(data.payload)
        return processAdminMessage(parsed)
    }

    private fun processAdminMessage(parsed: AdminMessage): RadioResponseResult = when {
        parsed.get_device_metadata_response != null ->
            RadioResponseResult.Metadata(parsed.get_device_metadata_response!!)

        parsed.get_channel_response != null -> RadioResponseResult.ChannelResponse(parsed.get_channel_response!!)

        parsed.get_owner_response != null -> RadioResponseResult.Owner(parsed.get_owner_response!!)

        parsed.get_config_response != null -> RadioResponseResult.ConfigResponse(parsed.get_config_response!!)

        parsed.get_module_config_response != null ->
            RadioResponseResult.ModuleConfigResponse(parsed.get_module_config_response!!)

        parsed.get_canned_message_module_messages_response != null ->
            RadioResponseResult.CannedMessages(parsed.get_canned_message_module_messages_response!!)

        parsed.get_ringtone_response != null -> RadioResponseResult.Ringtone(parsed.get_ringtone_response!!)

        parsed.get_device_connection_status_response != null ->
            RadioResponseResult.ConnectionStatus(parsed.get_device_connection_status_response!!)

        else -> {
            Logger.d { "No custom processing needed for $parsed" }
            RadioResponseResult.Success
        }
    }
}
