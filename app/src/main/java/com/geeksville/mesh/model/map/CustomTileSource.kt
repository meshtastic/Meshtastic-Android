package com.geeksville.mesh.model.map

import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.util.MapTileIndex


class CustomTileSource {

    //https://tile.openweathermap.org/map/{layer}/{z}/{x}/{y}.png?appid={API key}
    companion object {
        val OPENWEATHER_RADAR = object : OnlineTileSourceBase(
            "Open Weather Map", 1, 15, 256, ".png", arrayOf(
                "https://tile.openweathermap.org/map/"
            ), "Openweathermap",
            TileSourcePolicy(
                4,
                TileSourcePolicy.FLAG_NO_BULK
                        or TileSourcePolicy.FLAG_NO_PREVENTIVE
                        or TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL
                        or TileSourcePolicy.FLAG_USER_AGENT_NORMALIZED
            )
        ) {
            //{layer}/{z}/{x}/{y}.png?appid={API key}
            override fun getTileURLString(pMapTileIndex: Long): String {
                return baseUrl + "precipitation/" + (MapTileIndex.getZoom(pMapTileIndex)
                    .toString() + "/" + MapTileIndex.getX(pMapTileIndex)
                        + "/" + MapTileIndex.getY(pMapTileIndex)
                        + mImageFilenameEnding + "?appid=")
            }
        }
        //
//        val RAIN_VIEWER = object : OnlineTileSourceBase(
//            "RainViewer", 1, 15, 256, ".png", arrayOf(
//                "https://tilecache.rainviewer.com/v2/coverage/"
//            ), "RainViewer",
//            TileSourcePolicy(
//                4,
//                TileSourcePolicy.FLAG_NO_BULK
//                        or TileSourcePolicy.FLAG_NO_PREVENTIVE
//                        or TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL
//                        or TileSourcePolicy.FLAG_USER_AGENT_NORMALIZED
//            )
//        ) {
//            override fun getTileURLString(pMapTileIndex: Long): String {
//                return baseUrl + (MapTileIndex.getZoom(pMapTileIndex)
//                    .toString() + "/" + MapTileIndex.getY(pMapTileIndex)
//                        + "/" + MapTileIndex.getX(pMapTileIndex)
//                        + mImageFilenameEnding)
//            }
//        }


        private val ESRI_IMAGERY = object : OnlineTileSourceBase(
            "ESRI World Overview", 1, 20, 256, ".jpg", arrayOf(
                "https://clarity.maptiles.arcgis.com/arcgis/rest/services/World_Imagery/MapServer/tile/"
            ), "Esri, Maxar, Earthstar Geographics, and the GIS User Community",
            TileSourcePolicy(
                4,
                TileSourcePolicy.FLAG_NO_BULK
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

        private val ESRI_WORLD_TOPO = object : OnlineTileSourceBase(
            "ESRI World TOPO",
            1,
            20,
            256,
            ".jpg",
            arrayOf(
                "https://server.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/tile/"
            ),
            "Esri, HERE, Garmin, FAO, NOAA, USGS, © OpenStreetMap contributors, and the GIS User Community  ",
            TileSourcePolicy(
                4,
                TileSourcePolicy.FLAG_NO_BULK
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

        //Transparent Background
        //https://earthlive.maptiles.arcgis.com/arcgis/rest/services/GOES/GOES31D/MapServer/tile/
        private val NOAA_RADAR = object : OnlineTileSourceBase(
            "NOAA GOES Radar",
            0,
            18,
            256,
            "",
            arrayOf(
                "https://earthlive.maptiles.arcgis.com/arcgis/rest/services/GOES/GOES31D/MapServer/tile/"
            ),
            "Dataset Citation: GOES-R Calibration Working Group and GOES-R Series Program, (2017): NOAA GOES-R Series Advanced Baseline Imager (ABI) Level 1b Radiances Band 13. NOAA National Centers for Environmental Information. doi:10.7289/V5BV7DSR",
            TileSourcePolicy(
                2,
                TileSourcePolicy.FLAG_NO_BULK
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
        private val USGS_HYDRO_CACHE = object : OnlineTileSourceBase(
            "USGS Hydro Cache",
            0,
            18,
            256,
            "",
            arrayOf(
                "https://basemap.nationalmap.gov/arcgis/rest/services/USGSHydroCached/MapServer/tile/"
            ),
            "USGS",
            TileSourcePolicy(
                2,
                TileSourcePolicy.FLAG_NO_PREVENTIVE
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
        private val USGS_SHADED_RELIEF = object : OnlineTileSourceBase(
            "USGS Shaded Relief Only",
            0,
            18,
            256,
            "",
            arrayOf(
                "https://basemap.nationalmap.gov/arcgis/rest/services/USGSShadedReliefOnly/MapServer/tile/"
            ),
            "USGS",
            TileSourcePolicy(
                2,
                TileSourcePolicy.FLAG_NO_PREVENTIVE
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

        /**
         * WMS TILE SERVER
         * More research is required to get this to function correctly with overlays
         */
        val NOAA_RADAR_WMS = NOAAWmsTileSource(
            "Recent Weather Radar",
            arrayOf("https://new.nowcoast.noaa.gov/arcgis/services/nowcoast/radar_meteo_imagery_nexrad_time/MapServer/WmsServer?"),
            "1",
            "1.1.0",
            "",
            "EPSG%3A3857",
            "",
            "image/png"
        )

        /**
         * ===============================================================================================
         */

        private val MAPNIK: OnlineTileSourceBase = TileSourceFactory.MAPNIK
        private val USGS_TOPO: OnlineTileSourceBase = TileSourceFactory.USGS_TOPO
        private val OPEN_TOPO: OnlineTileSourceBase = TileSourceFactory.OpenTopo
        private val USGS_SAT: OnlineTileSourceBase = TileSourceFactory.USGS_SAT
        private val SEAMAP: OnlineTileSourceBase = TileSourceFactory.OPEN_SEAMAP
        val DEFAULT_TILE_SOURCE: OnlineTileSourceBase = TileSourceFactory.DEFAULT_TILE_SOURCE

        /**
         * The order in this list must match that in the arrays.xml under map_styles
         */
        val mTileSources: List<ITileSource> =
            listOf(
                MAPNIK,
                USGS_TOPO,
                OPEN_TOPO,
                ESRI_WORLD_TOPO,
                USGS_SAT,
                ESRI_IMAGERY,
            )


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