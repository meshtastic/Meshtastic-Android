/*
 * NTsocial MeshLink original work and modifications:
 * Copyright (c) 2026 LiberaNt LLC
 *
 * Meshtastic Android-derived portions, where present:
 * Copyright (c) 2026 Meshtastic LLC
 *
 * Developed and/or modified for NTsocial MeshLink in 2026.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
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
package com.ntsocial.meshlink.core.model.ntsocial

import com.ntsocial.meshlink.core.model.DataPacket
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.PortNum
import com.ntsocial.meshlink.core.model.Channel as ModelChannel

/** Stable, opaque identity exported for one enabled Meshtastic channel. */
data class NtsocialGatewayChannelIdentity(
    val sourceChannelId: String,
    val configuredName: String,
    val displayName: String,
    val securityClass: String,
)

/** Stable identity captured with a native broadcast text row when it is first persisted. */
data class NtsocialGatewayMessageIdentity(val sourceChannelId: String, val sourceMessageId: String)

/** One durable native-text change backed by the packet database's monotonic local row id. */
data class NtsocialGatewayMessageChange(
    val changeSeq: Long,
    val identity: NtsocialGatewayMessageIdentity?,
    val packet: DataPacket,
    val receivedAtMillis: Long,
    val originClientMessageId: String? = null,
)

/** Atomic history cursor domain exposed by Gateway v2 status. */
data class NtsocialGatewayHistoryState(val historyEpoch: String, val messageChangeSeq: Long)

/** Validation shared by the Android Gateway command boundary and its durable repository admission path. */
object NtsocialGatewayNativeText {
    const val MAX_UTF8_SIZE_BYTES = 180

    fun isValid(text: String): Boolean = text.isNotBlank() && text.encodeToByteArray().size <= MAX_UTF8_SIZE_BYTES
}

/**
 * Domain-separated gateway identities.
 *
 * Encrypted channels use only a domain-separated SHA-256 digest of their resolved PSK. Meshtastic's one-byte well-known
 * shorthand is expanded first, so shorthand and full-key forms converge while name, slot, role, and numeric channel ID
 * changes cannot split or merge encrypted-channel history. CLEAR channels retain their existing deterministic
 * identities.
 */
object NtsocialGatewayIdentity {
    private const val CHANNEL_PREFIX = "meshtastic:"
    private const val SOURCE_MESSAGE_ID_HEX_LENGTH = 32
    private const val MAX_WELL_KNOWN_PSK_INDEX = 10
    private val WELL_KNOWN_PSKS =
        (1..MAX_WELL_KNOWN_PSK_INDEX)
            .map { index -> ModelChannel(ChannelSettings(psk = byteArrayOf(index.toByte()).toByteString())).psk }
            .toSet()

    fun channel(channel: Channel, loraConfig: Config.LoRaConfig = Config.LoRaConfig()): NtsocialGatewayChannelIdentity {
        val settings = channel.settings ?: ChannelSettings()
        val model = ModelChannel(settings, loraConfig)
        val securityClass =
            when {
                model.psk.size == 0 -> "CLEAR"
                model.psk in WELL_KNOWN_PSKS -> "WELL_KNOWN"
                else -> "CUSTOM"
            }
        val stableMaterial =
            when {
                model.psk.size > 0 -> digest("ntsocial-gateway-channel-psk-v3".encodeUtf8(), model.psk)

                settings.id != 0 ->
                    digest("ntsocial-gateway-channel-id-v2".encodeUtf8(), settings.id.toUInt().toString().encodeUtf8())

                else ->
                    digest(
                        "ntsocial-gateway-channel-public-v2".encodeUtf8(),
                        canonicalPublicName(model.name).encodeUtf8(),
                        model.psk,
                    )
            }
        return NtsocialGatewayChannelIdentity(
            sourceChannelId = CHANNEL_PREFIX + stableMaterial.hex(),
            configuredName = settings.name,
            displayName = model.name,
            securityClass = securityClass,
        )
    }

    fun nativeBroadcastText(
        channel: NtsocialGatewayChannelIdentity,
        packet: DataPacket,
    ): NtsocialGatewayMessageIdentity? {
        val payload =
            packet.bytes?.takeIf {
                packet.dataType == PortNum.TEXT_MESSAGE_APP.value &&
                    packet.to == DataPacket.ID_BROADCAST &&
                    !packet.from.isNullOrBlank() &&
                    packet.from != DataPacket.ID_LOCAL
            }
        return payload?.let {
            val sourceMessageId =
                digest(
                    "ntsocial-gateway-message-v2".encodeUtf8(),
                    channel.sourceChannelId.encodeUtf8(),
                    packet.from.orEmpty().encodeUtf8(),
                    packet.id.toUInt().toString().encodeUtf8(),
                    packet.dataType.toString().encodeUtf8(),
                    payload.sha256(),
                )
            NtsocialGatewayMessageIdentity(
                sourceChannelId = channel.sourceChannelId,
                sourceMessageId = sourceMessageId.hex().take(SOURCE_MESSAGE_ID_HEX_LENGTH).uppercase(),
            )
        }
    }

    fun nativeBroadcastText(
        settings: ChannelSettings,
        loraConfig: Config.LoRaConfig,
        channelIndex: Int,
        packet: DataPacket,
    ): NtsocialGatewayMessageIdentity? = nativeBroadcastText(
        channel =
        channel(
            Channel(
                index = channelIndex,
                role = if (channelIndex == 0) Channel.Role.PRIMARY else Channel.Role.SECONDARY,
                settings = settings,
            ),
            loraConfig,
        ),
        packet = packet,
    )

    /** Resolves the durable local Meshtastic node ID used when capturing an outgoing native-text identity. */
    fun stableLocalNodeId(userId: String?, myId: String?, nodeNum: Int?): String? = sequenceOf(userId, myId)
        .mapNotNull { candidate -> candidate?.takeIf { it.isNotBlank() && it != DataPacket.ID_LOCAL } }
        .firstOrNull()
        ?: nodeNum
            ?.takeIf { it != 0 }
            ?.let { value -> "!${value.toUInt().toString(NODE_ID_HEX_RADIX).padStart(NODE_ID_HEX_LENGTH, '0')}" }

    private fun digest(vararg parts: ByteString): ByteString = framed(*parts).sha256()

    /**
     * [String.lowercase] is locale-independent in common Kotlin. Resolving [ModelChannel.name] first keeps an empty
     * default-channel name interoperable with an explicitly configured display name such as `LongFast`.
     */
    private fun canonicalPublicName(name: String): String = name.trim().lowercase()

    private fun framed(vararg parts: ByteString): ByteString {
        val buffer = Buffer()
        parts.forEach { part ->
            buffer.writeInt(part.size)
            buffer.write(part)
        }
        return buffer.readByteString()
    }

    private const val NODE_ID_HEX_LENGTH = 8
    private const val NODE_ID_HEX_RADIX = 16
}
