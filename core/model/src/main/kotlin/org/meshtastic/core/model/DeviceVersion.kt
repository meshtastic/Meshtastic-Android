/*
 * Copyright (c) 2025 Meshtastic LLC
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

import co.touchlab.kermit.Logger

/** Provide structured access to parse and compare device version strings */
data class DeviceVersion(val asString: String) : Comparable<DeviceVersion> {

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    val asInt
        get() =
            try {
                verStringToInt(asString)
            } catch (e: Exception) {
                Logger.w { "Exception while parsing version '$asString', assuming version 0" }
                0
            }

    /**
     * Convert a version string of the form 1.23.57 to a comparable integer of the form 12357.
     *
     * Or throw an exception if the string can not be parsed
     */
    @Suppress("TooGenericExceptionThrown", "MagicNumber")
    private fun verStringToInt(s: String): Int {
        // Allow 1 to two digits per match
        val versionString =
            if (s.split(".").size == 2) {
                "$s.0"
            } else {
                s
            }
        val match =
            Regex("(\\d{1,2}).(\\d{1,2}).(\\d{1,2})").find(versionString) ?: throw Exception("Can't parse version $s")
        val (major, minor, build) = match.destructured
        return major.toInt() * 10000 + minor.toInt() * 100 + build.toInt()
    }

    override fun compareTo(other: DeviceVersion): Int = asInt - other.asInt
}
