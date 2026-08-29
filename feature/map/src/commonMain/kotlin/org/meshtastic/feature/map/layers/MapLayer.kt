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
package org.meshtastic.feature.map.layers

import kotlin.uuid.Uuid

/**
 * The imported-overlay model, shared by every renderer.
 *
 * Only the final overlay draw is platform- or flavour-specific; the layer list, its storage and the import plumbing
 * live in [MapLayersManager]. This is common code rather than Android's because the desktop app imports layers too — so
 * [uri] is a plain string: an Android `content://`, a `file://` under the app's own storage, or an `http(s)` network
 * layer, parsed where it is used rather than by a platform URI type here.
 */
enum class LayerType {
    KML,
    GEOJSON,

    /**
     * A Site Planner coverage estimate. GeoJSON on the wire and parsed as such, but tracked as its own type so the
     * layers sheet can distinguish an estimate we generated from a GeoJSON file the user imported.
     */
    COVERAGE,
}

data class MapLayerItem(
    val id: String = Uuid.random().toString(),
    val name: String,
    val uri: String? = null,
    val isVisible: Boolean = true,
    val layerType: LayerType,
    val isNetwork: Boolean = false,
    /** Wall-clock creation time, from the backing file's mtime. Null for network layers, which have no local file. */
    val createdAt: Long? = null,
    /** UI indicator: whether a refresh is in flight (drives the sheet/toolbar spinner). */
    val isRefreshing: Boolean = false,
    /**
     * Monotonic counter bumped on refresh so the renderers reliably re-read the layer. A `StateFlow` conflates
     * transient values, so a bounced boolean flag can be missed — an ever-increasing token cannot.
     */
    val refreshToken: Int = 0,
)

private val KML_EXTENSIONS = listOf("kml", "kmz", "vnd.google-earth.kml+xml", "vnd.google-earth.kmz")
private val GEOJSON_EXTENSIONS = listOf("geojson", "json")

/** Zip magic bytes; a [LayerType.KML] source starting with these is a KMZ archive rather than bare KML. */
private val KMZ_MAGIC = byteArrayOf('P'.code.toByte(), 'K'.code.toByte())

/**
 * True if [this] starts with the zip magic bytes, meaning a nominally-[LayerType.KML] source (`.kml` and `.kmz` both
 * resolve to that one type) is actually a KMZ archive. Sniffed rather than taken from the file extension, which a
 * content resolver can get wrong.
 */
fun ByteArray.isKmzArchive(): Boolean = size >= KMZ_MAGIC.size && KMZ_MAGIC.indices.all { this[it] == KMZ_MAGIC[it] }

/** On-disk extension marking a saved coverage estimate, so [LayerType.COVERAGE] survives a restart. */
const val COVERAGE_EXTENSION = "coverage"

/**
 * Coverage estimates append a random UUID to their on-disk name so two estimates saved under the same title don't
 * collide. The layers list rebuilds its display name from that file name, so strip the suffix or the raw UUID shows up
 * in the UI.
 */
private val TRAILING_UUID =
    Regex("_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE)

/** Recover a layer's display name from its on-disk file name (sans extension). */
fun displayNameFromFileName(fileNameWithoutExtension: String): String =
    fileNameWithoutExtension.replace(TRAILING_UUID, "")

/**
 * Characters not allowed in an on-disk layer file name: path separators, so an import can't traverse out of the layers
 * directory, plus control characters. Everything else — spaces, punctuation, non-Latin scripts — is kept, because the
 * list rebuilds a layer's display name from this file name and users should see the name they chose.
 */
private val FILE_NAME_UNSAFE = Regex("""[/\\p{Cntrl}]""")

/**
 * Longest sanitized base name kept, in characters. Bounds the total path length: the UUID suffix and extension add ~46
 * bytes, and a non-Latin name can reach 4 bytes per character, which would otherwise overrun the 255-byte file name
 * limit and fail the write.
 */
private const val MAX_BASE_NAME_CHARS = 40

/**
 * Build the on-disk file name for a layer from its [displayName].
 *
 * [displayName] is untrusted (a name from another app's share/open-with, or whatever the user called the file), so
 * separators are stripped to keep the write inside the layers directory. The UUID suffix is load-bearing, not cosmetic:
 * two layers sharing a display name would otherwise resolve to the same path and the second write would truncate the
 * first. It also means a name of `..` can never itself be the file name. [displayNameFromFileName] strips it back off
 * for display.
 */
fun layerFileName(displayName: String, extension: String): String {
    val safeBase = displayName.replace(FILE_NAME_UNSAFE, "_").take(MAX_BASE_NAME_CHARS)
    return "${safeBase}_${Uuid.random()}.$extension"
}

/**
 * Resolve a file extension or MIME subtype (e.g. `geojson`, `vnd.geo+json`) to a [LayerType], or null if unsupported.
 */
fun resolveLayerType(extensionOrMime: String?): LayerType? = when (extensionOrMime?.lowercase()) {
    in KML_EXTENSIONS -> LayerType.KML

    in GEOJSON_EXTENSIONS -> LayerType.GEOJSON

    COVERAGE_EXTENSION -> LayerType.COVERAGE

    // MIME subtypes the content resolver may report for GeoJSON that aren't a bare "geojson"/"json".
    "geo+json",
    "vnd.geo+json",
    -> LayerType.GEOJSON

    else -> null
}
