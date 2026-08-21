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

import com.google.maps.android.data.parser.kml.KmlParser
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Canary for the android-maps-utils / xmlutil linkage behind KML map overlays.
 *
 * android-maps-utils' `data.parser` KmlParser and the app's CoT XML code share one resolved xmlutil, and the pairing
 * has already broken in production once: maps-utils 5.0.0 was compiled against xmlutil 0.91.x's removed
 * `XmlConfig.Builder.policyBuilder()`, so when the app moved to xmlutil 1.0.x (2.8.0) every KML/KMZ import crashed with
 * a fatal `NoSuchMethodError` until the maps-utils floor was raised to 5.1.1 (built against 1.0.x's compat builder).
 * Whichever side a future dependency bump moves first, this test fails the build on the resulting binary mismatch
 * instead of leaving it to be discovered by users opening map overlays.
 */
class KmlParserLinkageTest {

    @Test
    fun kmlParserParsesAMinimalDocumentAgainstTheResolvedXmlutil() {
        val kml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Placemark>
                <name>linkage-canary</name>
                <Point><coordinates>-122.084,37.421,0</coordinates></Point>
              </Placemark>
            </kml>
            """
                .trimIndent()

        assertNotNull(KmlParser().parse(kml.byteInputStream()))
    }
}
