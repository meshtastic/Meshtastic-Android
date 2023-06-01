package com.geeksville.mesh.ui.map

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.viewmodel.compose.viewModel
import com.geeksville.mesh.BuildConfig
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.NodeInfo
import com.geeksville.mesh.R
import com.geeksville.mesh.android.BuildUtils
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.copy
import com.geeksville.mesh.database.entity.Packet
import com.geeksville.mesh.databinding.MapViewBinding
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.model.map.CustomOverlayManager
import com.geeksville.mesh.model.map.CustomTileSource
import com.geeksville.mesh.model.map.MarkerWithLabel
import com.geeksville.mesh.ui.ScreenFragment
import com.geeksville.mesh.ui.map.components.CacheLayout
import com.geeksville.mesh.ui.map.components.DownloadButton
import com.geeksville.mesh.ui.map.components.MapStyleButton
import com.geeksville.mesh.ui.map.components.ToggleButton
import com.geeksville.mesh.util.SqlTileWriterExt
import com.geeksville.mesh.util.formatAgo
import com.geeksville.mesh.waypoint
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
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
import java.text.DateFormat
import kotlin.math.log2


@AndroidEntryPoint
class MapFragment : ScreenFragment("Map Fragment"), Logging {

    private val model: UIViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AppCompatTheme {
                    MapView(model)
                }
            }
        }
    }
}

