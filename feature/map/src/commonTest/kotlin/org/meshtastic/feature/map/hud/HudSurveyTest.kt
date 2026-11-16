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
import org.meshtastic.core.ui.component.Quality
import org.meshtastic.feature.map.survey.SurveyCell
import org.meshtastic.feature.map.survey.SurveyCellUi
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HudSurveyTest {

    private val here = PhoneLocation(latitude = 40.0, longitude = -105.0, altitude = null, timeMillis = 0)

    /** A 50 m square whose south-west corner is [northM] north and [eastM] east of the phone. */
    private fun square(northM: Double, eastM: Double, quality: Quality = Quality.GOOD): SurveyCellUi {
        val dLat = 1 / 111_320.0
        val dLon = 1 / (111_320.0 * kotlin.math.cos(40.0 * kotlin.math.PI / 180))
        val south = here.latitude + northM * dLat
        val west = here.longitude + eastM * dLon
        val cell = SurveyCell(south, west, south + 50 * dLat, west + 50 * dLon, 5f, -90, 1, 1, 0)
        return SurveyCellUi("$northM:$eastM", cell, quality)
    }

    /** Level, looking due north (magnetic). */
    private val northPose =
        CameraPose(right = Vec3(1.0, 0.0, 0.0), up = Vec3(0.0, 0.0, 1.0), forward = Vec3(0.0, 1.0, 0.0))

    @Test
    fun `a square north of the phone has its corners north of it in metres`() {
        val tile = listOf(square(northM = 100.0, eastM = 0.0)).toHudTiles(here, declinationDeg = 0f).single()

        val norths = tile.corners.map { it.second }
        val easts = tile.corners.map { it.first }
        assertTrue(norths.all { it in 99.0..151.0 }, norths.toString())
        assertTrue(easts.all { it in -1.0..51.0 }, easts.toString())
    }

    @Test
    fun `declination turns the square into the magnetic frame`() {
        // With 90 degrees east declination, true north is magnetic east-of-north by 90: a square due north sits to the
        // magnetic west.
        val tile = listOf(square(northM = 100.0, eastM = -25.0)).toHudTiles(here, declinationDeg = 90f).single()

        assertTrue(tile.corners.all { (east, _) -> east < -90 }, tile.corners.toString())
    }

    @Test
    fun `only squares within reach are kept and the nearest are drawn last`() {
        val tiles =
            listOf(square(200.0, 0.0), square(20.0, 0.0), square(2000.0, 0.0), square(100.0, 0.0)).toHudTiles(here, 0f)

        // To each square's center, farthest first; the one 2 km out is dropped.
        val expected = listOf(226.4, 127.5, 51.5)
        assertEquals(expected.size, tiles.size)
        expected.zip(tiles).forEach { (distance, tile) -> assertEquals(distance, tile.distanceM, 2.0) }
    }

    @Test
    fun `a square wholly in front of the camera is kept whole`() {
        val ground =
            listOf(Vec3(-5.0, 10.0, -1.5), Vec3(5.0, 10.0, -1.5), Vec3(5.0, 20.0, -1.5), Vec3(-5.0, 20.0, -1.5))

        assertEquals(ground, clipPolygonToNearPlane(ground, northPose))
    }

    @Test
    fun `a square behind the camera is dropped`() {
        val ground =
            listOf(Vec3(-5.0, -10.0, -1.5), Vec3(5.0, -10.0, -1.5), Vec3(5.0, -20.0, -1.5), Vec3(-5.0, -20.0, -1.5))

        assertTrue(clipPolygonToNearPlane(ground, northPose).isEmpty())
    }

    @Test
    fun `the square underfoot is cut at the near plane so its front part still draws`() {
        val ground = listOf(Vec3(-5.0, -5.0, -1.5), Vec3(5.0, -5.0, -1.5), Vec3(5.0, 5.0, -1.5), Vec3(-5.0, 5.0, -1.5))

        val clipped = clipPolygonToNearPlane(ground, northPose)

        assertEquals(4, clipped.size)
        assertTrue(clipped.all { (it dot northPose.forward) >= NEAR_PLANE - 1e-9 })
        assertTrue(clipped.count { abs(it.y - NEAR_PLANE) < 1e-9 } == 2, clipped.toString())
    }
}
