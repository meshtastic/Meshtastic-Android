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

package org.meshtastic.feature.node.compass

import android.Manifest
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import org.meshtastic.core.di.CoroutineDispatchers
import javax.inject.Inject

data class PhoneLocationState(
    val permissionGranted: Boolean,
    val providerEnabled: Boolean,
    val location: Location? = null,
) {
    val hasFix: Boolean
        get() = location != null
}

class PhoneLocationProvider
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: CoroutineDispatchers,
) {
    // Streams phone location (and permission/provider state) so the compass stays gated on real fixes.
    fun locationUpdates(): Flow<PhoneLocationState> = callbackFlow {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager == null) {
            trySend(PhoneLocationState(permissionGranted = false, providerEnabled = false))
            close()
            return@callbackFlow
        }

        val permissionGranted = hasLocationPermission()
        if (!permissionGranted) {
            trySend(PhoneLocationState(permissionGranted = false, providerEnabled = false))
            awaitClose {}
            return@callbackFlow
        }

        val listener = LocationListener { location ->
            trySend(
                PhoneLocationState(
                    permissionGranted = true,
                    providerEnabled = LocationManagerCompat.isLocationEnabled(locationManager),
                    location = location,
                ),
            )
        }

        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)

        try {
            // Emit last known location if available
            providers
                .mapNotNull { provider -> locationManager.getLastKnownLocation(provider) }
                .maxByOrNull { it.time }
                ?.let { lastLocation ->
                    trySend(
                        PhoneLocationState(
                            permissionGranted = true,
                            providerEnabled = LocationManagerCompat.isLocationEnabled(locationManager),
                            location = lastLocation,
                        ),
                    )
                }

            providers.forEach { provider ->
                if (locationManager.getProvider(provider) != null) {
                    locationManager.requestLocationUpdates(
                        provider,
                        MIN_UPDATE_INTERVAL_MS,
                        0f,
                        listener,
                        Looper.getMainLooper(),
                    )
                }
            }

            // Emit provider-disabled state if location is off
            if (!LocationManagerCompat.isLocationEnabled(locationManager)) {
                trySend(PhoneLocationState(permissionGranted = true, providerEnabled = false, location = null))
            }
        } catch (securityException: SecurityException) {
            trySend(PhoneLocationState(permissionGranted = false, providerEnabled = false))
            close(securityException)
            return@callbackFlow
        }

        awaitClose { locationManager.removeUpdates(listener) }
    }
        .flowOn(dispatchers.io)

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    companion object {
        private const val MIN_UPDATE_INTERVAL_MS = 1_000L
    }
}
