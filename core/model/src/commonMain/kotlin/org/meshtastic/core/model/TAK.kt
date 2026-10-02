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
package org.meshtastic.core.model

import org.meshtastic.proto.Team

@Suppress("CyclomaticComplexMethod", "MagicNumber")
fun getColorFrom(team: Team): Long = when (team) {
    Team.Unspecifed_Color -> 0xFF00FFFF

    // Default to Cyan
    Team.White -> 0xFFFFFFFF

    Team.Yellow -> 0xFFFFFF00

    Team.Orange -> 0xFFFFA500

    Team.Magenta -> 0xFFFF00FF

    Team.Red -> 0xFFFF0000

    Team.Maroon -> 0xFF800000

    Team.Purple -> 0xFF800080

    Team.Dark_Blue -> 0xFF00008B

    Team.Blue -> 0xFF0000FF

    Team.Cyan -> 0xFF00FFFF

    Team.Teal -> 0xFF008080

    Team.Green -> 0xFF00FF00

    Team.Dark_Green -> 0xFF006400

    Team.Brown -> 0xFFA52A2A
}
