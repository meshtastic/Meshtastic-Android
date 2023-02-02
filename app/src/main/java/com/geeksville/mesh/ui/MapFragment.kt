package com.geeksville.mesh.ui

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import com.geeksville.mesh.BuildConfig
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.NodeInfo
import com.geeksville.mesh.R
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.database.entity.Packet
import com.geeksville.mesh.databinding.MapViewBinding
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.model.map.CustomOverlayManager
import com.geeksville.mesh.model.map.CustomTileSource
import com.geeksville.mesh.util.SqlTileWriterExt
import com.geeksville.mesh.util.formatAgo
import com.geeksville.mesh.waypoint
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import dagger.hilt.android.AndroidEntryPoint
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
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
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.gridlines.LatLonGridlineOverlay2
import org.osmdroid.views.overlay.infowindow.InfoWindow
import java.io.File
import kotlin.math.log2


@AndroidEntryPoint
class MapFragment : ScreenFragment("Map Fragment"), Logging, View.OnClickListener {

    // UI Elements
    private lateinit var binding: MapViewBinding
    private lateinit var map: MapView
    private lateinit var cacheEstimate: TextView
    private lateinit var executeJob: Button
    private var downloadPrompt: AlertDialog? = null
    private var alertDialog: AlertDialog? = null
    private var cache: SqlTileWriterExt? = null

    // constants
    private val defaultMinZoom = 1.5
    private val defaultMaxZoom = 18.0
    private val defaultZoomSpeed = 3000L
    private val prefsName = "org.geeksville.osm.prefs"
    private val mapStyleId = "map_style_id"
    private var nodePositions = listOf<MarkerWithLabel>()
    private var wayPoints = listOf<MarkerWithLabel>()
    private val nodeLayer = 1

    // Distance of bottom corner to top corner of bounding box
    private val zoomLevelLowest = 13.0 // approx 5 miles long
    private val zoomLevelMiddle = 12.25 // approx 10 miles long
    private val zoomLevelHighest = 11.5 // approx 15 miles long

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
        binding.downloadButton.setOnClickListener(this)
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

    private fun performHapticFeedback() = requireView().performHapticFeedback(
        HapticFeedbackConstants.LONG_PRESS,
        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
    )

    private fun showCacheManagerDialog() {
        val alertDialogBuilder = AlertDialog.Builder(
            activity
        )
        // set title
        alertDialogBuilder.setTitle(R.string.map_offline_manager)
        // set dialog message
        alertDialogBuilder.setItems(
            arrayOf<CharSequence>(
                resources.getString(R.string.map_cache_size),
                resources.getString(R.string.map_download_region),
                resources.getString(R.string.map_clear_tiles),
                resources.getString(R.string.cancel)
            )
        ) { dialog, which ->
            when (which) {
                0 -> showCurrentCacheInfo()
                1 -> {
                    downloadJobAlert()
                    dialog.dismiss()
                }
                2 -> purgeTileSource()
                else -> dialog.dismiss()
            }
        }
        // create alert dialog
        alertDialog = alertDialogBuilder.create()

        // show it
        alertDialog!!.show()

    }

