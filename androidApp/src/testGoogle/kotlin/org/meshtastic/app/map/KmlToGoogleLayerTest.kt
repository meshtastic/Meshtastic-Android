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

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import com.google.maps.android.data.parser.geojson.GeoJsonParser
import com.google.maps.android.data.renderer.mapper.toLayer
import com.google.maps.android.data.renderer.model.PointStyle
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The seam the Google map's KML import now runs through.
 *
 * It used to parse KML with maps-utils' own `data.parser` KmlParser, which shared one resolved xmlutil with the app's
 * CoT code — a pairing that made every KML/KMZ import die with `NoSuchMethodError` in 2.8.0–2.8.1. KML is now read by
 * the app's converter and handed to maps-utils only as GeoJSON, so what needs guarding is that handover: that
 * maps-utils accepts what the converter writes, and that a placemark's icon survives it.
 *
 * This replaces `KmlParserLinkageTest`, whose subject no longer exists.
 */
@RunWith(RobolectricTestRunner::class)
class KmlToGoogleLayerTest {

    private val kmlWithIcon =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <kml xmlns="http://www.opengis.net/kml/2.2"><Document>
          <Style id="tower"><IconStyle><Icon>
            <href>https://example.org/tower.png</href>
          </Icon></IconStyle></Style>
          <Placemark><name>Repeater</name><styleUrl>#tower</styleUrl>
            <Point><coordinates>-107.62,34.07</coordinates></Point></Placemark>
          <Placemark><name>Plain</name>
            <Point><coordinates>-107.60,34.05</coordinates></Point></Placemark>
        </Document></kml>
        """
            .trimIndent()

    private val kmlNamingPackedIcon =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <kml xmlns="http://www.opengis.net/kml/2.2"><Document>
          <Style id="packed"><IconStyle><Icon><href>files/tower.png</href></Icon></IconStyle></Style>
          <Placemark><name>Packed</name><styleUrl>#packed</styleUrl>
            <Point><coordinates>-107.62,34.07</coordinates></Point></Placemark>
        </Document></kml>
        """
            .trimIndent()

    /** A KMZ holding [kml] as `doc.kml` plus a one-pixel PNG at [imageEntry]. */
    private fun kmz(kml: String, imageEntry: String): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("doc.kml"))
            zip.write(kml.toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(imageEntry))
            zip.write(onePixelPng())
            zip.closeEntry()
        }
        return bytes.toByteArray()
    }

    private fun onePixelPng(): ByteArray {
        val bitmap = createBitmap(1, 1)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    private fun layerFrom(archive: ByteArray) = convertKmlSource(archive.inputStream().buffered())?.let { imported ->
        GeoJsonParser().parse(imported.geoJson.byteInputStream())?.toLayer()?.applySimpleStyleSpec()?.let { layer ->
            layer.copy(properties = layer.properties + ("images" to imported.images))
        }
    }

    private fun layerFrom(kml: String) = layerFrom(kml.toByteArray())

    @Test
    fun `maps-utils parses what the converter writes`() {
        val layer = assertNotNull(layerFrom(kmlWithIcon))

        assertEquals(2, layer.features.size)
    }

    @Test
    fun `a placemark's icon reaches the style maps-utils renders`() {
        // maps-utils' GeoJSON mapper reads no icon property of its own, so without the mapping this test guards, the
        // Google map would silently lose the icons it has drawn all along.
        val layer = assertNotNull(layerFrom(kmlWithIcon))
        val styles = layer.features.map { it.style }

        assertTrue(
            styles.any { it is PointStyle && it.iconUrl == "https://example.org/tower.png" },
            "no feature carried the icon: $styles",
        )
    }

    @Test
    fun `a placemark with no icon is left with the mapper's own style`() {
        val layer = assertNotNull(layerFrom(kmlWithIcon))
        val iconed = layer.features.count { (it.style as? PointStyle)?.iconUrl != null }

        assertEquals(1, iconed)
    }

    @Test
    fun `a KMZ hands its packed images to the renderer's cache`() {
        // maps-utils used to extract these itself, in the KML parser this replaced. The renderer resolves an
        // archive-relative href through its image cache, which is seeded from this property — so a KMZ whose icons
        // live inside it draws nothing without them.
        val archive = kmz(kmlNamingPackedIcon, imageEntry = "files/tower.png")

        val layer = assertNotNull(layerFrom(archive))

        @Suppress("UNCHECKED_CAST")
        val images = layer.properties["images"] as? Map<String, *>
        assertNotNull(images)
        assertTrue("files/tower.png" in images, "packed image missing: ${images.keys}")
    }

    @Test
    fun `a file with nothing mappable yields no layer rather than an empty one`() {
        assertEquals(null, layerFrom("<kml><Document><Folder/></Document></kml>"))
    }
}
