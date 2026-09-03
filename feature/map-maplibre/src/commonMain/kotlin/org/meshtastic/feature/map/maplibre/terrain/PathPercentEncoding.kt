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
package org.meshtastic.feature.map.maplibre.terrain

/**
 * Percent-encodes the characters a legitimate Desktop/JVM user-data path can contain that are reserved in a URL — a
 * space in a Windows/macOS username is the common case, `#` (fragment) and a literal `%` less so but just as real. `/`
 * passes through untouched, since it is [OfflineTerrainRepository.tileUrlTemplate]'s own path separator, not something
 * to escape.
 *
 * Deliberately narrow rather than a full RFC 3986 path encoder: the directory it is applied to is platform file-system
 * output, not arbitrary or untrusted input, so only the characters that would otherwise break the `file://` URL need
 * handling. Its own file, not a member of [OfflineTerrainRepository], so that class stays under detekt's
 * `TooManyFunctions` per-class/per-file thresholds.
 */
internal fun String.percentEncodeUrlReserved(): String = buildString {
    for (char in this@percentEncodeUrlReserved) {
        when (char) {
            ' ' -> append("%20")
            '#' -> append("%23")
            '%' -> append("%25")
            else -> append(char)
        }
    }
}
