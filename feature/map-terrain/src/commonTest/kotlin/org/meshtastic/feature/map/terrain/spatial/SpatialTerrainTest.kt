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
package org.meshtastic.feature.map.terrain.spatial

import org.meshtastic.feature.map.terrain.GeoBounds
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpatialTerrainTest {
    private val seattle = GeoBounds(south = 47.5, west = -122.4, north = 47.7, east = -122.2)

    @Test
    fun `frame puts north away and the center at the origin`() {
        val frame = SpatialFrame(seattle)
        val center = frame.localPosition(frame.centerLatitude, frame.centerLongitude, 10.0)
        assertEquals(0f, center.x, 1e-3f)
        assertEquals(10f, center.y)
        val north = frame.localPosition(seattle.north, frame.centerLongitude, 0.0)
        assertTrue(north.z < 0f)
    }

    @Test
    fun `coordinate inverts local position`() {
        val frame = SpatialFrame(seattle)
        val p = frame.localPosition(47.6, -122.3, 0.0)
        val back = frame.coordinate(p.x, p.z)
        assertEquals(47.6, back.latitude, 1e-6)
        assertEquals(-122.3, back.longitude, 1e-6)
    }

    @Test
    fun `circle around keeps the minimum radius and contains its points`() {
        val points = listOf(LatLon(47.6, -122.3), LatLon(47.61, -122.31))
        val circle = assertNotNull(SpatialCircle.around(points))
        assertTrue(circle.radiusMeters >= 1_000.0)
        assertTrue(points.all { circle.contains(it) })
        assertNull(SpatialCircle.around(emptyList()))
    }

    @Test
    fun `zoom clamps to the full circle and pulls the center inside`() {
        val full = SpatialCircle(47.6, -122.3, 10_000.0)
        assertEquals(1.0, SpatialZoom.clamp(0.2, full.radiusMeters))
        assertEquals(10.0, SpatialZoom.clamp(500.0, full.radiusMeters))
        val zoomed = SpatialZoom.circle(full, 4.0, LatLon(47.9, -122.3))
        assertEquals(2_500.0, zoomed.radiusMeters, 1e-6)
        assertTrue(
            zoomed.distanceMeters(LatLon(full.latitude, full.longitude)) <=
                full.radiusMeters - zoomed.radiusMeters + 1.0,
        )
    }

    @Test
    fun `volume fit leaves the rim margin and never shrinks below the minimum`() {
        val fit = SpatialVolumeFit(1.1f, 1.1f, 0.5f)
        assertEquals(0.48f, fit.diskRadius, 1e-6f)
        assertEquals(0.1f, SpatialVolumeFit(0.1f, 0.1f, 0.5f).diskRadius)
    }

    @Test
    fun `wheel drag accumulates turns past a full circle`() {
        val drag = SpatialWheelDrag(0f, 0.5f, 0f)
        var yaw = 0f
        for (step in 1..40) {
            val a = step * (PI / 10).toFloat()
            yaw = drag.update(kotlin.math.cos(a) * 0.5f, -kotlin.math.sin(a) * 0.5f)
        }
        assertEquals((4 * PI).toFloat(), yaw, 1e-2f)
    }

    @Test
    fun `wheel motion eases to the target and coasts after a flick`() {
        val motion = SpatialWheelMotion()
        motion.grab()
        motion.drag(1f, 0.0)
        motion.drag(2f, 0.05)
        motion.release(0.05)
        assertTrue(motion.isCoasting)
        var frames = 0
        while (motion.isMoving && frames < 1_000) {
            motion.step(1 / 60.0)
            frames++
        }
        assertFalse(motion.isMoving)
    }

    private fun flatGrid(columns: Int, rows: Int, height: Float, uncoveredColumns: Int = 0) = SpatialHeightGrid(
        columns,
        rows,
        FloatArray(columns * rows) { height },
        BooleanArray(columns * rows) { (it % columns) >= uncoveredColumns },
        0f,
        0f,
    )

    @Test
    fun `cleaned flattens water and fills uncovered cells at the base`() {
        val cleaned = flatGrid(10, 10, 100f, uncoveredColumns = 3).cleaned()
        assertEquals(100f, cleaned.baseElevation)
        assertEquals(100f, cleaned.heights[9])
        assertEquals(100f, cleaned.heights[0])
        assertTrue(cleaned.hasAnyTerrain)
    }

    @Test
    fun `cleaned clamps below-sea and near-zero covered heights to zero`() {
        val columns = 10
        val rows = 10
        // Columns 0..2 below sea level, 3..5 within the 0.5 m water band, 6..9 land.
        val heights =
            FloatArray(columns * rows) {
                when (it % columns) {
                    in 0..2 -> -20f
                    in 3..5 -> 0.3f
                    else -> 100f
                }
            }
        val grid = SpatialHeightGrid(columns, rows, heights, BooleanArray(columns * rows) { true }, 0f, 0f).cleaned()
        val row = 5 * columns
        assertEquals(0f, grid.heights[row + 1])
        assertEquals(0f, grid.heights[row + 4])
        assertEquals(100f, grid.heights[row + 8])
        assertEquals(0f, grid.baseElevation)
    }

    @Test
    fun `median of nine ignores a spike`() {
        val window = floatArrayOf(5f, 5f, 5f, 5f, 900f, 5f, 5f, 5f, 5f)
        assertEquals(5f, SpatialHeightGrid.medianOfNine(window))
    }

    @Test
    fun `terrain geometry is a disk with a skirt and heights from the grid`() {
        val frame = SpatialFrame(seattle)
        val grid = flatGrid(40, 40, 300f).cleaned()
        val transform = SpatialModelTransform(frame, grid.baseElevation.toDouble(), grid.maxElevation.toDouble())
        val geometry = SpatialTerrainGeometry(grid, transform)
        val surface = geometry.columns * geometry.rows
        assertTrue(geometry.skirtRingCount > 0)
        assertEquals(surface + geometry.skirtRingCount * 2, geometry.positions.size / 3)
        assertEquals(geometry.positions.size / 3 * 2, geometry.textureCoordinates.size)
        assertTrue(geometry.indices.all { it in 0 until geometry.positions.size / 3 })
        for (i in 0 until surface) {
            val x = geometry.positions[i * 3]
            val z = geometry.positions[i * 3 + 2]
            assertTrue(x * x + z * z <= transform.diskRadius * transform.diskRadius * 1.001f)
        }
        val overlay = geometry.overlaySurface()
        assertEquals(surface, overlay.positions.size / 3)
        assertEquals(geometry.surfaceTriangleCount * 3, overlay.indices.size)
    }

    @Test
    fun `terrain coverage counts edges and decides create and expand`() {
        val box = seattle
        assertEquals(0.0, SpatialTerrainCoverage.fraction(box, emptyList()))
        assertEquals(1.0, SpatialTerrainCoverage.fraction(box, listOf(box)))
        val none =
            SpatialTerrainAutoDecision.make(
                box,
                emptyList(),
                isBusy = false,
                backoff = SpatialTerrainBackoff(),
                nowMillis = 0,
            )
        assertTrue(none is SpatialTerrainAutoDecision.Create)
        val managed =
            SpatialOfflineRegion(
                "a",
                GeoBounds(47.55, -122.35, 47.65, -122.25),
                hasTerrain = true,
                hasBasemap = true,
                managedBy = SpatialTerrainAutoDecision.MANAGER,
            )
        val grow =
            SpatialTerrainAutoDecision.make(
                box,
                listOf(managed),
                isBusy = false,
                backoff = SpatialTerrainBackoff(),
                nowMillis = 0,
            )
        assertEquals("a", (grow as SpatialTerrainAutoDecision.Expand).replacing)
    }

    @Test
    fun `backoff waits after a failure and gives up after three`() {
        val target = seattle
        var backoff = SpatialTerrainBackoff().recordingFailure(target, 0)
        assertFalse(backoff.allows(target, 1_000))
        assertTrue(backoff.allows(target, 600_000))
        backoff = backoff.recordingFailure(target, 0).recordingFailure(target, 0)
        assertTrue(backoff.gaveUp(target))
    }

    @Test
    fun `expanded box snaps outward and contains the original`() {
        val expanded = seattle.expandedForTerrain()
        assertTrue(expanded.contains(seattle))
        assertEquals(expanded, seattle.expandedForTerrain())
    }

    @Test
    fun `map area drops far nodes but keeps pinned ones`() {
        val radio = SpatialNodeSnapshot(1, LatLon(47.6, -122.3), isConnectedRadio = true)
        val far = SpatialNodeSnapshot(2, LatLon(35.0, 139.0))
        val near = SpatialNodeSnapshot(3, LatLon(47.61, -122.31))
        val farUnpinned = SpatialNodeSnapshot(4, LatLon(-33.9, 151.2))
        val fitted = SpatialMapArea.fit(listOf(radio, far, near, farUnpinned))
        assertEquals(2, fitted.outsideCount)
        assertEquals(listOf(1L, 3L), fitted.nodes.map { it.id })
        val pinned = SpatialMapArea.fit(listOf(radio, far, near, farUnpinned), pinned = setOf(2L))
        assertEquals(1, pinned.outsideCount)
        assertEquals(listOf(1L, 2L, 3L), pinned.nodes.map { it.id })
    }
}
