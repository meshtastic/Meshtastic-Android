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
package org.meshtastic.feature.node.metrics

import org.meshtastic.core.model.Node
import org.meshtastic.proto.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GpxTrackNameTest {

    private val node =
        Node(
            num = 1,
            user = User.Builder().also { wb -> wb.long_name = "Broadcast" }.build(),
            displayName = "Secret nickname",
        )

    @Test
    fun `gpx track name is the broadcast name when a display name is set`() {
        assertEquals("Broadcast", gpxTrackName(node))
    }

    @Test
    fun `exported gpx names the track with the broadcast name and omits the display name`() {
        val gpx = buildGpx(emptyList(), gpxTrackName(node))
        assertTrue(gpx.contains("<name>Broadcast</name>"))
        assertFalse(gpx.contains("Secret nickname"))
    }

    @Test
    fun `gpx track name is empty without a node`() {
        assertEquals("", gpxTrackName(null))
    }
}