@Composable
fun MapView(model: UIViewModel = viewModel()) {

    // UI Elements
    var cacheEstimate: String
    var cache: SqlTileWriterExt?

    // constants
    val defaultMinZoom = 1.5
    val defaultMaxZoom = 18.0
    val defaultZoomSpeed = 3000L
    val prefsName = "org.geeksville.osm.prefs"
    val mapStyleId = "map_style_id"
    var waypoints = mapOf<Int, MeshProtos.Waypoint?>()
    val nodeLayer = 1

    // Distance of bottom corner to top corner of bounding box
    val zoomLevelLowest = 13.0 // approx 5 miles long
    val zoomLevelMiddle = 12.25 // approx 10 miles long
    val zoomLevelHighest = 11.5 // approx 15 miles long

    var zoomLevelMin = 0.0
    var zoomLevelMax = 0.0

    // Map Elements
    var writer: SqliteArchiveTileWriter
    var downloadRegionBoundingBox: BoundingBox

    val context = LocalContext.current
    val mPrefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    val haptic = LocalHapticFeedback.current
    fun performHapticFeedback() = haptic.performHapticFeedback(HapticFeedbackType.LongPress)

    val map = remember {
        MapView(context).apply {
            clipToOutline = true
        }
    }
    var canDownload: Boolean by remember { mutableStateOf(false) }
    var showMarkerLongPressDialog: Int? by remember { mutableStateOf(null) }
    var showCurrentCacheInfo by remember { mutableStateOf(false) }
    var showDownloadRegionBoundingBox by remember { mutableStateOf(false) } // FIXME

    var nodePositions = listOf<MarkerWithLabel>()
    var waypointMarkers = listOf<MarkerWithLabel>()

    fun purgeTileSource() {
        cache = SqlTileWriterExt()
        val builder = MaterialAlertDialogBuilder(context)
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
                    context.getString(R.string.map_purge_success).format(item.source),
                    Toast.LENGTH_SHORT
                )
                    .show() else Toast.makeText(
                    context,
                    R.string.map_purge_fail,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        builder.setNegativeButton(R.string.cancel) { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    LaunchedEffect(showCurrentCacheInfo) {
        Toast.makeText(context, R.string.calculating, Toast.LENGTH_SHORT).show()
        val cacheManager = CacheManager(map) // Make sure CacheManager has latest from map
        val cacheCapacity = cacheManager.cacheCapacity()
        val currentCacheUsage = cacheManager.currentCacheUsage()

        val mapCacheInfoText = context.getString(
            R.string.map_cache_info,
            cacheCapacity / (1024.0 * 1024.0),
            currentCacheUsage / (1024.0 * 1024.0)
        )

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.map_cache_manager)
            .setMessage(mapCacheInfoText)
            .setNeutralButton(R.string.cancel) { dialog, _ ->
                showCurrentCacheInfo = false
                dialog.dismiss()
            }
            .show()
    }

    fun showCacheManagerDialog() {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.map_offline_manager)
            .setItems(
                arrayOf<CharSequence>(
                    context.getString(R.string.map_cache_size),
                    context.getString(R.string.map_download_region),
                    context.getString(R.string.map_clear_tiles),
                    context.getString(R.string.cancel)
                )
            ) { dialog, which ->
                when (which) {
                    0 -> showCurrentCacheInfo = true
                    1 -> {
                        downloadJobAlert()
                        dialog.dismiss()
                    }

                    2 -> purgeTileSource()
                    else -> dialog.dismiss()
                }
            }.show()
    }

    data class DialogBuilder(
        val builder: MaterialAlertDialogBuilder,
        val nameInput: EditText,
        val descInput: EditText,
        val lockedSwitch: SwitchMaterial,
    ) {
        val name get() = nameInput.text.toString().trim()
        val description get() = descInput.text.toString().trim()
    }

    fun createEditDialog(context: Context, title: String): DialogBuilder {
        val builder = MaterialAlertDialogBuilder(context)
        val layout = LayoutInflater.from(context).inflate(R.layout.dialog_add_waypoint, null)

        val nameInput: EditText = layout.findViewById(R.id.waypointName)
        val descInput: EditText= layout.findViewById(R.id.waypointDescription)
        val lockedSwitch: SwitchMaterial = layout.findViewById(R.id.waypointLocked)

        builder.setTitle(title)
        builder.setView(layout)

        return DialogBuilder(builder, nameInput, descInput, lockedSwitch)
    }

    fun showDeleteMarkerDialog(waypoint: MeshProtos.Waypoint) {
        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle(R.string.waypoint_delete)
        builder.setNeutralButton(R.string.cancel) { _, _ ->
            BuildUtils.debug("User canceled marker delete dialog")
        }
        builder.setNegativeButton(R.string.delete_for_me) { _, _ ->
            BuildUtils.debug("User deleted waypoint ${waypoint.id} for me")
            model.deleteWaypoint(waypoint.id)
        }
        if (waypoint.lockedTo in setOf(0, model.myNodeNum ?: 0) && model.isConnected())
            builder.setPositiveButton(R.string.delete_for_everyone) { _, _ ->
                BuildUtils.debug("User deleted waypoint ${waypoint.id} for everyone")
                model.sendWaypoint(waypoint.copy { expire = 1 })
                model.deleteWaypoint(waypoint.id)
            }
        val dialog = builder.show()
        for (button in setOf(
            androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL,
            androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE,
            androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE
        )) with(dialog.getButton(button)) { textSize = 12F; isAllCaps = false }
    }

    fun showEditMarkerDialog(waypoint: MeshProtos.Waypoint) {
        val dialog = createEditDialog(context, context.getString(R.string.waypoint_edit))
        dialog.nameInput.setText(waypoint.name)
        dialog.descInput.setText(waypoint.description)
        dialog.lockedSwitch.isEnabled = false
        dialog.lockedSwitch.isChecked = waypoint.lockedTo != 0
        dialog.builder.setNeutralButton(R.string.cancel) { _, _ ->
            BuildUtils.debug("User canceled marker edit dialog")
        }
        dialog.builder.setNegativeButton(R.string.delete) { _, _ ->
            BuildUtils.debug("User clicked delete waypoint ${waypoint.id}")
            showDeleteMarkerDialog(waypoint)
        }
        dialog.builder.setPositiveButton(R.string.send) { _, _ ->
            BuildUtils.debug("User edited waypoint ${waypoint.id}")
            model.sendWaypoint(waypoint.copy {
                name = dialog.name.ifEmpty { return@setPositiveButton }
                description = dialog.description
                expire = Int.MAX_VALUE // TODO add expire picker
                icon = 0 // TODO add emoji picker
            })
        }
        dialog.builder.show()
    }

    fun showMarkerLongPressDialog(id: Int) {
        BuildUtils.debug("marker long pressed id=${id}")
        val waypoint = waypoints[id] ?: return
        // edit only when unlocked or lockedTo myNodeNum
        if (waypoint.lockedTo in setOf(0, model.myNodeNum ?: 0) && model.isConnected())
            showEditMarkerDialog(waypoint)
        else
            showDeleteMarkerDialog(waypoint)
    }

//    fun downloadJobAlert() {
//        //prompt for input params .
//        binding.downloadButton.hide()
//        binding.mapStyleButton.visibility = View.GONE
//        binding.cacheLayout.visibility = View.VISIBLE
//        val builder = MaterialAlertDialogBuilder(context)
//        binding.box5miles.setOnClickListener{ generateBoxOverlay(zoomLevelLowest) }
//        binding.box10miles.setOnClickListener { generateBoxOverlay(zoomLevelMiddle) }
//        binding.box15miles.setOnClickListener { generateBoxOverlay(zoomLevelHighest) }
//        cacheEstimate = binding.cacheEstimate
//        generateBoxOverlay(zoomLevelLowest)
//        binding.executeJob.setOnClickListener { updateEstimate() }
//        binding.cancelDownload.setOnClickListener {
//            cacheEstimate.text = ""
//            defaultMapSettings()
//
//        }
//        builder.setCancelable(true)
//    }

    fun getUsername(id: String?) = if (id == DataPacket.ID_LOCAL) context.getString(R.string.you)
    else model.nodeDB.nodes.value?.get(id)?.user?.longName ?: context.getString(R.string.unknown_username)

    fun onWaypointChanged(wayPt: Collection<Packet>) {

        /**
         * Using the latest waypoint, generate GeoPoint
         */
        // Find all waypoints
        fun getCurrentWayPoints(): List<MarkerWithLabel> {
            BuildUtils.debug("Showing on map: ${wayPt.size} waypoints")
            val wayPoint = wayPt.map { pt ->
                lateinit var marker: MarkerWithLabel
                pt.data.waypoint?.let {
                    val lock = if (it.lockedTo != 0) "\uD83D\uDD12" else ""
                    val time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(pt.received_time)
                    val label = it.name + " " + formatAgo((pt.received_time / 1000).toInt())
                    val emoji = String(Character.toChars(if (it.icon == 0) 128205 else it.icon))
                    marker = MarkerWithLabel(map, label, emoji)
                    marker.id = "${it.id}"
                    marker.title = "${it.name} (${getUsername(pt.data.from)}$lock)"
                    marker.snippet = "[$time] " + it.description
                    marker.position = GeoPoint(it.latitudeI * 1e-7, it.longitudeI * 1e-7)
                    marker.setVisible(false)
                    marker.setOnLongClickListener {
                        showMarkerLongPressDialog(it.id)
                        true
                    }
                }
                marker
            }
            return wayPoint
        }
        waypointMarkers = getCurrentWayPoints()
    }

    fun onNodesChanged(nodes: Collection<NodeInfo>) {
        val nodesWithPosition = nodes.filter { it.validPosition != null }
        val ic = ContextCompat.getDrawable(context, R.drawable.ic_baseline_location_on_24)
        BuildUtils.debug("Showing on map: ${nodesWithPosition.size} nodes")
        nodePositions = nodesWithPosition.map { node ->
            val (p, u) = Pair(node.position!!, node.user!!)
            val marker = MarkerWithLabel(map, "${u.longName} ${formatAgo(p.time)}")
            marker.title = "${u.longName} ${node.batteryStr}"
            marker.snippet = model.gpsString(p)
            model.ourNodeInfo.value?.let { our ->
                our.distanceStr(node)?.let { dist ->
                    marker.subDescription = context.getString(R.string.map_subDescription)
                        .format(our.bearing(node), dist)
                }
            }
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.position = GeoPoint(p.latitude, p.longitude)
            marker.icon = ic
            marker
        }
    }


    /**
     * Create LatLong Grid line overlay
     * @param enabled: turn on/off gridlines
     */
    fun createLatLongGrid(enabled: Boolean) {
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

    /**
     * Adds copyright to map depending on what source is showing
     */
    fun addCopyright() {
        if (map.tileProvider.tileSource.copyrightNotice != null) {
            val copyrightNotice: String = map.tileProvider.tileSource.copyrightNotice
            val copyrightOverlay = CopyrightOverlay(context)
            copyrightOverlay.setCopyrightNotice(copyrightNotice)
            map.overlays.add(copyrightOverlay)
        }
    }

    fun drawOverlays() {
        map.overlayManager.overlays().clear()
        addCopyright()  // Copyright is required for certain map sources
        createLatLongGrid(false)
        map.overlayManager.addAll(nodeLayer, nodePositions)
        map.overlayManager.addAll(nodeLayer, waypointMarkers)
        map.overlayManager.add(nodeLayer, MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                InfoWindow.closeAllInfoWindowsOn(map)
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean {
                performHapticFeedback()
                if (!model.isConnected()) return true

                val dialog = createEditDialog(context, context.getString(R.string.waypoint_new))
                dialog.builder.setNeutralButton(R.string.cancel) { _, _ ->
                    BuildUtils.debug("User canceled marker create dialog")
                }
                dialog.builder.setPositiveButton(R.string.send) { _, _ ->
                    BuildUtils.debug("User created waypoint")
                    model.sendWaypoint(waypoint {
                        name = dialog.name.ifEmpty { return@setPositiveButton }
                        description = dialog.description
                        id = model.generatePacketId() ?: return@setPositiveButton
                        latitudeI = (p.latitude * 1e7).toInt()
                        longitudeI = (p.longitude * 1e7).toInt()
                        expire = Int.MAX_VALUE // TODO add expire picker
                        icon = 0 // TODO add emoji picker
                        lockedTo = if (!dialog.lockedSwitch.isChecked) 0 else model.myNodeNum ?: 0
                    })
                }
                dialog.builder.show()
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

    fun zoomToNodes(controller: IMapController) {
        val points: MutableList<GeoPoint> = mutableListOf()
        val nodesWithPosition =
            model.nodeDB.nodes.value?.values?.filter { it.validPosition != null }
        if (!nodesWithPosition.isNullOrEmpty()) {
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

    fun loadOnlineTileSourceBase(): ITileSource {
        val id = mPrefs.getInt(mapStyleId, 1)
        BuildUtils.debug("mapStyleId from prefs: $id")
        return CustomTileSource.mTileSources.getOrNull(id) ?: CustomTileSource.DEFAULT_TILE_SOURCE
    }

    Scaffold() { innerPadding ->
        AndroidView({ map }, modifier = Modifier
            .padding(innerPadding)
            .fillMaxHeight()
            .testTag("Meshtastic Map")
            .semantics { }
        ) { map ->
            val mapController = map.controller
            // Required to get online tiles
            Configuration.getInstance().userAgentValue = BuildConfig.APPLICATION_ID

            /**
             * Creates Box overlay showing what area can be downloaded
             */
            fun generateBoxOverlay(zoomLevel: Double) {
                drawOverlays()
                map.setMultiTouchControls(false)
                // furthest back
                zoomLevelMax = zoomLevel
                // furthest in min should be > than max
                zoomLevelMin = map.tileProvider.tileSource.maximumZoomLevel.toDouble()
                mapController.setZoom(zoomLevel)
                downloadRegionBoundingBox = map.boundingBox
                val polygon = Polygon().apply {
                    points = Polygon.pointsAsRect(downloadRegionBoundingBox)
                        .map { GeoPoint(it.latitude, it.longitude) }
                }
                map.overlayManager.add(polygon)
                mapController.setZoom(zoomLevel - 1.0)
                val cacheManager = CacheManager(map)
                val tileCount: Int =
                    cacheManager.possibleTilesInArea(
                        downloadRegionBoundingBox,
                        zoomLevelMax.toInt(),
                        zoomLevelMin.toInt()
                    )
                cacheEstimate = context.getString(R.string.map_cache_tiles).format(tileCount)
            }

            fun setupMapProperties() {
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
//                map.addMapListener(object : MapListener {
//                    override fun onScroll(event: ScrollEvent): Boolean {
//                        if (binding.cacheLayout.visibility == View.VISIBLE) {
//                            generateBoxOverlay(zoomLevelMax)
//                        }
//                        return true
//                    }
//
//                    override fun onZoom(event: ZoomEvent): Boolean {
//                        return false
//                    }
//                })
            }

            /**
             * Reset map to default settings & visible buttons
             */
            fun defaultMapSettings() {
                setupMapProperties()
                drawOverlays()
            }

            fun downloadRegion(bb: BoundingBox, zoommin: Int, zoommax: Int) {
                cacheManager.downloadAreaAsync(
                    context,
                    bb,
                    zoommin,
                    zoommax,
                    object : CacheManager.CacheManagerCallback {
                        override fun onTaskComplete() {
                            Toast.makeText(context, R.string.map_download_complete, Toast.LENGTH_LONG)
                                .show()
                            writer.onDetach()
                            defaultMapSettings()
                        }

                        override fun onTaskFailed(errors: Int) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.map_download_errors).format(errors),
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

            /**
             * if true, start the job
             * if false, just update the dialog box
             */
            fun updateEstimate() {
                try {
                    if (showDownloadRegionBoundingBox) {
                        val outputName =
                            Configuration.getInstance().osmdroidBasePath.absolutePath + File.separator + "mainFile.sqlite" // TODO: Accept filename input param from user
                        writer = SqliteArchiveTileWriter(outputName)
                        //nesw
                        try {
                            val cacheManager = CacheManager(map, writer) // Make sure cacheManager has latest from map
                        } catch (ex: TileSourcePolicyException) {
                            BuildUtils.debug("Tile source does not allow archiving: ${ex.message}")
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

            setupMapProperties()

            canDownload =
                (map.tileProvider.tileSource as OnlineTileSourceBase).tileSourcePolicy.acceptsBulkDownload()
            map.setTileSource(loadOnlineTileSourceBase())
//                if (binding.cacheLayout.visibility == View.GONE) {
//                    model.nodeDB.nodes.value?.let { nodes ->
//                        onNodesChanged(nodes.values)
//                        drawOverlays()
//                    }
//                }
//                if (binding.cacheLayout.visibility == View.GONE) {
//                    model.nodeDB.nodes.observe(viewLifecycleOwner) { nodes ->
//                        onNodesChanged(nodes.values)
//                        drawOverlays()
//                    }
//                    model.waypoints.observe(viewLifecycleOwner) {
//                        debug("New waypoints received: ${it.size}")
//                        waypoints = it.mapValues { p -> p.value.data.waypoint }
//                        onWaypointChanged(it.values)
//                        drawOverlays()
//                    }
//                }
            zoomToNodes(mapController)
        }
    }
    DownloadButton(
        cacheMenu = {
            CacheLayout(onExecuteJob = {
                updateEstimate()
            },
                onCancelDownload = {
                    cacheEstimate = ""
                    defaultMapSettings()
                }
            )
        }, canDownload
    ) {
        showCacheManagerDialog()
    }
    MapStyleButton {
        val builder = MaterialAlertDialogBuilder(context)
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
            BuildUtils.debug("Set mapStyleId pref to $which")
            val editor: SharedPreferences.Editor = mPrefs.edit()
            editor.putInt(mapStyleId, which)
            editor.apply()
            dialog.dismiss()
            map.setTileSource(loadOnlineTileSourceBase())
            drawOverlays()
            canDownload = (map.tileProvider.tileSource as OnlineTileSourceBase)
                .tileSourcePolicy.acceptsBulkDownload()
        }
        val dialog = builder.create()
        dialog.show()
    }
}
