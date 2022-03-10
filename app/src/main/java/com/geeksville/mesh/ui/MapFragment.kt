package com.geeksville.mesh.ui

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import com.geeksville.android.GeeksvilleApplication
import com.geeksville.android.Logging
import com.geeksville.mesh.NodeInfo
import com.geeksville.mesh.R
import com.geeksville.mesh.databinding.MapNotAllowedBinding
import com.geeksville.mesh.databinding.MapViewBinding
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.util.formatAgo
import com.mapbox.bindgen.Value
import com.mapbox.common.*
import com.mapbox.geojson.*
import com.mapbox.maps.*
import com.mapbox.maps.dsl.cameraOptions
import com.mapbox.maps.extension.style.expressions.generated.Expression
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.addPersistentLayer
import com.mapbox.maps.extension.style.layers.generated.LineLayer
import com.mapbox.maps.extension.style.layers.generated.SymbolLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.properties.generated.*
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.flyTo
import com.mapbox.maps.plugin.gestures.OnMapLongClickListener
import com.mapbox.maps.plugin.gestures.gestures
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.cos
import kotlin.math.sin


@AndroidEntryPoint
class MapFragment : ScreenFragment("Map"), Logging {


    private val tileStore: TileStore by lazy {
        TileStore.create().also {
            // Set default access token for the created tile store instance
            it.setOption(
                TileStoreOptions.MAPBOX_ACCESS_TOKEN,
                TileDataDomain.MAPS,
                Value(getString(R.string.mapbox_access_token))
            )
        }
    }

    //TODO: Update download animation

    private val resourceOptions: ResourceOptions by lazy {
        ResourceOptions.Builder().applyDefaultParams(requireContext()).tileStore(tileStore).build()
    }
    private val offlineManager: OfflineManager by lazy {
        OfflineManager(resourceOptions)
    }

    private lateinit var binding: MapViewBinding
    private lateinit var mapNotAllowedBinding: MapNotAllowedBinding
    private var userStyleURI: String? = null

    private lateinit var geoJsonSource: GeoJsonSource
    private lateinit var lineLayer: LineLayer

    private var point: Point? = null

    private val model: UIViewModel by activityViewModels()

    private val nodeSourceId = "node-positions"
    private val nodeLayerId = "node-layer"
    private val labelLayerId = "label-layer"
    private val markerImageId = "my-marker-image"
    private val userPointImageId = "user-image"
    private val boundingBoxId = "bounding-box-id"
    private val lineLayerId = "line-layer-id"

    private var stylePackCancelable: Cancelable? = null
    private var tilePackCancelable: Cancelable? = null

    private lateinit var squareRegion: Geometry

    private val userTouchPositionId = "user-touch-position"
    private val userTouchLayerId = "user-touch-layer"
    private var nodePositions = GeoJsonSource(GeoJsonSource.Builder(nodeSourceId))

    private var tileRegionDownloadSuccess = false
    private var stylePackDownloadSuccess = false
    private val userTouchPosition = GeoJsonSource(GeoJsonSource.Builder(userTouchPositionId))


    private val nodeLayer = SymbolLayer(nodeLayerId, nodeSourceId)
        .iconImage(markerImageId)
        .iconAnchor(IconAnchor.BOTTOM)
        .iconAllowOverlap(true)

    private val userTouchLayer = SymbolLayer(userTouchLayerId, userTouchPositionId)
        .iconImage(userPointImageId)
        .iconAnchor(IconAnchor.BOTTOM)

    private val labelLayer = SymbolLayer(labelLayerId, nodeSourceId)
        .textField(Expression.get("name"))
        .textSize(12.0)
        .textColor(Color.RED)
        .textAnchor(TextAnchor.TOP)
        //.textVariableAnchor(TextAnchor.TOP) //TODO investigate need for variable anchor vs normal anchor
        .textJustify(TextJustify.AUTO)
        .textAllowOverlap(true)


    private fun onNodesChanged(nodes: Collection<NodeInfo>) {
        val nodesWithPosition = nodes.filter { it.validPosition != null }

        /**
         * Using the latest nodedb, generate geojson features
         */
        fun getCurrentNodes(): FeatureCollection {
            // Find all nodes with valid locations

            val locations = nodesWithPosition.map { node ->
                val p = node.position!!
                debug("Showing on map: $node")

                val f = Feature.fromGeometry(
                    Point.fromLngLat(
                        p.longitude,
                        p.latitude
                    )
                )
                node.user?.let {
                    f.addStringProperty("name", it.longName + " " + formatAgo(p.time))
                }
                f
            }

            return FeatureCollection.fromFeatures(locations)
        }
        nodePositions.featureCollection(getCurrentNodes())
    }

