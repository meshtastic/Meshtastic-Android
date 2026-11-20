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
package org.meshtastic.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailabilityLight
import com.google.android.gms.location.LocationListener
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.meshtastic.core.data.repository.FusedLocationClient
import java.util.concurrent.Executor

/** Play services' fused location provider at high accuracy, or null when the phone has no working Play services. */
class GoogleFusedLocationClient(private val context: Context) : FusedLocationClient {
    @SuppressLint("MissingPermission")
    override fun requestLocationUpdates(
        intervalMillis: Long,
        executor: Executor,
        listener: (Location) -> Unit,
        onFailure: (Exception) -> Unit,
    ): AutoCloseable? {
        val availability = GoogleApiAvailabilityLight.getInstance().isGooglePlayServicesAvailable(context)
        if (availability != ConnectionResult.SUCCESS) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        val request =
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
                .setMinUpdateDistanceMeters(0f)
                .build()
        val callback = LocationListener { listener(it) }
        client.requestLocationUpdates(request, executor, callback).addOnFailureListener(executor, onFailure)
        return AutoCloseable { client.removeLocationUpdates(callback) }
    }
}
