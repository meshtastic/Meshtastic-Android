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
package org.meshtastic.core.service

import android.annotation.SuppressLint
import android.app.Application
import androidx.core.location.LocationCompat
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.core.annotation.Single
import org.meshtastic.core.common.hasLocationPermission
import org.meshtastic.core.model.Position
import org.meshtastic.core.repository.LocationRepository
import org.meshtastic.core.repository.MeshLocationManager
import kotlin.time.Duration.Companion.milliseconds
import org.meshtastic.proto.Position as ProtoPosition

@Single
class AndroidMeshLocationManager(private val context: Application, private val locationRepository: LocationRepository) :
    MeshLocationManager {
    private lateinit var scope: CoroutineScope
    private var locationFlow: Job? = null

    @SuppressLint("MissingPermission")
    override fun start(scope: CoroutineScope, sendPositionFn: (ProtoPosition) -> Unit) {
        this.scope = scope
        if (locationFlow?.isActive == true) return

        if (context.hasLocationPermission()) {
            locationFlow =
                locationRepository
                    .getLocations()
                    .onEach { location ->
                        sendPositionFn(
                            ProtoPosition(
                                latitude_i = Position.degI(location.latitude),
                                longitude_i = Position.degI(location.longitude),
                                altitude =
                                if (LocationCompat.hasMslAltitude(location)) {
                                    LocationCompat.getMslAltitudeMeters(location).toInt()
                                } else {
                                    null
                                },
                                altitude_hae = location.altitude.toInt(),
                                time = (location.time.milliseconds.inWholeSeconds).toInt(),
                                ground_speed = location.speed.toInt(),
                                ground_track = location.bearing.toInt(),
                                location_source = ProtoPosition.LocSource.LOC_EXTERNAL,
                            ),
                        )
                    }
                    .launchIn(scope)
        }
    }

    override fun stop() {
        if (locationFlow?.isActive == true) {
            Logger.i { "Stopping location requests" }
            locationFlow?.cancel()
            locationFlow = null
        }
    }
}
