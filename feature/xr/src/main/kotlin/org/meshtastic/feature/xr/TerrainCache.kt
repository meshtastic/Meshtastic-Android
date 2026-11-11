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
package org.meshtastic.feature.xr

import org.meshtastic.feature.map.terrain.spatial.SpatialCircle
import org.meshtastic.feature.map.terrain.spatial.SpatialHeightGrid

/**
 * The last ground and the last draped image the table built, so a chip that only turns an overlay on or off, or a new
 * trace route, does not fetch the same tiles again. Each is kept for the circle it was made for and nothing older.
 */
internal class TerrainCache {
    private var gridCircle: SpatialCircle? = null
    private var grid: SpatialHeightGrid? = null
    private var imageCircle: SpatialCircle? = null
    private var imageId: String? = null
    private var image: ByteArray? = null

    @Synchronized fun grid(circle: SpatialCircle): SpatialHeightGrid? = grid.takeIf { gridCircle == circle }

    @Synchronized
    fun keep(circle: SpatialCircle, grid: SpatialHeightGrid) {
        gridCircle = circle
        this.grid = grid
    }

    @Synchronized
    fun image(circle: SpatialCircle, imagery: String): ByteArray? = image.takeIf {
        imageCircle == circle && imageId == imagery
    }

    @Synchronized
    fun keep(circle: SpatialCircle, imagery: String, image: ByteArray) {
        imageCircle = circle
        imageId = imagery
        this.image = image
    }
}
