/*
 * Copyright (c) 2024 Meshtastic LLC
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

package com.geeksville.mesh.repository.location

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.app.Application
import android.location.LocationManager
import androidx.annotation.RequiresPermission
import androidx.core.location.LocationCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import androidx.core.location.altitude.AltitudeConverterCompat
import com.geeksville.mesh.android.GeeksvilleApplication
import com.geeksville.mesh.android.Logging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepository @Inject constructor(
    private val context: Application,
    private val locationManager: dagger.Lazy<LocationManager>,
) : Logging {

    /**
     * Status of whether the app is actively subscribed to location changes.
     */
    private val _receivingLocationUpdates: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val receivingLocationUpdates: StateFlow<Boolean> get() = _receivingLocationUpdates

    @RequiresPermission(anyOf = [ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION])
    private fun LocationManager.requestLocationUpdates() = callbackFlow {

        val intervalMs = 30 * 1000L // 30 seconds
        val minDistanceM = 0f

        val locationRequest = LocationRequestCompat.Builder(intervalMs)
            .setMinUpdateDistanceMeters(minDistanceM)
            .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY)
            .build()

        val locationListener = LocationListenerCompat { location ->
            if (location.hasAltitude() && !LocationCompat.hasMslAltitude(location)) {
                try {
                    AltitudeConverterCompat.addMslAltitudeToLocation(context, location)
                } catch (e: Exception) {
                    errormsg("addMslAltitudeToLocation() failed", e)
                }
            }
            // info("New location: $location")
            trySend(location)
        }

        val providerList = buildList {
            val providers = allProviders
            if (android.os.Build.VERSION.SDK_INT >= 31 && LocationManager.FUSED_PROVIDER in providers) {
                add(LocationManager.FUSED_PROVIDER)
            } else {
                if (LocationManager.GPS_PROVIDER in providers) add(LocationManager.GPS_PROVIDER)
                if (LocationManager.NETWORK_PROVIDER in providers) add(LocationManager.NETWORK_PROVIDER)
            }
        }

        info("Starting location updates with $providerList intervalMs=${intervalMs}ms and minDistanceM=${minDistanceM}m")
        _receivingLocationUpdates.value = true
        GeeksvilleApplication.analytics.track("location_start") // Figure out how many users needed to use the phone GPS

        try {
            providerList.forEach { provider ->
                LocationManagerCompat.requestLocationUpdates(
                    this@requestLocationUpdates,
                    provider,
                    locationRequest,
                    Dispatchers.IO.asExecutor(),
                    locationListener,
                )
            }
        } catch (e: Exception) {
            close(e) // in case of exception, close the Flow
        }

        awaitClose {
            info("Stopping location requests")
            _receivingLocationUpdates.value = false
            GeeksvilleApplication.analytics.track("location_stop")

            LocationManagerCompat.removeUpdates(this@requestLocationUpdates, locationListener)
        }
    }

    /**
     * Observable flow for location updates
     */
    @RequiresPermission(anyOf = [ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION])
    fun getLocations() = locationManager.get().requestLocationUpdates()
}
