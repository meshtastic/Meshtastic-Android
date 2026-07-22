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
package org.meshtastic.core.model.util

import kotlin.test.Test
import kotlin.test.assertEquals
import org.meshtastic.proto.Config.DisplayConfig.DisplayUnits

class DistanceExtensionsTest {

    @Test
    fun `kmhToSpeedString formats metric as-is`() {
        assertEquals("50 km/h", 50.kmhToSpeedString(DisplayUnits.METRIC))
    }

    @Test
    fun `kmhToSpeedString converts to mph for imperial`() {
        assertEquals("31 mph", 50.kmhToSpeedString(DisplayUnits.IMPERIAL))
    }

    @Test
    fun `kmhToSpeedString handles zero`() {
        assertEquals("0 km/h", 0.kmhToSpeedString(DisplayUnits.METRIC))
        assertEquals("0 mph", 0.kmhToSpeedString(DisplayUnits.IMPERIAL))
    }
}
