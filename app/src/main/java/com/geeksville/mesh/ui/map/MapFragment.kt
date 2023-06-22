package com.geeksville.mesh.ui.map

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.viewmodel.compose.viewModel
import com.geeksville.mesh.BuildConfig
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.MeshProtos.Waypoint
import com.geeksville.mesh.NodeInfo
import com.geeksville.mesh.R
import com.geeksville.mesh.android.BuildUtils.debug
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.copy
import com.geeksville.mesh.database.entity.Packet
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.model.map.CustomOverlayManager
import com.geeksville.mesh.model.map.CustomTileSource
import com.geeksville.mesh.model.map.MarkerWithLabel
import com.geeksville.mesh.ui.ScreenFragment
import com.geeksville.mesh.ui.map.components.CacheLayout
import com.geeksville.mesh.ui.map.components.DownloadButton
import com.geeksville.mesh.ui.map.components.EditWaypointDialog
import com.geeksville.mesh.ui.map.components.MapStyleButton
import com.geeksville.mesh.util.SqlTileWriterExt
import com.geeksville.mesh.util.requiredZoomLevel
import com.geeksville.mesh.util.formatAgo
import com.geeksville.mesh.waypoint
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
    var cacheEstimate by remember { mutableStateOf("") }
    var cache: SqlTileWriterExt?

    // constants
    val defaultMinZoom = 1.5
    val defaultMaxZoom = 18.0
    val defaultZoomSpeed = 3000L
    val prefsName = "org.geeksville.osm.prefs"
    val mapStyleId = "map_style_id"
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
    var mapStyleButtonVisibility by remember { mutableStateOf(false) }
    var cacheLayoutVisibility by remember { mutableStateOf(false) }
    var showEditWaypointDialog by remember { mutableStateOf<Waypoint?>(null) }
    var showCurrentCacheInfo by remember { mutableStateOf(false) }
    var showDownloadRegionBoundingBox by remember { mutableStateOf(false) } // FIXME

    fun onNodesChanged(nodes: Collection<NodeInfo>): List<MarkerWithLabel> {
        val nodesWithPosition = nodes.filter { it.validPosition != null }
        val ic = ContextCompat.getDrawable(context, R.drawable.ic_baseline_location_on_24)
        val ourNode = model.ourNodeInfo.value
        debug("Showing on map: ${nodesWithPosition.size} nodes")
        return nodesWithPosition.map { node ->
            val (p, u) = Pair(node.position!!, node.user!!)
            MarkerWithLabel(map, "${u.longName} ${formatAgo(p.time)}").apply {
                title = "${u.longName} ${node.batteryStr}"
                snippet = model.gpsString(p)
                ourNode?.distanceStr(node)?.let { dist ->
                    val string = context.getString(R.string.map_subDescription)
                    subDescription = string.format(ourNode.bearing(node), dist)
                }
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                position = GeoPoint(p.latitude, p.longitude)
                icon = ic
            }
        }
    }

    val nodes by model.nodeDB.nodes.observeAsState()
    val nodeMarkers = remember(nodes) {
        mutableStateListOf<MarkerWithLabel>().apply {
            nodes?.values?.let { addAll(onNodesChanged(it)) }
        }
    }

    fun showDeleteMarkerDialog(waypoint: Waypoint) {
        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle(R.string.waypoint_delete)
        builder.setNeutralButton(R.string.cancel) { _, _ ->
            debug("User canceled marker delete dialog")
        }
        builder.setNegativeButton(R.string.delete_for_me) { _, _ ->
            debug("User deleted waypoint ${waypoint.id} for me")
            model.deleteWaypoint(waypoint.id)
        }
        if (waypoint.lockedTo in setOf(0, model.myNodeNum ?: 0) && model.isConnected())
            builder.setPositiveButton(R.string.delete_for_everyone) { _, _ ->
                debug("User deleted waypoint ${waypoint.id} for everyone")
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

    fun showMarkerLongPressDialog(id: Int) {
        debug("marker long pressed id=${id}")
        val waypoint = model.waypoints.value?.get(id)?.data?.waypoint ?: return
        // edit only when unlocked or lockedTo myNodeNum
        if (waypoint.lockedTo in setOf(0, model.myNodeNum ?: 0) && model.isConnected())
            showEditWaypointDialog = waypoint
        else
            showDeleteMarkerDialog(waypoint)
    }

    fun getUsername(id: String?) = if (id == DataPacket.ID_LOCAL) context.getString(R.string.you)
    else model.nodeDB.nodes.value?.get(id)?.user?.longName
        ?: context.getString(R.string.unknown_username)

    fun onWaypointChanged(waypoints: Collection<Packet>): List<MarkerWithLabel> {
        debug("Showing on map: ${waypoints.size} waypoints")
        return waypoints.mapNotNull { waypoint ->
            val pt = waypoint.data.waypoint ?: return@mapNotNull null
            val lock = if (pt.lockedTo != 0) "\uD83D\uDD12" else ""
            val time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(waypoint.received_time)
            val label = pt.name + " " + formatAgo((waypoint.received_time / 1000).toInt())
            val emoji = String(Character.toChars(if (pt.icon == 0) 128205 else pt.icon))
            MarkerWithLabel(map, label, emoji).apply {
                id = "${pt.id}"
                title = "${pt.name} (${getUsername(waypoint.data.from)}$lock)"
                snippet = "[$time] " + pt.description
                position = GeoPoint(pt.latitudeI * 1e-7, pt.longitudeI * 1e-7)
                setVisible(false)
                setOnLongClickListener {
                    showMarkerLongPressDialog(pt.id)
                    true
                }
            }
        }
    }

    val waypoints by model.waypoints.observeAsState()
    val waypointMarkers = remember(waypoints) {
        mutableStateListOf<MarkerWithLabel>().apply {
            waypoints?.values?.let { addAll(onWaypointChanged(it)) }
        }
    }

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
        if (!showCurrentCacheInfo) return@LaunchedEffect
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
    fun downloadJobAlert() {
        //prompt for input params .
        canDownload = false
        mapStyleButtonVisibility = false
        cacheLayoutVisibility = false
        val builder = MaterialAlertDialogBuilder(context)
//        box5miles.setOnClickListener { generateBoxOverlay(zoomLevelLowest) }
//        box10miles.setOnClickListener { generateBoxOverlay(zoomLevelMiddle) }
//        box15miles.setOnClickListener { generateBoxOverlay(zoomLevelHighest) }
//        cacheEstimate = binding.cacheEstimate
//        generateBoxOverlay(zoomLevelLowest)
//        binding.executeJob.setOnClickListener { updateEstimate() }
//        binding.cancelDownload.setOnClickListener {
//            cacheEstimate.text = ""
//            defaultMapSettings()
//
//        }
        builder.setCancelable(true)
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

    fun downloadRegion(
        cacheManager: CacheManager,
        writer: SqliteArchiveTileWriter,
        bb: BoundingBox,
        zoomMin: Int,
        zoomMax: Int
    ) {
        cacheManager.downloadAreaAsync(
            context,
            bb,
            zoomMin,
            zoomMax,
            object : CacheManager.CacheManagerCallback {
                override fun onTaskComplete() {
                    Toast.makeText(
                        context,
                        R.string.map_download_complete,
                        Toast.LENGTH_LONG
                    )
                        .show()
                    writer.onDetach()
                    //defaultMapSettings()
                }

                override fun onTaskFailed(errors: Int) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.map_download_errors).format(errors),
                        Toast.LENGTH_LONG
                    ).show()
                    writer.onDetach()
                    // defaultMapSettings()
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

    fun drawOverlays() = map.apply {
        overlayManager.overlays().clear()
        addCopyright()  // Copyright is required for certain map sources
        createLatLongGrid(false)
        overlayManager.addAll(nodeLayer, nodeMarkers)
        overlayManager.addAll(nodeLayer, waypointMarkers)
        overlayManager.add(nodeLayer, MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                InfoWindow.closeAllInfoWindowsOn(map)
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean {
                performHapticFeedback()
                if (!model.isConnected()) return true

                showEditWaypointDialog = waypoint {
                    latitudeI = (p.latitude * 1e7).toInt()
                    longitudeI = (p.longitude * 1e7).toInt()
                }
                return true
            }
        }))
        invalidate()
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
        if (nodeMarkers.isNotEmpty()) {
            val points = nodeMarkers.map { it.position }
            val box = BoundingBox.fromGeoPoints(points)
            val center = GeoPoint(box.centerLatitude, box.centerLongitude)
            val maximumZoomLevel = map.tileProvider.tileSource.maximumZoomLevel.toDouble()
            val finalZoomLevel = minOf(box.requiredZoomLevel() * 0.8, maximumZoomLevel)
            controller.animateTo(center, finalZoomLevel, defaultZoomSpeed)
        } else map.controller.zoomIn()
    }

    fun loadOnlineTileSourceBase(): ITileSource {
        val id = mPrefs.getInt(mapStyleId, 1)
        debug("mapStyleId from prefs: $id")
        return CustomTileSource.mTileSources.getOrNull(id) ?: CustomTileSource.DEFAULT_TILE_SOURCE
    }

    /**
     * Creates Box overlay showing what area can be downloaded
     */
    fun generateBoxOverlay(zoomLevel: Double) = map.apply {
        overlayManager = CustomOverlayManager.disableDoubleTap(map, context) // disable double tap
        setMultiTouchControls(false)
        // furthest back
        zoomLevelMax = zoomLevel
        // furthest in min should be > than max
        zoomLevelMin = map.tileProvider.tileSource.maximumZoomLevel.toDouble()
        controller.setZoom(zoomLevel)
        downloadRegionBoundingBox = map.boundingBox
        val polygon = Polygon().apply {
            points = Polygon.pointsAsRect(downloadRegionBoundingBox).map {
                GeoPoint(it.latitude, it.longitude)
            }
        }
        overlayManager.add(polygon)
        controller.setZoom(zoomLevel - 1.0)
        val tileCount: Int = CacheManager(map).possibleTilesInArea(
            downloadRegionBoundingBox,
            zoomLevelMax.toInt(),
            zoomLevelMin.toInt()
        )
        cacheEstimate = context.getString(R.string.map_cache_tiles).format(tileCount)
    }

    /**
     * Reset map to default settings & visible buttons
     */
    fun defaultMapSettings() = map.apply {
        setTileSource(loadOnlineTileSourceBase())
        setDestroyMode(false) // keeps map instance alive when in the background.
        isVerticalMapRepetitionEnabled = false // disables map repetition
        overlayManager = CustomOverlayManager.default(map, context)
        setScrollableAreaLimitLatitude( // bounds scrollable map
            overlayManager.tilesOverlay.bounds.actualNorth,
            overlayManager.tilesOverlay.bounds.actualSouth,
            0
        )
        isTilesScaledToDpi = true // scales the map tiles to the display density of the screen
        minZoomLevel = defaultMinZoom // sets the minimum zoom level (the furthest out you can zoom)
        maxZoomLevel = defaultMaxZoom
        setMultiTouchControls(true) // Sets gesture controls to true.
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER) // Disables default +/- button for zooming
        addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent): Boolean {
                if (showDownloadRegionBoundingBox) { // TODO double check if this boolean works here
                    generateBoxOverlay(zoomLevelMax)
                }
                return true
            }

            override fun onZoom(event: ZoomEvent): Boolean {
                return false
            }
        })
        canDownload =
            (tileProvider.tileSource as OnlineTileSourceBase).tileSourcePolicy.acceptsBulkDownload()
    }

    /**
     * if true, start the job
     * if false, just update the dialog box
     */
    fun updateEstimate() {
        if (showDownloadRegionBoundingBox) try {
            val outputName =
                Configuration.getInstance().osmdroidBasePath.absolutePath + File.separator + "mainFile.sqlite" // TODO: Accept filename input param from user
            writer = SqliteArchiveTileWriter(outputName)
            downloadRegionBoundingBox = map.boundingBox // FIXME
            //nesw
            try {
                val cacheManager = CacheManager(
                    map,
                    writer
                ) // Make sure cacheManager has latest from map
                //this triggers the download
                downloadRegion(
                    cacheManager,
                    writer,
                    downloadRegionBoundingBox,
                    zoomLevelMax.toInt(),
                    zoomLevelMin.toInt(),
                )
            } catch (ex: TileSourcePolicyException) {
                debug("Tile source does not allow archiving: ${ex.message}")
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    fun showMapStyleDialog() {
        val builder = MaterialAlertDialogBuilder(context)
        val mapStyles = arrayOf<CharSequence>(
            "OpenStreetMap",
            "USGS TOPO",
            "Open TOPO",
            "ESRI World TOPO",
            "USGS Satellite",
            "ESRI World Overview",
        )

        val mapStyleInt = mPrefs.getInt(mapStyleId, 1)
        builder.setSingleChoiceItems(mapStyles, mapStyleInt) { dialog, which ->
            debug("Set mapStyleId pref to $which")
            mPrefs.edit().putInt(mapStyleId, which).apply()
            dialog.dismiss()
            map.setTileSource(loadOnlineTileSourceBase())
            canDownload = (map.tileProvider.tileSource as OnlineTileSourceBase)
                .tileSourcePolicy.acceptsBulkDownload()
        }
        val dialog = builder.create()
        dialog.show()
    }

    Scaffold(
        floatingActionButton = {
            DownloadButton(canDownload) { showCacheManagerDialog() }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            AndroidView(
                factory = {
                    map.apply {
                        // Required to get online tiles
                        Configuration.getInstance().userAgentValue = BuildConfig.APPLICATION_ID
                        defaultMapSettings()
                        zoomToNodes(controller)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { if (!showDownloadRegionBoundingBox) drawOverlays() },
            )
            if (showDownloadRegionBoundingBox) CacheLayout(
                cacheEstimate = cacheEstimate,
                onExecuteJob = {
                    updateEstimate()
                },
                onCancelDownload = {
                    cacheEstimate = ""
                    showDownloadRegionBoundingBox = false
                    defaultMapSettings()
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) else MapStyleButton(
                onClick = { showMapStyleDialog() },
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
    if (showEditWaypointDialog != null) {
        EditWaypointDialog(
            waypoint = showEditWaypointDialog ?: return,
            onSendClicked = { waypoint ->
                debug("User clicked send waypoint ${waypoint.id}")
                showEditWaypointDialog = null
                model.sendWaypoint(waypoint.copy {
                    if (id == 0) id = model.generatePacketId() ?: return@EditWaypointDialog // TODO check if needed
                    expire = Int.MAX_VALUE // TODO add expire picker
                    lockedTo = if (waypoint.lockedTo != 0) model.myNodeNum ?: 0 else 0
                })

            },
            onDeleteClicked = { waypoint ->
                debug("User clicked delete waypoint ${waypoint.id}")
                showEditWaypointDialog = null
                showDeleteMarkerDialog(waypoint)
            },
            onDismissRequest = {
                debug("User clicked cancel marker edit dialog")
                showEditWaypointDialog = null
            },
        )
    }
}
