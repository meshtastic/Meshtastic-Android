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

import android.graphics.Color
import androidx.core.graphics.toColorInt
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.meshtastic.app.map.cluster.RadiusMarkerClusterer
import org.osmdroid.bonuspack.kml.KmlDocument
import org.osmdroid.bonuspack.kml.KmlFeature
import org.osmdroid.bonuspack.kml.KmlLineString
import org.osmdroid.bonuspack.kml.KmlPlacemark
import org.osmdroid.bonuspack.kml.KmlPoint
import org.osmdroid.bonuspack.kml.KmlPolygon
import org.osmdroid.bonuspack.kml.KmlTrack
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import java.io.InputStream
import kotlin.math.roundToInt

private const val TAG = "MapOverlayRenderer"

// simplestyle-spec fallbacks, mirroring the Google flavor's applySimpleStyleSpec(); tune here.
private const val DEFAULT_GEOJSON_FILL_OPACITY = 0.35f
private const val DEFAULT_GEOJSON_STROKE_WIDTH = 2f
private const val OPAQUE = 255

/**
 * F-Droid flavor's map-overlay renderer: turns the shared [MapLayerItem] list into OSMdroid overlays via osmbonuspack's
 * [KmlDocument], honoring per-feature mapbox **simplestyle** (`fill`/`stroke`/`fill-opacity`/`stroke-width`) so
 * imported coverage draws in its dBm colors — the OSMdroid mirror of the Google flavor's
 * `GeoJsonLayer.applySimpleStyleSpec()`.
 *
 * A single instance is kept for the lifetime of the map composable. [reconcile] is called whenever the layer list
 * changes; it adds newly-visible layers, removes gone/hidden ones, and rebuilds any whose URI or refresh flag changed.
 */
class FdroidMapOverlayRenderer {

    private data class Rendered(val signature: String, val overlay: Overlay)

    // id -> currently-drawn overlay. Touched only from reconcile()'s (single-flight) coroutine.
    private val rendered = mutableMapOf<String, Rendered>()

    private fun signatureOf(item: MapLayerItem) = "${item.uri}|${item.refreshToken}"

    /** Reconcile the map's overlays with [layers]. [openStream] resolves a layer to its data (file or network). */
    suspend fun reconcile(
        map: MapView,
        layers: List<MapLayerItem>,
        openStream: suspend (MapLayerItem) -> InputStream?,
    ) {
        val visible = layers.filter { it.isVisible && it.uri != null }
        val wanted = visible.associateBy { it.id }
        var dirty = false

        // Drop overlays that are gone, hidden, or whose signature changed (rebuild).
        val stale = rendered.filter { (id, r) -> wanted[id]?.let { signatureOf(it) == r.signature } != true }
        if (stale.isNotEmpty()) {
            withContext(Dispatchers.Main.immediate) { stale.values.forEach { map.overlays.remove(it.overlay) } }
            stale.keys.forEach { rendered.remove(it) }
            dirty = true
        }

        // Build overlays for visible layers not already drawn.
        for (layer in visible) {
            if (rendered.containsKey(layer.id)) continue
            val doc = parse(layer, openStream) ?: continue
            val overlay =
                withContext(Dispatchers.Main.immediate) {
                    // Build on the main thread: overlay markers reference the MapView (info windows, defaults).
                    doc.mKmlRoot.buildOverlay(map, null, SimpleStyleStyler, doc).also { insertBelowMarkers(map, it) }
                }
            rendered[layer.id] = Rendered(signatureOf(layer), overlay)
            dirty = true
        }

        if (dirty) withContext(Dispatchers.Main.immediate) { map.invalidate() }
    }

    /**
     * Remove every layer overlay. Call on the main thread (e.g. from onDispose); the OSMdroid map outlives composition.
     */
    fun removeAll(map: MapView) {
        if (rendered.isEmpty()) return
        rendered.values.forEach { map.overlays.remove(it.overlay) }
        rendered.clear()
        map.invalidate()
    }

