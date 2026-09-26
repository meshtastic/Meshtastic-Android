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
package org.meshtastic.feature.map

import org.meshtastic.proto.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Suppress("MagicNumber")
class TrackRunsTest {

    // One degree of latitude is about 111 km, so 1e-4 degrees is about 11 m.
    private fun fix(time: Int, latitude: Double?, longitude: Double? = 0.0) = Position.Builder()
        .also { builder ->
            builder.latitude_i = latitude?.let { (it * 1e7).toInt() }
            builder.longitude_i = longitude?.let { (it * 1e7).toInt() }
            builder.time = time
        }
        .build()

    @Test
    fun `an empty track has no runs`() {
        assertEquals(emptyList(), mergeStationaryRuns(emptyList()))
    }

    @Test
    fun `a node that never moves is one point at its newest fix`() {
        val track = (0 until 1440).map { minute -> fix(time = 1_000 + minute * 60, latitude = 45.0) }

        val runs = mergeStationaryRuns(track)

        assertEquals(1, runs.size)
        assertEquals(track.last(), runs.single().position)
        assertEquals(1_000, runs.single().firstTime)
    }

    @Test
    fun `jitter inside the radius stays one point`() {
        val track = listOf(fix(1, 45.0), fix(2, 45.0001), fix(3, 44.9999), fix(4, 45.00015))

        assertEquals(1, mergeStationaryRuns(track).size)
    }

    @Test
    fun `moving keeps every fix`() {
        val track = (0 until 10).map { step -> fix(time = step, latitude = 45.0 + step * 0.001) }

        assertEquals(track, mergeStationaryRuns(track).map { it.position })
    }

    @Test
    fun `a slow drift breaks into new points`() {
        // Each step is about 11 m, under the radius, but the run is measured from its first fix.
        val track = (0 until 10).map { step -> fix(time = step, latitude = 45.0 + step * 0.0001) }

        val runs = mergeStationaryRuns(track)

        assertTrue(runs.size > 1)
        assertEquals(track.last(), runs.last().position)
    }

    @Test
    fun `a stop between two legs keeps both legs`() {
        val track = listOf(fix(1, 45.0), fix(2, 45.01), fix(3, 45.01), fix(4, 45.01), fix(5, 45.02))

        val runs = mergeStationaryRuns(track)

        assertEquals(listOf(1, 4, 5), runs.map { it.position.time })
        assertEquals(listOf(1, 2, 5), runs.map { it.firstTime })
    }

    @Test
    fun `a fix with no latitude is never merged`() {
        val track = listOf(fix(1, 45.0), fix(2, null), fix(3, null))

        assertEquals(3, mergeStationaryRuns(track).size)
    }

    @Test
    fun `a run covers every fix it absorbed and nothing outside it`() {
        val runs = mergeStationaryRuns(listOf(fix(10, 45.0), fix(20, 45.0), fix(30, 45.0), fix(40, 45.01)))
        val stop = runs.first()

        assertTrue(stop.covers(10))
        assertTrue(stop.covers(20))
        assertTrue(stop.covers(30))
        assertFalse(stop.covers(9))
        assertFalse(stop.covers(40))
    }
}
