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
package org.meshtastic.core.model

import org.meshtastic.proto.Config.LoRaConfig.RegionCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RegionSuggestionTest {

    @Test
    fun `country codes map to their region in any case`() {
        assertEquals(RegionCode.US, suggestedRegionForCountry("us"))
        assertEquals(RegionCode.EU_868, suggestedRegionForCountry("DE"))
        assertEquals(RegionCode.ANZ, suggestedRegionForCountry("NZ"))
        assertEquals(RegionCode.BR_902, suggestedRegionForCountry("BR"))
        assertEquals(RegionCode.JP, suggestedRegionForCountry("JP"))
        assertEquals(RegionCode.IN, suggestedRegionForCountry("IN"))
        assertEquals(RegionCode.TH, suggestedRegionForCountry("TH"))
    }

    @Test
    fun `an unknown or empty country suggests nothing`() {
        assertNull(suggestedRegionForCountry(""))
        assertNull(suggestedRegionForCountry("AQ"))
    }
}
