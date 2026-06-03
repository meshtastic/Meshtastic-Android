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
@file:Suppress("CyclomaticComplexMethod", "ReturnCount", "LongMethod", "MagicNumber")

package org.meshtastic.core.takserver

import co.touchlab.kermit.Logger
import okio.ByteString.Companion.toByteString
import org.meshtastic.proto.CotHow
import org.meshtastic.proto.CotType
import org.meshtastic.proto.GeoChat
import org.meshtastic.proto.GeoPointSource
import org.meshtastic.proto.MemberRole
import org.meshtastic.proto.TAKPacketV2
import org.meshtastic.proto.Team
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Conversion between CoTMessage and TAKPacketV2 (v2 wire protocol). */
object TAKPacketV2Conversion {

    fun CoTMessage.toTAKPacketV2(): TAKPacketV2? {
        val cotTypeEnum = TakV2TypeMapper.cotTypeFromString(type)
        val cotTypeStr = if (cotTypeEnum == CotType.CotType_Other) type else ""
        val howEnum = TakV2TypeMapper.cotHowFromString(how)

        val teamEnum =
            group?.let { Team.fromValue(TakConversionHelpers.getTeamValue(it.name)) } ?: Team.Unspecifed_Color

        val roleEnum =
            group?.let { MemberRole.fromValue(TakConversionHelpers.getMemberRoleValue(it.role)) }
                ?: MemberRole.Unspecifed

        val battery = status?.battery?.coerceAtLeast(0) ?: 0

        // PLI (position reports): match all atom CoT types ("a-*").
        // The original type is preserved via cot_type_id/cot_type_str so that hostile
        // (a-h-*), neutral (a-n-*), and unknown (a-u-*) markers round-trip correctly.
        // toCoTMessage() restores the exact type from those fields instead of defaulting
        // to DEFAULT_PLI_COT_TYPE, so all atom markers are treated as PLI on the wire.
        if (type.startsWith("a-")) {
            val callsign = contact?.callsign ?: "UNKNOWN"
            val deviceCallsign = uid

            return TAKPacketV2(
                cot_type_id = cotTypeEnum,
                cot_type_str = cotTypeStr,
                how = howEnum,
                callsign = callsign,
                device_callsign = deviceCallsign,
                uid = uid,
                team = teamEnum,
                role = roleEnum,
                latitude_i = (latitude * TAK_COORDINATE_SCALE).toInt(),
                longitude_i = (longitude * TAK_COORDINATE_SCALE).toInt(),
                altitude = if (hae >= TAK_UNKNOWN_POINT_VALUE || hae.isNaN()) 0 else hae.toInt(),
                // V2 encodes speed as cm/s (m/s × 100) and course as deg×100.
                // V1 (legacy TAKPacket, port 72) uses raw integers with no scaling.
                // These two paths are ALWAYS separate (different portnums) and must
                // never cross-feed: a V1 packet decoded in TAKMeshIntegration goes
                // through convertV1ToCoT() → CoTMessage.pli() → toXml(), NOT through
                // toTAKPacketV2(). If this invariant is ever broken, speed/course
                // would be silently off by ×100.
                speed = (track?.speed?.coerceAtLeast(0.0)?.times(100))?.toInt() ?: 0, // m/s -> cm/s
                course = (track?.course?.coerceAtLeast(0.0)?.times(100))?.toInt() ?: 0, // deg -> deg*100
                battery = battery,
                geo_src = GeoPointSource.GeoPointSource_GPS,
                alt_src = GeoPointSource.GeoPointSource_GPS,
                // v0.4.0: PLI is implicit — no payload_variant is set (the bool pli
                // oneof arm was removed). An a-f-* packet with no variant IS a PLI.
            )
        }

        // GeoChat
        if (type == "b-t-f") {
            val localChat = chat ?: return null
            // ATAK GeoChat events often omit <contact callsign="..."/> — the
            // sender identity is only in <__chat senderCallsign="..."/>.
            val callsign = contact?.callsign ?: localChat.senderCallsign ?: "UNKNOWN"
            val actualDeviceUid = uid.geoChatSenderUid()
            val messageId =
                if (uid.startsWith("GeoChat.")) {
                    uid.geoChatMessageId()
                } else {
                    Random.nextInt().toString(TAK_HEX_RADIX)
                }

            val smuggledCallsign =
                if (actualDeviceUid.isNotEmpty()) {
                    "$actualDeviceUid|$messageId"
                } else {
                    contact?.endpoint ?: ""
                }

            var toUid: String? = null
            var toCallsign: String? = null
            if (localChat.chatroom != "All Chat Rooms") {
                if (localChat.chatroom.startsWith(uid) || uid.startsWith("GeoChat")) {
                    val parts = uid.split(".")
                    if (parts.size >= TAK_DIRECT_MESSAGE_PARTS_MIN && parts[0] == "GeoChat") {
                        toUid = localChat.chatroom
                    }
                } else {
                    toCallsign = localChat.chatroom
                }
            }

            return TAKPacketV2(
                cot_type_id = CotType.CotType_b_t_f,
                how = CotHow.CotHow_h_g_i_g_o,
                callsign = callsign,
                device_callsign = smuggledCallsign,
                uid = uid,
                team = teamEnum,
                role = roleEnum,
                battery = battery,
                chat =
                GeoChat(
                    message = localChat.message,
                    to = toUid ?: if (toCallsign == null) "All Chat Rooms" else null,
                    to_callsign = toCallsign,
                ),
            )
        }

        // Fallback: wrap the whole detail XML in raw_detail for unmapped types
        // (user-drawn shapes like u-d-c-c, markers like b-m-*, alerts, etc.)
        val detailBytes = parsedDetailXml?.encodeToByteArray()
        if (detailBytes != null) {
            val callsign = contact?.callsign ?: "UNKNOWN"
            return TAKPacketV2(
                cot_type_id = cotTypeEnum,
                cot_type_str = cotTypeStr,
                how = howEnum,
                callsign = callsign,
                device_callsign = uid,
                uid = uid,
                team = teamEnum,
                role = roleEnum,
                latitude_i = (latitude * TAK_COORDINATE_SCALE).toInt(),
                longitude_i = (longitude * TAK_COORDINATE_SCALE).toInt(),
                altitude = if (hae >= TAK_UNKNOWN_POINT_VALUE || hae.isNaN()) 0 else hae.toInt(),
                battery = battery,
                raw_detail = detailBytes.toByteString(),
            )
        }

        Logger.w { "Cannot convert CoT to TAKPacketV2 for type $type (no parsed detail)" }
        return null
    }

