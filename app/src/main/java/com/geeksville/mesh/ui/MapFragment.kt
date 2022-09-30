package com.geeksville.mesh.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import com.geeksville.mesh.BuildConfig
import com.geeksville.mesh.NodeInfo
import com.geeksville.mesh.R
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.database.entity.Packet
import com.geeksville.mesh.databinding.MapViewBinding
import com.geeksville.mesh.model.CustomTileSource
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.util.formatAgo
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
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

    private val defaultMinZoom = 1.5
    private val nodeZoomLevel = 8.5
    private val defaultZoomSpeed = 3000L
    private val prefsName = "org.andnav.osm.prefs"
    private val mapStyleId = "map_style_id"
    private var nodePositions = listOf<MarkerWithLabel>()
    private var wayPoints = listOf<MarkerWithLabel>()
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
        map.setTileSource(loadOnlineTileSourceBase())
        map.let {
            if (view != null) {
                mapController = map.controller
                binding.mapStyleButton.setOnClickListener {
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
            model.waypoints.observe(viewLifecycleOwner) {
                debug("New waypoints received: ${it.size}")
                onWaypointChanged(it.values)
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
        val editor: SharedPreferences.Editor = mPrefs.edit()
        val mapStyleInt = mPrefs.getInt(mapStyleId, 1)
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

    private fun onWaypointChanged(wayPt: Collection<Packet>) {

        /**
         * Using the latest waypoint, generate GeoPoint
         */
        // Find all waypoints
        fun getCurrentWayPoints(): List<MarkerWithLabel> {
            val wayPoint = wayPt.map { pt ->
                debug("Showing on map: $pt")
                lateinit var marker: MarkerWithLabel
                pt.data.waypoint?.let {
                    val label = it.name + " " + formatAgo(it.expire)
                    marker = MarkerWithLabel(map, label)
                    marker.title = it.name
                    marker.snippet = it.description
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    marker.position = GeoPoint(it.latitudeI.toDouble(), it.longitudeI.toDouble())
                }
                marker
            }
            return wayPoint
        }
        wayPoints = getCurrentWayPoints()
    }

    private fun onNodesChanged(nodes: Collection<NodeInfo>) {
        val nodesWithPosition = nodes.filter { it.validPosition != null }

        /**
         * Using the latest nodedb, generate GeoPoint
         */
        // Find all nodes with valid locations
        fun getCurrentNodes(): List<MarkerWithLabel> {
            val mrkr = nodesWithPosition.map { node ->
                val p = node.position!!
                debug("Showing on map: $node")
                lateinit var marker: MarkerWithLabel
                node.user?.let {
                    val label = it.longName + " " + formatAgo(p.time)
                    marker = MarkerWithLabel(map, label)
                    marker.title = "${it.longName} ${node.batteryStr}"
                    marker.snippet = model.gpsString(p)
                    model.nodeDB.ourNodeInfo?.let { our ->
                        our.distanceStr(node)?.let { dist ->
                            marker.subDescription = "bearing: ${our.bearing(node)}° distance: $dist"
                        }
                    }
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    marker.position = GeoPoint(p.latitude, p.longitude)
                    marker.icon = ContextCompat.getDrawable(
                        requireActivity(),
                        R.drawable.ic_baseline_location_on_24
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
        map.overlayManager.addAll(nodeLayer, wayPoints)
        map.invalidate()
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

    private fun loadOnlineTileSourceBase(): ITileSource {
        val id = mPrefs.getInt(mapStyleId, 1)
        debug("mapStyleId from prefs: $id")
        return CustomTileSource.mTileSources.getOrNull(id) ?: CustomTileSource.DEFAULT_TILE_SOURCE
    }

    override fun onPause() {
        map.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onDestroy() {
        super.onDestroyView()
        map.onDetach()
    }

    private inner class MarkerWithLabel(mapView: MapView?, label: String) : Marker(mapView) {
        val mLabel = label

        private fun getTextBackgroundSize(text: String, x: Float, y: Float, paint: Paint): Rect {
            val fontMetrics: Paint.FontMetrics = paint.fontMetrics
            val halfTextLength: Float = paint.measureText(text) / 2 + 3
            return Rect(
                (x - halfTextLength).toInt(),
                (y + fontMetrics.top).toInt(),
                (x + halfTextLength).toInt(),
                (y + fontMetrics.bottom).toInt()
            )
        }

        override fun draw(c: Canvas, osmv: MapView?, shadow: Boolean) {
            draw(c, osmv)
        }

        fun draw(c: Canvas, osmv: MapView?) {
            super.draw(c, osmv, false)

            val p = mPositionPixels

            val textPaint = Paint()
            textPaint.textSize = 40f
            textPaint.color = Color.DKGRAY
            textPaint.isAntiAlias = true
            textPaint.isFakeBoldText = true
            textPaint.textAlign = Paint.Align.CENTER

            val bgRect = getTextBackgroundSize(mLabel, (p.x - 0f), (p.y - 110f), textPaint)
            val bgPaint = Paint()
            bgPaint.color = Color.WHITE

            c.drawRect(bgRect, bgPaint)
            c.drawText(mLabel, (p.x - 0f), (p.y - 110f), textPaint)
        }
    }
}



