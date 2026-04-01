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

import kotlinx.serialization.Serializable
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@Serializable
data class CoTMessage(
    val uid: String,
    val type: String,
    val time: Instant = Clock.System.now(),
    val start: Instant = time,
    val stale: Instant,
    val how: String = "m-g",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val hae: Double = TAK_UNKNOWN_POINT_VALUE,
    val ce: Double = TAK_UNKNOWN_POINT_VALUE,
    val le: Double = TAK_UNKNOWN_POINT_VALUE,
    val contact: CoTContact? = null,
    val group: CoTGroup? = null,
    val status: CoTStatus? = null,
    val track: CoTTrack? = null,
    val chat: CoTChat? = null,
    val remarks: String? = null,
    val rawDetailXml: String? = null,
) {
    companion object {
        fun pli(
            uid: String,
            callsign: String,
            latitude: Double,
            longitude: Double,
            altitude: Double = 9999999.0,
            speed: Double = 0.0,
            course: Double = 0.0,
            team: String = DEFAULT_TAK_TEAM_NAME,
            role: String = DEFAULT_TAK_ROLE_NAME,
            battery: Int = DEFAULT_TAK_BATTERY,
            staleMinutes: Int = DEFAULT_TAK_STALE_MINUTES,
        ): CoTMessage {
            val now = Clock.System.now()
            return CoTMessage(
                uid = uid,
                type = "a-f-G-U-C",
                time = now,
                start = now,
                stale = now + staleMinutes.minutes,
                how = "m-g",
                latitude = latitude,
                longitude = longitude,
                hae = altitude,
                ce = TAK_UNKNOWN_POINT_VALUE,
                le = TAK_UNKNOWN_POINT_VALUE,
                contact = CoTContact(callsign = callsign, endpoint = DEFAULT_TAK_ENDPOINT),
                group = CoTGroup(name = team, role = role),
                status = CoTStatus(battery = battery),
                track = CoTTrack(speed = speed, course = course),
            )
        }

        fun chat(
            senderUid: String,
            senderCallsign: String,
            message: String,
            chatroom: String = "All Chat Rooms",
        ): CoTMessage {
            val now = Clock.System.now()
            val messageId = Random.nextInt().toString(TAK_HEX_RADIX)
            return CoTMessage(
                uid = "GeoChat.$senderUid.$chatroom.$messageId",
                contact = CoTContact(callsign = senderCallsign, endpoint = DEFAULT_TAK_ENDPOINT),
                type = "b-t-f",
                time = now,
                start = now,
                stale = now + 1.days,
                how = "h-g-i-g-o",
                latitude = 0.0,
                longitude = 0.0,
                hae = TAK_UNKNOWN_POINT_VALUE,
                ce = TAK_UNKNOWN_POINT_VALUE,
                le = TAK_UNKNOWN_POINT_VALUE,
                chat = CoTChat(message = message, senderCallsign = senderCallsign, chatroom = chatroom),
                remarks = message,
            )
        }
    }
}

@Serializable data class CoTContact(val callsign: String, val endpoint: String? = null, val phone: String? = null)

@Serializable data class CoTGroup(val name: String, val role: String)

@Serializable data class CoTStatus(val battery: Int)

@Serializable data class CoTTrack(val speed: Double, val course: Double)

@Serializable
data class CoTChat(val message: String, val senderCallsign: String? = null, val chatroom: String = "All Chat Rooms")

data class TAKClientInfo(
    val id: String,
    val endpoint: String,
    val callsign: String? = null,
    val uid: String? = null,
    val connectedAt: Long = Clock.System.now().toEpochMilliseconds(),
) {
    val displayName: String
        get() = callsign ?: uid ?: endpoint
}

sealed class TAKConnectionEvent {
    data class Connected(val clientInfo: TAKClientInfo) : TAKConnectionEvent()

    data class ClientInfoUpdated(val clientInfo: TAKClientInfo) : TAKConnectionEvent()

    data class Message(val cotMessage: CoTMessage) : TAKConnectionEvent()

    data object Disconnected : TAKConnectionEvent()

    data class Error(val error: Throwable) : TAKConnectionEvent()
}

class TAKServerException : Exception("TAK Server Error")
