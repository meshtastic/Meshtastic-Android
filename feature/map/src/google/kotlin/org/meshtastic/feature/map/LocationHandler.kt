/*
 * Copyright (c) 2025 Meshtastic LLC
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

package org.meshtastic.feature.map

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority

private const val INTERVAL_MILLIS = 10000L

@Suppress("LongMethod")
@Composable
fun LocationPermissionsHandler(onPermissionResult: (Boolean) -> Unit) {
    val context = LocalContext.current
    var localHasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val requestLocationPermissionLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) { isGranted ->
            localHasPermission = isGranted
            // Defer to the LaunchedEffect(localHasPermission) to check settings before confirming via
            // onPermissionResult
            // if permission is granted. If not granted, immediately report false.
            if (!isGranted) {
                onPermissionResult(false)
            }
        }

    val locationSettingsLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                Logger.d { "Location settings changed by user." }
                // User has enabled location services or improved accuracy.
                onPermissionResult(true) // Settings are now adequate, and permission was already granted.
            } else {
                Logger.d { "Location settings change cancelled by user." }
                // User chose not to change settings. The permission itself is still granted,
                // but the experience might be degraded. For the purpose of enabling map features,
                // we consider this as success if the core permission is there.
                // If stricter handling is needed (e.g., block feature if settings not optimal),
                // this logic might change.
                onPermissionResult(localHasPermission)
            }
        }

    LaunchedEffect(Unit) {
        // Initial permission check
        when (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)) {
            PackageManager.PERMISSION_GRANTED -> {
                if (!localHasPermission) {
                    localHasPermission = true
                }
                // If permission is already granted, proceed to check location settings.
                // The LaunchedEffect(localHasPermission) will handle this.
                // No need to call onPermissionResult(true) here yet, let settings check complete.
            }

            else -> {
                // Request permission if not granted. The launcher's callback will update localHasPermission.
                requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    LaunchedEffect(localHasPermission) {
        // Handles logic after permission status is known/updated
        if (localHasPermission) {
            // Permission is granted, now check location settings
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MILLIS).build()

            val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)

            val client = LocationServices.getSettingsClient(context)
            val task = client.checkLocationSettings(builder.build())

            task.addOnSuccessListener {
                Logger.d { "Location settings are satisfied." }
                onPermissionResult(true) // Permission granted and settings are good
            }

            task.addOnFailureListener { exception ->
                if (exception is ResolvableApiException) {
                    try {
                        val intentSenderRequest = IntentSenderRequest.Builder(exception.resolution).build()
                        locationSettingsLauncher.launch(intentSenderRequest)
                        // Result of this launch will be handled by locationSettingsLauncher's callback
                    } catch (sendEx: ActivityNotFoundException) {
                        Logger.d { "Error launching location settings resolution ${sendEx.message}." }
                        onPermissionResult(true) // Permission is granted, but settings dialog failed. Proceed.
                    }
                } else {
                    Logger.d { "Location settings are not satisfiable.${exception.message}" }
                    onPermissionResult(true) // Permission is granted, but settings not ideal. Proceed.
                }
            }
        } else {
            // If permission is not granted, report false.
            // This case is primarily handled by the requestLocationPermissionLauncher's callback
            // if the initial state was denied, or if user denies it.
            onPermissionResult(false)
        }
    }
}
