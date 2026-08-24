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
package org.meshtastic.core.model.geofence

import org.meshtastic.proto.Config.DisplayConfig.DisplayUnits
import kotlin.test.Test
import kotlin.test.assertEquals

class GeofenceRadiusPresetsTest {

    @Test
    fun bothListsStartWithOff() {
        assertEquals(0, GeofenceRadiusPresets.METRIC_METERS.first())
        assertEquals(0, GeofenceRadiusPresets.IMPERIAL_METERS.first())
    }

    @Test
    fun forUnitsSelectsBySystem() {
        assertEquals(GeofenceRadiusPresets.IMPERIAL_METERS, GeofenceRadiusPresets.forUnits(DisplayUnits.IMPERIAL))
        assertEquals(GeofenceRadiusPresets.METRIC_METERS, GeofenceRadiusPresets.forUnits(DisplayUnits.METRIC))
    }

    @Test
    fun nearestSnapsToClosestPreset() {
        assertEquals(0, GeofenceRadiusPresets.nearest(0, DisplayUnits.METRIC))
        assertEquals(100, GeofenceRadiusPresets.nearest(120, DisplayUnits.METRIC))
        assertEquals(2000, GeofenceRadiusPresets.nearest(2600, DisplayUnits.METRIC))
        assertEquals(5000, GeofenceRadiusPresets.nearest(99999, DisplayUnits.METRIC))
    }
}
