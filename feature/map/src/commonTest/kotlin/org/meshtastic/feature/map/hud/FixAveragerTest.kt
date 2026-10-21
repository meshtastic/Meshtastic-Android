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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** About 1.1 m of latitude. */
private const val METER_DEG = 0.00001

private fun fix(lat: Double, t: Long, accuracy: Float? = 5f, speed: Float? = 0f) =
    PhoneLocation(40.0 + lat, -105.0, 100.0, t, accuracyM = accuracy, speedMps = speed)

class FixAveragerTest {

    @Test
    fun `a still phone averages its wander`() {
        val averager = FixAverager()
        averager.add(fix(METER_DEG, 1_000))
        val mean = averager.add(fix(-METER_DEG, 2_000))
        assertEquals(40.0, mean.latitude, 1e-9)
        assertEquals(2, averager.count)
    }

    @Test
    fun `better fixes count for more`() {
        val averager = FixAverager()
        averager.add(fix(0.0, 1_000, accuracy = 2f))
        val mean = averager.add(fix(4 * METER_DEG, 2_000, accuracy = 8f))
        // Weights 1/4 and 1/64: the mean sits close to the precise fix.
        assertEquals(40.0 + 4 * METER_DEG / 17, mean.latitude, 1e-9)
        assertEquals(2f, mean.accuracyM)
    }

    @Test
    fun `heights average weighted by their vertical accuracy`() {
        val averager = FixAverager()
        averager.add(fix(0.0, 1_000).copy(altitude = 100.0, mslAltitude = 120.0, verticalAccuracyM = 2f))
        val mean = averager.add(fix(0.0, 2_000).copy(altitude = 117.0, mslAltitude = 137.0, verticalAccuracyM = 8f))
        // Weights 1/4 and 1/64, as for position.
        assertEquals(101.0, assertNotNull(mean.altitude), 1e-9)
        assertEquals(121.0, assertNotNull(mean.mslAltitude), 1e-9)
        assertEquals(2f, mean.verticalAccuracyM)
    }

    @Test
    fun `a fix without a height leaves the height average to the fixes that have one`() {
        val averager = FixAverager()
        averager.add(fix(0.0, 1_000).copy(altitude = 150.0))
        val mean = averager.add(fix(0.0, 2_000).copy(altitude = null))
        assertEquals(150.0, assertNotNull(mean.altitude), 1e-9)
        assertNull(mean.mslAltitude)
    }

    @Test
    fun `walking restarts the average`() {
        val averager = FixAverager()
        averager.add(fix(0.0, 1_000))
        val mean = averager.add(fix(METER_DEG, 2_000, speed = 1.4f))
        assertEquals(40.0 + METER_DEG, mean.latitude, 1e-12)
        assertEquals(1, averager.count)
    }

    @Test
    fun `a jump far beyond the accuracy restarts the average`() {
        val averager = FixAverager()
        averager.add(fix(0.0, 1_000))
        averager.add(fix(100 * METER_DEG, 2_000))
        assertEquals(1, averager.count)
    }

    @Test
    fun `fixes without an accuracy are averaged at an assumed one`() {
        val averager = FixAverager()
        averager.add(fix(METER_DEG, 1_000, accuracy = null))
        val mean = averager.add(fix(-METER_DEG, 2_000, accuracy = null))
        assertEquals(40.0, mean.latitude, 1e-9)
        assertNull(mean.accuracyM)
        assertEquals(2, averager.count)
    }

    @Test
    fun `a fix without an accuracy weighs less than a precise one`() {
        val averager = FixAverager()
        averager.add(fix(0.0, 1_000, accuracy = 2f))
        val mean = averager.add(fix(4 * METER_DEG, 2_000, accuracy = null))
        // Weights 1/4 and 1/400.
        assertEquals(40.0 + 4 * METER_DEG / 101, mean.latitude, 1e-9)
        assertEquals(2f, mean.accuracyM)
    }

    @Test
    fun `a zero accuracy fix averages as one meter without dividing by zero`() {
        val averager = FixAverager()
        averager.add(fix(0.0, 1_000, accuracy = 0f))
        val mean = averager.add(fix(METER_DEG, 2_000, accuracy = 1f))
        assertFalse(mean.latitude.isNaN())
        assertEquals(40.0 + METER_DEG / 2, mean.latitude, 1e-9)
        assertEquals(0f, mean.accuracyM)
        assertEquals(2, averager.count)
    }

    @Test
    fun `a run of zero accuracy fixes keeps averaging through meter-scale wander`() {
        val averager = FixAverager()
        averager.add(fix(0.0, 1_000, accuracy = 0f))
        averager.add(fix(METER_DEG, 2_000, accuracy = 0f))
        averager.add(fix(-METER_DEG, 3_000, accuracy = 0f))
        assertEquals(3, averager.count)
    }

    @Test
    fun `a still fix after walking starts a fresh window`() {
        val averager = FixAverager()
        averager.add(fix(0.0, 1_000))
        averager.add(fix(0.0, 2_000))
        averager.add(fix(2 * METER_DEG, 3_000, speed = 1.4f))
        val mean = averager.add(fix(4 * METER_DEG, 4_000))
        assertEquals(40.0 + 3 * METER_DEG, mean.latitude, 1e-9)
        assertEquals(2, averager.count)
    }

    @Test
    fun `a missing speed counts as still`() {
        val averager = FixAverager()
        averager.add(fix(0.0, 1_000, speed = null))
        averager.add(fix(0.0, 2_000, speed = null))
        assertEquals(2, averager.count)
    }

    @Test
    fun `a move within a few accuracy radii is wander not a jump`() {
        val averager = FixAverager()
        averager.add(fix(0.0, 1_000))
        averager.add(fix(8 * METER_DEG, 2_000))
        assertEquals(2, averager.count)
    }

    @Test
    fun `a move past three accuracy radii of the new fix restarts the average`() {
        val averager = FixAverager()
        averager.add(fix(0.0, 1_000))
        val mean = averager.add(fix(20 * METER_DEG, 2_000))
        assertEquals(1, averager.count)
        assertEquals(40.0 + 20 * METER_DEG, mean.latitude, 1e-12)
    }

    @Test
    fun `old fixes leave the window`() {
        val averager = FixAverager()
        averager.add(fix(0.0, 1_000))
        averager.add(fix(0.0, 40_000))
        assertEquals(1, averager.count)
    }
}
