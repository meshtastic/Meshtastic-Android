/*
 * Copyright (c) 2025 Meshtastic LLC
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

package org.meshtastic.feature.map.model

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.util.MapTileIndex

@Suppress("LongParameterList")
open class OnlineTileSourceAuth(
    name: String,
    zoomLevel: Int,
    zoomMaxLevel: Int,
    tileSizePixels: Int,
    imageFileNameEnding: String,
    baseUrl: Array<String>,
    pCopyright: String,
    tileSourcePolicy: TileSourcePolicy,
    layerName: String?,
    apiKey: String,
) : OnlineTileSourceBase(
    name,
    zoomLevel,
    zoomMaxLevel,
    tileSizePixels,
    imageFileNameEnding,
    baseUrl,
    pCopyright,
    tileSourcePolicy,
) {
    private var layerName = ""
    private var apiKey = ""

    init {
        if (layerName != null) {
            this.layerName = layerName
        }
        this.apiKey = apiKey
    }

    override fun getTileURLString(pMapTileIndex: Long): String = "$baseUrl$layerName/" +
        (
            MapTileIndex.getZoom(pMapTileIndex).toString() +
                "/" +
                MapTileIndex.getX(pMapTileIndex).toString() +
                "/" +
                MapTileIndex.getY(pMapTileIndex).toString()
            ) +
        mImageFilenameEnding +
        "?appId=$apiKey"
}
