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
package org.meshtastic.core.data.manager

import okio.ByteString
import okio.ByteString.Companion.EMPTY
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceConnectionStatus
import org.meshtastic.proto.DeviceUIConfig
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.PortNum

/*
 * Copies of radio messages for the persisted log. Credential-bearing fields are cleared so the log table and its
 * exports never hold them; the in-memory processing path keeps using the original message.
 */

/** Returns a copy of this frame that is safe to persist in the log. */
internal fun FromRadio.redactedForLog(): FromRadio = newBuilder()
    .also { b ->
        config?.let { b.config = it.redactedForLog() }
        moduleConfig?.let { b.moduleConfig = it.redactedForLog() }
        channel?.let { b.channel = it.redactedForLog() }
        deviceuiConfig?.let { b.deviceuiConfig = it.redactedForLog() }
        clientNotification?.let { b.clientNotification = it.redactedForLog() }
        packet?.let { b.packet = it.redactedForLog() }
    }
    .build()

/**
 * Returns a copy of this packet that is safe to persist in the log. An admin payload is decoded, cleared and
 * re-encoded; one that cannot be decoded is logged with an empty payload rather than its raw bytes.
 */
internal fun MeshPacket.redactedForLog(): MeshPacket {
    val data = decoded
    if (data == null || data.portnum != PortNum.ADMIN_APP) return this
    val redactedPayload = runCatching {
        AdminMessage.ADAPTER.decode(data.payload)
    }
        .map { AdminMessage.ADAPTER.encodeByteString(it.redactedForLog()) }
        .getOrDefault(EMPTY)
    return newBuilder()
        .also { b -> b.decoded = data.newBuilder().also { it.payload = redactedPayload }.build() }
        .build()
}

internal fun AdminMessage.redactedForLog(): AdminMessage = newBuilder()
    .also { b ->
        b.session_passkey = EMPTY
        set_config?.let { b.set_config = it.redactedForLog() }
        get_config_response?.let { b.get_config_response = it.redactedForLog() }
        set_module_config?.let { b.set_module_config = it.redactedForLog() }
        get_module_config_response?.let { b.get_module_config_response = it.redactedForLog() }
        set_channel?.let { b.set_channel = it.redactedForLog() }
        get_channel_response?.let { b.get_channel_response = it.redactedForLog() }
        store_ui_config?.let { b.store_ui_config = it.redactedForLog() }
        get_ui_config_response?.let { b.get_ui_config_response = it.redactedForLog() }
        get_device_connection_status_response?.let { b.get_device_connection_status_response = it.redactedForLog() }
        lockdown_auth?.let { b.lockdown_auth = it.newBuilder().also { l -> l.passphrase = EMPTY }.build() }
        key_verification?.let {
            b.key_verification = it.newBuilder().also { k -> k.security_number = null }.build()
        }
    }
    .build()

internal fun Config.redactedForLog(): Config = newBuilder()
    .also { b ->
        device_ui?.let { b.device_ui = it.redactedForLog() }
        network?.let { b.network = it.newBuilder().also { n -> n.wifi_psk = "" }.build() }
        bluetooth?.let { b.bluetooth = it.newBuilder().also { n -> n.fixed_pin = 0 }.build() }
        security?.let {
            b.security =
                it.newBuilder()
                    .also { s ->
                        s.private_key = EMPTY
                        s.admin_key = emptyList<ByteString>()
                    }
                    .build()
        }
    }
    .build()

internal fun DeviceConnectionStatus.redactedForLog(): DeviceConnectionStatus =
    newBuilder().also { b -> bluetooth?.let { b.bluetooth = it.newBuilder().also { s -> s.pin = 0 }.build() } }.build()

internal fun ModuleConfig.redactedForLog(): ModuleConfig = newBuilder()
    .also { b ->
        mesh_beacon?.let { beacon ->
            beacon.broadcast_offer_channel?.let { offer ->
                b.mesh_beacon =
                    beacon
                        .newBuilder()
                        .also {
                            it.broadcast_offer_channel = offer.newBuilder().also { s -> s.psk = EMPTY }.build()
                        }
                        .build()
            }
        }
        mqtt?.let {
            b.mqtt =
                it.newBuilder()
                    .also { m ->
                        m.username = ""
                        m.password = ""
                    }
                    .build()
        }
    }
    .build()

internal fun Channel.redactedForLog(): Channel = newBuilder()
    .also { b -> settings?.let { b.settings = it.newBuilder().also { s -> s.psk = EMPTY }.build() } }
    .build()

internal fun DeviceUIConfig.redactedForLog(): DeviceUIConfig = newBuilder().also { it.pin_code = 0 }.build()

internal fun ClientNotification.redactedForLog(): ClientNotification = newBuilder()
    .also { b ->
        key_verification_number_inform?.let {
            b.key_verification_number_inform = it.newBuilder().also { k -> k.security_number = 0 }.build()
        }
    }
    .build()
