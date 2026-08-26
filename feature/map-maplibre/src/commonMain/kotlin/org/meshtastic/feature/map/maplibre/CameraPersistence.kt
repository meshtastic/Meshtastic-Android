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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import org.koin.compose.koinInject
import org.maplibre.compose.camera.CameraState
import org.maplibre.spatialk.geojson.Position
import org.meshtastic.core.repository.MapCameraPosition
import org.meshtastic.core.repository.MapPrefs

/**
 * Restores the map to wherever the user left it, and keeps that saved.
 *
 * The OSMdroid map did this and the Google flavor still does; the MapLibre map lost it in the cutover even though
 * `MapPrefs.setCameraPosition` and its stored value survived untouched. Returns null until the stored position has been
 * read, then whether there was one — the caller needs that to decide between the remembered view and framing the mesh,
 * and must not do either while the answer is unknown.
 */
@Composable
internal fun rememberRestoredCamera(cameraState: CameraState): Boolean? {
    val mapPrefs: MapPrefs = koinInject()
    var restored by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        val saved = mapPrefs.awaitCameraPosition()
        if (saved != null) {
            cameraState.position =
                cameraState.position.copy(
                    target = Position(longitude = saved.longitude, latitude = saved.latitude),
                    zoom = saved.zoom,
                )
        }
        restored = saved != null
    }

    LaunchedEffect(restored) {
        // Saving only starts once the restore has been attempted, and only while the camera is settled. Writing
        // before that would overwrite the remembered view with wherever the map happened to open.
        if (restored == null) return@LaunchedEffect

        snapshotFlow { cameraState.position.takeUnless { cameraState.isCameraMoving } }
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

    return restored
}
