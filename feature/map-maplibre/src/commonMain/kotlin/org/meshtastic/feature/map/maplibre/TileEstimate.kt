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
package org.meshtastic.feature.map.maplibre

import org.maplibre.spatialk.geojson.BoundingBox
import org.meshtastic.feature.map.maplibre.terrain.toGeoBounds
import org.meshtastic.feature.map.terrain.TerrainTileMath

/**
 * How many tiles cover [this] region between [minZoom] and [maxZoom] inclusive.
 *
 * Shown to the user before a download starts, which is what the OSMdroid map did: a region that looks modest on screen
 * can be thousands of tiles once a few zoom levels are included, and the only honest way to convey that is to count
 * them up front.
 *
 * Standard slippy-map arithmetic, so it matches what the renderer will actually request.
 */
internal fun BoundingBox.tileCount(minZoom: Int, maxZoom: Int): Long {
    val bounds = toGeoBounds()
    return (minZoom..maxZoom).sumOf { zoom -> TerrainTileMath.tileCountAt(zoom, bounds) }
}
