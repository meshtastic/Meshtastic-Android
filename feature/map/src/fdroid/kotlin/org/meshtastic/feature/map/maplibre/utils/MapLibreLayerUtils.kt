/*
 * Copyright (c) 2025 Meshtastic LLC
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

package org.meshtastic.feature.map.maplibre.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import org.meshtastic.feature.map.LayerType
import org.meshtastic.feature.map.MapLayerItem
import org.w3c.dom.Element
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** Loads persisted map layers from internal storage */
suspend fun loadPersistedLayers(context: Context): List<MapLayerItem> = withContext(Dispatchers.IO) {
    try {
        val layersDir = File(context.filesDir, "map_layers")
        if (layersDir.exists() && layersDir.isDirectory) {
            val persistedLayerFiles = layersDir.listFiles()
            persistedLayerFiles?.mapNotNull { file ->
                if (file.isFile) {
                    val layerType =
                        when (file.extension.lowercase()) {
                            "kml",
                            "kmz",
                            -> LayerType.KML
                            "geojson",
                            "json",
                            -> LayerType.GEOJSON
                            else -> null
                        }
                    layerType?.let {
                        val uri = Uri.fromFile(file)
                        MapLayerItem(name = file.nameWithoutExtension, uri = uri, isVisible = true, layerType = it)
                    }
                } else {
                    null
                }
            } ?: emptyList()
        } else {
            emptyList()
        }
    } catch (e: Exception) {
        Timber.tag("MapLibreLayerUtils").e(e, "Error loading persisted map layers")
        emptyList()
    }
}

/** Copies a file from URI to internal storage */
suspend fun copyFileToInternalStorage(context: Context, uri: Uri, fileName: String): Uri? =
    withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val directory = File(context.filesDir, "map_layers")
            if (!directory.exists()) {
                directory.mkdirs()
            }
            val outputFile = File(directory, fileName)
            val outputStream = FileOutputStream(outputFile)
            inputStream?.use { input -> outputStream.use { output -> input.copyTo(output) } }
            Uri.fromFile(outputFile)
        } catch (e: IOException) {
            Timber.tag("MapLibreLayerUtils").e(e, "Error copying file to internal storage")
            null
        }
    }

/** Deletes a file from internal storage */
suspend fun deleteFileFromInternalStorage(uri: Uri) = withContext(Dispatchers.IO) {
    try {
        val file = uri.toFile()
        if (file.exists()) {
            file.delete()
        }
    } catch (e: Exception) {
        Timber.tag("MapLibreLayerUtils").e(e, "Error deleting file from internal storage")
    }
}

/**
 * Gets InputStream from URI.
 *
 * **Important:** Caller is responsible for closing the returned stream using `.use {}` or try-with-resources.
 *
 * @Suppress("Recycle") is used because this function intentionally returns an unclosed stream for the caller to manage.
 */
@Suppress("Recycle")
suspend fun getInputStreamFromUri(context: Context, uri: Uri): InputStream? = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openInputStream(uri)
    } catch (_: Exception) {
        Timber.d("MapLibreLayerUtils: Error opening InputStream from URI: $uri")
        null
    }
}

/** Converts KML content to GeoJSON string */
suspend fun convertKmlToGeoJson(context: Context, layerItem: MapLayerItem): String? = withContext(Dispatchers.IO) {
    try {
        val uri = layerItem.uri ?: return@withContext null
        Timber.tag("MapLibreLayerUtils").d("convertKmlToGeoJson: uri=%s", uri)
        val inputStream = getInputStreamFromUri(context, uri) ?: return@withContext null

        inputStream.use { stream ->
            // Handle KMZ (ZIP) files
            val isKmz = layerItem.layerType == LayerType.KML && uri.toString().endsWith(".kmz", ignoreCase = true)
            Timber.tag("MapLibreLayerUtils").d("File type: isKmz=%s", isKmz)

            val content =
                if (isKmz) {
                    Timber.tag("MapLibreLayerUtils").d("Extracting KML from KMZ...")
                    extractKmlFromKmz(stream)
                } else {
                    Timber.tag("MapLibreLayerUtils").d("Reading KML directly...")
                    stream.bufferedReader().use { it.readText() }
                }

            if (content == null) {
                Timber.tag("MapLibreLayerUtils").w("Failed to extract KML content")
                return@withContext null
            }

            Timber.tag("MapLibreLayerUtils").d("KML content extracted: %d bytes", content.length)
            val result = parseKmlToGeoJson(content)
            Timber.tag("MapLibreLayerUtils").d("KML parsed to GeoJSON: %d bytes", result.length)
            result
        }
    } catch (e: Exception) {
        Timber.tag("MapLibreLayerUtils").e(e, "Error converting KML to GeoJSON")
        null
    }
}

