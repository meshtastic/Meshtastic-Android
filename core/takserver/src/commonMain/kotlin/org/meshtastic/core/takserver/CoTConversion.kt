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

import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

fun org.meshtastic.proto.Position.toCoTMessage(
    uid: String,
    callsign: String,
    team: String = DEFAULT_TAK_TEAM_NAME,
    role: String = DEFAULT_TAK_ROLE_NAME,
    battery: Int = DEFAULT_TAK_BATTERY,
    staleMinutes: Int = DEFAULT_TAK_STALE_MINUTES,
    remarks: String? = null,
): CoTMessage {
    val lat = (latitude_i ?: 0).toDouble() / TAK_COORDINATE_SCALE
    val lon = (longitude_i ?: 0).toDouble() / TAK_COORDINATE_SCALE
    val altitude = (altitude ?: 0).toDouble()
    val speed = (ground_speed ?: 0).toDouble()
    val course = (ground_track ?: 0).toDouble()

    return CoTMessage.pli(
        uid = uid,
        callsign = callsign,
        latitude = lat,
        longitude = lon,
        altitude = altitude,
        speed = speed,
        course = course,
        team = team,
        role = role,
        battery = battery,
        staleMinutes = staleMinutes,
        remarks = remarks,
    )
}

fun org.meshtastic.proto.User.toCoTMessage(
    position: org.meshtastic.proto.Position?,
    team: String = DEFAULT_TAK_TEAM_NAME,
    role: String = DEFAULT_TAK_ROLE_NAME,
    battery: Int = DEFAULT_TAK_BATTERY,
    uid: String = id,
    callsign: String = toTakCallsign(),
    staleMinutes: Int = DEFAULT_TAK_STALE_MINUTES,
    remarks: String? = null,
): CoTMessage = if (position != null) {
    position.toCoTMessage(
        uid = uid,
        callsign = callsign,
        team = team,
        role = role,
        battery = battery,
        staleMinutes = staleMinutes,
        remarks = remarks,
    )
} else {
    val now = Clock.System.now()
    CoTMessage(
        uid = uid,
        type = DEFAULT_PLI_COT_TYPE,
        time = now,
        start = now,
        stale = now + staleMinutes.minutes,
        how = "m-g",
        latitude = 0.0,
        longitude = 0.0,
        contact = CoTContact(callsign = callsign, endpoint = DEFAULT_TAK_ENDPOINT),
        group = CoTGroup(name = team, role = role),
        status = CoTStatus(battery = battery),
        remarks = remarks,
    )
}
