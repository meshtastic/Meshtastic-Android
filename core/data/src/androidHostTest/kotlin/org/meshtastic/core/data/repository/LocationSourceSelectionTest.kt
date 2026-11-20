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
package org.meshtastic.core.data.repository

import android.Manifest
import android.app.Application
import android.location.LocationManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.DataPair
import org.meshtastic.core.repository.PlatformAnalytics
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.Executor
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import android.location.Location as AndroidLocation

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocationSourceSelectionTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val manager = app.getSystemService(LocationManager::class.java)

    private object NoAnalytics : PlatformAnalytics {
        override fun track(event: String, vararg properties: DataPair) = Unit

        override fun setDeviceAttributes(firmwareVersion: String, model: String) = Unit

        override val isPlatformServicesAvailable: Boolean = false
    }

    private class FakeFused(val serves: Boolean) : FusedLocationClient {
        var interval: Long? = null
        var listener: ((AndroidLocation) -> Unit)? = null
        var onFailure: ((Exception) -> Unit)? = null
        var closed = false

        override fun requestLocationUpdates(
            intervalMillis: Long,
            executor: Executor,
            listener: (AndroidLocation) -> Unit,
            onFailure: (Exception) -> Unit,
        ): AutoCloseable? {
            if (!serves) return null
            interval = intervalMillis
            this.listener = listener
            this.onFailure = onFailure
            return AutoCloseable { closed = true }
        }
    }

    private fun repository(fused: FusedLocationClient) = LocationRepositoryImpl(
        context = app,
        locationManager = lazy { manager },
        analytics = NoAnalytics,
        dispatchers =
        CoroutineDispatchers(
            io = Dispatchers.Unconfined,
            main = Dispatchers.Unconfined,
            default = Dispatchers.Unconfined,
        ),
        fused = fused,
    )

    private fun platformRequests(): Int = manager.allProviders.sumOf { shadowOf(manager).getLocationRequests(it).size }

    init {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    @Test
    fun `Play services serve the fixes and the platform providers are left alone`() = runBlocking {
        val fused = FakeFused(serves = true)
        val first =
            launch(Dispatchers.Unconfined) {
                val location = withTimeout(5_000) { repository(fused).getLocations().first() }
                assertEquals(51.5, location.latitude)
            }
        assertEquals(30_000L, fused.interval)
        assertTrue(platformRequests() == 0, "no platform request while Play services serve")
        fused.listener?.invoke(
            AndroidLocation("fused").apply {
                latitude = 51.5
                longitude = -0.12
                time = 1_700_000_000_000L
            },
        )
        first.join()
        assertTrue(fused.closed, "the fused request ends with the flow")
    }

    @Test
    fun `Play services refusing the request after it returned hands over to the platform providers`() = runBlocking {
        val fused = FakeFused(serves = true)
        val collecting = launch(Dispatchers.Unconfined) { repository(fused).getLocations().first() }
        assertTrue(platformRequests() == 0, "no platform request while the fused registration is pending")
        fused.onFailure?.invoke(IllegalStateException("registration failed"))
        assertTrue(platformRequests() > 0, "the platform is asked once Play services refuse")
        collecting.cancel()
        collecting.join()
        assertTrue(platformRequests() == 0, "the platform request ends with the flow")
    }

    @Test
    fun `a Play services refusal after the flow ended starts nothing`() = runBlocking {
        val fused = FakeFused(serves = true)
        val collecting = launch(Dispatchers.Unconfined) { repository(fused).getLocations().first() }
        collecting.cancel()
        collecting.join()
        fused.onFailure?.invoke(IllegalStateException("registration failed"))
        assertTrue(platformRequests() == 0, "no platform request outlives the flow")
    }

    @Test
    fun `without Play services the platform providers serve`() = runBlocking {
        val collecting = launch(Dispatchers.Unconfined) { repository(FakeFused(serves = false)).getLocations().first() }
        assertTrue(
            platformRequests() > 0,
            "the platform is asked when Play services cannot serve",
        )
        collecting.cancel()
    }
}