/** Extracts KML content from KMZ (ZIP) file */
private fun extractKmlFromKmz(inputStream: InputStream): String? {
    return try {
        val zipInputStream = ZipInputStream(inputStream)
        var entry = zipInputStream.nextEntry

        // Look for KML file in the ZIP (usually named "doc.kml" or similar)
        while (entry != null) {
            val fileName = entry.name.lowercase()
            if (fileName.endsWith(".kml")) {
                // Read content without closing the stream
                val kmlContent = zipInputStream.bufferedReader().readText()
                zipInputStream.closeEntry()
                Timber.tag("MapLibreLayerUtils").d("Extracted KML from KMZ: ${entry.name}")
                return kmlContent
            }
            zipInputStream.closeEntry()
            entry = zipInputStream.nextEntry
        }

        Timber.tag("MapLibreLayerUtils").w("No KML file found in KMZ archive")
        null
    } catch (e: Exception) {
        Timber.tag("MapLibreLayerUtils").e(e, "Error extracting KML from KMZ")
        null
    }
}

/** Parses KML XML and converts to GeoJSON */
private fun parseKmlToGeoJson(kmlContent: String): String {
    try {
        Timber.tag("MapLibreLayerUtils").d("parseKmlToGeoJson: Parsing %d bytes of KML", kmlContent.length)

        // Log first 500 chars of KML to see structure
        val preview = kmlContent.take(500)
        Timber.tag("MapLibreLayerUtils").d("KML preview: %s", preview)

        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(kmlContent.byteInputStream())

        // Log root element
        val root = doc.documentElement
        Timber.tag("MapLibreLayerUtils").d("Root element: tagName=%s, namespaceURI=%s", root.tagName, root.namespaceURI)

        val features = mutableListOf<Feature>()

        // Parse Placemarks (points, lines, polygons)
        val placemarks = doc.getElementsByTagName("Placemark")
        Timber.tag("MapLibreLayerUtils").d("Found %d Placemark elements", placemarks.length)

        // Also try with namespace
        val placemarks2 = doc.getElementsByTagNameNS("*", "Placemark")
        Timber.tag("MapLibreLayerUtils").d("Found %d Placemark elements (with NS wildcard)", placemarks2.length)

        // Check for GroundOverlays (raster images)
        val groundOverlays = doc.getElementsByTagName("GroundOverlay")
        if (groundOverlays.length > 0) {
            Timber.tag("MapLibreLayerUtils")
                .w(
                    "Found %d GroundOverlay elements (raster tiles). These are not supported - only vector features (Placemarks) can be displayed.",
                    groundOverlays.length,
                )
        }

        for (i in 0 until placemarks.length) {
            val placemark = placemarks.item(i) as? Element ?: continue
            val name = placemark.getElementsByTagName("name").item(0)?.textContent ?: ""

            // Try Point
            val point = placemark.getElementsByTagName("Point").item(0) as? Element
            point?.let {
                val coordinates = it.getElementsByTagName("coordinates").item(0)?.textContent?.trim()
                coordinates?.let { coordStr ->
                    val parts = coordStr.split(",")
                    if (parts.size >= 2) {
                        val lon = parts[0].toDoubleOrNull() ?: return@let
                        val lat = parts[1].toDoubleOrNull() ?: return@let
                        val point = Point.fromLngLat(lon, lat)
                        val feature = Feature.fromGeometry(point)
                        feature.addStringProperty("name", name)
                        features.add(feature)
                    }
                }
            }

            // Try LineString
            val lineString = placemark.getElementsByTagName("LineString").item(0) as? Element
            lineString?.let {
                val coordinates = it.getElementsByTagName("coordinates").item(0)?.textContent?.trim()
                coordinates?.let { coordStr ->
                    val points = parseCoordinates(coordStr)
                    if (points.size >= 2) {
                        val lineString = LineString.fromLngLats(points)
                        val feature = Feature.fromGeometry(lineString)
                        feature.addStringProperty("name", name)
                        features.add(feature)
                    }
                }
            }

            // Try Polygon
            val polygon = placemark.getElementsByTagName("Polygon").item(0) as? Element
            polygon?.let {
                val outerBoundary = it.getElementsByTagName("outerBoundaryIs").item(0) as? Element
                outerBoundary?.let {
                    val linearRing = it.getElementsByTagName("LinearRing").item(0) as? Element
                    linearRing?.let {
                        val coordinates = it.getElementsByTagName("coordinates").item(0)?.textContent?.trim()
                        coordinates?.let { coordStr ->
                            val points = parseCoordinates(coordStr)
                            if (points.size >= 3) {
                                // Close the polygon
                                val closedPoints = points + points[0]
                                val polygon = Polygon.fromLngLats(listOf(closedPoints))
                                val feature = Feature.fromGeometry(polygon)
                                feature.addStringProperty("name", name)
                                features.add(feature)
                            }
                        }
                    }
                }
            }
        }

        Timber.tag("MapLibreLayerUtils").d("Parsed %d features from KML", features.size)
        val featureCollection = FeatureCollection.fromFeatures(features)
        val json = featureCollection.toJson()
        Timber.tag("MapLibreLayerUtils").d("Generated GeoJSON: %d bytes", json.length)
        return json
    } catch (e: Exception) {
        Timber.tag("MapLibreLayerUtils").e(e, "Error parsing KML")
        return """{"type":"FeatureCollection","features":[]}"""
    }
}

