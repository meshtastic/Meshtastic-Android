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
package org.meshtastic.feature.map.maplibre.layers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import co.touchlab.kermit.Logger
import kotlinx.coroutines.withContext
import okio.Path
import org.meshtastic.core.common.util.ioDispatcher
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.feature.map.geojson.geoJsonIconUrls
import org.meshtastic.feature.map.kml.KmlGroundOverlay
import org.meshtastic.feature.map.kml.KmlToGeoJson
import org.meshtastic.feature.map.kml.corners
import org.meshtastic.feature.map.kml.readKmlArchiveImages
import org.meshtastic.feature.map.kml.readKmlDocument
import org.meshtastic.feature.map.layers.LayerType
import org.meshtastic.feature.map.layers.MapLayerItem
import org.meshtastic.feature.map.layers.MapLayersManager
import org.meshtastic.feature.map.layers.mapLayerFileSystem
import org.meshtastic.feature.map.layers.mapLayersCacheDirectory
import org.meshtastic.feature.map.layers.toLocalPath

/**
 * Turns the visible map layers into what MapLibre can actually fetch.
 *
 * GeoJSON and Site Planner coverage estimates pass straight through. KML and KMZ are converted once into a GeoJSON file
 * in the cache and handed over as that file's URI — MapLibre has no KML source, and before this existed those imports
 * appeared in the layers sheet, could be toggled on, and drew nothing at all.
 *
 * Common code because both MapLibre hosts need it: the F-Droid map and the desktop map render the same imported layers.
 * Reading goes through [MapLayersManager.readLayerBytes], which also covers network layers — the old Android-only
 * version opened them through a ContentResolver, which cannot open `http`, so a network KML layer silently drew
 * nothing.
 *
 * Conversion is keyed by layer id *and* refresh token, so a refresh reconverts and a visibility toggle does not. The
 * result is cached in memory for the session, and the file it writes is reused across sessions — an existing file for a
 * given id-and-token cannot be stale, because a refresh changes the token and so changes the filename.
 */
@Composable
fun rememberRenderableLayers(manager: MapLayersManager, layers: List<MapLayerItem>): List<CustomLayer> {
    val converted = remember { mutableStateMapOf<String, ConvertedKml>() }
    val icons = remember { mutableStateMapOf<String, Set<String>>() }

    val kmlLayers = layers.filter { it.layerType == LayerType.KML && it.uri != null }

    // Keyed on the ids-plus-tokens rather than the list: a layer's visibility flicking on and off must not reconvert.
    val conversionKey = kmlLayers.joinToString(",") { it.conversionKey() }
    LaunchedEffect(conversionKey) {
        kmlLayers.forEach { layer ->
            val key = layer.conversionKey()
            if (!converted.containsKey(key)) {
                convertKmlLayer(manager, layer)?.let { converted[key] = it }
            }
        }
    }

    // A KML layer is absent until its conversion finishes, and for good if the file held nothing mappable.
    val renderable =
        layers.mapNotNull { layer ->
            when {
                layer.layerType != LayerType.KML ->
                    layer.uri?.let { CustomLayer(id = layer.id, uri = it, refreshToken = layer.refreshToken) }

                else ->
                    converted[layer.conversionKey()]?.let { conversion ->
                        CustomLayer(
                            id = layer.id,
                            uri = conversion.geoJsonUri,
                            refreshToken = layer.refreshToken,
                            groundOverlays = conversion.groundOverlays,
                        )
                    }
            }
        }

    // The renderer has to know a layer's icons before it composes, and the only place they exist is the GeoJSON
    // itself. Reading the finished document rather than threading the set out of the KML converter means an imported
    // GeoJSON that names its own icons gets them too, and a cached conversion does not have to remember them.
    val iconKey = renderable.joinToString(",") { "${it.id}@${it.refreshToken}" }
    LaunchedEffect(iconKey) {
        renderable.forEach { layer ->
            val key = "${layer.id}@${layer.refreshToken}"
            // Anything whose document ended up as a local file can be scanned — including a network KML, whose
            // conversion wrote one. Only a network GeoJSON stays unscanned: its document lives behind a URL
            // MapLibre fetches itself, and pulling it down a second time just for icons is not worth it.
            if (icons.containsKey(key) || !layer.uri.startsWith(FILE_URI_PREFIX)) return@forEach
            icons[key] = scanLayerIcons(layer.uri)
        }
    }

    return renderable.map { layer -> layer.copy(icons = icons["${layer.id}@${layer.refreshToken}"].orEmpty()) }
}

