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

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertContains
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What the Android side of KML import still owns: recognising a KMZ and pulling the KML out of it.
 *
 * The converter itself is common code and tested in `feature/map`. These stay here because a zip archive and the JVM
 * default locale are both platform concerns — the locale case in particular guards the formatting that once emitted
 * `0,498` into the JSON and made every import silently draw nothing.
 */
@RunWith(RobolectricTestRunner::class)
class KmlImportTest {

    private fun convert(kml: String): String? =
        convertKmlSource(BufferedInputStream(ByteArrayInputStream(kml.toByteArray())))

    private fun kmz(kml: String, entryName: String = "doc.kml"): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry(entryName))
            zip.write(kml.toByteArray())
            zip.closeEntry()
        }
        return bytes.toByteArray()
    }

    @Test
    fun `a kmz is unzipped and read`() {
        val archive = kmz("<kml><Placemark><Point><coordinates>-107.6,34.1</coordinates></Point></Placemark></kml>")
        val result = assertNotNull(convertKmlSource(BufferedInputStream(ByteArrayInputStream(archive))))

        assertContains(result, """"coordinates":[-107.6,34.1]""")
    }

    @Test
    fun `a kmz whose kml is not named doc is still found`() {
        val archive =
            kmz(
                "<kml><Placemark><Point><coordinates>1,2</coordinates></Point></Placemark></kml>",
                entryName = "files/export.kml",
            )

        assertNotNull(convertKmlSource(BufferedInputStream(ByteArrayInputStream(archive))))
    }

    @Test
    fun `a comma-decimal locale still emits valid json`() {
        // `%f` follows the device locale, so on a German phone the default would write 0,498 — invalid JSON, and
        // MapLibre rejects the whole file, so every KML import would silently draw nothing.
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val result =
                assertNotNull(
                    convert(
                        """
                        <kml><Document>
                          <Style id="zone"><PolyStyle><color>7f00ff00</color></PolyStyle></Style>
                          <Placemark><styleUrl>#zone</styleUrl><Polygon><outerBoundaryIs><LinearRing>
                            <coordinates>0,0 1,0 1,1 0,0</coordinates>
                          </LinearRing></outerBoundaryIs></Polygon></Placemark>
                        </Document></kml>
                        """,
                    ),
                )

            // The whole point: a dot, not a comma, in the property this locale would have written as 0,498.
            assertContains(result, """"fill-opacity":0.498""")
            assertTrue(""""fill-opacity":0,""" !in result, result)
        } finally {
            Locale.setDefault(original)
        }
    }
}
