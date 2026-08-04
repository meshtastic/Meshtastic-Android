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
package org.meshtastic.app.map

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import co.touchlab.kermit.Logger
import org.meshtastic.core.common.util.nowMillis
import kotlin.uuid.Uuid

/**
 * Flavor-neutral map-layer model shared by the Google (Google Maps data layer) and F-Droid (OSMdroid overlay) flavors.
 * Only the final overlay draw is flavor-specific; the layer list, storage, and import logic live in [MapLayersManager].
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
    val uri: Uri? = null,
    val isVisible: Boolean = true,
    val layerType: LayerType,
    val isNetwork: Boolean = false,
    /** Wall-clock creation time, from the backing file's mtime. Null for network layers, which have no local file. */
    val createdAt: Long? = null,
    /** UI indicator: whether a refresh is in flight (drives the sheet/toolbar spinner). */
    val isRefreshing: Boolean = false,
    /**
     * Monotonic counter bumped on refresh so the flavor renderers reliably re-read the layer. A [StateFlow] conflates
     * transient values, so a bounced boolean flag can be missed — an ever-increasing token cannot.
     */
    val refreshToken: Int = 0,
)

private val KML_EXTENSIONS = listOf("kml", "kmz", "vnd.google-earth.kml+xml", "vnd.google-earth.kmz")
private val GEOJSON_EXTENSIONS = listOf("geojson", "json")

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
 * Characters not allowed in an on-disk layer file name: path separators, so an import can't traverse out of
 * `map_layers/`, plus control characters. Everything else — spaces, punctuation, non-Latin scripts — is kept, because
 * the list rebuilds a layer's display name from this file name and users should see the name they chose.
 */
private val FILE_NAME_UNSAFE = Regex("""[/\\\p{Cntrl}]""")

/**
 * Longest sanitized base name kept, in characters. Bounds the total path length: the UUID suffix and extension add ~46
 * bytes, and a non-Latin name can reach 4 bytes per character, which would otherwise overrun the 255-byte file name
 * limit and fail the write.
 */
private const val MAX_BASE_NAME_CHARS = 40

/**
 * Build the on-disk file name for a layer from its [displayName].
 *
 * [displayName] is untrusted (a DISPLAY_NAME/lastPathSegment from another app's share/open-with), so separators are
 * stripped to keep the write inside `map_layers/`. The UUID suffix is load-bearing, not cosmetic: two layers sharing a
 * display name would otherwise resolve to the same path and the second write would truncate the first. It also means a
 * name of `..` can never itself be the file name. [displayNameFromFileName] strips it back off for display.
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

/**
 * Resolve a display file name for [this] URI, querying the content resolver for `content://` URIs. Untrusted providers
 * (share/open-with from other apps) can throw or return a null display name, so guard both and fall back to the URI's
 * last path segment.
 */
@Suppress("NestedBlockDepth")
fun Uri.getFileName(context: Context): String {
    var name = lastPathSegment ?: "layer_$nowMillis"
    if (scheme == "content") {
        try {
            context.contentResolver.query(this, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (displayNameIndex != -1) {
                        cursor.getString(displayNameIndex)?.let { name = it }
                    }
                }
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Keep the lastPathSegment fallback assigned above rather than crashing the import.
            Logger.withTag("MapLayer").w(e) { "Failed to resolve display name for content URI; using fallback" }
        }
    }
    return name
}
