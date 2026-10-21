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

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val W = 1000f
private const val H = 2000f
private const val FOCAL = 800f

/** Phone upright in portrait, back camera facing north, level. */
private val facingNorth =
    CameraPose(right = Vec3(1.0, 0.0, 0.0), up = Vec3(0.0, 0.0, 1.0), forward = Vec3(0.0, 1.0, 0.0))

/** Facing east: right is south, forward is east. */
private val facingEast =
    CameraPose(right = Vec3(0.0, -1.0, 0.0), up = Vec3(0.0, 0.0, 1.0), forward = Vec3(1.0, 0.0, 0.0))

class HudGeometryTest {

    @Test
    fun `a target straight ahead lands at the view center`() {
        val point = assertNotNull(project(directionEnu(0.0, 0.0), facingNorth, FOCAL, W, H))
        assertEquals(W / 2, point.x, 0.01f)
        assertEquals(H / 2, point.y, 0.01f)
    }

    @Test
    fun `a target east of the line of sight lands right of center`() {
        val point = assertNotNull(project(directionEnu(20.0, 0.0), facingNorth, FOCAL, W, H))
        assertTrue(point.x > W / 2)
        assertEquals(H / 2, point.y, 0.01f)
    }

    @Test
    fun `a target above the horizon lands above center`() {
        val point = assertNotNull(project(directionEnu(0.0, 10.0), facingNorth, FOCAL, W, H))
        assertTrue(point.y < H / 2)
    }

    @Test
    fun `a target behind the camera does not project`() {
        assertNull(project(directionEnu(180.0, 0.0), facingNorth, FOCAL, W, H))
    }

    @Test
    fun `the camera azimuth follows its forward axis`() {
        assertEquals(0.0, facingNorth.azimuthDeg, 1e-9)
        assertEquals(90.0, facingEast.azimuthDeg, 1e-9)
    }

    @Test
    fun `a node ahead and above the view pins to the top`() {
        assertEquals(PinEdge.TOP, pinEdge(null, W, yawDeg = 5.0, pitchDeltaDeg = 49.0, previous = null))
        assertEquals(PinEdge.BOTTOM, pinEdge(ScreenPoint(W / 2, H + 300f), W, 0.0, -30.0, previous = null))
    }

    @Test
    fun `a node behind pins to a side even with the camera pointing down`() {
        assertEquals(PinEdge.RIGHT, pinEdge(null, W, yawDeg = 150.0, pitchDeltaDeg = 49.0, previous = null))
        assertEquals(PinEdge.LEFT, pinEdge(null, W, yawDeg = -150.0, pitchDeltaDeg = 49.0, previous = null))
    }

    @Test
    fun `a node near straight behind keeps its side`() {
        assertEquals(PinEdge.LEFT, pinEdge(null, W, yawDeg = 175.0, pitchDeltaDeg = 0.0, previous = PinEdge.LEFT))
        assertEquals(PinEdge.RIGHT, pinEdge(null, W, yawDeg = 150.0, pitchDeltaDeg = 0.0, previous = PinEdge.LEFT))
    }

    @Test
    fun `the side threshold holds through small wobbles`() {
        assertEquals(PinEdge.RIGHT, pinEdge(null, W, yawDeg = 30.0, pitchDeltaDeg = 40.0, previous = PinEdge.RIGHT))
        assertEquals(PinEdge.TOP, pinEdge(null, W, yawDeg = 40.0, pitchDeltaDeg = 40.0, previous = PinEdge.TOP))
    }

    @Test
    fun `a pinned node sits along its edge by its angle`() {
        assertEquals(800f, edgeOffsetPx(PinEdge.TOP, yawDeg = 45.0, pitchDeltaDeg = 0.0, focalPx = 800f), 0.5f)
        assertEquals(-800f, edgeOffsetPx(PinEdge.LEFT, yawDeg = 0.0, pitchDeltaDeg = -45.0, focalPx = 800f), 0.5f)
    }

    @Test
    fun `a camera pointing straight down takes its heading from the screen top`() {
        val down = CameraPose(right = Vec3(1.0, 0.0, 0.0), up = Vec3(0.0, 1.0, 0.0), forward = Vec3(0.0, 0.0, -1.0))
        assertEquals(0.0, down.azimuthDeg, 1e-9)
    }

    @Test
    fun `a heading correction turns the pose clockwise`() {
        assertEquals(90.0, facingNorth.rotatedAboutVertical(90.0).azimuthDeg, 1e-9)
        assertEquals(350.0, facingNorth.rotatedAboutVertical(-10.0).azimuthDeg, 1e-9)
    }

    @Test
    fun `a known altitude gives a band from its accuracy`() {
        // 10 m of vertical uncertainty at 1 km is about 0.57 degrees.
        assertEquals(0.573, elevationSpreadDeg(1_000.0, verticalM = 10.0, elevationKnown = true), 0.001)
    }

    @Test
    fun `an unknown altitude gives a tall band`() {
        assertEquals(4.0, elevationSpreadDeg(1_000.0, verticalM = 0.0, elevationKnown = false), 1e-9)
    }

    @Test
    fun `turns take the short way round`() {
        assertEquals(20.0, signedTurnDeg(350.0, 10.0), 1e-9)
        assertEquals(-20.0, signedTurnDeg(10.0, 350.0), 1e-9)
    }

