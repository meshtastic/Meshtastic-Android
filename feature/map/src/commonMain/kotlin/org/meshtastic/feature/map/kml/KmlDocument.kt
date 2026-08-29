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

/**
 * The image entries a KMZ packs for its ground overlays, keyed by the entry name an overlay's `href` uses.
 *
 * Only entries named in [hrefs] are extracted — a KMZ can carry hundreds of images, and pulling all of them for a
 * document whose overlays name three would be pure waste. Bare KML returns nothing: its hrefs point outside the file,
 * and fetching those is the caller's decision. Expect/actual for the same reason as [readKmlDocument].
 */
expect fun readKmlArchiveImages(bytes: ByteArray, hrefs: Set<String>): Map<String, ByteArray>
