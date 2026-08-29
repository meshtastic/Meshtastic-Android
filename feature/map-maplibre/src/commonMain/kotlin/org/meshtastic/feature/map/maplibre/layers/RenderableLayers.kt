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
import org.meshtastic.core.common.util.ioDispatcher
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.feature.map.geojson.geoJsonIconUrls
import org.meshtastic.feature.map.kml.KmlToGeoJson
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
    val converted = remember { mutableStateMapOf<String, String>() }
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
                    converted[layer.conversionKey()]?.let {
                        CustomLayer(id = layer.id, uri = it, refreshToken = layer.refreshToken)
                    }
            }
        }

    // The renderer has to know a layer's icons before it composes, and the only place they exist is the GeoJSON
    // itself. Reading the finished document rather than threading the set out of the KML converter means an imported
    // GeoJSON that names its own icons gets them too, and a cached conversion does not have to remember them.
    val localLayers = layers.filterNot { it.isNetwork }.associateBy { it.id }
    val iconKey = renderable.joinToString(",") { "${it.id}@${it.refreshToken}" }
    LaunchedEffect(iconKey) {
        renderable.forEach { layer ->
            val key = "${layer.id}@${layer.refreshToken}"
            if (icons.containsKey(key) || localLayers[layer.id] == null) return@forEach
            icons[key] = scanLayerIcons(layer.uri)
        }
    }

    return renderable.map { layer -> layer.copy(icons = icons["${layer.id}@${layer.refreshToken}"].orEmpty()) }
}

/**
 * The icons a rendered layer's GeoJSON asks for, or none if it cannot be read.
 *
 * Network layers are skipped by the caller: their document lives behind a URL MapLibre fetches itself, and pulling it
 * down a second time here to look for icons is not worth it.
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

/** Converts one KML or KMZ import to a GeoJSON file in the cache, returning its URI. Null if nothing was mappable. */
private suspend fun convertKmlLayer(manager: MapLayersManager, layer: MapLayerItem): String? =
    withContext(ioDispatcher) {
        safeCatching {
            val fs = mapLayerFileSystem()
            val dir = mapLayersCacheDirectory()
            fs.createDirectories(dir)
            val target = dir / "${layer.conversionKey()}.geojson"
            // A file already here was converted from this same layer at this same token, so it is still good.
            if ((fs.metadataOrNull(target)?.size ?: 0L) > 0L) return@safeCatching "file://$target"

            val bytes = manager.readLayerBytes(layer)
            val geoJson = bytes?.let { readKmlDocument(it) }?.let { KmlToGeoJson.convert(it) }
            if (geoJson == null) {
                Logger.withTag(TAG).w { "Nothing mappable in an imported KML layer" }
                return@safeCatching null
            }
            // Written beside the target and moved into place, so a conversion cut short by leaving the screen
            // cannot
            // leave a half-file that the check above would then trust.
            val partial = dir / "${target.name}.part"
            fs.write(partial) { writeUtf8(geoJson) }
            fs.atomicMove(partial, target)
            "file://$target"
        }
            .onFailure { Logger.withTag(TAG).w(it) { "Could not convert an imported KML layer" } }
            .getOrNull()
    }

private const val TAG = "KmlLayers"