    private fun zoomToNodes(map: MapboxMap) {
        val points: MutableList<Point> = mutableListOf()
        val nodesWithPosition =
            model.nodeDB.nodes.value?.values?.filter { it.validPosition != null }
        if (nodesWithPosition != null && nodesWithPosition.isNotEmpty()) {
            val unit = if (nodesWithPosition.size >= 2) {

                // Multiple nodes, make them all fit on the map view
                nodesWithPosition.forEach {
                    points.add(
                        Point.fromLngLat(
                            it.position!!.longitude,
                            it.position!!.latitude
                        )
                    )
                }
                map.cameraForCoordinates(points)
            } else {
                // Only one node, just zoom in on it
                val it = nodesWithPosition[0].position!!
                points.add(Point.fromLngLat(it.longitude, it.latitude))
                map.cameraForCoordinates(points)
                cameraOptions {
                    this.zoom(9.0)
                    this.center(points[0])
                }
            }
            map.flyTo(
                unit,
                MapAnimationOptions.mapAnimationOptions { duration(1000) })
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // We can't allow mapbox if user doesn't want analytics
        return if ((requireContext().applicationContext as GeeksvilleApplication).isAnalyticsAllowed) {
            // Mapbox Access token
            binding = MapViewBinding.inflate(inflater, container, false)
            binding.root
        } else {
            mapNotAllowedBinding = MapNotAllowedBinding.inflate(inflater, container, false)
            mapNotAllowedBinding.root
        }
    }

    var mapView: MapView? = null


    private fun removeOfflineRegions() {
        // Remove the tile region with the tile region ID.
        // Note this will not remove the downloaded tile packs, instead, it will just mark the tileset
        // not a part of a tile region. The tiles still exists as a predictive cache in TileStore.
        tileStore.removeTileRegion(TILE_REGION_ID)

        // Set the disk quota to zero, so that tile regions are fully evicted
        // when removed. The TileStore is also used when `ResourceOptions.isLoadTilePacksFromNetwork`
        // is `true`, and also by the Navigation SDK.
        // This removes the tiles that do not belong to any tile regions.
        tileStore.setOption(TileStoreOptions.DISK_QUOTA, Value(0))

        // Remove the style pack with the style url.
        // Note this will not remove the downloaded style pack, instead, it will just mark the resources
        // not a part of the existing style pack. The resources still exists as disk cache.

        if (userStyleURI != null) {
            offlineManager.removeStylePack(userStyleURI!!)
        } else {
            offlineManager.removeStylePack(mapView?.getMapboxMap()?.getStyle()?.styleURI.toString())
        }
        MapboxMap.clearData(resourceOptions) {
            it.error?.let { error ->
                debug(error)
            }
        }
    }

    /**
     * Mapbox native code can crash painfully if you ever call a mapbox view function while the view is not actively being show
     */
    private val isViewVisible: Boolean
        get() = mapView?.isVisible == true

    override fun onViewCreated(viewIn: View, savedInstanceState: Bundle?) {
        super.onViewCreated(viewIn, savedInstanceState)

//        binding.fabStyleToggle.setOnClickListener {
//
//            //TODO: Setup Style menu for satellite view, street view, & outdoor view
//        }
        binding.downloadRegion.setOnClickListener {
            // Display menu for download region
            this.downloadRegionDialogFragment()
        }
        // We might not have a real mapview if running with analytics
        if ((requireContext().applicationContext as GeeksvilleApplication).isAnalyticsAllowed) {
            val vIn = viewIn.findViewById<MapView>(R.id.mapView)
            mapView = vIn
            mapView?.let { v ->

                // Each time the pane is shown start fetching new map info (we do this here instead of
                // onCreate because getMapAsync can die in native code if the view goes away)

                val map = v.getMapboxMap()
                if (view != null) { // it might have gone away by now
                    val markerIcon =
                        ContextCompat.getDrawable(
                            requireActivity(),
                            R.drawable.ic_twotone_person_pin_24
                        )!!.toBitmap()

                    map.loadStyleUri(Style.OUTDOORS) {
                        if (it.isStyleLoaded) {
                            it.addSource(nodePositions)
                            it.addImage(markerImageId, markerIcon)
                            it.addPersistentLayer(nodeLayer)
                            it.addPersistentLayer(labelLayer)
                        }
                    }

                    v.gestures.rotateEnabled = false
                    v.gestures.addOnMapLongClickListener(this.longClick)

                    // Provide initial positions
                    model.nodeDB.nodes.value?.let { nodes ->
                        onNodesChanged(nodes.values)
                    }
                }

                // Any times nodes change update our map
                model.nodeDB.nodes.observe(viewLifecycleOwner, Observer { nodes ->
                    if (isViewVisible)
                        onNodesChanged(nodes.values)
                })
                //viewAnnotationManager = v.viewAnnotationManager
                zoomToNodes(map)
            }
        }
    }

    private fun downloadOfflineRegion(styleURI: String = "") {

        val style = styleURI.ifEmpty {
            mapView?.getMapboxMap()
                ?.getStyle()?.styleURI.toString()
        }

        if (OfflineSwitch.getInstance().isMapboxStackConnected) {
            // By default, users may download up to 250MB of data for offline use without incurring
            // additional charges. This limit is subject to change during the beta.

            // - - - - - - - -

            // 1. Create style package with loadStylePack() call.

            // A style pack (a Style offline package) contains the loaded style and its resources: loaded
            // sources, fonts, sprites. Style packs are identified with their style URI.

            // Style packs are stored in the disk cache database, but their resources are not subject to
            // the data eviction algorithm and are not considered when calculating the disk cache size.

            stylePackCancelable = offlineManager.loadStylePack(
                style,
                // Build Style pack load options
                StylePackLoadOptions.Builder()
                    .glyphsRasterizationMode(GlyphsRasterizationMode.IDEOGRAPHS_RASTERIZED_LOCALLY)
                    .metadata(Value(STYLE_PACK_METADATA))
                    .build(),
                { progress ->
                    //TODO: Update the download progress to UI
                    updateStylePackDownloadProgress(
                        progress.completedResourceCount,
                        progress.requiredResourceCount
                    )
                },
                { expected ->
                    if (expected.isValue) {
                        expected.value?.let { stylePack ->
                            // Style pack download finishes successfully
                            debug("StylePack downloaded: $stylePack")
                            if (binding.stylePackDownloadProgress.progress == binding.stylePackDownloadProgress.max) {
                                debug("Doing stuff")
                                binding.stylePackDownloadProgress.visibility = View.INVISIBLE
                                stylePackDownloadSuccess = true
                            } else {
                                debug("Waiting for tile region download to be finished.")
                            }
                        }
                    }
                    expected.error?.let {
                        Toast.makeText(
                            requireContext(),
                            R.string.stylepack_download_error_alert,
                            Toast.LENGTH_SHORT
                        ).show()
                        // Handle error occurred during the style pack download.
                        debug("StylePackError: $it")
                    }
                }
            )

            // - - - - - - - -

            // 2. Create a tile region with tiles for the outdoors style

            // A Tile Region represents an identifiable geographic tile region with metadata, consisting of
            // a set of tiles packs that cover a given area (a polygon). Tile Regions allow caching tiles
            // packs in an explicit way: By creating a Tile Region, developers can ensure that all tiles in
            // that region will be downloaded and remain cached until explicitly deleted.

            // Creating a Tile Region requires supplying a description of the area geometry, the tilesets
            // and zoom ranges of the tiles within the region.

            // The tileset descriptor encapsulates the tile-specific data, such as which tilesets, zoom ranges,
            // pixel ratio etc. the cached tile packs should have. It is passed to the Tile Store along with
            // the region area geometry to load a new Tile Region.

            // The OfflineManager is responsible for creating tileset descriptors for the given style and zoom range.

            val tilesetDescriptor = offlineManager.createTilesetDescriptor(
                TilesetDescriptorOptions.Builder()
                    .styleURI(style)
                    .minZoom(0)
                    .maxZoom(10)
                    .build()
            )
            // Use the the default TileStore to load this region. You can create custom TileStores are are
            // unique for a particular file path, i.e. there is only ever one TileStore per unique path.

            // Note that the TileStore path must be the same with the TileStore used when initialise the MapView.
            tilePackCancelable = tileStore.loadTileRegion(
                TILE_REGION_ID, // Make this dynamic
                TileRegionLoadOptions.Builder()
                    .geometry(squareRegion)
                    .descriptors(listOf(tilesetDescriptor))
                    .metadata(Value(TILE_REGION_METADATA))
                    .acceptExpired(true)
                    .networkRestriction(NetworkRestriction.NONE)
                    .build(),
                { progress ->
                    updateTileRegionDownloadProgress(
                        progress.completedResourceCount,
                        progress.requiredResourceCount,
                    )
                }
            ) { expected ->
                if (expected.isValue) {
                    // Tile pack download finishes successfully
                    expected.value?.let { region ->
                        debug("TileRegion downloaded: $region")
                        if (binding.stylePackDownloadProgress.progress == binding.stylePackDownloadProgress.max) {
                            debug("Finished tilepack download")
                            binding.stylePackDownloadProgress.visibility = View.INVISIBLE
                            tileRegionDownloadSuccess = true
                        } else {
                            debug("Waiting for style pack download to be finished.")
                        }
                    }
                }
                expected.error?.let {
                    Toast.makeText(
                        requireContext(),
                        R.string.tileregion_error_alert,
                        Toast.LENGTH_SHORT
                    ).show()
                    // Handle error occurred during the tile region download.
                    debug("TileRegionError: $it")
                }
            }
        } else {
            Toast.makeText(
                requireContext(),
                R.string.download_region_connection_alert,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * OnLongClick of the map set a position marker.
     * If a user long-clicks again, the position of the first marker will be updated
     */
    private val longClick = OnMapLongClickListener {
        val userDefinedPointImg =
            ContextCompat.getDrawable(
                requireActivity(),
                R.drawable.baseline_location_on_white_24dp
            )!!
                .toBitmap()
        point = Point.fromLngLat(it.longitude(), it.latitude())


        /*
        Calculate region from user specified position.
        5 miles NE,NW,SE,SW from user center point.
        25 Sq Mile Region
        */
        //____________________________________________________________________________________________
        val topRight = calculateCoordinate(45.0, point?.latitude()!!, point?.longitude()!!)
        val topLeft = calculateCoordinate(135.0, point?.latitude()!!, point?.longitude()!!)
        val bottomLeft = calculateCoordinate(225.0, point?.latitude()!!, point?.longitude()!!)
        val bottomRight = calculateCoordinate(315.0, point?.latitude()!!, point?.longitude()!!)
        //____________________________________________________________________________________________

        val pointList = listOf(topRight, topLeft, bottomLeft, bottomRight, topRight)

        squareRegion = LineString.fromLngLats(pointList)

        geoJsonSource = geoJsonSource(boundingBoxId) {
            geometry(squareRegion)
        }
        lineLayer = lineLayer(lineLayerId, boundingBoxId) {
            lineCap(LineCap.ROUND)
            lineJoin(LineJoin.MITER)
            lineOpacity(0.7)
            lineWidth(1.5)
            lineColor("#888")
        }

        if (point != null) {
            binding.downloadRegion.visibility = View.VISIBLE
        }

        mapView?.getMapboxMap()?.getStyle()?.let { style ->
            userTouchPosition.geometry(point!!)
            if (!style.styleLayerExists(userTouchLayerId)) {
                style.addImage(userPointImageId, userDefinedPointImg)
                style.addSource(userTouchPosition)
                style.addSource(geoJsonSource)
                style.addPersistentLayer(lineLayer)
                style.addLayer(userTouchLayer)
            } else {
                style.removeStyleLayer(lineLayerId)
                style.removeStyleSource(boundingBoxId)
                style.addSource(geoJsonSource)
                style.addLayer(lineLayer)
            }
        }
        mapView?.getMapboxMap().also { mapboxMap ->
            mapboxMap?.flyTo(
                CameraOptions.Builder()
                    .zoom(ZOOM)
                    .center(point)
                    .build(), MapAnimationOptions.mapAnimationOptions { duration(1000) })
        }
        return@OnMapLongClickListener true
    }

    /**
     * Find's coordinates (Lat,Lon) a specified distance from given (lat,lon) using degrees to determine direction
     * @param degrees degree of desired position from current position. (center point is 0,0 and desired point, top right corner, is 45 degrees from 0,0)
     * @param lat latitude position (current position lat)
     * @param lon longitude position (current position lon)
     * @return Point
     */
    private fun calculateCoordinate(degrees: Double, lat: Double, lon: Double): Point {
        val deg = Math.toRadians(degrees)
        val distancesInMeters =
            1609.344 * 5 // 1609.344 is 1 mile in meters -> multiplier will be user specified up to a max of 10
        val radiusOfEarthInMeters = 6378137
        val x =
            lon + (180 / Math.PI) * (distancesInMeters / radiusOfEarthInMeters) * cos(
                deg
            )
        val y =
            lat + (180 / Math.PI) * (distancesInMeters / radiusOfEarthInMeters) * sin(deg)
        return Point.fromLngLat(x, y)
    }

    private fun updateStylePackDownloadProgress(
        progress: Long,
        max: Long,
    ) {
        binding.stylePackDownloadProgress.visibility = View.VISIBLE
        binding.stylePackDownloadProgress.max = max.toInt()
        binding.stylePackDownloadProgress.progress = progress.toInt()
    }

    private fun updateTileRegionDownloadProgress(
        progress: Long,
        max: Long,
    ) {
        binding.stylePackDownloadProgress.max = max.toInt()
        binding.stylePackDownloadProgress.progress = progress.toInt()
    }

    companion object {
        private const val ZOOM = 11.0
        private const val TILE_REGION_ID = "myTileRegion"
        private const val STYLE_PACK_METADATA = "my-outdoor-style-pack"
        private const val TILE_REGION_METADATA = "my-outdoors-tile-region"
    }

    private fun downloadRegionDialogFragment() {
        val mapDownloadView = layoutInflater.inflate(R.layout.dialog_map_download, null)
        val uri = mapDownloadView.findViewById<EditText>(R.id.uri)
        val downloadRegionDialogFragment = AlertDialog.Builder(context)


        downloadRegionDialogFragment.setView(mapDownloadView)
            .setTitle(R.string.download_region_dialog_title)
            .setMultiChoiceItems(
                R.array.MapMenuCheckbox,
                null,
            ) { _, _, isChecked ->
                if (isChecked) {
                    if (!uri.isVisible) {
                        uri.visibility =
                            View.VISIBLE
                    }
                } else {
                    if (uri.isVisible) {
                        uri.visibility =
                            View.GONE
                    }
                }
            }
            .setPositiveButton(
                R.string.save_btn, null
            )
            .setNeutralButton(R.string.view_region_btn) { _, _ ->
                mapView?.getMapboxMap().also {
                    it?.flyTo(
                        CameraOptions.Builder()
                            .zoom(ZOOM)
                            .center(point)
                            .build(),
                        MapAnimationOptions.mapAnimationOptions { duration(1000) })
                    if (userStyleURI != null) {
                        it?.loadStyleUri(userStyleURI.toString())
                    } else {
                        it?.getStyle().also { style ->
                            style?.removeStyleImage(userPointImageId)
                        }
                    }
                }
//                } else {
//                    Toast.makeText(
//                        requireContext(),
//                        R.string.no_download_region_alert,
//                        Toast.LENGTH_SHORT
//                    ).show()
//                }
            }
            .setNegativeButton(
                R.string.cancel
            ) { dialog, _ ->
                mapView?.getMapboxMap()?.getStyle { style ->
                    point = null
                    userStyleURI = null
                    style.removeStyleLayer(lineLayerId)
                    style.removeStyleSource(boundingBoxId)
                    style.removeStyleLayer(userTouchLayerId)
                    style.removeStyleSource(userTouchPositionId)
                    style.removeStyleImage(userPointImageId)
                }
                binding.downloadRegion.visibility = View.INVISIBLE

                removeOfflineRegions() //TODO: Add to offline manager window
                dialog.cancel()
            }

        val dialog = downloadRegionDialogFragment.create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (uri.isVisible) {
                if (uri.text.isNotEmpty()) {
                    // Save URI
                    userStyleURI = uri.text.toString()
                    uri.setText("") // clear text

                    downloadOfflineRegion(userStyleURI!!)
                    dialog.dismiss()
                } else {
                    Toast.makeText(
                        requireContext(),
                        R.string.style_uri_empty_alert,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                downloadOfflineRegion()
                dialog.dismiss()
            }
        }
    }
}



