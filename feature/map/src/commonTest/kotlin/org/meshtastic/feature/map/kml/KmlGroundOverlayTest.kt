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
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KmlGroundOverlayTest {

    @Test
    fun `a ground overlay is read with its box and image`() {
        val result =
            KmlToGeoJson.convertDocument(
                """
                <kml><Folder><GroundOverlay>
                  <name>Tile</name>
                  <Icon><href>files/tile.png</href></Icon>
                  <LatLonBox>
                    <north>50.172</north><south>50.109</south><east>-123.079</east><west>-123.085</west>
                  </LatLonBox>
                </GroundOverlay></Folder></kml>
                """,
            )

        val overlay = result.groundOverlays.single()
        assertEquals("Tile", overlay.name)
        assertEquals("files/tile.png", overlay.href)
        assertEquals(50.172, overlay.north)
        assertEquals(50.109, overlay.south)
        assertEquals(-123.079, overlay.east)
        assertEquals(-123.085, overlay.west)
        assertEquals(0.0, overlay.rotationDegrees)
    }

    @Test
    fun `rotation is read when present`() {
        val result =
            KmlToGeoJson.convertDocument(
                """
                <kml><GroundOverlay>
                  <Icon><href>a.png</href></Icon>
                  <LatLonBox><north>1</north><south>0</south><east>1</east><west>0</west>
                    <rotation>-14.5</rotation></LatLonBox>
                </GroundOverlay></kml>
                """,
            )

        assertEquals(-14.5, result.groundOverlays.single().rotationDegrees)
    }

    @Test
    fun `an overlay-only document is not "nothing mappable"`() {
        val result =
            KmlToGeoJson.convertDocument(
                """
                <kml><GroundOverlay>
                  <Icon><href>a.png</href></Icon>
                  <LatLonBox><north>1</north><south>0</south><east>1</east><west>0</west></LatLonBox>
                </GroundOverlay></kml>
                """,
            )

        assertNull(result.geoJson)
        assertEquals(1, result.groundOverlays.size)
    }

    @Test
    fun `overlays and placemarks come out of one document together`() {
        val result =
            KmlToGeoJson.convertDocument(
                """
                <kml><Document>
                  <Placemark><Point><coordinates>1,2</coordinates></Point></Placemark>
                  <GroundOverlay>
                    <Icon><href>a.png</href></Icon>
                    <LatLonBox><north>3</north><south>2</south><east>2</east><west>1</west></LatLonBox>
                  </GroundOverlay>
                </Document></kml>
                """,
            )

        assertTrue(result.geoJson!!.contains("\"coordinates\":[1.0,2.0]"), result.geoJson)
        assertEquals(1, result.groundOverlays.size)
    }

    @Test
    fun `an overlay with no image or no box is dropped`() {
        val result =
            KmlToGeoJson.convertDocument(
                """
                <kml>
                  <GroundOverlay>
                    <LatLonBox><north>1</north><south>0</south><east>1</east><west>0</west></LatLonBox>
                  </GroundOverlay>
                  <GroundOverlay><Icon><href>a.png</href></Icon></GroundOverlay>
                </kml>
                """,
            )

        assertTrue(result.groundOverlays.isEmpty())
    }

    @Test
    fun `an overlay does not swallow the placemark after it`() {
        // The reader tracks element depth; a bug there would eat the rest of the document silently.
        val result =
            KmlToGeoJson.convertDocument(
                """
                <kml><Document>
                  <GroundOverlay>
                    <Icon><href>a.png</href></Icon>
                    <LatLonBox><north>1</north><south>0</south><east>1</east><west>0</west></LatLonBox>
                  </GroundOverlay>
                  <Placemark><Point><coordinates>5,6</coordinates></Point></Placemark>
                </Document></kml>
                """,
            )

        assertTrue(result.geoJson!!.contains("\"coordinates\":[5.0,6.0]"), result.geoJson)
    }
}
