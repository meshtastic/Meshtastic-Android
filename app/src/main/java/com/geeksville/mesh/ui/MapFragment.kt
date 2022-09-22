package com.geeksville.mesh.ui

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import com.geeksville.mesh.BuildConfig
import com.geeksville.mesh.NodeInfo
import com.geeksville.mesh.R
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.databinding.MapViewBinding
import com.geeksville.mesh.model.CustomTileSource
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.util.formatAgo
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dagger.hilt.android.AndroidEntryPoint
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.*
import kotlin.math.pow


@AndroidEntryPoint
class MapFragment : ScreenFragment("Map"), Logging, View.OnClickListener, OnSeekBarChangeListener,
    TextWatcher {

    private lateinit var binding: MapViewBinding
    private lateinit var map: MapView
    private lateinit var mapController: IMapController
    private lateinit var mPrefs: SharedPreferences
    private val model: UIViewModel by activityViewModels()

    private lateinit var cacheManager: CacheManager
    private lateinit var btnCache: FloatingActionButton

    private lateinit var cacheNorth: EditText
    private lateinit var cacheSouth: EditText
    private lateinit var cacheEast: EditText
    private lateinit var cacheWest: EditText

    private lateinit var cacheEstimate: TextView

    private lateinit var zoomMin: SeekBar
    private lateinit var zoomMax: SeekBar

    private var downloadPrompt: AlertDialog? = null
    private var alertDialog: AlertDialog? = null
    private lateinit var executeJob: Button


    private val defaultMinZoom = 1.5
    private val nodeZoomLevel = 8.5
    private val defaultZoomSpeed = 3000L
    private val prefsName = "org.geeksville.osm.prefs"
    private val mapStyleId = "map_style_id"
    private var nodePositions = listOf<MarkerWithLabel>()
    private val nodeLayer = 1


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = MapViewBinding.inflate(inflater)
        btnCache = binding.root.findViewById(R.id.downloadButton)
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
                // build Collection<Packet> from (it.values)
            }
            zoomToNodes(mapController)
        }
        btnCache.setOnClickListener(this)
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.executeJob -> updateEstimate(true)
            R.id.downloadButton -> showCacheManagerDialog()
        }
    }


    private fun showCacheManagerDialog() {
        val alertDialogBuilder = AlertDialog.Builder(
            activity
        )
        // set title
        alertDialogBuilder.setTitle("Cache Manager")
        //.setMessage(R.string.cache_manager_description);

        // set dialog message
        alertDialogBuilder.setItems(
            arrayOf<CharSequence>(
                "Cache current size",
                "Cache Download",
                resources.getString(R.string.cancel)
            )
        ) { dialog, which ->
            when (which) {
                0 -> showCurrentCacheInfo()
                1 -> {
                    downloadJobAlert()
                    dialog.dismiss()
                }
                else -> dialog.dismiss()
            }
        }


        // create alert dialog
        alertDialog = alertDialogBuilder.create()

        // show it
        alertDialog!!.show()

    }


    private fun showCurrentCacheInfo() {
        Toast.makeText(activity, "Calculating...", Toast.LENGTH_SHORT).show()
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
        //prompt for input params
        val builder = AlertDialog.Builder(activity)
        val view = View.inflate(activity, R.layout.cache_mgr_input, null)
        val boundingBox: BoundingBox = map.boundingBox
        zoomMax = view.findViewById(R.id.slider_zoom_max)
        zoomMax.max = map.maxZoomLevel.toInt()
        zoomMax.setOnSeekBarChangeListener(this)
        zoomMin = view.findViewById(R.id.slider_zoom_min)
        zoomMin.max = map.maxZoomLevel.toInt()
        zoomMin.progress = map.minZoomLevel.toInt()
        zoomMin.setOnSeekBarChangeListener(this)
        cacheEast = view.findViewById(R.id.cache_east)
        cacheEast.setText(boundingBox.lonEast.toString() + "")
        cacheNorth = view.findViewById(R.id.cache_north)
        cacheNorth.setText(boundingBox.latNorth.toString() + "")
        cacheSouth = view.findViewById(R.id.cache_south)
        cacheSouth.setText(boundingBox.latSouth.toString() + "")
        cacheWest = view.findViewById(R.id.cache_west)
        cacheWest.setText(boundingBox.lonWest.toString() + "")
        cacheEstimate = view.findViewById(R.id.cache_estimate)

        //change listeners for both validation and to trigger the download estimation
        cacheEast.addTextChangedListener(this)
        cacheNorth.addTextChangedListener(this)
        cacheSouth.addTextChangedListener(this)
        cacheWest.addTextChangedListener(this)
        executeJob = view.findViewById(R.id.executeJob)
        executeJob.setOnClickListener {
            builder.setOnCancelListener {
                cacheEast.text = null
                cacheSouth.text = null
                cacheEstimate.text = ""
                cacheNorth.text = null
                cacheWest.text = null
                zoomMin.progress = 0
                zoomMax.progress = 0
            }
        }
        builder.setView(view)
        builder.setCancelable(true)
        downloadPrompt = builder.create()
        downloadPrompt!!.show()
    }

    /**
     * if true, start the job
     * if false, just update the dialog box
     */
    private fun updateEstimate(startJob: Boolean) {
        try {
            if (cacheWest.text != null && cacheNorth.text != null && cacheSouth.text != null && zoomMax.progress != 0 && zoomMin.progress != 0) {
                val n: Double = cacheNorth.text.toString().toDouble()
                val s: Double = cacheSouth.text.toString().toDouble()
                val e: Double = cacheEast.text.toString().toDouble()
                val w: Double = cacheWest.text.toString().toDouble()
                val zoommin: Int = zoomMin.progress
                val zoommax: Int = zoomMax.progress
                //nesw
                val bb = BoundingBox(n, e, s, w)
                val tilecount: Int = cacheManager.possibleTilesInArea(bb, zoommin, zoommax)
                cacheEstimate.text = ("$tilecount tiles")
                if (startJob) {
                    if (downloadPrompt != null) {
                        downloadPrompt!!.dismiss()
                        downloadPrompt = null
                    }

                    //this triggers the download
                    cacheManager.downloadAreaAsync(activity,
                        bb,
                        zoommin,
                        zoommax,
                        object : CacheManager.CacheManagerCallback {
                            override fun onTaskComplete() {
                                Toast.makeText(activity, "Download complete!", Toast.LENGTH_LONG)
                                    .show()
                            }

                            override fun onTaskFailed(errors: Int) {
                                Toast.makeText(
                                    activity,
                                    "Download complete with $errors errors",
                                    Toast.LENGTH_LONG
                                ).show()
                            }

                            override fun updateProgress(
                                progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int
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
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
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

    private fun drawOverlays() {
        map.overlayManager.overlays().clear()
        addCopyright()  // Copyright is required for certain map sources
        map.overlayManager.addAll(nodeLayer, nodePositions)
    }

    /**
     * Adds copyright to map depending on what source is showing
     */
    private fun addCopyright() {
        val copyrightNotice: String = map.tileProvider.tileSource.copyrightNotice
        val copyrightOverlay = CopyrightOverlay(context)
        copyrightOverlay.setCopyrightNotice(copyrightNotice)
        map.overlays.add(copyrightOverlay)
    }

    private fun setupMapProperties() {
        if (this::map.isInitialized) {
            cacheManager = CacheManager(map)
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

    override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
        updateEstimate(false)
    }

    override fun onStartTrackingTouch(p0: SeekBar?) {
    }

    override fun onStopTrackingTouch(p0: SeekBar?) {
    }

    override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
    }

    override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
        updateEstimate(false)
    }

    override fun afterTextChanged(p0: Editable?) {
    }
}



