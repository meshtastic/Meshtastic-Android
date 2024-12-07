/*
 * Copyright (c) 2024 Meshtastic LLC
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

package com.geeksville.mesh.model.map

import android.content.res.Resources
import android.util.Log
import org.osmdroid.api.IMapView
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.util.MapTileIndex
import kotlin.math.atan
import kotlin.math.pow
import kotlin.math.sinh

open class NOAAWmsTileSource(
    aName: String,
    aBaseUrl: Array<String>,
    layername: String,
    version: String,
    time: String?,
    srs: String,
    style: String?,
    format: String,
) : OnlineTileSourceBase(
    aName, 0, 5, 256, "png", aBaseUrl, "", TileSourcePolicy(
        2,
        TileSourcePolicy.FLAG_NO_BULK
                or TileSourcePolicy.FLAG_NO_PREVENTIVE
                or TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL
                or TileSourcePolicy.FLAG_USER_AGENT_NORMALIZED
    )
) {

    // array indexes for array to hold bounding boxes.
    private val MINX = 0
    private val MAXX = 1
    private val MINY = 2
    private val MAXY = 3

    // Web Mercator n/w corner of the map.
    private val TILE_ORIGIN = doubleArrayOf(-20037508.34789244, 20037508.34789244)

    //array indexes for that data
    private val ORIG_X = 0
    private val ORIG_Y = 1 // "

    // Size of square world map in meters, using WebMerc projection.
    private val MAP_SIZE = 20037508.34789244 * 2
    private var layer = ""
    private var version = "1.1.0"
    private var srs = "EPSG%3A3857" //used by geo server
    private var format = ""
    private var time = ""
    private var style: String? = null
    private var forceHttps = false
    private var forceHttp = false

    init {
        Log.i(IMapView.LOGTAG, "WMS support is BETA. Please report any issues")
        layer = layername
        this.version = version
        this.srs = srs
        this.style = style
        this.format = format
        if (time != null) this.time = time
    }

//    fun createFrom(endpoint: WMSEndpoint, layer: WMSLayer): WMSTileSource? {
//        var srs: String? = "EPSG:900913"
//        if (layer.srs.isNotEmpty()) {
//            srs = layer.srs[0]
//        }
//        return if (layer.styles.isEmpty()) {
//            WMSTileSource(
//                layer.name, arrayOf(endpoint.baseurl), layer.name,
//                endpoint.wmsVersion, srs, null, layer.pixelSize
//            )
//        } else WMSTileSource(
//            layer.name, arrayOf(endpoint.baseurl), layer.name,
//            endpoint.wmsVersion, srs, layer.styles[0], layer.pixelSize
//        )
//    }


    private fun tile2lon(x: Int, z: Int): Double {
        return x / 2.0.pow(z.toDouble()) * 360.0 - 180
    }

    private fun tile2lat(y: Int, z: Int): Double {
        val n = Math.PI - 2.0 * Math.PI * y / 2.0.pow(z.toDouble())
        return Math.toDegrees(atan(sinh(n)))
    }

    // Return a web Mercator bounding box given tile x/y indexes and a zoom
    // level.
    private fun getBoundingBox(x: Int, y: Int, zoom: Int): DoubleArray {
        val tileSize = MAP_SIZE / 2.0.pow(zoom.toDouble())
        val minx = TILE_ORIGIN[ORIG_X] + x * tileSize
        val maxx = TILE_ORIGIN[ORIG_X] + (x + 1) * tileSize
        val miny = TILE_ORIGIN[ORIG_Y] - (y + 1) * tileSize
        val maxy = TILE_ORIGIN[ORIG_Y] - y * tileSize
        val bbox = DoubleArray(4)
        bbox[MINX] = minx
        bbox[MINY] = miny
        bbox[MAXX] = maxx
        bbox[MAXY] = maxy
        return bbox
    }

    fun isForceHttps(): Boolean {
        return forceHttps
    }

    fun setForceHttps(forceHttps: Boolean) {
        this.forceHttps = forceHttps
    }

    fun isForceHttp(): Boolean {
        return forceHttp
    }

    fun setForceHttp(forceHttp: Boolean) {
        this.forceHttp = forceHttp
    }

    override fun getTileURLString(pMapTileIndex: Long): String? {
        var baseUrl = baseUrl
        if (forceHttps) baseUrl = baseUrl.replace("http://", "https://")
        if (forceHttp) baseUrl = baseUrl.replace("https://", "http://")
        val sb = StringBuilder(baseUrl)
        if (!baseUrl.endsWith("&"))
            sb.append("service=WMS")
        sb.append("&request=GetMap")
        sb.append("&version=").append(version)
        sb.append("&layers=").append(layer)
        if (style != null) sb.append("&styles=").append(style)
        sb.append("&format=").append(format)
        sb.append("&transparent=true")
        sb.append("&height=").append(Resources.getSystem().displayMetrics.heightPixels)
        sb.append("&width=").append(Resources.getSystem().displayMetrics.widthPixels)
        sb.append("&srs=").append(srs)
        sb.append("&size=").append(getSize())
        sb.append("&bbox=")
        val bbox = getBoundingBox(
            MapTileIndex.getX(pMapTileIndex),
            MapTileIndex.getY(pMapTileIndex),
            MapTileIndex.getZoom(pMapTileIndex)
        )
        sb.append(bbox[MINX]).append(",")
        sb.append(bbox[MINY]).append(",")
        sb.append(bbox[MAXX]).append(",")
        sb.append(bbox[MAXY])
        Log.i(IMapView.LOGTAG, sb.toString())
        return sb.toString()
    }

    private fun getSize(): String {
        val height = Resources.getSystem().displayMetrics.heightPixels
        val width = Resources.getSystem().displayMetrics.widthPixels
        return "$width,$height"

    }
}