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
package org.meshtastic.feature.map.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TracerouteOverlayTest {

    @Test
    fun `TracerouteOverlay handles empty routes correctly`() {
        val overlay = TracerouteOverlay(requestId = 1)

        assertEquals(1, overlay.requestId)
        assertTrue(overlay.forwardRoute.isEmpty())
        assertTrue(overlay.returnRoute.isEmpty())
        assertTrue(overlay.relatedNodeNums.isEmpty())
        assertFalse(overlay.hasRoutes)
    }

    @Test
    fun `TracerouteOverlay processes populated routes correctly`() {
        val overlay = TracerouteOverlay(requestId = 2, forwardRoute = listOf(1, 2, 3), returnRoute = listOf(3, 4, 1))

        assertEquals(setOf(1, 2, 3, 4), overlay.relatedNodeNums)
        assertTrue(overlay.hasRoutes)
    }
}
