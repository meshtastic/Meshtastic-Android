package com.geeksville.mesh.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.isVisible
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
import com.mapbox.maps.*
import com.mapbox.maps.dsl.cameraOptions
import com.mapbox.maps.extension.style.expressions.generated.Expression
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.SymbolLayer
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.extension.style.layers.properties.generated.TextAnchor
import com.mapbox.maps.extension.style.layers.properties.generated.TextJustify
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.easeTo
import com.mapbox.maps.plugin.animation.flyTo
import com.mapbox.maps.plugin.gestures.gestures


class MapFragment : ScreenFragment("Map"), Logging {

    private val model: UIViewModel by activityViewModels()

    private val nodeSourceId = "node-positions"
    private val nodeLayerId = "node-layer"
    private val labelLayerId = "label-layer"
    private val markerImageId = "my-marker-image"

    private var nodePositions = GeoJsonSource(GeoJsonSource.Builder(nodeSourceId))

    private val nodeLayer = SymbolLayer(nodeLayerId, nodeSourceId)
        .iconImage(markerImageId)
        .iconAnchor(IconAnchor.BOTTOM)
        .iconAllowOverlap(true)

    private val labelLayer = SymbolLayer(labelLayerId, nodeSourceId)
        .textField(Expression.get("name"))
        .textSize(12.0)
        .textColor(Color.RED)
        .textAnchor(TextAnchor.TOP)
        //.textVariableAnchor(TextAnchor.TOP) //TODO investigate need for variable anchor vs normal anchor
        .textJustify(TextJustify.AUTO)
        .textAllowOverlap(true)


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
        nodePositions.featureCollection(getCurrentNodes())
    }

    //TODO Update camera movements
    private fun zoomToNodes(map: MapboxMap) {
        val points: MutableList<Point> = mutableListOf()
        val nodesWithPosition =
            model.nodeDB.nodes.value?.values?.filter { it.validPosition != null }
        if (nodesWithPosition != null && nodesWithPosition.isNotEmpty()) {
            val unit = if (nodesWithPosition.size >= 2) {

                // Multiple nodes, make them all fit on the map view
                for (node in nodesWithPosition) {
                    val point =
                        Point.fromLngLat(node.position!!.longitude, node.position!!.latitude)
                    points.add(point)
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
        get() = mapView?.isVisible == true

    override fun onViewCreated(viewIn: View, savedInstanceState: Bundle?) {
        super.onViewCreated(viewIn, savedInstanceState)

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
                            it.addLayer(nodeLayer)
                            it.addLayer(labelLayer)
                        }
                    }

                    v.gestures.rotateEnabled = false

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




