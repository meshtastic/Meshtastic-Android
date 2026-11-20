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
package org.meshtastic.web.platform

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.takeWhile
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.util.GeoConstants
import org.meshtastic.core.model.util.UnitConversions
import org.meshtastic.core.repository.Location
import org.meshtastic.core.repository.LocationRepository
import org.meshtastic.core.repository.LocationService
import org.meshtastic.core.repository.MeshLocationManager
import org.meshtastic.core.repository.PhoneLocation
import org.meshtastic.core.repository.PhoneLocationProvider
import org.meshtastic.core.repository.PhoneLocationState
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import org.meshtastic.proto.Position as ProtoPosition

/** Fixes from `watchPosition`. A refusal ends the flow quietly; the browser's own prompt is the only ask. */
class WebLocationRepository : LocationRepository {
    private val receiving = MutableStateFlow(false)
    override val receivingLocationUpdates: StateFlow<Boolean> = receiving.asStateFlow()

    override fun getLocations(): Flow<Location> = watchGeolocation()
        .takeWhile { it !is GeoReading.Denied }
        .filterIsInstance<GeoReading.Fix>()
        .map { it.location }
        .onStart { receiving.value = true }
        .onCompletion { receiving.value = false }
}

/** One `getCurrentPosition` fix, or null when the browser refuses or has none. */
class WebLocationService : LocationService {
    override suspend fun getCurrentLocation(): Location? = (currentGeolocation() as? GeoReading.Fix)?.location
}

/**
 * Sends the browser's fixes as the local node's position while position sharing is on, at most one per
 * [MIN_SEND_INTERVAL_MS] as Android's location request does: `watchPosition` has no interval and fires on every move.
 */
class WebMeshLocationManager(private val locationRepository: LocationRepository) : MeshLocationManager {
    private var scope: CoroutineScope? = null
    private var sendPositionFn: (suspend (ProtoPosition) -> Boolean)? = null
    private var locationJob: Job? = null

    override fun start(scope: CoroutineScope, sendPositionFn: suspend (ProtoPosition) -> Boolean) {
        this.scope = scope
        this.sendPositionFn = sendPositionFn
        if (locationJob?.isActive == true) return
        var lastSentMillis: Long? = null
        locationJob =
            locationRepository
                .getLocations()
                .onEach { location ->
                    val last = lastSentMillis
                    if (last != null && location.timeMillis - last < MIN_SEND_INTERVAL_MS) return@onEach
                    lastSentMillis = location.timeMillis
                    sendPositionFn(location.toPhonePosition())
                }
                .launchIn(scope)
    }

    override fun restart() {
        val currentScope = scope
        val fn = sendPositionFn
        if (currentScope == null || fn == null) {
            Logger.d { "restart() before start() — no-op until MeshConnectionManagerImpl wires location" }
            return
        }
        start(currentScope, fn)
    }

    override fun stop() {
        locationJob?.cancel()
        locationJob = null
    }

    private companion object {
        const val MIN_SEND_INTERVAL_MS = 30_000L
    }
}

/**
 * The phone's position for the compass and the signal views. Like Android it never prompts: until the page holds the
 * permission it reports none, and it starts watching once the permission is granted elsewhere.
 */
class WebPhoneLocationProvider : PhoneLocationProvider {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun locationUpdates(): Flow<PhoneLocationState> = geolocationPermission().flatMapLatest { state ->
        if (state == "granted") {
            watchGeolocation()
                .map { it.toPhoneLocationState() }
                .onStart { emit(PhoneLocationState(permissionGranted = true, providerEnabled = true)) }
        } else {
            flowOf(PhoneLocationState(permissionGranted = false, providerEnabled = false))
        }
    }
}

private fun GeoReading.toPhoneLocationState(): PhoneLocationState = when (this) {
    is GeoReading.Fix ->
        PhoneLocationState(
            permissionGranted = true,
            providerEnabled = true,
            location =
            PhoneLocation(
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = location.altitudeMeters,
                timeMillis = location.timeMillis,
                accuracyM = location.accuracyMeters,
                verticalAccuracyM = verticalAccuracyM,
                speedMps = location.speedMetersPerSecond,
            ),
        )

    GeoReading.Denied -> PhoneLocationState(permissionGranted = false, providerEnabled = false)

    GeoReading.Unavailable -> PhoneLocationState(permissionGranted = true, providerEnabled = false)
}

/**
 * Mirrors `core:service`'s internal `phonePosition`: an unreported reading stays unset rather than going out as 0, and
 * speed and track use firmware's own GPS units, km/h and 1e-5 degrees. A browser reports only ellipsoid height.
 */
private fun Location.toPhonePosition(): ProtoPosition = ProtoPosition.Builder()
    .also { wb ->
        wb.latitude_i = Position.degI(latitude)
        wb.longitude_i = Position.degI(longitude)
        wb.altitude = mslAltitudeMeters?.toInt()
        wb.altitude_hae = altitudeMeters?.toInt()
        wb.time = timeMillis.milliseconds.inWholeSeconds.toInt()
        wb.ground_speed = speedMetersPerSecond?.let { UnitConversions.metersPerSecondToKph(it).roundToInt() }
        wb.ground_track = bearingDegrees?.let { (it / GeoConstants.HEADING_DEG).roundToInt() }
        wb.location_source = ProtoPosition.LocSource.LOC_EXTERNAL
    }
    .build()
