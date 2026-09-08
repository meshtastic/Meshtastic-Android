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
package org.meshtastic.feature.map.terrain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContourGeneratorTest {

    /** A 5x5 grid where elevation = column × 10 (0,10,20,30,40), constant down every row — a pure east-facing ramp. */
    private fun eastFacingRamp(): ElevationTile {
        val row = floatArrayOf(0f, 10f, 20f, 30f, 40f)
        val elevations = FloatArray(25)
        for (y in 0 until 5) for (x in 0 until 5) elevations[y * 5 + x] = row[x]
        return ElevationTile(5, 5, elevations)
    }

    @Test
    fun `a contour level below every sample produces no lines`() {
        assertEquals(emptyList(), ContourGenerator.generate(eastFacingRamp(), levels = listOf(-5f)))
    }

    @Test
    fun `zero and negative levels are excluded even when requested`() {
        // The ramp does cross 0 (its own minimum), but level 0 itself is filtered by the positive-only rule.
        assertEquals(emptyList(), ContourGenerator.generate(eastFacingRamp(), levels = listOf(0f, -10f)))
    }

    @Test
    fun `a level crossing the ramp produces one chained vertical line at the expected x`() {
        val lines = ContourGenerator.generate(eastFacingRamp(), levels = listOf(20f))

        val line = lines.single()
        assertEquals(20f, line.elevationMeters)
        // Column 2 holds elevation 20 — normalized x = 2/4 = 0.5 (4 cells across a 5-sample grid).
        assertTrue(
            line.points.all { kotlin.math.abs(it.x - 0.5f) < 1e-4f },
            "expected every point near x=0.5, got ${line.points}",
        )
        // Four row-cells' segments should have chained into one polyline spanning the full grid height.
        val ys = line.points.map { it.y }.sorted()
        assertEquals(0f, ys.first(), absoluteTolerance = 1e-4f)
        assertEquals(1f, ys.last(), absoluteTolerance = 1e-4f)
    }

    @Test
    fun `multiple requested levels each produce their own line`() {
        val lines = ContourGenerator.generate(eastFacingRamp(), levels = listOf(10f, 20f, 30f))
        assertEquals(setOf(10f, 20f, 30f), lines.map { it.elevationMeters }.toSet())
    }

    @Test
    fun `a saddle cell produces two separate segments - not a crossing artifact`() {
        // 2x2 grid: high corners on one diagonal (TL, BR = 100), low on the other (TR, BL = 0) — the textbook
        // marching-squares saddle case. A level of 50 should produce two lines, each isolating one high corner,
        // not a single line that incorrectly connects both diagonal high points through the cell's center.
        val saddle = ElevationTile(2, 2, floatArrayOf(100f, 0f, 0f, 100f))
        val lines = ContourGenerator.generate(saddle, levels = listOf(50f))
        assertEquals(2, lines.size, "expected two disjoint segments from a saddle cell, got $lines")
    }
}
