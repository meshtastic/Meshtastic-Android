package com.geeksville.mesh.ui

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.compose.Composable
import androidx.compose.onCommit
import androidx.ui.core.ContextAmbient
import androidx.ui.fakeandroidview.AndroidView
import androidx.ui.material.MaterialTheme
import androidx.ui.tooling.preview.Preview
import com.geeksville.android.Logging
import com.geeksville.mesh.R
import com.geeksville.mesh.model.NodeDB
import com.geeksville.mesh.model.UIState
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.style.layers.Property
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import com.mapbox.mapboxsdk.style.layers.SymbolLayer
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource


object mapLog : Logging


/**
 * mapbox requires this, until compose has a nicer way of doing it, do it here
 */
private val mapLifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
    var view: MapView? = null

    override fun onActivityPaused(activity: Activity) {
        view!!.onPause()
    }

    override fun onActivityStarted(activity: Activity) {
        view!!.onStart()
    }

    override fun onActivityDestroyed(activity: Activity) {
        view!!.onDestroy()
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        view!!.onSaveInstanceState(outState)
    }

    override fun onActivityStopped(activity: Activity) {
        view!!.onStop()
    }

    /**
     * Called when the Activity calls [super.onCreate()][Activity.onCreate].
     */
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
    }

    override fun onActivityResumed(activity: Activity) {
        view!!.onResume()
    }
}


@Composable
fun MapContent() {
    analyticsScreen(name = "map")

    val typography = MaterialTheme.typography()
    val context = ContextAmbient.current

    onCommit(AppStatus.currentScreen) {
        onDispose {
            // We no longer care about activity lifecycle
            (context.applicationContext as Application).unregisterActivityLifecycleCallbacks(
                mapLifecycleCallbacks
            )
            mapLifecycleCallbacks.view = null
        }
    }

    // Find all nodes with valid locations
    val locations = NodeDB.nodes.values.mapNotNull { node ->
        val p = node.position
        if (p != null && (p.latitude != 0.0 || p.longitude != 0.0))
            Feature.fromGeometry(
                Point.fromLngLat(
                    p.latitude,
                    p.longitude
                )
            )
        else
            null
    }
    val nodeSourceId = "node-positions"
    val nodeLayerId = "node-layer"
    val markerImageId = "my-marker-image"
    val nodePositions =
        GeoJsonSource(nodeSourceId, FeatureCollection.fromFeatures(locations))

    // val markerIcon = BitmapFactory.decodeResource(context.resources, R.drawable.ic_twotone_person_pin_24)
    val markerIcon = context.getDrawable(R.drawable.ic_twotone_person_pin_24)!!

    val nodeLayer = SymbolLayer(nodeLayerId, nodeSourceId)
    nodeLayer.setProperties(
        PropertyFactory.iconImage(markerImageId),
        PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM)
    )

    //Column {
    AndroidView(R.layout.map_view) { view ->
        view as MapView
        view.onCreate(UIState.savedInstanceState)

        mapLifecycleCallbacks.view = view
        (context.applicationContext as Application).registerActivityLifecycleCallbacks(
            mapLifecycleCallbacks
        )

        view.getMapAsync { map ->
            map.setStyle(Style.OUTDOORS) { style ->
                style.addImage(markerImageId, markerIcon)
                style.addSource(nodePositions)
                style.addLayer(nodeLayer)
            }
        }
    }
    //}
}


@Preview
@Composable
fun previewMap() {
    // another bug? It seems modaldrawerlayout not yet supported in preview
    MaterialTheme(colors = palette) {
        MapContent()
    }
}
