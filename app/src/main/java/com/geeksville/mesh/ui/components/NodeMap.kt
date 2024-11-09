package com.geeksville.mesh.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.model.MetricsViewModel
import com.geeksville.mesh.ui.map.rememberMapViewWithLifecycle
import com.geeksville.mesh.util.addCopyright
import com.geeksville.mesh.util.addPolyline
import com.geeksville.mesh.util.addPositionMarkers
import com.geeksville.mesh.util.addScaleBarOverlay
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint

private const val DegD = 1e-7

@Composable
fun NodeMapScreen(
    viewModel: MetricsViewModel = hiltViewModel(),
) {
    val density = LocalDensity.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val geoPoints = state.positionLogs.map { GeoPoint(it.latitudeI * DegD, it.longitudeI * DegD) }
    val cameraView = remember { BoundingBox.fromGeoPoints(geoPoints) }
    val mapView = rememberMapViewWithLifecycle(cameraView)

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { mapView },
        update = { map ->
            map.overlays.clear()
            map.addCopyright()
            map.addScaleBarOverlay(density)

            map.addPolyline(density, geoPoints) {}
            map.addPositionMarkers(state.positionLogs) {}
        }
    )
}
