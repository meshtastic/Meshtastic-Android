package com.geeksville.mesh.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import com.geeksville.android.GeeksvilleApplication
import com.geeksville.android.Logging
import com.geeksville.mesh.NodeInfo
import com.geeksville.mesh.R
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.util.formatAgo
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.geometry.LatLngBounds
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.style.expressions.Expression
import com.mapbox.mapboxsdk.style.layers.Property
import com.mapbox.mapboxsdk.style.layers.Property.TEXT_ANCHOR_TOP
import com.mapbox.mapboxsdk.style.layers.Property.TEXT_JUSTIFY_AUTO
import com.mapbox.mapboxsdk.style.layers.PropertyFactory.*
import com.mapbox.mapboxsdk.style.layers.SymbolLayer
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource


class MapFragment : ScreenFragment("Map"), Logging {

    private val model: UIViewModel by activityViewModels()

    private val nodeSourceId = "node-positions"
    private val nodeLayerId = "node-layer"
    private val labelLayerId = "label-layer"
    private val markerImageId = "my-marker-image"

    private val nodePositions = GeoJsonSource(nodeSourceId)

    private val nodeLayer = SymbolLayer(nodeLayerId, nodeSourceId).withProperties(
        iconImage(markerImageId),
        iconAnchor(Property.ICON_ANCHOR_BOTTOM),
        iconAllowOverlap(true)
    )

    private val labelLayer = SymbolLayer(labelLayerId, nodeSourceId).withProperties(
        textField(Expression.get("name")),
        textSize(12f),
        textColor(Color.RED),
        textVariableAnchor(arrayOf(TEXT_ANCHOR_TOP)),
        textJustify(TEXT_JUSTIFY_AUTO),
        textAllowOverlap(true)
    )


    private fun onNodesChanged(map: MapboxMap, nodes: Collection<NodeInfo>) {
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



        nodePositions.setGeoJson(getCurrentNodes()) // Update node positions
    }

    fun zoomToNodes(map: MapboxMap) {
        val nodesWithPosition =
            model.nodeDB.nodes.value?.values?.filter { it.validPosition != null }
        if (nodesWithPosition != null && nodesWithPosition.isNotEmpty()) {
            val update = if (nodesWithPosition.size >= 2) {
                // Multiple nodes, make them all fit on the map view
                val bounds = LatLngBounds.Builder()

                // Add all positions
                bounds.includes(nodesWithPosition.map { it.position!! }
                    .map { LatLng(it.latitude, it.longitude) })

                CameraUpdateFactory.newLatLngBounds(bounds.build(), 150)
            } else {
                // Only one node, just zoom in on it
                val it = nodesWithPosition[0].position!!

                val cameraPos = CameraPosition.Builder().target(
                    LatLng(it.latitude, it.longitude)
                ).zoom(9.0).build()
                CameraUpdateFactory.newCameraPosition(cameraPos)
            }
            map.animateCamera(update, 1000)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // We can't allow mapbox if user doesn't want analytics
        val id =
            if ((requireContext().applicationContext as GeeksvilleApplication).isAnalyticsAllowed) {
                // Mapbox Access token
                R.layout.map_view
            } else {
                R.layout.map_not_allowed
            }

        return inflater.inflate(id, container, false)
    }

    var mapView: MapView? = null

    /**
     * Mapbox native code can crash painfully if you ever call a mapbox view function while the view is not actively being show
     */
    private val isViewVisible: Boolean
        get() = !(mapView?.isDestroyed ?: true)

    override fun onViewCreated(viewIn: View, savedInstanceState: Bundle?) {
        super.onViewCreated(viewIn, savedInstanceState)

        // We might not have a real mapview if running with analytics
        if ((requireContext().applicationContext as GeeksvilleApplication).isAnalyticsAllowed) {
            val vIn = viewIn.findViewById<MapView>(R.id.mapView)
            mapView = vIn
            mapView?.let { v ->
                v.onCreate(savedInstanceState)

                // Each time the pane is shown start fetching new map info (we do this here instead of
                // onCreate because getMapAsync can die in native code if the view goes away)
                v.getMapAsync { map ->

                    if (view != null) { // it might have gone away by now
                        // val markerIcon = BitmapFactory.decodeResource(context.resources, R.drawable.ic_twotone_person_pin_24)
                        val markerIcon =
                            ContextCompat.getDrawable(
                                requireActivity(),
                                R.drawable.ic_twotone_person_pin_24
                            )!!

                        map.setStyle(Style.OUTDOORS) { style ->
                            style.addSource(nodePositions)
                            style.addImage(markerImageId, markerIcon)
                            style.addLayer(nodeLayer)
                            style.addLayer(labelLayer)
                        }

                        map.uiSettings.isRotateGesturesEnabled = false
                        // Provide initial positions
                        model.nodeDB.nodes.value?.let { nodes ->
                            onNodesChanged(map, nodes.values)
                        }
                    }

                    // Any times nodes change update our map
                    model.nodeDB.nodes.observe(viewLifecycleOwner, Observer { nodes ->
                        if (isViewVisible)
                            onNodesChanged(map, nodes.values)
                    })
                    zoomToNodes(map)
                }
            }
        }
    }

    override fun onPause() {
        mapView?.onPause()
        super.onPause()
    }

    override fun onStart() {
        super.onStart()
        mapView?.onStart()
    }

    override fun onStop() {
        mapView?.onStop()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mapView?.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        mapView?.let {
            if (!it.isDestroyed)
                it.onSaveInstanceState(outState)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        mapView?.let {
            if (!it.isDestroyed)
                it.onLowMemory()
        }
        super.onLowMemory()
    }
}




