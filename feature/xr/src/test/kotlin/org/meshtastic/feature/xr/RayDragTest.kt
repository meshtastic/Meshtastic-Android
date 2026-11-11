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
package org.meshtastic.feature.xr

import org.meshtastic.feature.map.terrain.spatial.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals

class RayDragTest {
    private val eye = Vec3(0f, 1.5f, 0f)

    @Test
    fun `a ray that does not move holds the thing still`() {
        val drag = RayDrag(eye, Vec3(0f, 0f, -1f), distance = 2f)

        assertEquals(Vec3(0f, 0f, 0f), drag.move(eye, Vec3(0f, 0f, -1f)))
    }

    @Test
    fun `turning the ray swings the held point round the user at the grab distance`() {
        val drag = RayDrag(eye, Vec3(0f, 0f, -1f), distance = 2f)

        val moved = drag.move(eye, Vec3(1f, 0f, 0f))

        // From 2 m ahead to 2 m to the right.
        assertEquals(2f, moved.x, 1e-6f)
        assertEquals(0f, moved.y, 1e-6f)
        assertEquals(2f, moved.z, 1e-6f)
    }

    @Test
    fun `moves add up to the whole way and the pointing vector's length does not matter`() {
        val drag = RayDrag(eye, Vec3(0f, 0f, -3f), distance = 1f)

        val first = drag.move(Vec3(0f, 1.5f, 0f), Vec3(0f, 5f, 0f))
        val second = drag.move(Vec3(0.5f, 1.5f, 0f), Vec3(0f, 0f, -0.2f))

        assertEquals(1f, first.y, 1e-6f)
        assertEquals(-1f, second.y, 1e-6f)
        assertEquals(0.5f, second.x, 1e-6f)
        // From 1 m ahead of the eye to 1 m ahead of the eye moved half a meter right: no net rise or depth.
        assertEquals(0f, first.y + second.y, 1e-6f)
        assertEquals(0f, first.z + second.z, 1e-6f)
    }
}
