package com.geeksville.mesh.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import com.geeksville.android.Logging
import com.geeksville.mesh.BuildConfig
import com.geeksville.mesh.NodeInfo
import com.geeksville.mesh.R
import com.geeksville.mesh.databinding.MapViewBinding
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.util.formatAgo
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker

@AndroidEntryPoint
class MapFragment : ScreenFragment("Map"), Logging {

    private lateinit var binding: MapViewBinding
    private lateinit var map: MapView
    private lateinit var mapController: IMapController
    private lateinit var mPrefs: SharedPreferences
    private val model: UIViewModel by activityViewModels()

    private lateinit var esriTileSource: OnlineTileSourceBase

    private val defaultMinZoom = 1.5
    private val nodeZoomLevel = 8.5
    private val defaultZoomSpeed = 3000L
    private val prefsName = "org.andnav.osm.prefs"
    private val prefsZoomLevelDouble = "prefsZoomLevelDouble"
    private val prefsTileSource = "prefsTileSource"
    private val mapStyleId = "map_style_id"
    private val uiPrefs = "ui-prefs"
    private var nodePositions = listOf<Marker>()
    private val nodeLayer = 1


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = MapViewBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(viewIn: View, savedInstanceState: Bundle?) {
        super.onViewCreated(viewIn, savedInstanceState)
        Configuration.getInstance().userAgentValue =
            BuildConfig.APPLICATION_ID // Required to get online tiles
        map = viewIn.findViewById(R.id.map)
        mPrefs = context!!.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

        setupMapProperties()
        loadOnlineTileSourceBase()
        map.let {
            if (view != null) {
                mapController = map.controller
                binding.fabStyleToggle.setOnClickListener {
                    chooseMapStyle()
                }
                model.nodeDB.nodes.value?.let { nodes ->
                    onNodesChanged(nodes.values)
                    drawOverlays()
                }
            }
            // Any times nodes change update our map
            model.nodeDB.nodes.observe(viewLifecycleOwner) { nodes ->
                onNodesChanged(nodes.values)
                drawOverlays()
            }
            zoomToNodes(mapController)
        }
    }

    private fun chooseMapStyle() {
        /// Prepare dialog and its items
        val builder = MaterialAlertDialogBuilder(context!!)
        builder.setTitle(getString(R.string.preferences_map_style))
        val mapStyles by lazy { resources.getStringArray(R.array.map_styles) }

        /// Load preferences and its value
        val prefs = UIViewModel.getPreferences(context!!)
        val editor: SharedPreferences.Editor = prefs.edit()
        val mapStyleInt = prefs.getInt(mapStyleId, 1)
        debug("mapStyleId from prefs: $mapStyleInt")

        builder.setSingleChoiceItems(mapStyles, mapStyleInt) { dialog, which ->
            debug("Set mapStyleId pref to $which")
            editor.putInt(mapStyleId, which)
            editor.apply()
            dialog.dismiss()
            map.setTileSource(loadOnlineTileSourceBase())
        }
        val dialog = builder.create()
        dialog.show()
    }

    private fun onNodesChanged(nodes: Collection<NodeInfo>) {
        val nodesWithPosition = nodes.filter { it.validPosition != null }

        /**
         * Using the latest nodedb, generate GeoPoint
         */
        // Find all nodes with valid locations
        fun getCurrentNodes(): List<Marker> {
            val mrkr = nodesWithPosition.map { node ->
                val p = node.position!!
                debug("Showing on map: $node")
                val f = GeoPoint(p.latitude, p.longitude)
                lateinit var marker: MarkerWithLabel
                node.user?.let {
                    val label = it.longName + " " + formatAgo(p.time)
                    marker = MarkerWithLabel(map, label)
                    marker.title = label
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    marker.position = f
                    marker.icon = ContextCompat.getDrawable(
                        requireActivity(),
                        R.drawable.ic_twotone_person_pin_24
                    )
                }
                marker
            }
            return mrkr
        }
        nodePositions = getCurrentNodes()
    }

    private fun drawOverlays() {
        map.overlayManager.overlays().clear()
        addCopyright()  // Copyright is required for certain map sources
        map.overlayManager.addAll(nodeLayer, nodePositions)
    }

    /**
     * Adds copyright to map depending on what source is showing
     */
    private fun addCopyright() {
        val copyrightNotice: String =
            map.tileProvider.tileSource.copyrightNotice
        val copyrightOverlay = CopyrightOverlay(context)
        copyrightOverlay.setCopyrightNotice(copyrightNotice)
        map.overlays.add(copyrightOverlay)
    }

