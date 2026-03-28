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
@file:Suppress(
    "MatchingDeclarationName",
    "LongMethod",
    "CyclomaticComplexMethod",
    "MaxLineLength",
    "TooGenericExceptionCaught",
    "SwallowedException",
)

package org.meshtastic.core.takserver

import kotlin.time.Instant

fun CoTMessage.toXml(): String {
    val sb = StringBuilder()
    sb.append(
        "<?xml version='1.0' encoding='UTF-8' standalone='yes'?><event version='2.0' uid='${uid.xmlEscaped()}' type='$type' time='${time.toXmlString()}' start='${start.toXmlString()}' stale='${stale.toXmlString()}' how='$how'><point lat='$latitude' lon='$longitude' hae='$hae' ce='$ce' le='$le'/><detail>",
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
        val senderUid =
            if (uid.startsWith("GeoChat.")) {
                uid.split(".").getOrElse(1) { uid }
            } else {
                uid
            }
        val messageId =
            if (uid.startsWith("GeoChat.")) {
                uid.split(".").lastOrNull() ?: uid
            } else {
                uid
            }
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

private fun Instant.toXmlString(): String = this.toString()

private fun String.xmlEscaped(): String =
    this.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
