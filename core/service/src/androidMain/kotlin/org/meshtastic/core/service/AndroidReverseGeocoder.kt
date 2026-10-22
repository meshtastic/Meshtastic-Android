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

import android.app.Application
import android.location.Address
import android.location.Geocoder
import android.os.Build
import co.touchlab.kermit.Logger
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.GeocodedPlace
import org.meshtastic.core.repository.ReverseGeocoder
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume

/**
 * The platform geocoder in US English whatever the device's locale, since root topic segments use English place names.
 * Devices without a geocoder backend (no Play services on the F-Droid flavor) get null and the topic finder falls back
 * to the region and what it hears.
 */
@Single
class AndroidReverseGeocoder(private val context: Application, private val dispatchers: CoroutineDispatchers) :
    ReverseGeocoder {
    override suspend fun place(latitude: Double, longitude: Double): GeocodedPlace? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.US)
        val address =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocation(
                        latitude,
                        longitude,
                        1,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                continuation.resume(addresses.firstOrNull())
                            }

                            override fun onError(errorMessage: String?) {
                                Logger.w { "Reverse geocode failed" }
                                continuation.resume(null)
                            }
                        },
                    )
                }
            } else {
                withContext(dispatchers.io) {
                    try {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
                    } catch (e: IOException) {
                        Logger.w { "Reverse geocode failed: ${e::class.simpleName}" }
                        null
                    }
                }
            }
        return address?.let { GeocodedPlace(it.countryCode, it.adminArea, it.locality) }
    }
}
