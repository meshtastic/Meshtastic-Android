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
package org.meshtastic.app.map.offline.terrain

import org.meshtastic.feature.map.terrain.MapterhornEndpoints
import org.meshtastic.feature.map.terrain.TerrainSource

/**
 * Which [TerrainSource] tier a tile at [zoom] was downloaded into — matches how
 * [org.meshtastic.feature.map.terrain.TerrainRegionExtractor.download] itself splits zoom ranges across the two tiers.
 * Shared by [HillshadeTileProvider] and [ContourOverlay] so both read from the same tier for a given zoom.
 *
 * Zoom levels beyond what was actually downloaded (global-only regions past [MapterhornEndpoints.GLOBAL_MAX_ZOOM])
 * simply have no tiles on disk — [org.meshtastic.feature.map.terrain.TerrainTileStore.readTile] returns `null` and both
 * renderers degrade gracefully to "nothing here" rather than upscaling or crashing.
 */
internal fun terrainSourceForZoom(zoom: Int): TerrainSource =
    if (zoom > MapterhornEndpoints.GLOBAL_MAX_ZOOM) TerrainSource.REGIONAL else TerrainSource.GLOBAL
