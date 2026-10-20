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
package org.meshtastic.feature.map.hud

import org.meshtastic.core.repository.PhoneLocation
import org.meshtastic.proto.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val here = PhoneLocation(40.0, -105.0, 100.0, 0L, accuracyM = 5f)
private val northPose = CameraPose(right = Vec3(1.0, 0.0, 0.0), up = Vec3(0.0, 0.0, 1.0), forward = Vec3(0.0, 1.0, 0.0))

private fun at(lat: Double, time: Int = 0, altitude: Int? = 100) = Position.Builder()
    .also { b ->
        b.latitude_i = (lat * 1e7).toInt()
        b.longitude_i = (-105.0 * 1e7).toInt()
        b.altitude = altitude
        b.time = time
    }
    .build()

class HudPathsTest {

    @Test
    fun `a segment wholly in front is left alone`() {
        val a = Vec3(0.0, 1.0, 0.0)
        val b = Vec3(0.5, 1.0, 0.0)
        assertEquals(a to b, clipToNearPlane(a, b, northPose))
    }

    @Test
    fun `a segment wholly behind is dropped`() {
        assertNull(clipToNearPlane(Vec3(0.0, -1.0, 0.0), Vec3(0.5, -1.0, 0.0), northPose))
    }

    @Test
    fun `a segment crossing the near plane is cut at it`() {
        val (a, b) = assertNotNull(clipToNearPlane(Vec3(0.0, -1.0, 0.0), Vec3(0.0, 1.0, 0.0), northPose))
        assertEquals(NEAR_PLANE, a.y, 1e-9)
        assertEquals(1.0, b.y, 1e-9)
        assertNotNull(project(a, northPose, 800f, 1000f, 2000f))
    }

    @Test
    fun `an arc starts and ends at its hops and lifts between them`() {
        val a = PathPoint(bearingDeg = 0.0, elevationDeg = 0.0, distanceM = 1000.0, atViewer = false)
        val b = PathPoint(bearingDeg = 40.0, elevationDeg = 0.0, distanceM = 1000.0, atViewer = false)
        val arc = arcDirections(a, b, liftSign = 1)
        assertEquals(directionEnu(0.0, 0.0).x, arc.first().x, 1e-9)
        assertEquals(directionEnu(40.0, 0.0).x, arc.last().x, 1e-9)
        assertTrue(arc[arc.size / 2].z > 0)
        assertTrue(arcDirections(a, b, liftSign = -1)[arc.size / 2].z < 0)
    }

    @Test
    fun `a route counts unknown relays apart and reads SNR in dB`() {
        val positions = mapOf(1 to at(40.0), 3 to at(40.01))
        val route =
            buildHudRoute(
                here,
                declination = 0f,
                forward = listOf(1, UNKNOWN_HOP, 3),
                back = emptyList(),
                snrTowards = listOf(20, -128),
                snrBack = null,
                positionOf = positions::get,
            )
        assertEquals(2, route.total)
        assertEquals(2, route.mappable)
        assertNull(route.forward.points[1])
        assertEquals(listOf(5f, null), route.forward.snrDb)
        assertNull(route.back)
    }

    @Test
    fun `SNR is ignored when it does not match the route`() {
        val route = buildHudRoute(here, 0f, listOf(1, 2, 3), emptyList(), listOf(12), null) { at(40.01) }
        assertEquals(listOf(null, null), route.forward.snrDb)
    }

    @Test
    fun `a hop at the phone starts the route from the viewer`() {
        val route =
            buildHudRoute(here, 0f, listOf(1, 2), emptyList(), null, null) { num ->
                if (num == 1) at(40.0) else at(40.01)
            }
        assertTrue(assertNotNull(route.forward.points[0]).atViewer)
        assertFalse(assertNotNull(route.forward.points[1]).atViewer)
    }

    @Test
    fun `the connected radio is at the viewer even with no position or a distant one`() {
        val unplaced =
            buildHudRoute(here, 0f, listOf(1, 2), listOf(2, 1), null, null, viewerNum = 1) { num ->
                if (num == 1) null else at(40.01)
            }
        assertTrue(assertNotNull(unplaced.forward.points[0]).atViewer)
        assertTrue(assertNotNull(assertNotNull(unplaced.back).points[1]).atViewer)
        val distant = buildHudRoute(here, 0f, listOf(1, 2), emptyList(), null, null, viewerNum = 1) { at(40.01) }
        assertTrue(assertNotNull(distant.forward.points[0]).atViewer)
        assertEquals(2, unplaced.mappable)
    }

    @Test
    fun `a track runs oldest first and flags points with no altitude`() {
        val track =
            buildHudTrack(
                here,
                declination = 0f,
                nodeNum = 7,
                color = 0,
                positions = listOf(at(40.02, time = 300), at(40.01, time = 100, altitude = null)),
                nowSeconds = 400,
            )
        assertEquals(listOf(300L, 100L), track.points.map { it.ageSec })
        assertFalse(track.points.first().altitudeKnown)
    }
}
