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
package org.meshtastic.feature.coverage

import org.meshtastic.feature.map.component.SitePlannerParams
import kotlin.test.Test
import kotlin.test.assertEquals

class SitePlannerEstimateTest {

    @Test
    fun bandsStartAtTheReceiverSensitivity() {
        val style =
            SitePlannerParams(name = "Test", latitude = 0.0, longitude = 0.0, rxSensitivityDbm = -139.0)
                .toCoverageStyle()

        assertEquals(-139.0, style.minDbm)
        assertEquals(-89.0, style.maxDbm)
    }
}
