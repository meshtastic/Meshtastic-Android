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

/** About 1.1 m of latitude. */
private const val METRE_DEG = 0.00001

private fun fix(lat: Double, t: Long, accuracy: Float = 5f, speed: Float? = 0f) =
    PhoneLocation(40.0 + lat, -105.0, 100.0, t, accuracyM = accuracy, speedMps = speed)

class FixAveragerTest {

    @Test
    fun `a still phone averages its wander`() {
        val averager = FixAverager()
        averager.add(fix(METRE_DEG, 1_000))
        val mean = averager.add(fix(-METRE_DEG, 2_000))
        assertEquals(40.0, mean.latitude, 1e-9)
        assertEquals(2, averager.count)
    }

    @Test
    fun `better fixes count for more`() {
        val averager = FixAverager()
        averager.add(fix(0.0, 1_000, accuracy = 2f))
        val mean = averager.add(fix(4 * METRE_DEG, 2_000, accuracy = 8f))
        // Weights 1/4 and 1/64: the mean sits close to the precise fix.
        assertEquals(40.0 + 4 * METRE_DEG / 17, mean.latitude, 1e-9)
        assertEquals(2f, mean.accuracyM)
    }

    @Test
    fun `walking restarts the average`() {
        val averager = FixAverager()
        averager.add(fix(0.0, 1_000))
        val mean = averager.add(fix(METRE_DEG, 2_000, speed = 1.4f))
        assertEquals(40.0 + METRE_DEG, mean.latitude, 1e-12)
        assertEquals(1, averager.count)
    }

    @Test
    fun `a jump far beyond the accuracy restarts the average`() {
        val averager = FixAverager()
        averager.add(fix(0.0, 1_000))
        averager.add(fix(100 * METRE_DEG, 2_000))
        assertEquals(1, averager.count)
    }

    @Test
    fun `old fixes leave the window`() {
        val averager = FixAverager()
        averager.add(fix(0.0, 1_000))
        averager.add(fix(0.0, 40_000))
        assertEquals(1, averager.count)
    }
}