/** Parses coordinate string into list of Points */
private fun parseCoordinates(coordStr: String): List<Point> = coordStr.split(" ").mapNotNull { line ->
    val parts = line.trim().split(",")
    if (parts.size >= 2) {
        val lon = parts[0].toDoubleOrNull()
        val lat = parts[1].toDoubleOrNull()
        if (lon != null && lat != null) {
            Point.fromLngLat(lon, lat)
        } else {
            null
        }
    } else {
        null
    }
}

/** Loads GeoJSON from a layer item (converting KML if needed) */
suspend fun loadLayerGeoJson(context: Context, layerItem: MapLayerItem): String? = withContext(Dispatchers.IO) {
    Timber.tag("MapLibreLayerUtils")
        .d("loadLayerGeoJson: name=%s, type=%s, uri=%s", layerItem.name, layerItem.layerType, layerItem.uri)
    val result =
        when (layerItem.layerType) {
            LayerType.KML -> {
                Timber.tag("MapLibreLayerUtils").d("Converting KML to GeoJSON for: %s", layerItem.name)
                convertKmlToGeoJson(context, layerItem)
            }
            LayerType.GEOJSON -> {
                Timber.tag("MapLibreLayerUtils").d("Loading GeoJSON directly for: %s", layerItem.name)
                val uri = layerItem.uri ?: return@withContext null
                getInputStreamFromUri(context, uri)?.use { stream -> stream.bufferedReader().use { it.readText() } }
            }
        }
    if (result != null) {
        Timber.tag("MapLibreLayerUtils")
            .d("Successfully loaded GeoJSON for %s: %d bytes", layerItem.name, result.length)
    } else {
        Timber.tag("MapLibreLayerUtils").e("Failed to load GeoJSON for: %s", layerItem.name)
    }
    result
}

/** Extension function to get file name from URI */
fun Uri.getFileName(context: Context): String? {
    var name = this.lastPathSegment
    if (this.scheme == "content") {
        context.contentResolver.query(this, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    name = cursor.getString(nameIndex)
                }
            }
        }
    }
    return name
}
