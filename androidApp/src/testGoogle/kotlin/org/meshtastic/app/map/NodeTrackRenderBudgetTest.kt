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
package org.meshtastic.app.map

import org.junit.runner.RunWith
import org.meshtastic.feature.map.LastHeardFilter
import org.meshtastic.feature.map.includes
import org.meshtastic.proto.Position
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Budgets the Google node-track overlay consumes: marker sampling, gradient buckets, and the age-filter input. Device
 * traces cover frame time; these tests cover the geometry that made a full history allocate one map object per point.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class NodeTrackRenderBudgetTest {

    @Test
    fun `empty one-point and two-point tracks have finite alphas and no invalid line`() {
        val emptyBudget = nodeTrackRenderBudget(emptyList())
        assertRenderable(emptyList(), emptyBudget)
        assertTrue(emptyBudget.lines.isEmpty())

        val one = history(1)
        val single = nodeTrackRenderBudget(one)
        assertRenderable(one, single)
        assertEquals(1f, single.markers.single().alpha)
        assertTrue(single.markers.single().alpha.isFinite())
        assertTrue(single.markers.single().isNewest)
        assertEquals(0, single.markers.single().index)
        assertTrue(single.lines.isEmpty())

        val two = history(2)
        val pair = nodeTrackRenderBudget(two)
        assertRenderable(two, pair)
        assertEquals(0f, pair.markers[0].alpha)
        assertEquals(1f, pair.markers[1].alpha)
        assertFalse(pair.markers[0].isNewest)
        assertTrue(pair.markers[1].isNewest)
        assertEquals(1, pair.lines.size)
        // One segment used to divide by (segmentCount - 1) == 0.
        assertEquals(1f, pair.lines.single().alpha)
        assertTrue(pair.lines.single().alpha.isFinite())
        assertEquals(two.map { it.toLatLng() }, pair.lines.single().points)
    }

    @Test
    fun `five thousand points stay inside the decoration budget`() {
        val positions = history(TRACK_LIMIT)
        val budget = nodeTrackRenderBudget(positions)

        assertEquals(NODE_TRACK_MARKER_BUDGET, budget.markers.size)
        assertEquals(NODE_TRACK_LINE_BUCKET_BUDGET, budget.lines.size)
        assertRenderable(positions, budget)

        val stationaryCount = TRACK_LIMIT / STATIONARY_SHARE
        assertEquals(stationaryCount, budget.coordinates.takeWhile { it == budget.coordinates.first() }.size)
        val peak = budget.coordinates[stationaryCount + 1]
        assertTrue(peak.latitude > budget.coordinates[stationaryCount].latitude)
        assertTrue(peak.latitude > budget.coordinates[stationaryCount + 2].latitude)

        val sampled = budget.markers.map { it.index }.toSet()
        val unsampled = (1 until positions.lastIndex).first { it !in sampled }
        val selected = budget.withSelectedMarker(unsampled)

        assertEquals(NODE_TRACK_MARKER_BUDGET + 1, selected.markers.size)
        assertTrue(selected.markers.size <= NODE_TRACK_MARKER_BUDGET + 1)
        assertTrue(selected.markers.any { it.index == unsampled })
        assertSame(budget.lines, selected.lines)
        assertSame(budget.coordinates, selected.coordinates)
        assertEquals(selected.markers, selected.withSelectedMarker(unsampled).markers)
        assertRenderable(positions, selected)

        assertEquals(budget.markers, budget.withSelectedMarker(0).markers)
        assertEquals(budget.markers, budget.withSelectedMarker(positions.lastIndex).markers)
        assertEquals(budget.markers, budget.withSelectedMarker(null).markers)
        assertEquals(budget.markers, budget.withSelectedMarker(-1).markers)
        assertEquals(budget.markers, budget.withSelectedMarker(positions.size).markers)
        assertSame(budget, budget.withSelectedMarker(null))
    }

    @Test
    fun `tracks within the marker budget keep every point on the route`() {
        listOf(10, 50, NODE_TRACK_MARKER_BUDGET).forEach { count ->
            val positions = history(count)
            val budget = nodeTrackRenderBudget(positions)

            assertEquals(count, budget.markers.size)
            assertEquals((0 until count).toList(), budget.markers.map { it.index })
            assertEquals(minOf(NODE_TRACK_LINE_BUCKET_BUDGET, count - 1), budget.lines.size)
            assertRenderable(positions, budget)
            val stationary = budget.coordinates.first()
            assertTrue(budget.coordinates.count { it == stationary } >= count / STATIONARY_SHARE)
        }
    }

    @Test
    fun `equal timestamps keep distinct marker indices`() {
        val pair = history(2) { SHARED_TIME }
        val pairBudget = nodeTrackRenderBudget(pair)
        assertRenderable(pair, pairBudget)
        assertEquals(listOf(0, 1), pairBudget.markers.map { it.index })
        assertEquals(0, selectedTrackIndex(pair, SHARED_TIME))
        assertEquals(pairBudget.markers, pairBudget.withSelectedMarker(selectedTrackIndex(pair, SHARED_TIME)).markers)
        // The timestamp contract matches every copy. The index only places a dot; it does not pick a winner.
        assertTrue(pair.all { it.time == SHARED_TIME })

        val crowded = history(NODE_TRACK_MARKER_BUDGET + 40) { SHARED_TIME }
        val crowdedBudget = nodeTrackRenderBudget(crowded)
        assertRenderable(crowded, crowdedBudget)
        assertEquals(NODE_TRACK_MARKER_BUDGET, crowdedBudget.markers.size)
        assertEquals(crowdedBudget.markers.size, crowdedBudget.markers.map { it.index }.distinct().size)
        assertEquals(0, selectedTrackIndex(crowded, SHARED_TIME))
        assertNull(selectedTrackIndex(crowded, SHARED_TIME + 1))
        assertEquals(
            crowdedBudget.markers,
            crowdedBudget.withSelectedMarker(selectedTrackIndex(crowded, SHARED_TIME)).markers,
        )

        val mixed = history(3) { index -> if (index == 2) SHARED_TIME + 1 else SHARED_TIME }
        assertEquals(0, selectedTrackIndex(mixed, SHARED_TIME))
        assertEquals(2, selectedTrackIndex(mixed, SHARED_TIME + 1))
        assertTrue(mixed.take(2).all { it.time == SHARED_TIME })
    }

    @Test
    fun `an age filter changes the route and drops a selection it removed`() {
        val positions =
            history(FILTERED_TRACK) { index -> (NOW_SECONDS - (FILTERED_TRACK - 1 - index) * STEP_SECONDS).toInt() }
        val recordedNewestFirst = positions.asReversed()
        val wide = filterSortedTrackPositions(recordedNewestFirst, LastHeardFilter.Any, NOW_SECONDS)
        val narrow = filterSortedTrackPositions(recordedNewestFirst, LastHeardFilter.OneHour, NOW_SECONDS)

        assertEquals(positions, wide)
        assertTrue(narrow.size < wide.size)
        assertTrue(narrow.size <= NODE_TRACK_MARKER_BUDGET)
        assertEquals(wide.last().time, narrow.last().time)
        assertTrue(narrow.first().time > wide.first().time)

        val wideBudget = nodeTrackRenderBudget(wide)
        val narrowBudget = nodeTrackRenderBudget(narrow)
        assertRenderable(wide, wideBudget)
        assertRenderable(narrow, narrowBudget)
        assertEquals(NODE_TRACK_MARKER_BUDGET, wideBudget.markers.size)
        assertEquals(narrow.size, narrowBudget.markers.size)
        assertTrue(narrowBudget.coordinates.size < wideBudget.coordinates.size)

        val droppedTime = wide.first().time
        assertFalse(LastHeardFilter.OneHour.includes(droppedTime, NOW_SECONDS))
        assertTrue(narrow.all { LastHeardFilter.OneHour.includes(it.time, NOW_SECONDS) })
        assertEquals(0, selectedTrackIndex(wide, droppedTime))
        assertNull(selectedTrackIndex(narrow, droppedTime))
        assertSame(narrowBudget, narrowBudget.withSelectedMarker(selectedTrackIndex(narrow, droppedTime)))
    }

    @Test
    fun `a new position becomes the newest marker and extends the route`() {
        val initial = history(TRACK_LIMIT)
        val before = nodeTrackRenderBudget(initial)
        val arrived =
            trackPosition(latitudeE7 = STATIONARY_LAT_E7 + TURN_LAT_E7, longitudeE7 = 0, time = initial.last().time + 1)
        val extended = initial + arrived
        val after = nodeTrackRenderBudget(extended)

        assertEquals(initial.lastIndex, before.markers.single { it.isNewest }.index)
        assertEquals(extended.lastIndex, after.markers.single { it.isNewest }.index)
        assertEquals(NODE_TRACK_MARKER_BUDGET, after.markers.size)
        assertEquals(NODE_TRACK_LINE_BUCKET_BUDGET, after.lines.size)
        assertEquals(before.coordinates + arrived.toLatLng(), after.coordinates)
        assertRenderable(extended, after)

        val small = history(4)
        val smallBefore = nodeTrackRenderBudget(small)
        val smallAfterPositions = small + arrived
        val smallAfter = nodeTrackRenderBudget(smallAfterPositions)
        assertEquals(small.size, smallBefore.markers.size)
        assertEquals(smallAfterPositions.size, smallAfter.markers.size)
        assertEquals(small.lastIndex, smallBefore.markers.single { it.isNewest }.index)
        assertEquals(smallAfterPositions.lastIndex, smallAfter.markers.single { it.isNewest }.index)
        assertFalse(smallAfter.markers[small.lastIndex].isNewest)
        assertEquals(smallBefore.coordinates + arrived.toLatLng(), smallAfter.coordinates)
        assertRenderable(smallAfterPositions, smallAfter)
    }

    private fun assertRenderable(positions: List<Position>, budget: NodeTrackRenderBudget) {
        assertEquals(positions.map { it.toLatLng() }, budget.coordinates)
        assertTrue(budget.markers.size <= NODE_TRACK_MARKER_BUDGET + 1)
        assertTrue(budget.lines.size <= NODE_TRACK_LINE_BUCKET_BUDGET)
        assertEquals(budget.markers.map { it.index }.distinct().sorted(), budget.markers.map { it.index })
        val last = budget.coordinates.lastIndex
        assertTrue(
            budget.markers.all { marker ->
                marker.index in budget.coordinates.indices &&
                    marker.alpha.isFinite() &&
                    marker.alpha == expectedAlpha(marker.index, last) &&
                    marker.isNewest == (last >= 0 && marker.index == last)
            },
        )
        assertTrue(
            budget.lines.all { line ->
                line.alpha.isFinite() &&
                    line.alpha == expectedAlpha(line.endIndex, last) &&
                    line.endIndex > line.startIndex &&
                    line.points == budget.coordinates.subList(line.startIndex, line.endIndex + 1)
            },
        )
        if (positions.isEmpty()) {
            assertTrue(budget.markers.isEmpty())
            assertTrue(budget.lines.isEmpty())
            return
        }
        assertEquals(0, budget.markers.first().index)
        assertEquals(positions.lastIndex, budget.markers.single { it.isNewest }.index)
        if (positions.size < 2) {
            assertTrue(budget.lines.isEmpty())
            return
        }
        assertRouteCovered(budget)
    }

    private fun assertRouteCovered(budget: NodeTrackRenderBudget) {
        val flattened =
            budget.lines.flatMapIndexed { index, bucket -> if (index == 0) bucket.points else bucket.points.drop(1) }
        assertEquals(budget.coordinates, flattened)
        assertEquals(budget.coordinates.zipWithNext(), budget.lines.flatMap { it.points.zipWithNext() })
        assertEquals(0, budget.lines.first().startIndex)
        assertEquals(budget.coordinates.lastIndex, budget.lines.last().endIndex)
        budget.lines.zipWithNext().forEach { (previous, next) ->
            assertEquals(previous.endIndex, next.startIndex)
            assertEquals(previous.points.last(), next.points.first())
        }
    }

    private fun expectedAlpha(index: Int, lastIndex: Int): Float =
        if (lastIndex <= 0) 1f else index.toFloat() / lastIndex.toFloat()

    private fun history(count: Int, timeOf: (Int) -> Int = { BASE_TIME + it }): List<Position> = List(count) { index ->
        val stationary = index < count / STATIONARY_SHARE
        val latitudeE7 = if (stationary || index % 2 == 0) STATIONARY_LAT_E7 else STATIONARY_LAT_E7 + TURN_LAT_E7
        val longitudeE7 = if (stationary) STATIONARY_LON_E7 else STATIONARY_LON_E7 + index * STEP_LON_E7
        trackPosition(latitudeE7, longitudeE7, timeOf(index))
    }

    private fun trackPosition(latitudeE7: Int, longitudeE7: Int, time: Int): Position = Position.Builder()
        .also { wb ->
            wb.latitude_i = latitudeE7
            wb.longitude_i = longitudeE7
            wb.time = time
        }
        .build()

    private companion object {
        const val TRACK_LIMIT = 5_000
        const val FILTERED_TRACK = 400
        const val STEP_SECONDS = 30L
        const val BASE_TIME = 1_700_000_000
        const val SHARED_TIME = 50
        const val NOW_SECONDS = 1_800_000_000L
        const val STATIONARY_SHARE = 5
        const val STATIONARY_LAT_E7 = 350_000_000
        const val STATIONARY_LON_E7 = -1_060_000_000
        const val STEP_LON_E7 = 1_000
        const val TURN_LAT_E7 = 100_000
    }
}
