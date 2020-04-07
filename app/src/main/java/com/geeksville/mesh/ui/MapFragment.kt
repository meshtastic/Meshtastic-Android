package com.geeksville.mesh.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.geeksville.android.Logging
import com.geeksville.mesh.R
import com.geeksville.mesh.model.NodeDB
import com.geeksville.mesh.model.UIState
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

    private fun nodesWithPosition() = NodeDB.nodes.values.filter { it.validPosition != null }

    /**
     * Using the latest nodedb, generate geojson features
     */
    private fun getCurrentNodes(): FeatureCollection {
        // Find all nodes with valid locations

        val locations = nodesWithPosition().map { node ->
            val p = node.position!!
            debug("Showing on map: $node")

            val f = Feature.fromGeometry(
                Point.fromLngLat(
                    p.longitude,
                    p.latitude
                )
            )
            node.user?.let {
                f.addStringProperty("name", it.longName)
            }
            f
        }

        return FeatureCollection.fromFeatures(locations)
    }

    private fun zoomToNodes(map: MapboxMap) {
        val nodes = nodesWithPosition()
        if (nodes.isNotEmpty()) {
            val update = if (nodes.size >= 2) {
                // Multiple nodes, make them all fit on the map view
                val bounds = LatLngBounds.Builder()

                // Add all positions
                bounds.includes(nodes.map { it.position!! }
                    .map { LatLng(it.latitude, it.longitude) })

                CameraUpdateFactory.newLatLngBounds(bounds.build(), 150)
            } else {
                // Only one node, just zoom in on it
                val it = nodes[0].position!!

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
    ): View? = inflater.inflate(R.layout.map_view, container, false)

    lateinit var mapView: MapView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mapView = view.findViewById(R.id.mapView)
        mapView.onCreate(UIState.savedInstanceState)

        mapView.getMapAsync { map ->

            // val markerIcon = BitmapFactory.decodeResource(context.resources, R.drawable.ic_twotone_person_pin_24)
            val markerIcon = activity!!.getDrawable(R.drawable.ic_twotone_person_pin_24)!!

            map.setStyle(Style.OUTDOORS) { style ->
                style.addSource(nodePositions)
                style.addImage(markerImageId, markerIcon)
                style.addLayer(nodeLayer)
                style.addLayer(labelLayer)
            }

            //map.uiSettings.isScrollGesturesEnabled = true
            //map.uiSettings.isZoomGesturesEnabled = true
        }
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onStop() {
        mapView.onStop()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()

        mapView.getMapAsync { map ->
            nodePositions.setGeoJson(getCurrentNodes()) // Update node positions
            zoomToNodes(map)
        }
    }

    override fun onDestroy() {
        mapView.onDestroy()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        mapView.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }
}