    private fun setupMapProperties() {
        if (this::map.isInitialized) {
            map.setDestroyMode(false) // keeps map instance alive when in the background.
            map.isVerticalMapRepetitionEnabled = false // disables map repetition
            map.setScrollableAreaLimitLatitude(
                map.overlayManager.tilesOverlay.bounds.actualNorth,
                map.overlayManager.tilesOverlay.bounds.actualSouth,
                0
            ) // bounds scrollable map
            map.isTilesScaledToDpi =
                true // scales the map tiles to the display density of the screen
            map.minZoomLevel =
                defaultMinZoom // sets the minimum zoom level (the furthest out you can zoom)
            map.setMultiTouchControls(true) // Sets gesture controls to true.
            map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER) // Disables default +/- button for zooming
            setESRITileSource()
        }
    }

    private fun zoomToNodes(controller: IMapController) {
        val points: MutableList<GeoPoint> = mutableListOf()
        val nodesWithPosition =
            model.nodeDB.nodes.value?.values?.filter { it.validPosition != null }
        if ((nodesWithPosition != null) && nodesWithPosition.isNotEmpty()) {
            if (nodesWithPosition.size >= 2) {
                // Multiple nodes, make them all fit on the map view
                nodesWithPosition.forEach {
                    points.add(
                        GeoPoint(
                            it.position!!.latitude,
                            it.position!!.longitude
                        )
                    )
                }
                val box = BoundingBox.fromGeoPoints(points)
                val point = GeoPoint(box.centerLatitude, box.centerLongitude)
                controller.animateTo(point, nodeZoomLevel, defaultZoomSpeed)
            } else {
                // Only one node, just zoom in on it
                val it = nodesWithPosition[0].position!!
                points.add(GeoPoint(it.latitude, it.longitude))
                controller.animateTo(points[0], nodeZoomLevel, defaultZoomSpeed)
            }
        }
    }

    private fun setESRITileSource() {
        esriTileSource = object : OnlineTileSourceBase(
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
    }

    private fun loadOnlineTileSourceBase(): OnlineTileSourceBase {
        val prefs = context?.getSharedPreferences(uiPrefs, Context.MODE_PRIVATE)
        val mapSourceId = prefs?.getInt(mapStyleId, 1)
        debug("mapStyleId from prefs: $mapSourceId")
        val mapSource = when (mapSourceId) {
            0 -> TileSourceFactory.MAPNIK
            1 -> TileSourceFactory.USGS_TOPO
            2 -> TileSourceFactory.USGS_SAT
            3 -> esriTileSource
            else -> TileSourceFactory.DEFAULT_TILE_SOURCE
        }
        return mapSource
    }

    override fun onPause() {
        val edit = mPrefs.edit()
        edit.putString(prefsTileSource, map.tileProvider.tileSource.name())
        edit.putFloat(prefsZoomLevelDouble, map.zoomLevelDouble.toFloat())
        edit.commit()

        map.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        map.invalidate()
        val tileSourceName = mPrefs.getString(
            prefsTileSource,
            TileSourceFactory.DEFAULT_TILE_SOURCE.name()
        )
        try {
            map.setTileSource(matchOnlineTileSourceBase(tileSourceName!!))
        } catch (e: IllegalArgumentException) {
            map.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE)
        }
        map.onResume()
    }

    private fun matchOnlineTileSourceBase(name: String): OnlineTileSourceBase {
        val tileSourceBase = when (name) {
            TileSourceFactory.MAPNIK.name() -> TileSourceFactory.MAPNIK
            TileSourceFactory.USGS_TOPO.name() -> TileSourceFactory.USGS_TOPO
            TileSourceFactory.USGS_SAT.name() -> TileSourceFactory.USGS_SAT
            esriTileSource.name() -> esriTileSource
            else -> TileSourceFactory.DEFAULT_TILE_SOURCE
        }
        return tileSourceBase

    }

    override fun onDestroy() {
        super.onDestroyView()
        map.onDetach()
    }

    private inner class MarkerWithLabel(mapView: MapView?, label: String) : Marker(mapView) {
        val mLabel = label

        override fun draw(c: Canvas, osmv: MapView?, shadow: Boolean) {
            draw(c, osmv)
        }

        fun draw(c: Canvas, osmv: MapView?) {
            super.draw(c, osmv, false)

            val p = mPositionPixels

            val textPaint = Paint()
            textPaint.textSize = 50f
            textPaint.color = Color.RED
            textPaint.isAntiAlias = true
            textPaint.textAlign = Paint.Align.CENTER

            c.drawText(mLabel, (p.x - 0).toFloat(), (p.y - 60).toFloat(), textPaint)
        }
    }
}



