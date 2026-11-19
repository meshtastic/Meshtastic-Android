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
package org.meshtastic.feature.connections

import org.meshtastic.core.network.radio.NodeGpsState
import org.meshtastic.feature.connections.model.GpsNotice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GpsNoticeTest {
    @Test
    fun a_running_gps_shows_no_notice() {
        assertNull(NodeGpsState.READY.toNotice())
    }

    @Test
    fun each_reason_the_gps_cannot_run_has_its_notice() {
        assertEquals(GpsNotice.NO_PERMISSION, NodeGpsState.NO_PERMISSION.toNotice())
        assertEquals(GpsNotice.APPROXIMATE_ONLY, NodeGpsState.APPROXIMATE_ONLY.toNotice())
        assertEquals(GpsNotice.LOCATION_OFF, NodeGpsState.LOCATION_OFF.toNotice())
        assertEquals(GpsNotice.NO_GPS, NodeGpsState.NO_GPS.toNotice())
    }
}
