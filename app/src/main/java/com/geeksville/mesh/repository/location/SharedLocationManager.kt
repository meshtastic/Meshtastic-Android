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

    // TODO use positionBroadcastSecs / test locationRequest settings
    // if unset, use positionBroadcastSecs default
    // positionBroadcastSecs.takeIf { it != 0L }?.times(1000L) ?: (15 * 60 * 1000L)
    private val fastestInterval = 30 * 1000L
    private val smallestDisplacement = 50F // 50 meters

    @SuppressLint("MissingPermission")
    private val _locationUpdates = callbackFlow {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val callback = LocationListener { location ->
            // info("New location: ${result.lastLocation}")
            trySend(location)
        }

        if (!context.hasBackgroundPermission()) close()


        info("Starting location updates with minTime=${fastestInterval}ms and minDistance=${smallestDisplacement}m")
        _receivingLocationUpdates.value = true
        GeeksvilleApplication.analytics.track("location_start") // Figure out how many users needed to use the phone GPS

        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                fastestInterval,
                smallestDisplacement,
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
