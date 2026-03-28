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
package org.meshtastic.core.takserver

import nl.adaptivity.xmlutil.serialization.XML
import kotlin.time.Clock
import kotlin.time.Instant

private val xmlParser = XML {
    defaultPolicy {
        ignoreUnknownChildren()
        repairNamespaces = false
    }
}

class CoTXmlParser(private val xml: String) {
    fun parse(): Result<CoTMessage> = try {
        val event = xmlParser.decodeFromString(CoTEventXml.serializer(), xml)
        Result.success(buildCoTMessage(event))
    } catch (e: IllegalArgumentException) {
        Result.failure(e)
    } catch (e: kotlinx.serialization.SerializationException) {
        Result.failure(e)
    } catch (e: nl.adaptivity.xmlutil.XmlException) {
        Result.failure(e)
    }

    private fun buildCoTMessage(event: CoTEventXml): CoTMessage {
        val detail = event.detail
        return CoTMessage(
            uid = event.uid.ifEmpty { "tak-0" },
            type = event.type.ifEmpty { "a-f-G-U-C" },
            time = parseDate(event.time),
            start = parseDate(event.start),
            stale = parseDate(event.stale),
            how = event.how.ifEmpty { "m-g" },
            latitude = event.point.lat,
            longitude = event.point.lon,
            hae = event.point.hae,
            ce = event.point.ce,
            le = event.point.le,
            contact = buildContact(detail),
            group = buildGroup(detail),
            status = detail?.status?.let { CoTStatus(battery = it.battery) },
            track = detail?.track?.let { CoTTrack(speed = it.speed, course = it.course) },
            chat = buildChat(detail),
            remarks = buildRemarks(detail),
        )
    }

    private fun buildContact(detail: CoTDetailXml?): CoTContact? = detail?.contact?.let {
        if (it.callsign.isNotEmpty() || it.endpoint != null || it.phone != null) {
            CoTContact(callsign = it.callsign, endpoint = it.endpoint, phone = it.phone)
        } else {
            null
        }
    }

    private fun buildGroup(detail: CoTDetailXml?): CoTGroup? = detail?.group?.let {
        if (it.name.isNotEmpty() || it.role.isNotEmpty()) {
            CoTGroup(
                name = it.name.ifEmpty { DEFAULT_TAK_TEAM_NAME },
                role = it.role.ifEmpty { DEFAULT_TAK_ROLE_NAME },
            )
        } else {
            null
        }
    }

    private fun buildChat(detail: CoTDetailXml?): CoTChat? = detail?.chat?.let {
        val remarksText = detail.remarks?.value ?: ""
        CoTChat(
            message = remarksText,
            senderCallsign = it.senderCallsign,
            chatroom = it.chatroom.ifEmpty { it.id ?: "All Chat Rooms" },
        )
    }

    private fun buildRemarks(detail: CoTDetailXml?): String? =
        if (detail?.chat == null && detail?.remarks != null && detail.remarks.value.isNotEmpty()) {
            detail.remarks.value
        } else {
            null
        }

    private fun parseDate(dateString: String?): Instant {
        if (dateString.isNullOrEmpty()) return Clock.System.now()

        return try {
            Instant.parse(dateString)
        } catch (ignored: IllegalArgumentException) {
            try {
                val cleaned = dateString.replace(Regex("""\.\d+"""), "").replace("Z", "+00:00")
                Instant.parse(cleaned)
            } catch (ignoredInner: IllegalArgumentException) {
                Clock.System.now() // Return now as fallback
            }
        }
    }
}
