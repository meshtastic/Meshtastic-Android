package com.geeksville.mesh.ui


import android.content.Context
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
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.util.formatAgo
import dagger.hilt.android.AndroidEntryPoint
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker


@AndroidEntryPoint
class MapFragment : ScreenFragment("Map"), Logging {

    private lateinit var map: MapView
    private lateinit var mapController: IMapController
    private val model: UIViewModel by activityViewModels()


    private val defaultLat = 38.8976763
    private val defaultLong = -77.0365298
    private val defaultZoomLevel = 6.0
    private val defaultZoomSpeed = 3000L
    private val defaultMinZoom = 3.0


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.map_view, container, false)
    }

    private fun onNodesChanged(nodes: Collection<NodeInfo>) {
        val nodesWithPosition = nodes.filter { it.validPosition != null }

        /**
         * Using the latest nodedb, generate GeoPoint
         */
        // Find all nodes with valid locations

        nodesWithPosition.map { node ->
            val p = node.position!!
            debug("Showing on map: $node")
            val f = GeoPoint(p.latitude, p.longitude)

            node.user?.let {
                val marker = Marker(map)
                marker.title = it.longName + " " + formatAgo(p.time)
                marker.setAnchor(Marker.ANCHOR_BOTTOM, Marker.ANCHOR_CENTER)
                marker.position = f
                marker.icon = ContextCompat.getDrawable(
                    requireActivity(),
                    R.drawable.ic_twotone_person_pin_24
                )
                map.overlays.add(marker)
                map.invalidate()
            }
            f
        }
    }

    override fun onViewCreated(viewIn: View, savedInstanceState: Bundle?) {
        super.onViewCreated(viewIn, savedInstanceState)
        Configuration.getInstance().userAgentValue =
            BuildConfig.APPLICATION_ID // Required to get online tiles

        map = viewIn.findViewById(R.id.map) as MapView

        /**
         * Copyright layer required
         */
        ////////////////////////////////////////////////////////////
        val copyrightNotice: String =
            map.tileProvider.tileSource.copyrightNotice
        val copyrightOverlay = CopyrightOverlay(context)
        copyrightOverlay.setCopyrightNotice(copyrightNotice)
        map.overlays.add(copyrightOverlay)
        ///////////////////////////////////////////////////////////

        setupMapProperties()
        if (view != null) {
            model.nodeDB.nodes.value?.let { nodes ->
                onNodesChanged(nodes.values)
            }

            zoomToNodes(mapController)
            // Any times nodes change update our map
            model.nodeDB.nodes.observe(viewLifecycleOwner) { nodes ->
                onNodesChanged(nodes.values)
            }

        }


    }

    private fun setupMapProperties() {
        if (this::map.isInitialized) {
            map.minZoomLevel = defaultMinZoom
            map.setMultiTouchControls(true) // Sets gesture controls to true
            map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER) // Disables default +/- button for zooming
            mapController = map.controller
            val point = GeoPoint(defaultLat, defaultLong) //White House Coordinates, Washington DC
            mapController.animateTo(point, defaultZoomLevel, defaultZoomSpeed)
        }
    }

    private fun zoomToNodes(controller: IMapController) {
        val points: MutableList<GeoPoint> = mutableListOf()
        val nodesWithPosition =
            model.nodeDB.nodes.value?.values?.filter { it.validPosition != null }
        if ((nodesWithPosition != null) && nodesWithPosition.isNotEmpty()) {
            val unit = if (nodesWithPosition.size >= 2) {
                // Multiple nodes, make them all fit on the map view
                nodesWithPosition.forEach {
                    points.add(
                        GeoPoint(
                            it.position!!.longitude,
                            it.position!!.latitude
                        )
                    )
                }
                map.zoomToBoundingBox(BoundingBox.fromGeoPoints(points), true)
            } else {
                // Only one node, just zoom in on it
                val it = nodesWithPosition[0].position!!
                points.add(GeoPoint(it.longitude, it.latitude))
                controller.animateTo(points[0], defaultZoomLevel, defaultZoomSpeed)
            }
        }
    }

    private fun loadOnlineTileSourceBase(): OnlineTileSourceBase {
        val prefs = context?.getSharedPreferences("ui-prefs", Context.MODE_PRIVATE)
        val mapSourceId = prefs?.getInt("map_style_id", 1)
        debug("mapStyleId from prefs: $mapSourceId")
        val mapSource = when (mapSourceId) {
            0 -> TileSourceFactory.MAPNIK
            1 -> TileSourceFactory.USGS_TOPO
            2 -> TileSourceFactory.USGS_SAT
            3 -> TileSourceFactory.OpenTopo
            4 -> TileSourceFactory.ROADS_OVERLAY_NL
            5 -> TileSourceFactory.CLOUDMADESMALLTILES
            6 -> TileSourceFactory.ChartbundleENRH
            7 -> TileSourceFactory.ChartbundleWAC
            else -> TileSourceFactory.MAPNIK
        }
        return mapSource
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
        super.onDestroy()
        map.onDetach()
    }
}



