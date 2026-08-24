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
@file:Suppress("CyclomaticComplexMethod", "ReturnCount")

package org.meshtastic.core.takserver

import co.touchlab.kermit.Logger
import org.meshtastic.proto.Contact
import org.meshtastic.proto.GeoChat
import org.meshtastic.proto.Group
import org.meshtastic.proto.MemberRole
import org.meshtastic.proto.PLI
import org.meshtastic.proto.Status
import org.meshtastic.proto.TAKPacket
import org.meshtastic.proto.Team
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Legacy v1 CoT <-> TAKPacket conversion for firmware <= 2.7.x.
 *
 * Wire format: bare protobuf-encoded [TAKPacket] on `ATAK_PLUGIN` port 72, no zstd compression (the proto has an
 * `is_compressed` flag but the firmware doesn't act on it). Supports only PLI and GeoChat payloads — shape, marker,
 * route, casevac, emergency, and task CoT events return null and are dropped.
 *
 * For the SDK-backed path that handles all payload types with zstd dictionary compression on `ATAK_PLUGIN_V2` port 78,
 * see [TAKPacketV2Conversion].
 *
 * [TAKMeshIntegration] picks between the two paths based on `Capabilities.supportsTakV2` (firmware >= 2.8.0).
 */
object TAKPacketConversion {

    fun CoTMessage.toTAKPacket(): TAKPacket? {
        val group =
            this.group?.let {
                Group(
                    role =
                    MemberRole.fromValue(TakConversionHelpers.getMemberRoleValue(it.role)) ?: MemberRole.Unspecifed,
                    team = Team.fromValue(TakConversionHelpers.getTeamValue(it.name)) ?: Team.Unspecifed_Color,
                )
            }

        val status = this.status?.let { Status(battery = it.battery.coerceAtLeast(0)) }

        if (type.startsWith("a-f-G") || type.startsWith("a-f-g")) {
            return createPliPacket(group, status)
        }

        if (type == "b-t-f") {
            return createChatPacket(group, status)
        }

        Logger.w { "Cannot convert CoT to TAKPacket for type $type" }
        return null
    }

    private fun CoTMessage.createPliPacket(group: Group?, status: Status?): TAKPacket {
        val contact = this.contact?.let { Contact(callsign = it.callsign, device_callsign = this.uid) }
        val pli =
            PLI(
                latitude_i = (latitude * TAK_COORDINATE_SCALE).toInt(),
                longitude_i = (longitude * TAK_COORDINATE_SCALE).toInt(),
                altitude = if (hae >= TAK_UNKNOWN_POINT_VALUE || hae.isNaN()) 0 else hae.toInt(),
                speed = track?.speed?.coerceAtLeast(0.0)?.toInt() ?: 0,
                course = track?.course?.coerceAtLeast(0.0)?.toInt() ?: 0,
            )

        return TAKPacket(is_compressed = false, contact = contact, group = group, status = status, pli = pli)
    }

    private fun CoTMessage.createChatPacket(group: Group?, status: Status?): TAKPacket? {
        val localChat = this.chat ?: return null
        val chatMsg = localChat.message
        var toUid: String? = null
        var toCallsign: String? = null

        val actualDeviceUid = this.uid.geoChatSenderUid()
        val messageId =
            if (this.uid.startsWith("GeoChat.")) {
                this.uid.geoChatMessageId()
            } else {
                Random.nextInt().toString(TAK_HEX_RADIX)
            }

        val contact =
            this.contact?.let {
                val smuggledCallsign =
                    if (actualDeviceUid.isNotEmpty()) {
                        "$actualDeviceUid|$messageId"
                    } else {
                        it.endpoint ?: ""
                    }
                Contact(callsign = it.callsign, device_callsign = smuggledCallsign)
            }

        if (localChat.chatroom.startsWith(this.uid) || this.uid.startsWith("GeoChat")) {
            val parts = this.uid.split(".")
            if (parts.size >= TAK_DIRECT_MESSAGE_PARTS_MIN && parts[0] == "GeoChat") {
                toUid = localChat.chatroom
            }
        } else if (localChat.chatroom != "All Chat Rooms") {
            toCallsign = localChat.chatroom
        }

        val chat =
            GeoChat(
                message = chatMsg,
                to = toUid ?: if (toCallsign == null) "All Chat Rooms" else null,
                to_callsign = toCallsign,
            )

        return TAKPacket(is_compressed = false, contact = contact, group = group, status = status, chat = chat)
    }

    fun TAKPacket.toCoTMessage(): CoTMessage? {
        val rawDeviceCallsign = contact?.device_callsign ?: "UNKNOWN"
        val senderCallsign = contact?.callsign ?: "UNKNOWN"
        val timeNow = Clock.System.now()
        val staleTime = timeNow + DEFAULT_TAK_STALE_MINUTES.minutes

        val (senderUid, messageId) = TakConversionHelpers.parseDeviceCallsign(rawDeviceCallsign)

        val localPli = pli
        if (localPli != null) {
            return CoTMessage.pli(
                uid = senderUid,
                callsign = senderCallsign,
                latitude = localPli.latitude_i.toDouble() / TAK_COORDINATE_SCALE,
                longitude = localPli.longitude_i.toDouble() / TAK_COORDINATE_SCALE,
                altitude = localPli.altitude.toDouble(),
                speed = localPli.speed.toDouble(),
                course = localPli.course.toDouble(),
                team = TakConversionHelpers.teamToColorName(group?.team),
                role = TakConversionHelpers.roleToName(group?.role),
                battery = status?.battery ?: DEFAULT_TAK_BATTERY,
                staleMinutes = DEFAULT_TAK_STALE_MINUTES,
            )
        }

        val localChat = chat
        if (localChat != null) {
            val chatroom =
                if (localChat.to != null || localChat.to_callsign != null) {
                    localChat.to_callsign ?: localChat.to ?: "Direct Message"
                } else {
                    "All Chat Rooms"
                }

            val msgId = messageId ?: Random.nextInt().toString(TAK_HEX_RADIX)

            return CoTMessage(
                uid = "GeoChat.$senderUid.$chatroom.$msgId",
                type = "b-t-f",
                how = "h-g-i-g-o",
                time = timeNow,
                start = timeNow,
                stale = staleTime,
                latitude = 0.0,
                longitude = 0.0,
                contact = CoTContact(callsign = senderCallsign, endpoint = DEFAULT_TAK_ENDPOINT),
                group =
                CoTGroup(
                    name = TakConversionHelpers.teamToColorName(group?.team),
                    role = TakConversionHelpers.roleToName(group?.role),
                ),
                status = CoTStatus(battery = status?.battery ?: DEFAULT_TAK_BATTERY),
                chat = CoTChat(chatroom = chatroom, senderCallsign = senderCallsign, message = localChat.message),
            )
        }

        return null
    }
}
