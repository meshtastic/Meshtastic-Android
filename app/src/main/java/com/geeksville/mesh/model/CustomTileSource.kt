package com.geeksville.mesh.model

import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.util.MapTileIndex


class CustomTileSource {
    companion object {

        val ESRI_IMAGERY = object : OnlineTileSourceBase(
            "ESRI World Overview", 0, 18, 256, "", arrayOf(
                "https://wayback.maptiles.arcgis.com/arcgis/rest/services/World_Imagery/WMTS/1.0.0/default028mm/MapServer/tile/"
            ), "Esri, Maxar, Earthstar Geographics, and the GIS User Community" +
                    "URL\n" +
                    "View\n",
            TileSourcePolicy(
                2, TileSourcePolicy.FLAG_NO_BULK
                        or TileSourcePolicy.FLAG_NO_PREVENTIVE
                        or TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL
                        or TileSourcePolicy.FLAG_USER_AGENT_NORMALIZED
            )
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String {
                return baseUrl + (MapTileIndex.getZoom(pMapTileIndex)
                    .toString() + "/" + MapTileIndex.getY(pMapTileIndex)
                        + "/" + MapTileIndex.getX(pMapTileIndex)
                        + mImageFilenameEnding)
            }
        }
        val MAPNIK: OnlineTileSourceBase = TileSourceFactory.MAPNIK
        val USGS_TOPO: OnlineTileSourceBase = TileSourceFactory.USGS_TOPO
        val USGS_SAT: OnlineTileSourceBase = TileSourceFactory.USGS_SAT
        val DEFAULT_TILE_SOURCE: OnlineTileSourceBase = TileSourceFactory.DEFAULT_TILE_SOURCE

        val mTileSources: List<ITileSource> =
            listOf(MAPNIK, USGS_TOPO, USGS_SAT, ESRI_IMAGERY)


        fun getTileSource(aName: String): ITileSource {
            for (tileSource: ITileSource in mTileSources) {
                if (tileSource.name().equals(aName)) {
                    return tileSource;
                }
            }
            throw IllegalArgumentException("No such tile source: $aName")
        }
    }

}