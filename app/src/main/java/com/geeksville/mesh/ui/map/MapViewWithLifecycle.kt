/*
 * Copyright (c) 2025 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.geeksville.mesh.ui.map

import android.annotation.SuppressLint
import android.content.Context
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.geeksville.mesh.BuildConfig
import com.geeksville.mesh.android.BuildUtils.errormsg
import com.geeksville.mesh.util.requiredZoomLevel
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView

@SuppressLint("WakelockTimeout")
private fun PowerManager.WakeLock.safeAcquire() {
    if (!isHeld) try {
        acquire()
    } catch (e: SecurityException) {
        errormsg("WakeLock permission exception: ${e.message}")
    } catch (e: IllegalStateException) {
        errormsg("WakeLock acquire() exception: ${e.message}")
    }
}

private fun PowerManager.WakeLock.safeRelease() {
    if (isHeld) try {
        release()
    } catch (e: IllegalStateException) {
        errormsg("WakeLock release() exception: ${e.message}")
    }
}

const val MAP_STYLE_ID = "map_style_id"

private const val MinZoomLevel = 1.5
private const val MaxZoomLevel = 20.0

@Composable
internal fun rememberMapViewWithLifecycle(
    box: BoundingBox,
    tileSource: ITileSource = TileSourceFactory.DEFAULT_TILE_SOURCE,
): MapView {
    val zoom = box.requiredZoomLevel()
    val center = GeoPoint(box.centerLatitude, box.centerLongitude)
    return rememberMapViewWithLifecycle(zoom, center, tileSource)
}

@Composable
internal fun rememberMapViewWithLifecycle(
    zoomLevel: Double = MinZoomLevel,
    mapCenter: GeoPoint = GeoPoint(0.0, 0.0),
    tileSource: ITileSource = TileSourceFactory.DEFAULT_TILE_SOURCE,
): MapView {
    var savedZoom by rememberSaveable { mutableDoubleStateOf(zoomLevel) }
    var savedCenter by rememberSaveable(stateSaver = Saver(
        save = { mapOf("latitude" to it.latitude, "longitude" to it.longitude) },
        restore = { GeoPoint(it["latitude"] ?: 0.0, it["longitude"] ?: .0) }
    )) { mutableStateOf(mapCenter) }

    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            clipToOutline = true

            // Required to get online tiles
            Configuration.getInstance().userAgentValue = BuildConfig.APPLICATION_ID
            setTileSource(tileSource)
            isVerticalMapRepetitionEnabled = false // disables map repetition
            setMultiTouchControls(true)
            val bounds = overlayManager.tilesOverlay.bounds // bounds scrollable map
            setScrollableAreaLimitLatitude(bounds.actualNorth, bounds.actualSouth, 0)
            // scales the map tiles to the display density of the screen
            isTilesScaledToDpi = true
            // sets the minimum zoom level (the furthest out you can zoom)
            minZoomLevel = MinZoomLevel
            maxZoomLevel = MaxZoomLevel
            // Disables default +/- button for zooming
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)

            controller.setZoom(savedZoom)
            controller.setCenter(savedCenter)
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

        @Suppress("DEPRECATION")
        val wakeLock =
            powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK, "Meshtastic:MapViewLock")

        wakeLock.safeAcquire()

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    wakeLock.safeRelease()
                    mapView.onPause()
                }

                Lifecycle.Event.ON_RESUME -> {
                    wakeLock.safeAcquire()
                    mapView.onResume()
                }

                Lifecycle.Event.ON_STOP -> {
                    savedCenter = mapView.projection.currentCenter
                    savedZoom = mapView.zoomLevelDouble
                }

                else -> {}
            }
        }

        lifecycle.addObserver(observer)

        onDispose {
            lifecycle.removeObserver(observer)
            wakeLock.safeRelease()
            mapView.onDetach()
        }
    }
    return mapView
}
