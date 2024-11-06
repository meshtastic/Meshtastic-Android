package com.geeksville.mesh.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.BuildConfig
import com.geeksville.mesh.model.MetricsViewModel
import com.geeksville.mesh.ui.map.rememberMapViewWithLifecycle
import com.geeksville.mesh.util.addCopyright
import com.geeksville.mesh.util.addPositionMarkers
import com.geeksville.mesh.util.addPolyline
import com.geeksville.mesh.util.addScaleBarOverlay
import com.geeksville.mesh.util.requiredZoomLevel
import org.osmdroid.config.Configuration
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController

private const val DegD = 1e-7

@Composable
fun NodeMapScreen(
    viewModel: MetricsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val mapView = rememberMapViewWithLifecycle(context)

    val state by viewModel.state.collectAsStateWithLifecycle()
    val geoPoints = state.positionLogs.map { GeoPoint(it.latitudeI * DegD, it.longitudeI * DegD) }

    var savedCenter by rememberSaveable(stateSaver = Saver(
        save = { mapOf("latitude" to it.latitude, "longitude" to it.longitude) },
        restore = { GeoPoint(it["latitude"] ?: 0.0, it["longitude"] ?: .0) }
    )) {
        val box = BoundingBox.fromGeoPoints(geoPoints)
        mutableStateOf(GeoPoint(box.centerLatitude, box.centerLongitude))
    }
    var savedZoom by rememberSaveable {
        val box = BoundingBox.fromGeoPoints(geoPoints)
        mutableDoubleStateOf(box.requiredZoomLevel())
    }

    LifecycleStartEffect(true) {
        onStopOrDispose {
            savedCenter = mapView.projection.currentCenter
            savedZoom = mapView.zoomLevelDouble
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            mapView.apply {
                Configuration.getInstance().userAgentValue = BuildConfig.APPLICATION_ID
                setMultiTouchControls(true)
                isTilesScaledToDpi = true
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                controller.setCenter(savedCenter)
                controller.setZoom(savedZoom)
            }
        },
        update = { map ->
            map.overlays.clear()
            map.addCopyright()
            map.addScaleBarOverlay(density)

            map.addPolyline(density, geoPoints) {}
            map.addPositionMarkers(state.positionLogs) {}
        }
    )
}
