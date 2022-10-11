package com.geeksville.mesh.model.map

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.util.MapTileIndex

open class OnlineTileSourceAuth(
    aName: String,
    aZoomLevel: Int,
    aZoomMaxLevel: Int,
    aTileSizePixels: Int,
    aImageFileNameEnding: String,
    aBaseUrl: Array<String>,
    pCopyright: String,
    tileSourcePolicy: TileSourcePolicy,
    layerName: String?,
    apiKey: String
) :
    OnlineTileSourceBase(
        aName,
        aZoomLevel,
        aZoomMaxLevel,
        aTileSizePixels,
        aImageFileNameEnding,
        aBaseUrl,
        pCopyright,
        tileSourcePolicy

    ) {
    private var layerName = ""
    private var apiKey = ""

    init {
        if (layerName != null) {
            this.layerName = layerName
        }
        this.apiKey = apiKey

    }

    override fun getTileURLString(pMapTileIndex: Long): String {
        return "$baseUrl$layerName/" + (MapTileIndex.getZoom(pMapTileIndex)
            .toString() + "/" + MapTileIndex.getX(pMapTileIndex)
            .toString() + "/" + MapTileIndex.getY(pMapTileIndex)
            .toString()) + mImageFilenameEnding + "?appId=$apiKey"
    }
}