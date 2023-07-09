package com.geeksville.mesh.repository.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.geeksville.mesh.android.GeeksvilleApplication
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.android.hasBackgroundPermission
import com.geeksville.mesh.android.isGooglePlayAvailable
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
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

    private val desiredInterval = 1 * 60 * 1000L

    // Set up the Fused Location Provider and LocationRequest
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private val locationRequest = LocationRequest.Builder(desiredInterval)
        .setMinUpdateIntervalMillis(30 * 1000L)
        .setMaxUpdateDelayMillis(5 * 60 * 1000L)
        // .setMinUpdateDistanceMeters(30f) // 30 meters
        .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
        .build()

    @SuppressLint("MissingPermission")
    private val _locationUpdates = callbackFlow {
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                // info("New location: ${result.lastLocation}")
                result.lastLocation?.let { lastLocation ->
                    trySend(lastLocation)
                }
            }
        }
        if (!context.hasBackgroundPermission() || !isGooglePlayAvailable(context)) close()

        info("Starting location requests with interval=${desiredInterval}ms")
        _receivingLocationUpdates.value = true
        GeeksvilleApplication.analytics.track("location_start") // Figure out how many users needed to use the phone GPS

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            callback,
            Looper.getMainLooper()
        ).addOnFailureListener { ex ->
            errormsg("Failed to listen to GPS error: ${ex.message}")
            close(ex) // in case of exception, close the Flow
        }

        awaitClose {
            info("Stopping location requests")
            _receivingLocationUpdates.value = false
            GeeksvilleApplication.analytics.track("location_stop")
            fusedLocationClient.removeLocationUpdates(callback) // clean up when Flow collection ends
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
