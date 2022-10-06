package com.geeksville.mesh.ui

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import com.geeksville.mesh.BuildConfig
import com.geeksville.mesh.NodeInfo
import com.geeksville.mesh.R
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.database.entity.Packet
import com.geeksville.mesh.databinding.MapViewBinding
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.model.map.CustomOverlayManager
import com.geeksville.mesh.model.map.CustomTileSource
import com.geeksville.mesh.util.formatAgo
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dagger.hilt.android.AndroidEntryPoint
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.cachemanager.CacheManager.CacheManagerCallback
import org.osmdroid.tileprovider.modules.SqliteArchiveTileWriter
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourcePolicyException
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.*
import org.osmdroid.views.overlay.gridlines.LatLonGridlineOverlay2
import java.io.File
import kotlin.math.pow


@AndroidEntryPoint
class MapFragment : ScreenFragment("Map"), Logging, View.OnClickListener {

    // UI Elements
    private lateinit var binding: MapViewBinding
    private lateinit var map: MapView
    private lateinit var downloadBtn: FloatingActionButton
    private lateinit var cacheEstimate: TextView
    private lateinit var cancelDownload: Button
    private lateinit var fiveMileButton: Button
    private lateinit var tenMileButton: Button
    private lateinit var fifteenMileButton: Button
    private lateinit var executeJob: Button
    private var downloadPrompt: AlertDialog? = null
    private var alertDialog: AlertDialog? = null

    // constants
    private val defaultMinZoom = 1.5
    private val nodeZoomLevel = 8.5
    private val defaultZoomSpeed = 3000L
    private val prefsName = "org.geeksville.osm.prefs"
    private val mapStyleId = "map_style_id"
    private var nodePositions = listOf<MarkerWithLabel>()
    private var wayPoints = listOf<MarkerWithLabel>()
    private val nodeLayer = 1
    private val zoomLevelLowest = 16.0
    private val zoomLevelMiddle = 14.0
    private val zoomLevelHighest = 10.0
    private var zoomLevelMin = 0.0
    private var zoomLevelMax = 0.0