/**
 * The icons a rendered layer's GeoJSON asks for, or none if it cannot be read.
 *
 * Only called for documents that live in a local file — see the gate at the call site.
 */
private suspend fun scanLayerIcons(uri: String): Set<String> = withContext(ioDispatcher) {
    safeCatching {
        val fs = mapLayerFileSystem()
        fs.read(uri.toLocalPath()) { geoJsonIconUrls(readUtf8()) }
    }
        .onFailure { Logger.withTag(TAG).w(it) { "Could not read icons from an imported layer" } }
        .getOrNull()
        .orEmpty()
}

private fun MapLayerItem.conversionKey(): String = "$id@$refreshToken"

private fun CustomLayer.conversionKey(): String = "$id@$refreshToken"

/** What one KML conversion produced: the GeoJSON file's URI and the draped images. */
internal data class ConvertedKml(val geoJsonUri: String, val groundOverlays: List<LayerGroundOverlay>)

/**
 * Converts one KML or KMZ import: GeoJSON to a cache file, ground-overlay images extracted beside it, and the overlay
 * corner data into a sidecar the cache hit can read back — the GeoJSON cannot carry it, and reconverting on every
 * launch would defeat the cache. Null when nothing in the file was mappable at all.
 */
private suspend fun convertKmlLayer(manager: MapLayersManager, layer: MapLayerItem): ConvertedKml? =
    withContext(ioDispatcher) {
        safeCatching {
            val fs = mapLayerFileSystem()
            val dir = mapLayersCacheDirectory()
            fs.createDirectories(dir)
            val key = layer.conversionKey()
            val target = dir / "$key.geojson"
            val sidecar = dir / "$key.overlays"

            // Files already here were converted from this same layer at this same token, so they are still good —
            // but only if every extracted image also survived: Android may clear individual cache files, and a
            // sidecar pointing at deleted images would drape nothing until a manual refresh.
            val cachedOverlays = readOverlaySidecar(sidecar)
            val imagesIntact =
                cachedOverlays?.all { (fs.metadataOrNull(it.imagePath.toLocalPath())?.size ?: 0L) > 0L } == true
            if (imagesIntact && (fs.metadataOrNull(target)?.size ?: 0L) > 0L) {
                return@safeCatching ConvertedKml("$FILE_URI_PREFIX$target", cachedOverlays.orEmpty())
            }

            val bytes = manager.readLayerBytes(layer) ?: return@safeCatching null
            val document = readKmlDocument(bytes) ?: return@safeCatching null
            val conversion = KmlToGeoJson.convertDocument(document)
            val overlays = resolveGroundOverlays(layer, bytes, conversion.groundOverlays, dir)
            if (conversion.geoJson == null && overlays.isEmpty()) {
                Logger.withTag(TAG).w { "Nothing mappable in an imported KML layer" }
                return@safeCatching null
            }

            // An overlay-only document still writes a (empty) collection: the layer keeps its uniform shape, and a
            // real file is something MapLibre certainly fetches, where a data: URI is a gamble. Written beside the
            // target and moved into place, so a conversion cut short by leaving the screen cannot leave a half-file
            // that the cache check above would then trust.
            val partial = dir / "${target.name}.part"
            fs.write(partial) { writeUtf8(conversion.geoJson ?: EMPTY_FEATURE_COLLECTION) }
            fs.atomicMove(partial, target)
            writeOverlaySidecar(sidecar, overlays)
            ConvertedKml("$FILE_URI_PREFIX$target", overlays)
        }
            .onFailure { Logger.withTag(TAG).w(it) { "Could not convert an imported KML layer" } }
            .getOrNull()
    }

