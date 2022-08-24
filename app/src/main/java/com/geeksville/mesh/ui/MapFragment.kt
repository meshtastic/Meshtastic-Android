package com.geeksville.mesh.ui


import android.content.Context
import android.content.SharedPreferences
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


    private val defaultLat = 38.8976763
    private val defaultLong = -77.0365298
    private val defaultMinZoom = 3.0
    private val defaultZoomLevel = 6.0
    private val defaultZoomSpeed = 3000L
    private val prefsName = "org.andnav.osm.prefs"
    private val prefsZoomLevelDouble = "prefsZoomLevelDouble"
    private val prefsTileSource = "prefsTileSource"
    private val mapStyleId = "map_style_id"
    private val mapTag = "mapView"
    private val uiPrefs = "ui-prefs"


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = MapViewBinding.inflate(inflater, container, false)
        map = binding.map
        map.setDestroyMode(false)
        map.tag = mapTag
        return binding.root
    }

    override fun onViewCreated(viewIn: View, savedInstanceState: Bundle?) {
        super.onViewCreated(viewIn, savedInstanceState)
        Configuration.getInstance().userAgentValue =
            BuildConfig.APPLICATION_ID // Required to get online tiles

        mPrefs = context!!.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

        addCopyright() // Copyright is required for certain map sources
        setupMapProperties()
        loadOnlineTileSourceBase()
        mapController = map.controller
        val point = GeoPoint(defaultLat, defaultLong) //White House Coordinates, Washington DC
        mapController.animateTo(point, defaultZoomLevel, defaultZoomSpeed)
        if (view != null) {
            binding.fabStyleToggle.setOnClickListener {
                chooseMapStyle()
            }
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

    private fun chooseMapStyle() {
        /// Prepare dialog and its items
        val builder = MaterialAlertDialogBuilder(context!!)
        builder.setTitle(getString(R.string.preferences_map_style))
        val mapStyles by lazy { resources.getStringArray(R.array.map_styles) }

        /// Load preferences and its value
        val prefs = UIViewModel.getPreferences(context!!)
        val editor: SharedPreferences.Editor = prefs.edit()
        val mapStyleId = prefs.getInt("map_style_id", 1)
        debug("mapStyleId from prefs: $mapStyleId")

        builder.setSingleChoiceItems(mapStyles, mapStyleId) { dialog, which ->
            debug("Set mapStyleId pref to $which")
            editor.putInt("map_style_id", which)
            editor.apply()
            dialog.dismiss()
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
                map.invalidate()
                map.overlays.add(marker)
            }
            f
        }
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
            map.isTilesScaledToDpi =
                true // scales the map tiles to the display density of the
            map.minZoomLevel =
                defaultMinZoom // sets the minimum zoom level (the furthest out you can zoom)
            map.setMultiTouchControls(true) // Sets gesture controls to true
            map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER) // Disables default +/- button for zooming
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
        val prefs = context?.getSharedPreferences(uiPrefs, Context.MODE_PRIVATE)
        val mapSourceId = prefs?.getInt(mapStyleId, 1)
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
        val edit = mPrefs.edit()
        edit.putString(prefsTileSource, loadOnlineTileSourceBase().name())
        edit.putFloat(prefsZoomLevelDouble, map.zoomLevelDouble.toFloat())
        edit.commit()

        map.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        val tileSourceName = mPrefs.getString(
            prefsTileSource,
            TileSourceFactory.DEFAULT_TILE_SOURCE.name()
        )
        try {
            val tileSource = TileSourceFactory.getTileSource(tileSourceName)
            map.setTileSource(tileSource)
        } catch (e: IllegalArgumentException) {
            map.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE)
        }
    }

    override fun onDestroy() {
        super.onDestroyView()
        map.onDetach()
    }
}



