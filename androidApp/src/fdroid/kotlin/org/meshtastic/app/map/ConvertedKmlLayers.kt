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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.feature.map.maplibre.layers.CustomLayer
import java.io.BufferedInputStream
import java.io.File

private const val CONVERTED_DIR = "kml-geojson"

/**
 * Turns the visible map layers into what MapLibre can actually fetch.
 *
 * GeoJSON and Site Planner coverage estimates pass straight through. KML and KMZ are converted once into a GeoJSON file
 * in the cache and handed over as that file's URI — MapLibre has no KML source, and until now these were filtered out
 * entirely: the import appeared in the layers sheet, could be toggled on, and drew nothing at all.
 *
 * Conversion is keyed by layer id *and* refresh token, so a refresh reconverts and a visibility toggle does not. The
 * result is cached in memory for the session, and the file it writes is reused across sessions — an existing file for a
 * given id-and-token cannot be stale, because a refresh changes the token and so changes the filename.
 */
@Composable
internal fun rememberRenderableLayers(layers: List<MapLayerItem>): List<CustomLayer> {
    val context = LocalContext.current
    val converted = remember { mutableStateMapOf<String, String>() }

    val kmlLayers = layers.filter { it.layerType == LayerType.KML && it.uri != null }

    // Keyed on the ids-plus-tokens rather than the list: a layer's visibility flicking on and off must not reconvert.
    val conversionKey = kmlLayers.joinToString(",") { "${it.id}@${it.refreshToken}" }
    LaunchedEffect(conversionKey) {
        kmlLayers.forEach { layer ->
            val key = layer.conversionKey()
            if (converted.containsKey(key)) return@forEach
            convertKmlLayer(context, layer)?.let { converted[key] = it }
        }
    }

    // A KML layer is absent until its conversion finishes, and for good if the file held nothing mappable.
    return layers.mapNotNull { layer ->
        when {
            layer.layerType != LayerType.KML ->
                layer.uri?.let { CustomLayer(id = layer.id, uri = it.toString(), refreshToken = layer.refreshToken) }

            else ->
                converted[layer.conversionKey()]?.let {
                    CustomLayer(id = layer.id, uri = it, refreshToken = layer.refreshToken)
                }
        }
    }
}

private fun MapLayerItem.conversionKey(): String = "$id@$refreshToken"

/** Converts one KML or KMZ import to a GeoJSON file in the cache, returning its URI. Null if nothing was mappable. */
private suspend fun convertKmlLayer(context: Context, layer: MapLayerItem): String? = withContext(Dispatchers.IO) {
    val source = layer.uri ?: return@withContext null
    safeCatching {
        val target =
            File(File(context.cacheDir, CONVERTED_DIR).apply { mkdirs() }, "${layer.conversionKey()}.geojson")
        // A file already here was converted from this same layer at this same token, so it is still good.
        if (target.length() > 0L) return@safeCatching Uri.fromFile(target).toString()

        val geoJson =
            context.contentResolver.openInputStream(source)?.use { stream ->
                KmlToGeoJson.convert(BufferedInputStream(stream))
            }
        if (geoJson == null) {
            Logger.withTag("KmlLayers").w { "Nothing mappable in an imported KML layer" }
            return@safeCatching null
        }
        // Written beside the target and moved into place, so a conversion cut short by leaving the screen
        // cannot
        // leave a half-file that the check above would then trust.
        val partial = File(target.parentFile, "${target.name}.part")
        partial.writeText(geoJson)
        partial.renameTo(target)
        Uri.fromFile(target).toString()
    }
        .onFailure { Logger.withTag("KmlLayers").w(it) { "Could not convert an imported KML layer" } }
        .getOrNull()
}