/**
 * Resolve each overlay's image to a file in the cache. A KMZ-packed image is extracted; anything else — above all the
 * Site Planner's KML export, whose href names a sibling file that was never in an archive — is logged and skipped
 * rather than draped as a broken image.
 */
private fun resolveGroundOverlays(
    layer: MapLayerItem,
    bytes: ByteArray,
    overlays: List<KmlGroundOverlay>,
    dir: Path,
): List<LayerGroundOverlay> {
    if (overlays.isEmpty()) return emptyList()
    val fs = mapLayerFileSystem()
    val packed = readKmlArchiveImages(bytes, overlays.map { it.href }.toSet())
    return overlays.mapIndexedNotNull { index, overlay ->
        val image = packed[overlay.href]
        if (image == null) {
            // Count, not href: an overlay's image name is user content and does not belong in the log.
            Logger.withTag(TAG).w { "Skipping a ground overlay whose image is not packed in the archive" }
            return@mapIndexedNotNull null
        }
        // The href is file content, so its "extension" can hold anything — a '/' in it would put a separator
        // in the cache filename and fail the write. Cosmetic anyway: decoders sniff bytes, not names.
        val extension =
            overlay.href.substringAfterLast('.', "png").takeIf {
                it.length <= MAX_EXTENSION_LENGTH && it.isNotEmpty() && it.all(Char::isLetterOrDigit)
            } ?: "png"
        val path = dir / "${layer.conversionKey()}-overlay-$index.$extension"
        fs.write(path) { write(image) }
        val corners = overlay.corners()
        LayerGroundOverlay(
            imagePath = path.toString(),
            corners =
            listOf(
                corners.topLeft.longitude to corners.topLeft.latitude,
                corners.topRight.longitude to corners.topRight.latitude,
                corners.bottomRight.longitude to corners.bottomRight.latitude,
                corners.bottomLeft.longitude to corners.bottomLeft.latitude,
            ),
        )
    }
}

private fun readOverlaySidecar(sidecar: Path): List<LayerGroundOverlay>? {
    val fs = mapLayerFileSystem()
    if (fs.metadataOrNull(sidecar) == null) return null
    return safeCatching {
        val lines = fs.read(sidecar) { readUtf8() }.lines().filter { it.isNotBlank() }
        lines.map { line ->
            val parts = line.split('\t')
            LayerGroundOverlay(
                imagePath = parts[0],
                corners =
                parts.drop(1).map { pair ->
                    val (lon, lat) = pair.split(',')
                    lon.toDouble() to lat.toDouble()
                },
            )
        }
    }
        .getOrNull()
}

/**
 * One overlay per line: the image path, then four `lon,lat` corners, tab-separated. Hand-rolled rather than
 * serialization because the shape is four numbers and a path, and a corrupt file just costs a reconversion.
 */
private fun writeOverlaySidecar(sidecar: Path, overlays: List<LayerGroundOverlay>) {
    val fs = mapLayerFileSystem()
    val text =
        overlays.joinToString("\n") { overlay ->
            (listOf(overlay.imagePath) + overlay.corners.map { (lon, lat) -> "$lon,$lat" }).joinToString("\t")
        }
    fs.write(sidecar) { writeUtf8(text) }
}

private const val TAG = "KmlLayers"

/** What an overlay-only conversion writes where its vector features would go. */
private const val EMPTY_FEATURE_COLLECTION = """{"type":"FeatureCollection","features":[]}"""

/** The scheme a layer URI carries when its document is a file this process can read. */
private const val FILE_URI_PREFIX = "file://"

/** Longest file extension carried over from an overlay's href; longer or stranger falls back to `png`. */
private const val MAX_EXTENSION_LENGTH = 8
