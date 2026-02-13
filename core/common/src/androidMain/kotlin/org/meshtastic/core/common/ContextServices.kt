/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.core.common

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Checks if the device has a GPS receiver. */
fun Context.hasGps(): Boolean {
    val lm = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    return lm?.allProviders?.contains(LocationManager.GPS_PROVIDER) == true
}

/** Checks if the device has a GPS receiver and it is currently disabled. */
fun Context.gpsDisabled(): Boolean {
    val lm = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
    return if (lm.allProviders.contains(LocationManager.GPS_PROVIDER)) {
        !lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
    } else {
        false
    }
}

/**
 * Determines the list of Bluetooth permissions that are currently missing. Internal helper for
 * [hasBluetoothPermission].
 *
 * For Android S (API 31) and above, this includes [Manifest.permission.BLUETOOTH_SCAN] and
 * [Manifest.permission.BLUETOOTH_CONNECT]. For older versions, it includes [Manifest.permission.ACCESS_FINE_LOCATION]
 * as it is required for Bluetooth scanning.
 *
 * @return Array of missing Bluetooth permission strings. Empty if all are granted.
 */
private fun Context.getBluetoothPermissions(): Array<String> {
    val requiredPermissions = mutableListOf<String>()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
        requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        // ACCESS_FINE_LOCATION is required for Bluetooth scanning on pre-S devices.
        requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    return requiredPermissions
        .filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        .toTypedArray()
}

/** Checks if all necessary Bluetooth permissions have been granted. */
fun Context.hasBluetoothPermission(): Boolean = getBluetoothPermissions().isEmpty()

/** @return true if the user already has location permission (ACCESS_FINE_LOCATION). */
fun Context.hasLocationPermission(): Boolean {
    val perms = listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    return perms.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
}

/**
 * Extension for Context to register a BroadcastReceiver in a compatible way across Android versions.
 *
 * @param receiver The receiver to register.
 * @param filter The intent filter.
 * @param flag The export flag (defaults to [ContextCompat.RECEIVER_EXPORTED]).
 */
fun Context.registerReceiverCompat(
    receiver: BroadcastReceiver,
    filter: IntentFilter,
    flag: Int = ContextCompat.RECEIVER_EXPORTED,
) {
    ContextCompat.registerReceiver(this, receiver, filter, flag)
}