    @Test
    fun `a level camera has no pitch`() {
        assertEquals(0.0, facingNorth.pitchDeg, 1e-9)
    }

    @Test
    fun `curvature puts a distant level target below the horizon`() {
        // Dip at 10 km with 4/3 Earth radius is about 5.9 m, so about -0.034 degrees.
        val elevation = elevationDeg(distanceM = 10_000.0, riseM = 0.0)
        assertTrue(elevation < 0)
        assertTrue(abs(elevation + 0.0337) < 0.001)
    }

    @Test
    fun `a missing altitude places the target level with the observer`() {
        val elevation = elevationDeg(distanceM = 100.0, riseM = null)
        assertTrue(abs(elevation) < 0.001)
    }

    @Test
    fun `a higher target nearby sits well above the horizon`() {
        val elevation = elevationDeg(distanceM = 1_000.0, riseM = 100.0)
        assertEquals(5.71, elevation, 0.01)
    }

    @Test
    fun `precision spans the angle its radius subtends`() {
        // 13 bits is about 2918 m; at 10 km that is about 16.3 degrees each side.
        assertEquals(16.27, assertNotNull(precisionHalfAngleDeg(2918.0, 10_000.0)), 0.01)
    }

    @Test
    fun `inside the precision circle the direction is unknown`() {
        assertNull(precisionHalfAngleDeg(2918.0, 2_000.0))
    }

    @Test
    fun `chips on the same bearing stack in list order`() {
        val box = AngularBox(bearingDeg = 90.0, elevationDeg = 0.0, halfWidthDeg = 2.0, heightDeg = 1.0)
        assertEquals(listOf(0, 1, 2), stackLevels(listOf(box, box, box), gapDeg = 0.1).toList())
    }

    @Test
    fun `chips far apart in bearing do not stack`() {
        val a = AngularBox(bearingDeg = 10.0, elevationDeg = 0.0, halfWidthDeg = 2.0, heightDeg = 1.0)
        val b = a.copy(bearingDeg = 20.0)
        assertEquals(listOf(0, 0), stackLevels(listOf(a, b), gapDeg = 0.1).toList())
    }

    @Test
    fun `stacking sees across north`() {
        val a = AngularBox(bearingDeg = 359.0, elevationDeg = 0.0, halfWidthDeg = 2.0, heightDeg = 1.0)
        val b = a.copy(bearingDeg = 1.0)
        assertEquals(listOf(0, 1), stackLevels(listOf(a, b), gapDeg = 0.1).toList())
    }

    @Test
    fun `a stack past its cap overflows rather than rising further`() {
        val box = AngularBox(bearingDeg = 90.0, elevationDeg = 0.0, halfWidthDeg = 2.0, heightDeg = 1.0)
        val levels = stackLevels(List(5) { box }, gapDeg = 0.1, leaderRunDeg = 0.5, maxLevel = 3)
        assertEquals(listOf(0, 1, 2, 3, OVERFLOW_LEVEL), levels.toList())
    }

    @Test
    fun `lifted chips step aside right then left and a chip on its point does not`() {
        assertEquals(listOf(0, 1, -1, 1), (0..3).map(::leaderSide))
    }

    @Test
    fun `stepping aside still keeps lifted chips from overlapping`() {
        val box = AngularBox(bearingDeg = 90.0, elevationDeg = 0.0, halfWidthDeg = 2.0, heightDeg = 1.0)
        val levels = stackLevels(List(3) { box }, gapDeg = 0.1, leaderRunDeg = 0.5, maxLevel = 3)
        assertEquals(listOf(0, 1, 2), levels.toList())
    }

    @Test
    fun `a chip fills the lowest free row`() {
        val low = AngularBox(bearingDeg = 0.0, elevationDeg = 0.0, halfWidthDeg = 2.0, heightDeg = 1.0)
        val high = low.copy(elevationDeg = 5.0)
        assertEquals(listOf(0, 0, 1), stackLevels(listOf(low, high, low), gapDeg = 0.1).toList())
    }

    @Test
    fun `a wide stream from a squarer sensor loses sensor height`() {
        // A 16:9 stream from a 4 by 3 mm sensor covers the full 4 mm but only 2.25 mm of the height.
        val streamed = CameraIntrinsics(4f, 4f, 3f).forStream(1920, 1080, cropLong = 1920, cropShort = 1080)
        assertEquals(4f, streamed.sensorLongMm, 0.001f)
        assertEquals(2.25f, streamed.sensorShortMm, 0.001f)
    }

    @Test
    fun `a crop inside the stream narrows the sensor area further`() {
        val streamed = CameraIntrinsics(4f, 4f, 3f).forStream(1600, 1200, cropLong = 800, cropShort = 1200)
        assertEquals(2f, streamed.sensorLongMm, 0.001f)
        assertEquals(3f, streamed.sensorShortMm, 0.001f)
    }

    @Test
    fun `intrinsics scale by the axis that fills the view`() {
        // A 4 by 3 mm sensor behind a 4 mm lens, filling a 1000 by 2000 portrait view: the long axis governs.
        val focal = CameraIntrinsics(focalLengthMm = 4f, sensorLongMm = 4f, sensorShortMm = 3f).focalPx(W, H)
        assertEquals(2000f, focal, 0.01f)
    }
}
