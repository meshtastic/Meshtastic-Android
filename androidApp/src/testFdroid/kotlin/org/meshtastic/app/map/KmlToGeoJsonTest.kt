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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The KML reader, which is what lets an imported KML draw on the MapLibre map at all.
 *
 * Runs under Robolectric because the parser is `android.util.Xml`'s, the one Android ships.
 */
@RunWith(RobolectricTestRunner::class)
class KmlToGeoJsonTest {

    private fun convert(kml: String): String? =
        KmlToGeoJson.convert(BufferedInputStream(ByteArrayInputStream(kml.toByteArray())))

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
    fun `a point placemark becomes a point feature`() {
        val result =
            assertNotNull(
                convert(
                    """
                    <kml><Document><Placemark>
                      <name>Repeater</name>
                      <Point><coordinates>-107.6197182,34.1320507,2137</coordinates></Point>
                    </Placemark></Document></kml>
                    """,
                ),
            )

        assertContains(result, """"type":"FeatureCollection"""")
        assertContains(result, """"type":"Point"""")
        // Altitude is dropped: nothing downstream reads it.
        assertContains(result, """"coordinates":[-107.6197182,34.1320507]""")
        assertContains(result, """"title":"Repeater"""")
    }

    @Test
    fun `a line placemark keeps its order and needs two positions`() {
        val result =
            assertNotNull(
                convert(
                    """
                    <kml><Placemark><LineString>
                      <coordinates>-107.62,34.07 -107.60,34.10 -107.59,34.14</coordinates>
                    </LineString></Placemark></kml>
                    """,
                ),
            )

        assertContains(result, """"type":"LineString"""")
        assertContains(result, """[-107.62,34.07],[-107.6,34.1],[-107.59,34.14]""")
    }

    @Test
    fun `a one-position line is not emitted as a line`() {
        assertNull(
            convert(
                "<kml><Placemark><LineString><coordinates>-107.62,34.07</coordinates></LineString></Placemark></kml>",
            ),
        )
    }

    @Test
    fun `a polygon ring is closed even when the source leaves it open`() {
        val result =
            assertNotNull(
                convert(
                    """
                    <kml><Placemark><Polygon><outerBoundaryIs><LinearRing>
                      <coordinates>-107.62,34.07 -107.59,34.07 -107.59,34.14</coordinates>
                    </LinearRing></outerBoundaryIs></Polygon></Placemark></kml>
                    """,
                ),
            )

        assertContains(result, """"type":"Polygon"""")
        // First position repeated at the end.
        assertContains(result, """[[[-107.62,34.07],[-107.59,34.07],[-107.59,34.14],[-107.62,34.07]]]""")
    }

    @Test
    fun `a polygon hole is ignored rather than drawn as a second shape`() {
        val result =
            assertNotNull(
                convert(
                    """
                    <kml><Placemark><Polygon>
                      <outerBoundaryIs><LinearRing>
                        <coordinates>0,0 4,0 4,4 0,4 0,0</coordinates>
                      </LinearRing></outerBoundaryIs>
                      <innerBoundaryIs><LinearRing>
                        <coordinates>1,1 2,1 2,2 1,2 1,1</coordinates>
                      </LinearRing></innerBoundaryIs>
                    </Polygon></Placemark></kml>
                    """,
                ),
            )

        assertEquals(1, Regex("\"type\":\"Polygon\"").findAll(result).count())
        assertContains(result, """[[[0.0,0.0],[4.0,0.0],[4.0,4.0],[0.0,4.0],[0.0,0.0]]]""")
    }

    @Test
    fun `kml colour bytes are reversed into css and split into an opacity`() {
        val result =
            assertNotNull(
                convert(
                    """
                    <kml><Document>
                      <Style id="route"><LineStyle><color>ff0000ff</color><width>4</width></LineStyle></Style>
                      <Placemark><styleUrl>#route</styleUrl>
                        <LineString><coordinates>0,0 1,1</coordinates></LineString>
                      </Placemark>
                    </Document></kml>
                    """,
                ),
            )

        // aabbggrr: ff 00 00 ff is opaque red, not opaque blue.
        assertContains(result, """"stroke":"#ff0000"""")
        assertContains(result, """"stroke-opacity":1.000""")
        assertContains(result, """"stroke-width":4.0""")
    }

    @Test
    fun `a half-transparent poly fill carries its opacity`() {
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

        assertContains(result, """"fill":"#00ff00"""")
        assertTrue(result.contains(""""fill-opacity":0.49""") || result.contains(""""fill-opacity":0.50"""), result)
    }

    @Test
    fun `fill zero means outline only`() {
        val result =
            assertNotNull(
                convert(
                    """
                    <kml><Document>
                      <Style id="outline"><PolyStyle><color>ff0000ff</color><fill>0</fill></PolyStyle></Style>
                      <Placemark><styleUrl>#outline</styleUrl><Polygon><outerBoundaryIs><LinearRing>
                        <coordinates>0,0 1,0 1,1 0,0</coordinates>
                      </LinearRing></outerBoundaryIs></Polygon></Placemark>
                    </Document></kml>
                    """,
                ),
            )

        assertTrue(""""fill":""" !in result, result)
    }

    @Test
    fun `a style map resolves through its normal pair`() {
        val result =
            assertNotNull(
                convert(
                    """
                    <kml><Document>
                      <Style id="plain"><LineStyle><color>ff00ff00</color></LineStyle></Style>
                      <StyleMap id="pair">
                        <Pair><key>normal</key><styleUrl>#plain</styleUrl></Pair>
                        <Pair><key>highlight</key><styleUrl>#other</styleUrl></Pair>
                      </StyleMap>
                      <Placemark><styleUrl>#pair</styleUrl>
                        <LineString><coordinates>0,0 1,1</coordinates></LineString>
                      </Placemark>
                    </Document></kml>
                    """,
                ),
            )

        assertContains(result, """"stroke":"#00ff00"""")
    }

    @Test
    fun `a description with quotes and newlines stays valid json`() {
        val result =
            assertNotNull(
                convert(
                    "<kml><Placemark><name>A</name><description>say \"hi\"\nthen go</description>" +
                        "<Point><coordinates>1,2</coordinates></Point></Placemark></kml>",
                ),
            )

        assertContains(result, """say \"hi\"\nthen go""")
    }

    @Test
    fun `several placemarks all reach the collection`() {
        val result =
            assertNotNull(
                convert(
                    """
                    <kml><Document>
                      <Placemark><Point><coordinates>1,1</coordinates></Point></Placemark>
                      <Placemark><Point><coordinates>2,2</coordinates></Point></Placemark>
                      <Placemark><LineString><coordinates>3,3 4,4</coordinates></LineString></Placemark>
                    </Document></kml>
                    """,
                ),
            )

        assertEquals(3, Regex("\"type\":\"Feature\"").findAll(result).count())
    }

    @Test
    fun `a kmz is unzipped and read`() {
        val archive = kmz("<kml><Placemark><Point><coordinates>-107.6,34.1</coordinates></Point></Placemark></kml>")
        val result = assertNotNull(KmlToGeoJson.convert(BufferedInputStream(ByteArrayInputStream(archive))))

        assertContains(result, """"coordinates":[-107.6,34.1]""")
    }

    @Test
    fun `a kmz whose kml is not named doc is still found`() {
        val archive =
            kmz(
                "<kml><Placemark><Point><coordinates>1,2</coordinates></Point></Placemark></kml>",
                entryName = "files/export.kml",
            )

        assertNotNull(KmlToGeoJson.convert(BufferedInputStream(ByteArrayInputStream(archive))))
    }

    @Test
    fun `a file with nothing mappable converts to nothing rather than an empty collection`() {
        // An empty FeatureCollection would draw nothing but claim the import succeeded.
        assertNull(convert("<kml><Document><name>Empty</name></Document></kml>"))
        assertNull(convert("not xml at all"))
    }

    @Test
    fun `unsupported kml features are skipped without losing the rest of the file`() {
        val result =
            assertNotNull(
                convert(
                    """
                    <kml><Document>
                      <GroundOverlay><name>Imagery</name><Icon><href>x.png</href></Icon></GroundOverlay>
                      <NetworkLink><Link><href>remote.kml</href></Link></NetworkLink>
                      <Placemark><Point><coordinates>5,6</coordinates></Point></Placemark>
                    </Document></kml>
                    """,
                ),
            )

        assertEquals(1, Regex("\"type\":\"Feature\"").findAll(result).count())
        assertContains(result, """"coordinates":[5.0,6.0]""")
    }
}
