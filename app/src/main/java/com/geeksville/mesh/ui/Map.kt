package com.geeksville.mesh.ui

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.compose.Composable
import androidx.compose.onCommit
import androidx.ui.core.ContextAmbient
import androidx.ui.fakeandroidview.AndroidView
import androidx.ui.layout.Column
import androidx.ui.material.MaterialTheme
import androidx.ui.tooling.preview.Preview
import com.geeksville.android.Logging
import com.geeksville.mesh.R
import com.geeksville.mesh.model.UIState
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.Style

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

    Column {
        AndroidView(R.layout.map_view) { view ->
            view as MapView
            view.onCreate(UIState.savedInstanceState)

            mapLifecycleCallbacks.view = view
            (context.applicationContext as Application).registerActivityLifecycleCallbacks(
                mapLifecycleCallbacks
            )

            view.getMapAsync { map ->
                map.setStyle(Style.OUTDOORS) {
                    // Map is set up and the style has loaded. Now you can add data or make other map adjustments
                }
            }
        }
    }
}


@Preview
@Composable
fun previewMap() {
    // another bug? It seems modaldrawerlayout not yet supported in preview
    MaterialTheme(colors = palette) {
        MapContent()
    }
}
