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

package com.geeksville.mesh.ui.node

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.geeksville.mesh.model.MetricsViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import kotlinx.coroutines.launch
import java.text.DateFormat

const val DegD = 1e-7


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeMapScreen(
    metricsViewModel: MetricsViewModel = hiltViewModel(),
) {
    val state by metricsViewModel.state.collectAsState()
    val positions = state.positionLogs
    val latLngs = positions.map { LatLng(it.latitudeI * DegD, it.longitudeI * DegD) }
    val cameraPositionState = rememberCameraPositionState {
        // Default position if node location is not yet available
        position = CameraPosition.fromLatLngZoom(latLngs.getOrElse(0) { LatLng(0.0, 0.0) }, 12f)
    }
    val coroutineScope = rememberCoroutineScope()
    val dateFormat =
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    // Enable scale controls, disable zoom controls as pinch-to-zoom is default
    val uiSettings = remember { MapUiSettings() }
    val mapProperties = remember {
        MapProperties(
            isMyLocationEnabled = true
        )
    }
    LaunchedEffect(latLngs) {
        val latLngBounds = LatLngBounds.Builder().apply {
            latLngs.forEach { include(it) }
        }.build()
        coroutineScope.launch {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngBounds(latLngBounds, 100)
            )
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = uiSettings,
            properties = mapProperties
        ) {
            latLngs.forEachIndexed { index, latLng ->
                Marker(
                    state = rememberUpdatedMarkerState(position = latLng),
                    title = "Position ${index}",
                    snippet = "Time of position: ${dateFormat.format(positions[index].time)}",
                )
            }
            Polyline(points = latLngs, jointType = JointType.ROUND)
        }
    }
}