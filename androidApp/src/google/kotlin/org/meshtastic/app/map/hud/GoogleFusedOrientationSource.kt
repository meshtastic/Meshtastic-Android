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
package org.meshtastic.app.map.hud

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailabilityLight
import com.google.android.gms.location.DeviceOrientationListener
import com.google.android.gms.location.DeviceOrientationRequest
import com.google.android.gms.location.LocationServices
import org.meshtastic.feature.map.hud.FusedOrientationSource
import java.util.concurrent.Executor

/** The platform's SENSOR_DELAY_UI period, which the rotation-vector path drops to when the phone runs hot. */
private const val REDUCED_PERIOD_MICROS = 66_667L

/** Play services' Fused Orientation Provider, the same fusion Google Maps draws its heading from. */
class GoogleFusedOrientationSource(private val context: Context) : FusedOrientationSource {

    override fun start(
        reduced: Boolean,
        executor: Executor,
        listener: FusedOrientationSource.Listener,
        onFailure: () -> Unit,
    ): AutoCloseable? {
        val availability = GoogleApiAvailabilityLight.getInstance().isGooglePlayServicesAvailable(context)
        if (availability != ConnectionResult.SUCCESS) return null
        val client = LocationServices.getFusedOrientationProviderClient(context)
        val callback = DeviceOrientationListener { orientation ->
            val headingError =
                if (orientation.hasConservativeHeadingErrorDegrees()) {
                    orientation.conservativeHeadingErrorDegrees
                } else {
                    orientation.headingErrorDegrees
                }
            // FOP has no per-sample flag: it applies the declination whenever Play services knows the location,
            // which it does whenever the view has the fix it needs to place anything.
            listener.onSample(
                attitude = orientation.attitude,
                headingErrorDeg = headingError,
                trueNorth = true,
                timestampNs = orientation.elapsedRealtimeNs,
            )
        }
        val period = if (reduced) REDUCED_PERIOD_MICROS else DeviceOrientationRequest.OUTPUT_PERIOD_DEFAULT
        client
            .requestOrientationUpdates(DeviceOrientationRequest.Builder(period).build(), executor, callback)
            .addOnFailureListener(executor) { onFailure() }
        return AutoCloseable { client.removeOrientationUpdates(callback) }
    }
}
