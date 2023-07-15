package com.geeksville.mesh.repository.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import androidx.core.location.LocationListenerCompat
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
    private val distanceTravelMinimum = 0f

    @SuppressLint("MissingPermission")
    private val _locationUpdates = callbackFlow {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val callback = LocationListenerCompat { location ->
            // info("New location: $location")
            trySend(location)
        }

        if (!context.hasBackgroundPermission()) close()

        val providerList = buildList {
            val providers = locationManager.allProviders
            if (android.os.Build.VERSION.SDK_INT >= 31 && LocationManager.FUSED_PROVIDER in providers) {
                add(LocationManager.FUSED_PROVIDER)
            } else {
                if (LocationManager.NETWORK_PROVIDER in providers) add(LocationManager.NETWORK_PROVIDER)
                if (LocationManager.GPS_PROVIDER in providers) add(LocationManager.GPS_PROVIDER)
            }
        }

        info("Starting location updates with $providerList minTimeMs=${timeTravelMinimum}ms and minDistanceM=${distanceTravelMinimum}m")
        _receivingLocationUpdates.value = true
        GeeksvilleApplication.analytics.track("location_start") // Figure out how many users needed to use the phone GPS

        try {
            providerList.forEach { provider ->
                locationManager.requestLocationUpdates(
                    provider,
                    timeTravelMinimum,
                    distanceTravelMinimum,
                    callback,
                    context.mainLooper
                )
            }
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
