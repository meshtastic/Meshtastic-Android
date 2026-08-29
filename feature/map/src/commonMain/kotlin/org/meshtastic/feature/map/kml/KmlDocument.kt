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
package org.meshtastic.feature.map.kml

/**
 * The KML text inside [bytes] — the file itself if it is bare KML, or the first `.kml` entry if it is a KMZ archive.
 *
 * Expect/actual because unpacking the archive takes `java.util.zip`, which iOS does not have: there the KMZ case
 * returns null (and nothing renders a map there yet anyway). Bare KML is common to all platforms.
 */
expect fun readKmlDocument(bytes: ByteArray): String?
