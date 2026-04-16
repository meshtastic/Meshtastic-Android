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
@file:Suppress("MatchingDeclarationName", "LongMethod", "CyclomaticComplexMethod", "MaxLineLength")

package org.meshtastic.core.takserver

import kotlin.time.Instant

/**
 * Serialize this [CoTMessage] to a single `<event>` XML element suitable for the CoT streaming
 * TCP protocol used by ATAK / iTAK / WinTAK clients.
 *
 * **Important:** the output must NOT include an `<?xml ... ?>` declaration. The CoT stream
 * protocol is a continuous sequence of `<event>` elements concatenated together; an XML
 * declaration is only legal at the very start of a document and ATAK will drop the connection
 * as malformed the moment it sees a second declaration mid-stream.
 */
fun CoTMessage.toXml(): String {
    val sb = StringBuilder()
    sb.append(
        "<event version='2.0' uid='${uid.xmlEscaped()}' type='$type' time='${time.toXmlString()}' start='${start.toXmlString()}' stale='${stale.toXmlString()}' how='$how'><point lat='$latitude' lon='$longitude' hae='$hae' ce='$ce' le='$le'/><detail>",
    )

    contact?.let {
        sb.append(
            "<contact endpoint='${it.endpoint ?: DEFAULT_TAK_ENDPOINT}' callsign='${it.callsign.xmlEscaped()}'/><uid Droid='${it.callsign.xmlEscaped()}'/>",
        )
    }

    group?.let { sb.append("<__group role='${it.role.xmlEscaped()}' name='${it.name.xmlEscaped()}'/>") }

    status?.let { sb.append("<status battery='${it.battery}'/>") }

    track?.let { sb.append("<track course='${it.course}' speed='${it.speed}'/>") }

    if (chat != null) {
        val senderUid = uid.geoChatSenderUid()
        val messageId = uid.geoChatMessageId()
        sb.append(
            "<__chat parent='RootContactGroup' groupOwner='false' messageId='$messageId' chatroom='${chat.chatroom.xmlEscaped()}' id='${chat.chatroom.xmlEscaped()}' senderCallsign='${chat.senderCallsign?.xmlEscaped() ?: ""}'><chatgrp uid0='${senderUid.xmlEscaped()}' uid1='${chat.chatroom.xmlEscaped()}' id='${chat.chatroom.xmlEscaped()}'/></__chat>",
        )
        sb.append("<link uid='${senderUid.xmlEscaped()}' type='a-f-G-U-C' relation='p-p'/>")
        sb.append("<__serverdestination destinations='0.0.0.0:4242:tcp:${senderUid.xmlEscaped()}'/>")
        sb.append(
            "<remarks source='BAO.F.ATAK.${senderUid.xmlEscaped()}' to='${chat.chatroom.xmlEscaped()}' time='${time.toXmlString()}'>${chat.message.xmlEscaped()}</remarks>",
        )
    } else if (!remarks.isNullOrEmpty()) {
        sb.append("<remarks>${remarks.xmlEscaped()}</remarks>")
    }

    rawDetailXml?.let {
        if (it.isNotEmpty()) {
            sb.append(it)
        }
    }

    sb.append("</detail></event>")
    return sb.toString()
}

/**
 * Format this [Instant] for CoT XML `time` / `start` / `stale` attributes.
 *
 * Always emits millisecond precision (`YYYY-MM-DDThh:mm:ss.SSSZ`). kotlinx-datetime's default
 * [Instant.toString] can emit up to nanosecond precision; some TAK implementations choke on
 * anything beyond milliseconds, so we truncate to ms and always include the millisecond field
 * even when it would otherwise be zero.
 */
private fun Instant.toXmlString(): String {
    val millis = this.toEpochMilliseconds()
    val truncated = Instant.fromEpochMilliseconds(millis)
    val base = truncated.toString()
    // kotlinx-datetime omits the fractional part when it's zero; pad it ourselves so the
    // CoT timestamp format is stable at ms precision.
    return if (base.contains('.')) base else base.removeSuffix("Z") + ".000Z"
}
