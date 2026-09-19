/*
 * Copyright (c) 2026 Meshtastic LLC
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
package org.meshtastic.feature.map.maplibre

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import org.koin.compose.koinInject
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.map.MapState
import org.maplibre.spatialk.geojson.Position
import org.meshtastic.core.repository.MapCameraPosition
import org.meshtastic.core.repository.MapPrefs

/**
 * Reads back the camera the user left the map on.
 *
 * The OSMdroid map did this and the Google flavor still does; the MapLibre map lost it in the cutover even though
 * `MapPrefs.setCameraPosition` and its stored value survived untouched.
 *
 * The stored position is handed to `rememberMapState` as its initial camera rather than written to the map afterwards,
 * which is what maplibre-compose 0.16.0 made possible — a map created at the remembered position opens there instead of
 * opening on a default and then moving.
 *
 * Pair with [SaveCameraPosition], which is what keeps the value up to date.
 */
@Composable
internal fun rememberRestoredCamera(): RestoredCamera? {
    val mapPrefs: MapPrefs = koinInject()
    var restored by remember { mutableStateOf<RestoredCamera?>(null) }

    LaunchedEffect(Unit) {
        val saved = mapPrefs.awaitCameraPosition()
        restored =
            RestoredCamera(
                saved?.let {
                    CameraPosition(target = Position(longitude = it.longitude, latitude = it.latitude), zoom = it.zoom)
                },
            )
    }

    return restored
}

/**
 * Keeps the stored camera position in step with [mapState].
 *
 * Only writes while the camera is settled: saving mid-gesture would store every frame of a pan, and the value that
 * matters is the one the user stopped on.
 */
@Composable
internal fun SaveCameraPosition(mapState: MapState) {
    val mapPrefs: MapPrefs = koinInject()

    LaunchedEffect(mapState) {
        snapshotFlow { mapState.cameraPosition.takeUnless { mapState.isCameraMoving } }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { position ->
                mapPrefs.setCameraPosition(
                    MapCameraPosition(
                        latitude = position.target.latitude,
                        longitude = position.target.longitude,
                        zoom = position.zoom,
                    ),
                )
            }
    }
}

/**
 * Where the user left the map, once that is known.
 *
 * [position] is null when there was nothing stored, which is the caller's cue to frame the mesh instead. The whole
 * value is null while the answer is still being read, and the caller must do neither until it arrives.
 */
@Immutable internal class RestoredCamera(val position: CameraPosition?)
