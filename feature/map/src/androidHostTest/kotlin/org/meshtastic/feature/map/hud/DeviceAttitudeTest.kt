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

import android.view.Surface
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals

private const val TOLERANCE = 1e-5

private val EAST = Vec3(1.0, 0.0, 0.0)
private val NORTH = Vec3(0.0, 1.0, 0.0)
private val SOUTH = Vec3(0.0, -1.0, 0.0)
private val UP = Vec3(0.0, 0.0, 1.0)
private val DOWN = Vec3(0.0, 0.0, -1.0)

private fun assertNear(expected: Vec3, actual: Vec3) {
    assertEquals(expected.x, actual.x, TOLERANCE, "x of $actual")
    assertEquals(expected.y, actual.y, TOLERANCE, "y of $actual")
    assertEquals(expected.z, actual.z, TOLERANCE, "z of $actual")
}

/** Quaternions are (x, y, z, w) device-to-east-north-up, the layout of both the rotation vector and FOP's attitude. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeviceAttitudeTest {

    @Test
    fun `identity is the phone flat on its back with its top to the north`() {
        val pose = attitudeToCameraPose(floatArrayOf(0f, 0f, 0f, 1f), Surface.ROTATION_0)
        assertNear(DOWN, pose.forward)
        assertNear(EAST, pose.right)
        assertNear(NORTH, pose.up)
        assertEquals(0.0, pose.azimuthDeg, TOLERANCE)
    }

    @Test
    fun `tilting the top up about device x faces the camera north`() {
        val half = sqrt(0.5).toFloat()
        val pose = attitudeToCameraPose(floatArrayOf(half, 0f, 0f, half), Surface.ROTATION_0)
        assertNear(NORTH, pose.forward)
        assertNear(EAST, pose.right)
        assertNear(UP, pose.up)
        assertEquals(0.0, pose.azimuthDeg, TOLERANCE)
    }

    @Test
    fun `an upright portrait phone facing east has south on its right`() {
        val pose = attitudeToCameraPose(floatArrayOf(0.5f, -0.5f, -0.5f, 0.5f), Surface.ROTATION_0)
        assertNear(EAST, pose.forward)
        assertNear(SOUTH, pose.right)
        assertNear(UP, pose.up)
        assertEquals(90.0, pose.azimuthDeg, TOLERANCE)
    }

    @Test
    fun `a landscape phone with its top to the left takes the screen axes from the display rotation`() {
        val pose = attitudeToCameraPose(floatArrayOf(0.5f, -0.5f, 0.5f, 0.5f), Surface.ROTATION_90)
        assertNear(NORTH, pose.forward)
        assertNear(EAST, pose.right)
        assertNear(UP, pose.up)
        assertEquals(0.0, pose.azimuthDeg, TOLERANCE)
    }
}
