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
import kotlinx.coroutines.CancellationException
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
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.roundToInt

private const val TAG = "MapOverlayRenderer"

/**
 * Cap on a spooled KMZ's size: `openStream` can be an unbounded network fetch, and osmbonuspack's KMZ path needs the
 * whole archive on disk before it can parse it. 50 MB comfortably covers a legitimate overlay plus embedded icons.
 */
private const val MAX_KMZ_BYTES = 50L * 1024 * 1024

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
            val doc = parse(layer, openStream, map.context.cacheDir) ?: continue
            val overlay =
                withContext(Dispatchers.Main.immediate) {
                    // Build on the main thread: overlay markers reference the MapView (info windows, defaults).
                    doc.mKmlRoot.buildOverlay(map, null, SimpleStyleStyler(map, doc), doc).also {
                        insertBelowMarkers(map, it)
                    }
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

    private suspend fun parse(
        layer: MapLayerItem,
        openStream: suspend (MapLayerItem) -> InputStream?,
        cacheDir: File,
    ): KmlDocument? {
        val stream = openStream(layer) ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val doc = KmlDocument()
                val ok =
                    stream.use { input ->
                        when (layer.layerType) {
                            LayerType.GEOJSON,
                            LayerType.COVERAGE,
                            -> doc.parseGeoJSON(input.bufferedReader().readText())

                            LayerType.KML -> {
                                val buffered = BufferedInputStream(input)
                                if (buffered.isKmzArchive()) {
                                    parseKmz(buffered, doc, cacheDir, layer.name)
                                } else {
                                    doc.parseKMLStream(buffered, null)
                                }
                            }
                        }
                    }
                if (ok) {
                    doc
                } else {
                    Logger.withTag(TAG).w { "Failed to parse map layer (malformed KML/KMZ/GeoJSON?): ${layer.name}" }
                    null
                }
            } catch (e: CancellationException) {
                // reconcile() is re-invoked on every layer-list change, cancelling an in-flight parse routinely —
                // not a load failure. Matches the Google flavor's MapLayerOverlay handling of the same pattern.
                throw e
            } catch (e: Exception) {
                Logger.withTag(TAG).e(e) { "Error parsing map layer: ${layer.name}" }
                null
            }
        }
    }

    /**
     * osmbonuspack only exposes KMZ parsing via [KmlDocument.parseKMZFile], which needs random-access zip seeking (to
     * resolve embedded images) that a sequential [InputStream] can't do. So spool the already-sniffed KMZ stream to a
     * temp file — matching how the Google flavor treats KMZ uniformly regardless of whether the layer's source is a
     * local file or a network fetch — rather than reimplementing the unzip here.
     */
    private fun parseKmz(stream: InputStream, doc: KmlDocument, cacheDir: File, layerName: String): Boolean {
        val temp = File.createTempFile("layer", ".kmz", cacheDir)
        return try {
            val bytesCopied = temp.outputStream().use { stream.copyToBounded(it, MAX_KMZ_BYTES) }
            if (bytesCopied > MAX_KMZ_BYTES) {
                // Oversized input, not an app defect — warn (matching the malformed-input path), don't error.
                Logger.withTag(TAG).w { "KMZ layer exceeds $MAX_KMZ_BYTES byte limit, skipping: $layerName" }
                false
            } else {
                doc.parseKMZFile(temp)
            }
        } finally {
            temp.delete()
        }
    }

    /**
     * Like [InputStream.copyTo], but stops as soon as more than [limit] bytes have been read, returning the count read
     * so far (which will exceed [limit]) instead of continuing to buffer an unbounded stream to disk.
     */
    private fun InputStream.copyToBounded(out: OutputStream, limit: Long): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read == -1) return total
            total += read
            if (total > limit) return total
            out.write(buffer, 0, read)
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
 * osmbonuspack stores as KML ExtendedData) onto the built osmdroid geometry, layered on top of osmbonuspack's own
 * native KML `<Style>` resolution.
 *
 * osmbonuspack's `buildOverlay()` is an either/or branch: passing a non-null [KmlFeature.Styler] (as [reconcile] does,
 * for both KML and GeoJSON) makes it skip `applyDefaultStyling()` entirely and call this styler instead — it is NOT an
 * addition on top of the default. So every `on*` override here calls the matching `applyDefaultStyling()` itself first
 * (resolving the placemark's real KML `<Style>`/`<IconStyle>`, and — for polygons — the tap info-window bubble), then
 * applies simplestyle overrides only when the corresponding GeoJSON property is actually present.
 */
private class SimpleStyleStyler(private val map: MapView, private val kmlDocument: KmlDocument) : KmlFeature.Styler {
    override fun onFeature(overlay: Overlay?, kmlFeature: KmlFeature?) = Unit

    override fun onPoint(marker: Marker?, kmlPlacemark: KmlPlacemark?, kmlPoint: KmlPoint?) {
        marker ?: return
        kmlPoint?.applyDefaultStyling(marker, null, kmlPlacemark, kmlDocument, map)
        // simplestyle's marker-color/marker-symbol aren't modeled here (they'd need Maki icon
        // sprites or manual icon tinting) — KML's own <IconStyle>, applied above, is what this
        // fixes; GeoJSON points still fall back to osmdroid's default marker icon.
    }

    override fun onLineString(polyline: Polyline?, kmlPlacemark: KmlPlacemark?, kmlLineString: KmlLineString?) {
        polyline ?: return
        kmlLineString?.applyDefaultStyling(polyline, null, kmlPlacemark, kmlDocument, map)
        val stroke = kmlPlacemark?.cssColor("stroke") ?: kmlPlacemark?.cssColor("color")
        stroke?.let { polyline.color = it }
        kmlPlacemark?.getExtendedData("stroke-width")?.toFloatOrNull()?.let { polyline.width = it }
    }

    override fun onPolygon(polygon: Polygon?, kmlPlacemark: KmlPlacemark?, kmlPolygon: KmlPolygon?) {
        polygon ?: return
        kmlPolygon?.applyDefaultStyling(polygon, null, kmlPlacemark, kmlDocument, map)
        val hasNativeKmlStyle = kmlDocument.getStyle(kmlPlacemark?.mStyle) != null
        val fill = kmlPlacemark?.cssColor("fill") ?: kmlPlacemark?.cssColor("color")
        val stroke = kmlPlacemark?.cssColor("stroke") ?: kmlPlacemark?.cssColor("color")
        val fillOpacity = kmlPlacemark?.getExtendedData("fill-opacity")?.toFloatOrNull()
        val strokeWidth = kmlPlacemark?.getExtendedData("stroke-width")?.toFloatOrNull()
        fill?.let { polygon.fillColor = it.resolveFillAlpha(fillOpacity) }
        stroke?.let { polygon.strokeColor = it }
        // Only fall back to the GeoJSON default when neither a real KML <Style> nor an explicit
        // simplestyle stroke-width applied above — otherwise this would overwrite a native KML
        // polygon's own stroke width every time (KML files don't carry a stroke-width ExtendedData).
        polygon.strokeWidth =
            strokeWidth ?: if (hasNativeKmlStyle) polygon.strokeWidth else DEFAULT_GEOJSON_STROKE_WIDTH
    }

    override fun onTrack(polyline: Polyline?, kmlPlacemark: KmlPlacemark?, kmlTrack: KmlTrack?) {
        polyline ?: return
        kmlTrack?.applyDefaultStyling(polyline, null, kmlPlacemark, kmlDocument, map)
    }
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