    // Map Elements
    private lateinit var mapController: IMapController
    private lateinit var mPrefs: SharedPreferences
    private lateinit var writer: SqliteArchiveTileWriter
    private val model: UIViewModel by activityViewModels()
    private lateinit var cacheManager: CacheManager
    private lateinit var downloadRegionBoundingBox: BoundingBox


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = MapViewBinding.inflate(inflater)
        downloadBtn = binding.root.findViewById(R.id.downloadButton)
        binding.cacheLayout.visibility = View.GONE
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
        renderDownloadButton()
        map.let {
            if (view != null) {
                mapController = map.controller
                binding.mapStyleButton.setOnClickListener {
                    chooseMapStyle()
                }
                if (binding.cacheLayout.visibility == View.GONE) {
                    model.nodeDB.nodes.value?.let { nodes ->
                        onNodesChanged(nodes.values)
                        drawOverlays()
                    }
                }
            }
            if (binding.cacheLayout.visibility == View.GONE) {
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
            }
            zoomToNodes(mapController)
        }
        downloadBtn.setOnClickListener(this)
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.executeJob -> updateEstimate()
            R.id.downloadButton -> showCacheManagerDialog()
            R.id.box5miles -> generateBoxOverlay(zoomLevelLowest)
            R.id.box10miles -> generateBoxOverlay(zoomLevelMiddle)
            R.id.box15miles -> generateBoxOverlay(zoomLevelHighest)
        }
    }


    private fun showCacheManagerDialog() {
        val alertDialogBuilder = AlertDialog.Builder(
            activity
        )
        // set title
        alertDialogBuilder.setTitle("Cache Manager")

        // set dialog message
        alertDialogBuilder.setItems(
            arrayOf<CharSequence>(
                "Cache current size",
                "Download Region",
                "Clear Cache",
                resources.getString(R.string.cancel)
            )
        ) { dialog, which ->
            when (which) {
                0 -> showCurrentCacheInfo()
                1 -> {
                    downloadJobAlert()
                    dialog.dismiss()
                }
                2 -> clearCache()
                else -> dialog.dismiss()
            }
        }
        // create alert dialog
        alertDialog = alertDialogBuilder.create()

        // show it
        alertDialog!!.show()

    }

    private fun clearCache() {
        map.canZoomIn()
    }


    private fun showCurrentCacheInfo() {
        Toast.makeText(activity, "Calculating...", Toast.LENGTH_SHORT).show()
        cacheManager = CacheManager(map) // Make sure CacheManager has latest from map
        Thread {
            val alertDialogBuilder = AlertDialog.Builder(
                activity
            )
            // set title
            alertDialogBuilder.setTitle("Cache Manager")
                .setMessage(
                    """
                    Cache Capacity (mb): ${cacheManager.cacheCapacity() * 2.0.pow(-20.0)}
                    Cache Usage (mb): ${cacheManager.currentCacheUsage() * 2.0.pow(-20.0)}
                    """.trimIndent()
                )
            // set dialog message
            alertDialogBuilder.setItems(
                arrayOf<CharSequence>(
                    resources.getString(R.string.cancel)
                )
            ) { dialog, _ -> dialog.dismiss() }
            activity!!.runOnUiThread { // show it
                // create alert dialog
                val alertDialog = alertDialogBuilder.create()
                alertDialog.show()
            }
        }.start()
    }


    private fun downloadJobAlert() {
        //prompt for input params .
        map.overlayManager = CustomOverlayManager.create(map, context)
        binding.downloadButton.hide()
        binding.cacheLayout.visibility = View.VISIBLE
        val builder = AlertDialog.Builder(activity)
        fiveMileButton = binding.box5miles
        fiveMileButton.setOnClickListener(this)
        tenMileButton = binding.box10miles
        tenMileButton.setOnClickListener(this)
        fifteenMileButton = binding.box15miles
        fifteenMileButton.setOnClickListener(this)
        cacheEstimate = binding.cacheEstimate
        generateBoxOverlay(zoomLevelLowest)
        executeJob = binding.executeJob
        executeJob.setOnClickListener(this)
        binding.cancelDownload.setOnClickListener {
            cacheEstimate.text = ""
            drawOverlays()
            binding.downloadButton.show()
            binding.cacheLayout.visibility = View.GONE
            setupMapProperties()
        }
        builder.setCancelable(true)
    }

    /**
     * Creates Box overlay showing what area can be downloaded
     */
    private fun generateBoxOverlay(zoomLevel: Double) {
        map.invalidate()
        map.setMultiTouchControls(false)
        drawOverlays()
        zoomLevelMax = zoomLevel
        zoomLevelMin = map.minZoomLevel
        mapController.setZoom(zoomLevel)
        downloadRegionBoundingBox = map.boundingBox
        val polygon = Polygon()
        polygon.points = Polygon.pointsAsRect(downloadRegionBoundingBox) as MutableList<GeoPoint>
        map.overlayManager.add(polygon)
        mapController.setZoom(zoomLevel - 1.0)
        cacheManager = CacheManager(map)
        val tilecount: Int =
            cacheManager.possibleTilesInArea(
                downloadRegionBoundingBox,
                zoomLevelMin.toInt(),
                zoomLevelMax.toInt()
            )
        cacheEstimate.text = ("$tilecount tiles")
    }


    /**
     * if true, start the job
     * if false, just update the dialog box
     */
    private fun updateEstimate() {
        try {
            if (this::downloadRegionBoundingBox.isInitialized) {
                val outputName =
                    Configuration.getInstance().osmdroidBasePath.absolutePath + File.separator + "mainFile.sqlite" // TODO: Accept filename input param from user
                writer = SqliteArchiveTileWriter(outputName)
                //nesw
                if (downloadPrompt != null) {
                    downloadPrompt!!.dismiss()
                    downloadPrompt = null
                }
                try {
                    cacheManager =
                        CacheManager(map, writer) // Make sure cacheManager has latest from map
                } catch (ex: TileSourcePolicyException) {
                    Log.d("MapFragment", "Tilesource does not allow archiving: ${ex.message}")
                    return
                }
                //this triggers the download
                downloadRegion(
                    downloadRegionBoundingBox,
                    zoomLevelMin.toInt(),
                    zoomLevelMax.toInt()
                )
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    private fun downloadRegion(bb: BoundingBox, zoommin: Int, zoommax: Int) {
        cacheManager.downloadAreaAsync(
            activity,
            bb,
            zoommin,
            zoommax,
            object : CacheManagerCallback {
                override fun onTaskComplete() {
                    Toast.makeText(activity, "Download complete!", Toast.LENGTH_LONG)
                        .show()
                    writer.onDetach()
                    binding.downloadButton.show()
                    binding.cacheLayout.visibility = View.GONE
                    setupMapProperties()
                    drawOverlays()
                }

                override fun onTaskFailed(errors: Int) {
                    Toast.makeText(
                        activity,
                        "Download complete with $errors errors",
                        Toast.LENGTH_LONG
                    ).show()
                    writer.onDetach()
                }

                override fun updateProgress(
                    progress: Int,
                    currentZoomLevel: Int,
                    zoomMin: Int,
                    zoomMax: Int
                ) {
                    //NOOP since we are using the build in UI
                }

                override fun downloadStarted() {
                    //NOOP since we are using the build in UI
                }

                override fun setPossibleTilesInArea(total: Int) {
                    //NOOP since we are using the build in UI
                }
            })
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
            renderDownloadButton()
        }
        val dialog = builder.create()
        dialog.show()
    }

    private fun renderDownloadButton() {
        if (!(map.tileProvider.tileSource as OnlineTileSourceBase).tileSourcePolicy.acceptsBulkDownload()) {
            downloadBtn.hide()
        } else {
            downloadBtn.show()
        }
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
                        requireActivity(), R.drawable.ic_baseline_location_on_24
                    )
                }
                marker
            }
            return mrkr
        }
        nodePositions = getCurrentNodes()
    }


    /**
     * Create LatLong Grid line overlay
     * @param enabled: turn on/off gridlines
     */
    private fun createLatLongGrid(enabled: Boolean) {
        val latLongGridOverlay = LatLonGridlineOverlay2()
        latLongGridOverlay.isEnabled = enabled
        if (latLongGridOverlay.isEnabled) {
            val textPaint = Paint()
            textPaint.textSize = 40f
            textPaint.color = Color.GRAY
            textPaint.isAntiAlias = true
            textPaint.isFakeBoldText = true
            textPaint.textAlign = Paint.Align.CENTER
            latLongGridOverlay.textPaint = textPaint
            latLongGridOverlay.setBackgroundColor(Color.TRANSPARENT)
            latLongGridOverlay.setLineWidth(3.0f)
            latLongGridOverlay.setLineColor(Color.GRAY)
            map.overlayManager.add(latLongGridOverlay)
        }
    }

    private fun drawOverlays() {
        map.overlayManager.overlays().clear()
        addCopyright()  // Copyright is required for certain map sources
        createLatLongGrid(false)
        map.overlayManager.addAll(nodeLayer, nodePositions)
        map.overlayManager.addAll(nodeLayer, wayPoints)
        map.invalidate()
    }

    /**
     * Adds copyright to map depending on what source is showing
     */
    private fun addCopyright() {
        if (map.tileProvider.tileSource.copyrightNotice != null) {
            val copyrightNotice: String = map.tileProvider.tileSource.copyrightNotice
            val copyrightOverlay = CopyrightOverlay(context)
            copyrightOverlay.setCopyrightNotice(copyrightNotice)
            map.overlays.add(copyrightOverlay)
        }
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
            map.addMapListener(object : MapListener {
                override fun onScroll(event: ScrollEvent): Boolean {
                    if (binding.cacheLayout.visibility == View.VISIBLE) {
                        generateBoxOverlay(zoomLevelMax)
                    }
                    return true
                }

                override fun onZoom(event: ZoomEvent): Boolean {
                    return false
                }
            })
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
                            it.position!!.latitude, it.position!!.longitude
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
        if (alertDialog != null && alertDialog!!.isShowing) {
            alertDialog!!.dismiss()
        }
        if (downloadPrompt != null && downloadPrompt!!.isShowing) {
            downloadPrompt!!.dismiss()
        }
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



