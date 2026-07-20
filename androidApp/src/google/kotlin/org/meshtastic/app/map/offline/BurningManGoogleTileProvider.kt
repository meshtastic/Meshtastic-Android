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
package org.meshtastic.app.map.offline

import com.google.android.gms.maps.model.Tile
import com.google.android.gms.maps.model.TileProvider
import java.io.ByteArrayOutputStream
import java.io.File

/** Renders the validated, app-private Burning Man PMTiles vector pack without any network fallback. */
class BurningManGoogleTileProvider(file: File) : TileProvider {
    private val renderer = BurningManPmtilesRenderer(file)

    override fun getTile(x: Int, y: Int, zoom: Int): Tile? = renderer.tile(x, y, zoom)?.let { bitmap ->
        ByteArrayOutputStream().use { output ->
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, PNG_QUALITY, output))
            Tile(TILE_SIZE, TILE_SIZE, output.toByteArray())
        }
    } ?: TileProvider.NO_TILE

    fun covers(latitude: Double, longitude: Double): Boolean = renderer.covers(latitude, longitude)

    private companion object {
        const val TILE_SIZE = 256
        const val PNG_QUALITY = 100
    }
}