    fun TAKPacketV2.toCoTMessage(): CoTMessage? {
        val senderCallsign = callsign.ifEmpty { "UNKNOWN" }
        val rawDeviceCallsign = device_callsign.ifEmpty { uid.ifEmpty { "UNKNOWN" } }
        val timeNow = Clock.System.now()
        val (senderUid, messageId) = TakConversionHelpers.parseDeviceCallsign(rawDeviceCallsign)

        // PLI — v0.4.0: implicit (the `bool pli` oneof arm was removed). A packet
        // with NO typed payload_variant arm set is a position report. Every typed
        // arm must be excluded so a shape/marker/route/etc. isn't mis-rendered as a
        // PLI dot if it ever reaches this fallback path.
        if (!hasTypedPayload()) {
            val staleMinutes = if (stale_seconds > 0) (stale_seconds / 60) else DEFAULT_TAK_STALE_MINUTES
            // Restore the original CoT type and how from the packet — pli() defaults to
            // DEFAULT_PLI_COT_TYPE/"m-g" but the sending node may have been hostile (a-h-*),
            // neutral (a-n-*), unknown (a-u-*), etc.
            val resolvedType =
                cot_type_str.ifEmpty { TakV2TypeMapper.cotTypeToString(cot_type_id) ?: DEFAULT_PLI_COT_TYPE }
            val resolvedHow = TakV2TypeMapper.cotHowToString(how) ?: "m-g"
            return CoTMessage.pli(
                uid = senderUid.ifEmpty { uid },
                callsign = senderCallsign,
                latitude = latitude_i.toDouble() / TAK_COORDINATE_SCALE,
                longitude = longitude_i.toDouble() / TAK_COORDINATE_SCALE,
                altitude = altitude.toDouble(),
                speed = speed.toDouble() / 100.0, // cm/s -> m/s
                course = course.toDouble() / 100.0, // deg*100 -> deg
                team = TakConversionHelpers.teamToColorName(team),
                role = TakConversionHelpers.roleToName(role),
                battery = battery,
                staleMinutes = staleMinutes,
            )
                .copy(type = resolvedType, how = resolvedHow)
        }

        // GeoChat
        val localChat = chat
        if (localChat != null) {
            // chat.to carries the recipient/room ID for DMs; null means broadcast.
            // Do NOT fall through to chat.to_callsign here — despite the name,
            // it holds the SENDER's callsign (the parser stores __chat[@senderCallsign]
            // there), not a chatroom name.
            val chatroom = localChat.to ?: "All Chat Rooms"

            val msgId = messageId ?: Random.nextInt().toString(TAK_HEX_RADIX)
            val staleTime =
                timeNow +
                    if (stale_seconds > 0) {
                        stale_seconds.seconds
                    } else {
                        DEFAULT_TAK_STALE_MINUTES.minutes
                    }

            return CoTMessage(
                uid = "GeoChat.$senderUid.$chatroom.$msgId",
                type = "b-t-f",
                how = "h-g-i-g-o",
                time = timeNow,
                start = timeNow,
                stale = staleTime,
                latitude = latitude_i.toDouble() / TAK_COORDINATE_SCALE,
                longitude = longitude_i.toDouble() / TAK_COORDINATE_SCALE,
                contact = CoTContact(callsign = senderCallsign, endpoint = DEFAULT_TAK_ENDPOINT),
                group =
                CoTGroup(
                    name = TakConversionHelpers.teamToColorName(team),
                    role = TakConversionHelpers.roleToName(role),
                ),
                status = CoTStatus(battery = battery),
                chat = CoTChat(chatroom = chatroom, senderCallsign = senderCallsign, message = localChat.message),
            )
        }

        // Raw detail: unmapped CoT types round-tripped as opaque detail bytes.
        // Emit a bare CoTMessage whose <detail> is the raw bytes verbatim. Do NOT populate
        // contact/group/status here — those would be double-emitted by toXml() alongside
        // rawDetailXml, corrupting the CoT stream.
        val rawDetail = raw_detail
        if (rawDetail != null) {
            val rawXml = rawDetail.utf8()
            val resolvedType =
                cot_type_str.ifEmpty { TakV2TypeMapper.cotTypeToString(cot_type_id) ?: DEFAULT_PLI_COT_TYPE }
            val resolvedHow = TakV2TypeMapper.cotHowToString(how) ?: "m-g"
            val staleTime =
                timeNow +
                    if (stale_seconds > 0) {
                        stale_seconds.seconds
                    } else {
                        DEFAULT_TAK_STALE_MINUTES.minutes
                    }
            return CoTMessage(
                uid = uid.ifEmpty { senderUid.ifEmpty { "tak-raw" } },
                type = resolvedType,
                how = resolvedHow,
                time = timeNow,
                start = timeNow,
                stale = staleTime,
                latitude = latitude_i.toDouble() / TAK_COORDINATE_SCALE,
                longitude = longitude_i.toDouble() / TAK_COORDINATE_SCALE,
                hae = if (altitude == 0) TAK_UNKNOWN_POINT_VALUE else altitude.toDouble(),
                rawDetailXml = rawXml,
            )
        }

        Logger.w { "Cannot convert TAKPacketV2 to CoTMessage: no PLI, chat, or raw_detail payload" }
        return null
    }

    /** True when any typed payload_variant arm is set (i.e. NOT a bare PLI). */
    private fun TAKPacketV2.hasTypedPayload(): Boolean = chat != null ||
        taktalk != null ||
        taktalk_room != null ||
        aircraft != null ||
        shape != null ||
        marker != null ||
        rab != null ||
        route != null ||
        casevac != null ||
        emergency != null ||
        task != null ||
        raw_detail != null
}
