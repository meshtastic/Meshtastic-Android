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

import org.meshtastic.proto.Config.DisplayConfig.DisplayUnits
import kotlin.test.Test
import kotlin.test.assertEquals

class DistanceExtensionsTest {

    @Test
    fun `kmhIn returns value unchanged for metric`() {
        assertEquals(50, 50.kmhIn(DisplayUnits.METRIC))
    }

    @Test
    fun `kmhIn converts to mph for imperial`() {
        assertEquals(31, 50.kmhIn(DisplayUnits.IMPERIAL))
        assertEquals(50, 80.kmhIn(DisplayUnits.IMPERIAL))
    }

    @Test
    fun `kmhIn handles zero`() {
        assertEquals(0, 0.kmhIn(DisplayUnits.METRIC))
        assertEquals(0, 0.kmhIn(DisplayUnits.IMPERIAL))
    }
}