    private suspend fun parse(layer: MapLayerItem, openStream: suspend (MapLayerItem) -> InputStream?): KmlDocument? {
        val stream = openStream(layer) ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val doc = KmlDocument()
                val ok =
                    stream.use { input ->
                        when (layer.layerType) {
                            LayerType.GEOJSON -> doc.parseGeoJSON(input.bufferedReader().readText())
                            LayerType.KML -> doc.parseKMLStream(input, null)
                        }
                    }
                if (ok) doc else null
            } catch (e: Exception) {
                Logger.withTag(TAG).e(e) { "Error parsing map layer: ${layer.name}" }
                null
            }
        }
    }

    // Keep coverage under the node markers/clusterer so nodes stay visible + tappable (matching the Google flavor).
    private fun insertBelowMarkers(map: MapView, overlay: Overlay) {
        val idx = map.overlays.indexOfFirst { it is RadiusMarkerClusterer || it is Marker }
        if (idx >= 0) map.overlays.add(idx, overlay) else map.overlays.add(overlay)
    }
}

/**
 * osmbonuspack styler that maps mapbox simplestyle properties (read from a GeoJSON feature's `properties`, which
 * osmbonuspack stores as KML ExtendedData) onto the built osmdroid geometry. Only overrides when a property is present,
 * so KML files keep their own `<Style>`.
 */
private object SimpleStyleStyler : KmlFeature.Styler {
    override fun onFeature(overlay: Overlay?, kmlFeature: KmlFeature?) = Unit

    override fun onPoint(marker: Marker?, kmlPlacemark: KmlPlacemark?, kmlPoint: KmlPoint?) = Unit

    override fun onLineString(polyline: Polyline?, kmlPlacemark: KmlPlacemark?, kmlLineString: KmlLineString?) {
        polyline ?: return
        val stroke = kmlPlacemark?.cssColor("stroke") ?: kmlPlacemark?.cssColor("color")
        stroke?.let { polyline.color = it }
        kmlPlacemark?.getExtendedData("stroke-width")?.toFloatOrNull()?.let { polyline.width = it }
    }

    override fun onPolygon(polygon: Polygon?, kmlPlacemark: KmlPlacemark?, kmlPolygon: KmlPolygon?) {
        polygon ?: return
        val fill = kmlPlacemark?.cssColor("fill") ?: kmlPlacemark?.cssColor("color")
        val stroke = kmlPlacemark?.cssColor("stroke") ?: kmlPlacemark?.cssColor("color")
        val fillOpacity = kmlPlacemark?.getExtendedData("fill-opacity")?.toFloatOrNull()
        val strokeWidth = kmlPlacemark?.getExtendedData("stroke-width")?.toFloatOrNull() ?: DEFAULT_GEOJSON_STROKE_WIDTH
        fill?.let { polygon.fillColor = it.resolveFillAlpha(fillOpacity) }
        stroke?.let { polygon.strokeColor = it }
        polygon.strokeWidth = strokeWidth
    }

    override fun onTrack(polyline: Polyline?, kmlPlacemark: KmlPlacemark?, kmlTrack: KmlTrack?) = Unit
}

private fun KmlPlacemark.cssColor(key: String): Int? = getExtendedData(key)?.let { parseCssColor(it) }

/**
 * Resolve a polygon fill's alpha: `fill-opacity` wins when present; otherwise keep any alpha the color already carries
 * (`rgba()`/`#AARRGGBB`), falling back to [DEFAULT_GEOJSON_FILL_OPACITY] for opaque fills.
 */
private fun Int.resolveFillAlpha(fillOpacity: Float?): Int = when {
    fillOpacity != null -> withAlpha(fillOpacity)
    Color.alpha(this) < OPAQUE -> this
    else -> withAlpha(DEFAULT_GEOJSON_FILL_OPACITY)
}

/** Parse a hex (`#RRGGBB`/`#AARRGGBB`), `rgb()/rgba()`, or named color to an ARGB int; null if invalid. */
private fun parseCssColor(raw: String): Int? {
    val value = raw.trim()
    return try {
        if (value.startsWith("rgb", ignoreCase = true)) {
            val parts = value.substringAfter('(').substringBefore(')').split(',').map { it.trim() }
            if (parts.size < 3) return null
            val alpha = if (parts.size >= 4) (parts[3].toFloat() * OPAQUE).roundToInt() else OPAQUE
            Color.argb(alpha, parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
        } else {
            value.toColorInt() // #hex or named color
        }
    } catch (e: IllegalArgumentException) {
        Logger.withTag(TAG).w(e) { "Unparseable GeoJSON color: $raw" }
        null
    }
}

private fun Int.withAlpha(opacity: Float): Int =
    Color.argb((opacity.coerceIn(0f, 1f) * OPAQUE).roundToInt(), Color.red(this), Color.green(this), Color.blue(this))
