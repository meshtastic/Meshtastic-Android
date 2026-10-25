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
package org.meshtastic.feature.map.maplibre.layers

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClusterCannotSplitTest {

    @Test
    fun `nodes on one spot report an expansion zoom past the cluster ceiling and are listed`() {
        assertTrue(clusterCannotSplit(21.0))
    }

    @Test
    fun `a cluster that splits at or below the ceiling is zoomed into`() {
        assertFalse(clusterCannotSplit(12.0))
        assertFalse(clusterCannotSplit(20.0))
    }

    @Test
    fun `a sentinel from a query that could not answer is listed rather than zoomed`() {
        assertTrue(clusterCannotSplit(0.0))
        assertTrue(clusterCannotSplit(-1.0))
    }
}
