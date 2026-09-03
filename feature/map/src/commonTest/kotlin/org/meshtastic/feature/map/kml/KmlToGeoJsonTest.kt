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
package org.meshtastic.feature.map.kml

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The KML reader, which is what lets an imported KML draw on the MapLibre map at all.
 *
 * Common code, so these run on the JVM and iOS as well as Android — the parser is xmlutil's, not the platform's.
 */
class KmlToGeoJsonTest {

    private fun convert(kml: String): String? = KmlToGeoJson.convert(kml)

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
    fun `a line crossing the antimeridian is cut rather than drawn around the world`() {
        // RFC 7946 §3.1.9. Uncut, a two-degree hop between 179°E and 179°W draws as a line back across every
        // meridian on Earth.
        val result =
            assertNotNull(
                convert(
                    """
                    <kml><Placemark><LineString>
                      <coordinates>179,10 -179,10</coordinates>
                    </LineString></Placemark></kml>
                    """,
                ),
            )

        assertContains(result, """"type":"MultiLineString"""")
        assertContains(result, """[[[179.0,10.0],[180.0,10.0]],[[-180.0,10.0],[-179.0,10.0]]]""")
    }

    @Test
    fun `the cut interpolates the latitude where the line meets the meridian`() {
        val result =
            assertNotNull(
                convert(
                    """
                    <kml><Placemark><LineString>
                      <coordinates>170,0 -170,20</coordinates>
                    </LineString></Placemark></kml>
                    """,
                ),
            )

        // Halfway along the twenty degrees of longitude it actually travels, so halfway up the latitude too.
        assertContains(result, """[[[170.0,0.0],[180.0,10.0]],[[-180.0,10.0],[-170.0,20.0]]]""")
    }

    @Test
    fun `a line that stays put is left as a plain LineString`() {
        val result =
            assertNotNull(
                convert(
                    """
                    <kml><Placemark><LineString>
                      <coordinates>-107.62,34.07 -107.59,34.14</coordinates>
                    </LineString></Placemark></kml>
                    """,
                ),
            )

        assertContains(result, """"type":"LineString"""")
    }

    @Test
    fun `a clockwise ring is turned to follow the right-hand rule`() {
        // RFC 7946 §3.1.6 requires an exterior ring to wind counterclockwise. KML says nothing about winding, so
        // exporters emit both and the ring has to be turned rather than trusted.
        val result =
            assertNotNull(
                convert(
                    """
                    <kml><Placemark><Polygon><outerBoundaryIs><LinearRing>
                      <coordinates>-107.62,34.07 -107.59,34.14 -107.59,34.07</coordinates>
                    </LinearRing></outerBoundaryIs></Polygon></Placemark></kml>
                    """,
                ),
            )

        assertContains(result, """[[[-107.59,34.07],[-107.59,34.14],[-107.62,34.07],[-107.59,34.07]]]""")
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
    fun `a description holding raw html elements does not take the import down`() {
        // Valid XML, not CDATA: exporters do write nested markup here, and one such description used to abort the
        // whole document. The markup is dropped; the text and the geometry survive.
        val result =
            assertNotNull(
                convert(
                    "<kml><Placemark><name>A</name><description>a <b>bold</b> claim</description>" +
                        "<Point><coordinates>1,2</coordinates></Point></Placemark></kml>",
                ),
            )

        assertContains(result, "a bold claim")
        assertContains(result, "[1.0,2.0]")
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
    fun `a point carries its packed icon path`() {
        val result =
            assertNotNull(
                convert(
                    """
                    <kml><Document>
                      <Style id="tower"><IconStyle><Icon>
                        <href>files/tower.png</href>
                      </Icon></IconStyle></Style>
                      <Placemark><styleUrl>#tower</styleUrl>
                        <Point><coordinates>-107.62,34.07</coordinates></Point>
                      </Placemark>
                    </Document></kml>
                    """,
                ),
            )

        assertContains(result, """"icon-url":"files/tower.png"""")
    }

    @Test
    fun `a point strips a remote icon url`() {
        val result =
            assertNotNull(
                convert(
                    """
                    <kml><Document>
                      <Style id="tower"><IconStyle><Icon>
                        <href>https://example.org/tower.png</href>
                      </Icon></IconStyle></Style>
                      <Placemark><styleUrl>#tower</styleUrl>
                        <Point><coordinates>-107.62,34.07</coordinates></Point>
                      </Placemark>
                    </Document></kml>
                    """,
                ),
            )

        assertFalse("icon-url" in result, result)
    }

    @Test
    fun `a line does not carry an icon even when its style names one`() {
        // An icon says how a point is drawn; on a route it is noise the renderer would have to filter out again.
        val result =
            assertNotNull(
                convert(
                    """
                    <kml><Document>
                      <Style id="route"><IconStyle><Icon><href>https://example.org/x.png</href></Icon></IconStyle>
                        <LineStyle><color>ff0000ff</color></LineStyle></Style>
                      <Placemark><styleUrl>#route</styleUrl>
                        <LineString><coordinates>-107.62,34.07 -107.59,34.14</coordinates></LineString>
                      </Placemark>
                    </Document></kml>
                    """,
                ),
            )

        assertFalse("icon-url" in result, result)
    }

    @Test
    fun `a NetworkLink href is not mistaken for an icon`() {
        // <Link><href> lives outside any IconStyle, which is the whole reason the reader keys on the enclosing tag.
        val result =
            convert(
                """
                <kml><Document>
                  <NetworkLink><Link><href>https://example.org/more.kml</href></Link></NetworkLink>
                  <Placemark><Point><coordinates>-107.62,34.07</coordinates></Point></Placemark>
                </Document></kml>
                """,
            )

        assertNotNull(result)
        assertFalse("icon-url" in result, result)
    }

    @Test
    fun `an empty icon href is treated as no icon at all`() {
        val result =
            assertNotNull(
                convert(
                    """
                    <kml><Document>
                      <Style id="blank"><IconStyle><Icon><href></href></Icon></IconStyle></Style>
                      <Placemark><styleUrl>#blank</styleUrl>
                        <Point><coordinates>-107.62,34.07</coordinates></Point>
                      </Placemark>
                    </Document></kml>
                    """,
                ),
            )

        assertFalse("icon-url" in result, result)
    }

    @Test
    fun `an icon or label colour is not mistaken for the line colour`() {
        // Google Earth exports carry these alongside a real LineStyle, or with no LineStyle at all.
        val result =
            assertNotNull(
                convert(
                    """
                    <kml><Document>
                      <Style id="pin">
                        <IconStyle><color>ff00ffff</color><scale>1.2</scale></IconStyle>
                        <LabelStyle><color>ffffffff</color></LabelStyle>
                      </Style>
                      <Placemark><styleUrl>#pin</styleUrl>
                        <LineString><coordinates>0,0 1,1</coordinates></LineString>
                      </Placemark>
                    </Document></kml>
                    """,
                ),
            )

        assertTrue(""""stroke":""" !in result, result)
    }

    @Test
    fun `a line style is still read when an icon style sits beside it`() {
        val result =
            assertNotNull(
                convert(
                    """
                    <kml><Document>
                      <Style id="both">
                        <IconStyle><color>ff00ffff</color></IconStyle>
                        <LineStyle><color>ff0000ff</color><width>3</width></LineStyle>
                      </Style>
                      <Placemark><styleUrl>#both</styleUrl>
                        <LineString><coordinates>0,0 1,1</coordinates></LineString>
                      </Placemark>
                    </Document></kml>
                    """,
                ),
            )

        assertContains(result, """"stroke":"#ff0000"""")
        assertContains(result, """"stroke-width":3.0""")
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

    @Test
    fun `an inline placemark style is applied in preference to a styleUrl`() {
        // KML lets a Placemark carry its own Style, and says it wins over a shared one. The top-level scan cannot see
        // such a Style, because the placemark reader has already consumed to the Placemark's end tag.
        val result =
            assertNotNull(
                convert(
                    """
                    <kml><Document>
                      <Style id="shared"><LineStyle><color>ff0000ff</color></LineStyle></Style>
                      <Placemark>
                        <styleUrl>#shared</styleUrl>
                        <Style><LineStyle><color>ff00ff00</color><width>4</width></LineStyle></Style>
                        <LineString><coordinates>1,2 3,4</coordinates></LineString>
                      </Placemark>
                    </Document></kml>
                    """,
                ),
            )

        assertContains(result, """"stroke":"#00ff00"""")
        assertContains(result, """"stroke-width":4""")
    }

    @Test
    fun `an inline style is read without swallowing the geometry beside it`() {
        val result =
            assertNotNull(
                convert(
                    """
                    <kml><Document><Placemark>
                      <Style><PolyStyle><color>ffffff00</color></PolyStyle></Style>
                      <Point><coordinates>7,8</coordinates></Point>
                    </Placemark></Document></kml>
                    """,
                ),
            )

        assertContains(result, """"coordinates":[7.0,8.0]""")
    }

    @Test
    fun `non-finite coordinates are rejected rather than written into the json`() {
        // toDoubleOrNull accepts these, and they are interpolated straight into the output — one of them would emit a
        // bare NaN token and make the whole file unparseable, taking every other placemark down with it.
        assertNull(
            convert(
                "<kml><Document><Placemark><Point><coordinates>NaN,5</coordinates></Point></Placemark></Document></kml>",
            ),
        )
        assertNull(
            convert(
                "<kml><Document><Placemark><Point><coordinates>Infinity,-Infinity</coordinates></Point>" +
                    "</Placemark></Document></kml>",
            ),
        )
    }

    @Test
    fun `out-of-range coordinates are rejected`() {
        assertNull(
            convert(
                "<kml><Document><Placemark><Point><coordinates>200,10</coordinates></Point></Placemark></Document></kml>",
            ),
        )
        assertNull(
            convert(
                "<kml><Document><Placemark><Point><coordinates>10,95</coordinates></Point></Placemark></Document></kml>",
            ),
        )
    }

    @Test
    fun `a bad position does not discard the good positions beside it`() {
        val result =
            assertNotNull(
                convert(
                    """
                    <kml><Document><Placemark>
                      <LineString><coordinates>1,2 NaN,4 5,6</coordinates></LineString>
                    </Placemark></Document></kml>
                    """,
                ),
            )

        assertContains(result, """[[1.0,2.0],[5.0,6.0]]""")
    }
}
