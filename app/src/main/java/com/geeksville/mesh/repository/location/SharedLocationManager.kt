package com.geeksville.mesh.repository.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import com.geeksville.mesh.android.GeeksvilleApplication
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.android.hasBackgroundPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.shareIn

/**
 * Wraps LocationCallback() in callbackFlow
 *
 * Derived in part from https://github.com/android/location-samples/blob/main/LocationUpdatesBackgroundKotlin/app/src/main/java/com/google/android/gms/location/sample/locationupdatesbackgroundkotlin/data/MyLocationManager.kt
 * and https://github.com/googlecodelabs/kotlin-coroutines/blob/master/ktx-library-codelab/step-06/myktxlibrary/src/main/java/com/example/android/myktxlibrary/LocationUtils.kt
 */
class SharedLocationManager constructor(
    private val context: Context,
    externalScope: CoroutineScope
) : Logging {

    private val _receivingLocationUpdates: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val receivingLocationUpdates: StateFlow<Boolean> get() = _receivingLocationUpdates

    // Defaults from device positionBroadcastSmart
    private val timeTravelMinimum = 30 * 1000L // 30 seconds
    private val distanceTravelMinimum = 30F // 30 meters

    @SuppressLint("MissingPermission")
    private val _locationUpdates = callbackFlow {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val callback = object: LocationListener {
            override fun onLocationChanged(location: Location) {
                // info("New location: $location")
                trySend(location)
            }

            override fun onProviderDisabled(provider: String) {
                close()
            }
        }

        if (!context.hasBackgroundPermission()) close()

        info("Starting location updates with minTimeMs=${timeTravelMinimum}ms and minDistanceM=${distanceTravelMinimum}m")
        _receivingLocationUpdates.value = true
        GeeksvilleApplication.analytics.track("location_start") // Figure out how many users needed to use the phone GPS

        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                timeTravelMinimum,
                distanceTravelMinimum,
                callback,
                context.mainLooper
            )
        } catch (e: Exception) {
            close(e) // in case of exception, close the Flow
        }

        awaitClose {
            info("Stopping location requests")
            _receivingLocationUpdates.value = false
            GeeksvilleApplication.analytics.track("location_stop")
            locationManager.removeUpdates(callback) // clean up when Flow collection ends
        }
    }.shareIn(
        externalScope,
        replay = 0,
        started = SharingStarted.WhileSubscribed()
    )

    fun locationFlow(): Flow<Location> {
        return _locationUpdates
    }
}