    private fun purgeTileSource() {
        cache = SqlTileWriterExt()
        val builder = AlertDialog.Builder(context)
        builder.setTitle(R.string.map_tile_source)
        val sources = cache!!.sources
        val sourceList = mutableListOf<String>()
        for (i in sources.indices) {
            sourceList.add(sources[i].source as String)
        }
        val selected: BooleanArray? = null
        val selectedList = mutableListOf<Int>()
        builder.setMultiChoiceItems(
            sourceList.toTypedArray(),
            selected
        ) { _, i, b ->
            if (b) {
                selectedList.add(i)
            } else {
                selectedList.remove(i)
            }

        }
        builder.setPositiveButton(R.string.clear) { _, _ ->
            for (x in selectedList) {
                val item = sources[x]
                val b = cache!!.purgeCache(item.source)
                if (b) Toast.makeText(
                    context,
                    getString(R.string.map_purge_success).format(item.source),
                    Toast.LENGTH_SHORT
                )
                    .show() else Toast.makeText(
                    context,
                    R.string.map_purge_fail,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        builder.setNegativeButton(
            R.string.cancel
        ) { dialog, _ -> dialog.cancel() }
        builder.show()
    }


    private fun showCurrentCacheInfo() {
        Toast.makeText(activity, R.string.calculating, Toast.LENGTH_SHORT).show()
        cacheManager = CacheManager(map) // Make sure CacheManager has latest from map
        Thread {
            val alertDialogBuilder = AlertDialog.Builder(
                activity
            )
            // set title
            alertDialogBuilder.setTitle(R.string.map_cache_manager)
                .setMessage(
                    getString(R.string.map_cache_info).format(
                        cacheManager.cacheCapacity() / (1024.0 * 1024.0),
                        cacheManager.currentCacheUsage() / (1024.0 * 1024.0)
                    )
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

    fun showMarkerLongPressDialog(id: Int) {
        debug("marker long pressed id=${id}")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("${getString(R.string.delete)}?")
            .setNeutralButton(R.string.cancel) { _, _ ->
                debug("User canceled marker edit dialog")
            }
//            .setNegativeButton(R.string.edit) { _, _ ->
//                debug("Negative button pressed") // TODO add Edit option
//            }
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                debug("User deleted local waypoint $id")
                model.deleteWaypoint(id)
            }
            .show()
    }

    private fun downloadJobAlert() {
        //prompt for input params .
        binding.downloadButton.hide()
        binding.mapStyleButton.visibility = View.GONE
        binding.cacheLayout.visibility = View.VISIBLE
        val builder = AlertDialog.Builder(activity)
        binding.box5miles.setOnClickListener(this)
        binding.box10miles.setOnClickListener(this)
        binding.box15miles.setOnClickListener(this)
        cacheEstimate = binding.cacheEstimate
        generateBoxOverlay(zoomLevelLowest)
        executeJob = binding.executeJob
        executeJob.setOnClickListener(this)
        binding.cancelDownload.setOnClickListener {
            cacheEstimate.text = ""
            defaultMapSettings()

        }
        builder.setCancelable(true)
    }

    /**
     * Reset map to default settings & visible buttons
     */
    private fun defaultMapSettings() {
        binding.downloadButton.show()
        binding.mapStyleButton.visibility = View.VISIBLE
        binding.cacheLayout.visibility = View.GONE
        setupMapProperties()
        drawOverlays()
    }

    /**
     * Creates Box overlay showing what area can be downloaded
     */
    private fun generateBoxOverlay(zoomLevel: Double) {
        drawOverlays()
        map.setMultiTouchControls(false)
        zoomLevelMax = zoomLevel // furthest back
        zoomLevelMin =
            map.tileProvider.tileSource.maximumZoomLevel.toDouble() // furthest in min should be > than max
        mapController.setZoom(zoomLevel)
        downloadRegionBoundingBox = map.boundingBox
        val polygon = Polygon().apply {
            points = Polygon.pointsAsRect(downloadRegionBoundingBox)
                .map { GeoPoint(it.latitude, it.longitude) }
        }
        map.overlayManager.add(polygon)
        mapController.setZoom(zoomLevel - 1.0)
        cacheManager = CacheManager(map)
        val tileCount: Int =
            cacheManager.possibleTilesInArea(
                downloadRegionBoundingBox,
                zoomLevelMax.toInt(),
                zoomLevelMin.toInt()
            )
        cacheEstimate.text = getString(R.string.map_cache_tiles).format(tileCount)
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
                    debug("Tile source does not allow archiving: ${ex.message}")
                    return
                }
                //this triggers the download
                downloadRegion(
                    downloadRegionBoundingBox,
                    zoomLevelMax.toInt(),
                    zoomLevelMin.toInt(),
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
                    Toast.makeText(activity, R.string.map_download_complete, Toast.LENGTH_LONG)
                        .show()
                    writer.onDetach()
                    defaultMapSettings()
                }

                override fun onTaskFailed(errors: Int) {
                    Toast.makeText(
                        activity,
                        getString(R.string.map_download_errors).format(errors),
                        Toast.LENGTH_LONG
                    ).show()
                    writer.onDetach()
                    defaultMapSettings()
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
        val mapStyles = arrayOf<CharSequence>(
            "OpenStreetMap",
            "USGS TOPO",
            "Open TOPO",
            "ESRI World TOPO",
            "USGS Satellite",
            "ESRI World Overview",
        )

        /// Load preferences and its value
        val mapStyleInt = mPrefs.getInt(mapStyleId, 1)
        builder.setSingleChoiceItems(mapStyles, mapStyleInt) { dialog, which ->
            debug("Set mapStyleId pref to $which")
            val editor: SharedPreferences.Editor = mPrefs.edit()
            editor.putInt(mapStyleId, which)
            editor.apply()
            dialog.dismiss()
            map.setTileSource(loadOnlineTileSourceBase())
            renderDownloadButton()
            drawOverlays()
        }
        val dialog = builder.create()
        dialog.show()
    }

    private fun renderDownloadButton() {
        if (!(map.tileProvider.tileSource as OnlineTileSourceBase).tileSourcePolicy.acceptsBulkDownload()) {
            binding.downloadButton.hide()
        } else {
            binding.downloadButton.show()
        }
    }

    private fun getUsername(id: String?) = if (id == DataPacket.ID_LOCAL) getString(R.string.you)
    else model.nodeDB.nodes.value?.get(id)?.user?.longName ?: getString(R.string.unknown_username)

    private fun onWaypointChanged(wayPt: Collection<Packet>) {

        /**
         * Using the latest waypoint, generate GeoPoint
         */
        // Find all waypoints
        fun getCurrentWayPoints(): List<MarkerWithLabel> {
            debug("Showing on map: ${wayPt.size} waypoints")
            val wayPoint = wayPt.map { pt ->
                lateinit var marker: MarkerWithLabel
                pt.data.waypoint?.let {
                    val lock = if (it.lockedTo != 0) "\uD83D\uDD12" else ""
                    val label = it.name + " " + formatAgo((pt.received_time / 1000).toInt())
                    val emoji = String(Character.toChars(if (it.icon == 0) 128205 else it.icon))
                    marker = MarkerWithLabel(map, label, emoji)
                    marker.id = "${it.id}"
                    marker.title = "${it.name} (${getUsername(pt.data.from)}$lock)"
                    marker.snippet = it.description
                    marker.position = GeoPoint(it.latitudeI * 1e-7, it.longitudeI * 1e-7)
                    marker.setVisible(false)
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
            debug("Showing on map: ${nodesWithPosition.size} nodes")
            val mrkr = nodesWithPosition.map { node ->
                val p = node.position!!
                lateinit var marker: MarkerWithLabel
                node.user?.let {
                    val label = it.longName + " " + formatAgo(p.time)
                    marker = MarkerWithLabel(map, label)
                    marker.title = "${it.longName} ${node.batteryStr}"
                    marker.snippet = model.gpsString(p)
                    model.ourNodeInfo.value?.let { our ->
                        our.distanceStr(node)?.let { dist ->
                            marker.subDescription = getString(R.string.map_subDescription).format(
                                our.bearing(node),
                                dist
                            )
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
        map.overlayManager.add(nodeLayer, MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                InfoWindow.closeAllInfoWindowsOn(map)
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean {
                performHapticFeedback()
                if (!model.isConnected()) return true

                val layout = LayoutInflater.from(requireContext())
                    .inflate(R.layout.dialog_add_waypoint, null)

                val nameInput: EditText = layout.findViewById(R.id.waypointName)
                val descriptionInput: EditText= layout.findViewById(R.id.waypointDescription)
                val lockedInput: SwitchMaterial = layout.findViewById(R.id.waypointLocked)

                MaterialAlertDialogBuilder(requireContext())
                    .setView(layout)
                    .setNeutralButton(R.string.cancel) { _, _ ->
                        debug("User canceled marker create dialog")
                    }
                    .setPositiveButton(getString(R.string.send)) { _, _ ->
                        debug("User created waypoint")
                        model.sendWaypoint(waypoint {
                            name = nameInput.text.toString().ifEmpty { return@setPositiveButton }
                            description = descriptionInput.text.toString()
                            id = model.generatePacketId() ?: return@setPositiveButton
                            latitudeI = (p.latitude * 1e7).toInt()
                            longitudeI = (p.longitude * 1e7).toInt()
                            expire = Int.MAX_VALUE // TODO add expire picker
                            icon = 0 // TODO add emoji picker
                            lockedTo = if (!lockedInput.isChecked) 0
                            else model.myNodeInfo.value?.myNodeNum ?: 0
                        })
                    }
                    .show()
                return true
            }
        }))
        map.invalidate()
    }

//    private fun addWeatherLayer() {
    //        if (map.tileProvider.tileSource.name()
//                .equals(CustomTileSource.getTileSource("ESRI World TOPO").name())
//        ) {
//            val layer = TilesOverlay(
//                MapTileProviderBasic(
//                    activity,
//                    CustomTileSource.OPENWEATHER_RADAR
//                ), context
//            )
//            layer.loadingBackgroundColor = Color.TRANSPARENT
//            layer.loadingLineColor = Color.TRANSPARENT
//            map.overlayManager.add(layer)
//        }
//    }

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
            map.overlayManager = CustomOverlayManager.create(map, context)
            map.setScrollableAreaLimitLatitude(
                map.overlayManager.tilesOverlay.bounds.actualNorth,
                map.overlayManager.tilesOverlay.bounds.actualSouth,
                0
            ) // bounds scrollable map
            map.isTilesScaledToDpi =
                true // scales the map tiles to the display density of the screen
            map.minZoomLevel =
                defaultMinZoom // sets the minimum zoom level (the furthest out you can zoom)
            map.maxZoomLevel = defaultMaxZoom
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
            val maximumZoomLevel = map.tileProvider.tileSource.maximumZoomLevel.toDouble()
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
                val topLeft = GeoPoint(box.latNorth, box.lonWest)
                val bottomRight = GeoPoint(box.latSouth, box.lonEast)
                val latLonWidth = topLeft.distanceToAsDouble(GeoPoint(topLeft.latitude, bottomRight.longitude))
                val latLonHeight = topLeft.distanceToAsDouble(GeoPoint(bottomRight.latitude, topLeft.longitude))
                val requiredLatZoom = log2(360.0 / (latLonHeight / 111320))
                val requiredLonZoom = log2(360.0 / (latLonWidth / 111320))
                val requiredZoom = requiredLatZoom.coerceAtLeast(requiredLonZoom)
                val finalZoomLevel = (requiredZoom * 0.8).coerceAtMost(maximumZoomLevel)
                controller.animateTo(point, finalZoomLevel, defaultZoomSpeed)
            } else {
                // Only one node, just zoom in on it
                val it = nodesWithPosition[0].position!!
                points.add(GeoPoint(it.latitude, it.longitude))
                controller.animateTo(points[0], maximumZoomLevel, defaultZoomSpeed)
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

    override fun onDestroyView() {
        super.onDestroyView()
        map.onDetach()
    }

    private inner class MarkerWithLabel(mapView: MapView?, label: String, emoji: String? = null) :
        Marker(mapView) {
        private val mLabel = label
        private val mEmoji = emoji
        private val textPaint = Paint().apply {
            textSize = 40f
            color = Color.DKGRAY
            isAntiAlias = true
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        private val emojiPaint = Paint().apply {
            textSize = 80f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        private val bgPaint = Paint().apply { color = Color.WHITE }

        private fun getTextBackgroundSize(text: String, x: Float, y: Float): Rect {
            val fontMetrics = textPaint.fontMetrics
            val halfTextLength = textPaint.measureText(text) / 2 + 3
            return Rect(
                (x - halfTextLength).toInt(),
                (y + fontMetrics.top).toInt(),
                (x + halfTextLength).toInt(),
                (y + fontMetrics.bottom).toInt()
            )
        }

        override fun onLongPress(event: MotionEvent?, mapView: MapView?): Boolean {
            val touched = hitTest(event, mapView)
            if (touched && this.id != null) {
                performHapticFeedback()
                this.id.toIntOrNull()?.run(::showMarkerLongPressDialog)
            }
            return super.onLongPress(event, mapView)
        }

        override fun draw(c: Canvas, osmv: MapView?, shadow: Boolean) {
            super.draw(c, osmv, false)
            val p = mPositionPixels
            val bgRect = getTextBackgroundSize(mLabel, (p.x - 0f), (p.y - 110f))
            c.drawRect(bgRect, bgPaint)
            c.drawText(mLabel, (p.x - 0f), (p.y - 110f), textPaint)
            mEmoji?.let { c.drawText(it, (p.x - 0f), (p.y - 30f), emojiPaint) }
        }
    }
}



