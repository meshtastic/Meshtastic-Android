/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import org.koin.core.annotation.Single
import org.meshtastic.core.di.CoroutineDispatchers

@Single
class AndroidPhoneLocationProvider(private val context: Context, private val dispatchers: CoroutineDispatchers) :
    PhoneLocationProvider {

    @SuppressLint("MissingPermission")
    override fun locationUpdates(): Flow<PhoneLocationState> = callbackFlow {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager == null) {
            trySend(PhoneLocationState(permissionGranted = false, providerEnabled = false))
            close()
            return@callbackFlow
        }

        if (!hasLocationPermission()) {
            trySend(PhoneLocationState(permissionGranted = false, providerEnabled = false))
            close()
            return@callbackFlow
        }

        var lastLocation: Location? = null

        fun sendUpdate() {
            trySend(
                PhoneLocationState(
                    permissionGranted = true,
                    providerEnabled = LocationManagerCompat.isLocationEnabled(locationManager),
                    location = lastLocation?.toPhoneLocation(),
                ),
            )
        }

        val listener =
            object : LocationListenerCompat {
                override fun onLocationChanged(location: Location) {
                    // Subscribing to both GPS and NETWORK providers means coarse Wi-Fi/cell fixes
                    // would otherwise overwrite a fresh, accurate GPS fix on every callback,
                    // making the compass distance/bearing jitter between two positions
                    // (see issue #4864). Apply the canonical isBetterLocation filter so we
                    // prefer the most accurate, recent fix and fall back to network only
                    // when no usable GPS fix is available.
                    if (isBetterLocation(location, lastLocation)) {
                        lastLocation = location
                        sendUpdate()
                    }
                }

                override fun onProviderEnabled(provider: String) = sendUpdate()

                override fun onProviderDisabled(provider: String) = sendUpdate()
            }

        val locationRequest =
            LocationRequestCompat.Builder(MIN_UPDATE_INTERVAL_MS)
                .setMinUpdateDistanceMeters(0f)
                .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY)
                .build()

        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)

        try {
            lastLocation =
                providers
                    .mapNotNull { provider -> locationManager.getLastKnownLocation(provider) }
                    .reduceOrNull { best, candidate ->
                        if (isBetterLocation(candidate, best)) candidate else best
                    }

            sendUpdate()

            providers.forEach { provider ->
                if (provider in locationManager.allProviders) {
                    LocationManagerCompat.requestLocationUpdates(
                        locationManager,
                        provider,
                        locationRequest,
                        listener,
                        Looper.getMainLooper(),
                    )
                }
            }
        } catch (securityException: SecurityException) {
            trySend(PhoneLocationState(permissionGranted = false, providerEnabled = false))
            close(securityException)
            return@callbackFlow
        }

        awaitClose { LocationManagerCompat.removeUpdates(locationManager, listener) }
    }
        .flowOn(dispatchers.io)

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun Location.toPhoneLocation() =
        PhoneLocation(latitude = latitude, longitude = longitude, altitude = altitude, timeMillis = time)

    companion object {
        private const val MIN_UPDATE_INTERVAL_MS = 1_000L
        private const val SIGNIFICANTLY_NEWER_MS = 2 * 60 * 1000L
        private const val SIGNIFICANTLY_LESS_ACCURATE_M = 200f

        /**
         * Canonical Android "is this fix better than the last one?" comparison (adapted from the framework's
         * LocationListener guide). Without this, subscribing to GPS_PROVIDER and NETWORK_PROVIDER simultaneously causes
         * coarse Wi-Fi/cell fixes to overwrite recent fine GPS fixes, making the compass distance and bearing jump
         * between two positions (issue #4864).
         */
        @Suppress("ReturnCount")
        internal fun isBetterLocation(candidate: Location, current: Location?): Boolean {
            if (current == null) return true

            val timeDelta = candidate.time - current.time
            val isSignificantlyNewer = timeDelta > SIGNIFICANTLY_NEWER_MS
            val isSignificantlyOlder = timeDelta < -SIGNIFICANTLY_NEWER_MS
            val isNewer = timeDelta > 0

            // A much newer fix is always preferred even if accuracy is worse — the device
            // has likely moved, so a stale "accurate" fix is worse than a fresh coarse one.
            if (isSignificantlyNewer) return true
            if (isSignificantlyOlder) return false

            val accuracyDelta = candidate.accuracy - current.accuracy
            val isMoreAccurate = accuracyDelta < 0f
            val isLessAccurate = accuracyDelta > 0f
            val isSignificantlyLessAccurate = accuracyDelta > SIGNIFICANTLY_LESS_ACCURATE_M
            val isFromSameProvider = candidate.provider == current.provider

            return when {
                isMoreAccurate -> true
                isNewer && !isLessAccurate -> true
                isNewer && !isSignificantlyLessAccurate && isFromSameProvider -> true
                else -> false
            }
        }
    }
}
