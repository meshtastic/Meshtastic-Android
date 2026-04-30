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

import org.meshtastic.proto.MemberRole
import org.meshtastic.proto.Team
import org.meshtastic.proto.User

internal const val DEFAULT_TAK_PORT = 8087
internal const val DEFAULT_TAK_ENDPOINT = "0.0.0.0:4242:tcp"
internal const val DEFAULT_TAK_TEAM_NAME = "Cyan"
internal const val DEFAULT_TAK_ROLE_NAME = "Team Member"
internal const val DEFAULT_TAK_BATTERY = 100
internal const val DEFAULT_TAK_STALE_MINUTES = 10
internal const val TAK_HEX_RADIX = 16
internal const val TAK_XML_READ_BUFFER_SIZE = 4_096
internal const val TAK_KEEPALIVE_INTERVAL_MS = 30_000L
internal const val TAK_KEEPALIVE_STALE_MULTIPLIER = 3
internal const val TAK_READ_IDLE_TIMEOUT_MULTIPLIER = 5
internal const val TAK_ACCEPT_LOOP_DELAY_MS = 100L
internal const val TAK_COORDINATE_SCALE = 1e7
internal const val TAK_UNKNOWN_POINT_VALUE = 9_999_999.0
internal const val TAK_DIRECT_MESSAGE_PARTS_MIN = 3

internal fun Team?.toTakTeamName(): String = when (this) {
    null,
    Team.Unspecifed_Color,
    -> DEFAULT_TAK_TEAM_NAME

    else -> name.replace('_', ' ')
}

internal fun MemberRole?.toTakRoleName(): String = when (this) {
    null,
    MemberRole.Unspecifed,
    -> DEFAULT_TAK_ROLE_NAME

    MemberRole.TeamMember -> DEFAULT_TAK_ROLE_NAME

    MemberRole.TeamLead -> "Team Lead"

    MemberRole.ForwardObserver -> "Forward Observer"

    else -> name
}

internal fun User.toTakCallsign(): String = when {
    short_name.isNotBlank() -> short_name
    long_name.isNotBlank() -> long_name
    else -> id
}
