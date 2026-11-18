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
package org.meshtastic.app.node

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailabilityLight
import com.google.android.gms.location.LocationListener
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.meshtastic.node.android.LocationFeed
import java.util.concurrent.Executor
import kotlin.time.Duration

/**
 * Play services' fused provider as the node's location feed, at high accuracy and unbatched, since a delayed fix is a
 * delayed broadcast. It fixes faster than the bare GPS and indoors from Wi-Fi; satellites and dilution of precision
 * still come from the receiver's NMEA, so a fix without a GNSS lock carries none.
 */
class PlayLocationFeed private constructor(context: Context) : LocationFeed {
    private val client = LocationServices.getFusedLocationProviderClient(context)
    private var callback: LocationListener? = null

    @SuppressLint("MissingPermission")
    override fun request(interval: Duration, executor: Executor, listener: (Location) -> Unit) {
        remove()
        val millis = interval.inWholeMilliseconds
        val request =
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, millis)
                .setMinUpdateIntervalMillis(millis)
                .setMinUpdateDistanceMeters(0f)
                .build()
        val next = LocationListener { listener(it) }
        client.requestLocationUpdates(request, executor, next)
        callback = next
    }

    override fun remove() {
        callback?.let(client::removeLocationUpdates)
        callback = null
    }

    companion object {
        /** The fused feed, or null on a phone without a working Play services, which takes the platform GPS. */
        fun orNull(context: Context): LocationFeed? = if (
            GoogleApiAvailabilityLight.getInstance().isGooglePlayServicesAvailable(context) ==
            ConnectionResult.SUCCESS
        ) {
            PlayLocationFeed(context.applicationContext)
        } else {
            null
        }
    }
}
