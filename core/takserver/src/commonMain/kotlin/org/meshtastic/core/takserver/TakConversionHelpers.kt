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

/**
 * Internal helpers shared by [TAKPacketConversion] (legacy v1, firmware <= 2.7.x) and [TAKPacketV2Conversion]
 * (firmware >= 2.8.x). Both paths map between the SDK's [CoTMessage] model and Meshtastic's Wire-generated proto types
 * using identical logic for color/role lookup and the "<senderUid>|<messageId>" smuggled-callsign format that survives
 * the wire round trip.
 */
internal object TakConversionHelpers {

    /** Split a `<senderUid>|<messageId>` smuggled callsign back into its parts. */
    fun parseDeviceCallsign(combined: String): Pair<String, String?> {
        val parts = combined.split("|", limit = 2)
        return if (parts.size == 2) {
            Pair(parts[0], parts[1].ifEmpty { null })
        } else {
            Pair(combined, null)
        }
    }

    /** Map a [Team] proto enum back to its CoT color-name string. Unspecified -> default. */
    fun teamToColorName(team: Team?): String {
        if (team == null || team == Team.Unspecifed_Color) return DEFAULT_TAK_TEAM_NAME
        return team.toTakTeamName()
    }

    /** Map a [MemberRole] proto enum back to its CoT role-name string. Unspecified -> default. */
    fun roleToName(role: MemberRole?): String {
        if (role == null || role == MemberRole.Unspecifed) return DEFAULT_TAK_ROLE_NAME
        return role.toTakRoleName()
    }

    /** Reverse lookup from CoT color-name string to [Team] proto enum value (0 = Unspecified). */
    fun getTeamValue(name: String): Int = Team.entries.find { it.name.equals(name, ignoreCase = true) }?.value ?: 0

    /** Reverse lookup from CoT role-name string to [MemberRole] proto enum value (0 = Unspecified). */
    fun getMemberRoleValue(roleName: String): Int =
        MemberRole.entries.find { it.name.equals(roleName.replace(" ", ""), ignoreCase = true) }?.value ?: 0
}
