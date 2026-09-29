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

/**
 * Fetches individual tiles from a remote PMTiles archive by range request — never the whole archive, just the bytes of
 * the tile asked for. Here it's pointed at Mapterhorn's Terrarium elevation archives.
 */
interface TerrainTileFetcher : AutoCloseable {

    /** Raw tile bytes (WebP, Terrarium-encoded) at [zoom]/[x]/[y] — google/osm XYZ convention — or `null` if absent. */
    fun fetchTile(zoom: Int, x: Int, y: Int): ByteArray?
}

/** Opens the remote PMTiles archive at [pmtilesUrl]. */
expect fun TerrainTileFetcher(pmtilesUrl: String): TerrainTileFetcher
