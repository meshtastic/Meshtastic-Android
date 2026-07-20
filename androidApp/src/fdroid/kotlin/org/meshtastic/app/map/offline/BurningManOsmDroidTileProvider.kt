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

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import org.osmdroid.tileprovider.MapTileProviderBase
import org.osmdroid.tileprovider.modules.IFilesystemCache
import org.osmdroid.tileprovider.tilesource.BitmapTileSourceBase
import org.osmdroid.util.MapTileIndex
import java.io.File

/** Renders the validated, app-private Burning Man PMTiles vector pack without any network fallback. */
class BurningManOsmDroidTileProvider(file: File) : MapTileProviderBase(LocalTileSource) {
    private val renderer = BurningManPmtilesRenderer(file)

    init {
        setUseDataConnection(false)
    }

    override fun getMapTile(pMapTileIndex: Long): Drawable? = renderer
        .tile(
            MapTileIndex.getX(pMapTileIndex),
            MapTileIndex.getY(pMapTileIndex),
            MapTileIndex.getZoom(pMapTileIndex),
        )
        ?.let { BitmapDrawable(null, it) }

    override fun getMinimumZoomLevel(): Int = renderer.minZoom

    override fun getMaximumZoomLevel(): Int = renderer.maxZoom

    override fun getTileWriter(): IFilesystemCache? = null

    override fun getQueueSize(): Long = 0

    fun covers(latitude: Double, longitude: Double): Boolean = renderer.covers(latitude, longitude)

    private object LocalTileSource : BitmapTileSourceBase("Burning Man local", 0, 15, TILE_SIZE, ".png")

    private companion object {
        const val TILE_SIZE = 256
    }
}
