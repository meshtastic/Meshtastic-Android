package com.geeksville.mesh.android

import android.Manifest
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.location.LocationManager
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import com.geeksville.mesh.MainActivity
import com.geeksville.mesh.R

/**
 * @return null on platforms without a BlueTooth driver (i.e. the emulator)
 */
val Context.bluetoothManager: BluetoothManager?
    get() = getSystemService(Context.BLUETOOTH_SERVICE).takeIf { hasBluetoothPermission() } as? BluetoothManager?

val Context.deviceManager: CompanionDeviceManager?
    get() {
        if (GeeksvilleApplication.currentActivity is MainActivity) {
            val activity = GeeksvilleApplication.currentActivity
            if (hasCompanionDeviceApi()) return activity?.getSystemService(Context.COMPANION_DEVICE_SERVICE) as? CompanionDeviceManager?
        }
        return null
    }

val Context.usbManager: UsbManager get() = requireNotNull(getSystemService(Context.USB_SERVICE) as? UsbManager?) { "USB_SERVICE is not available"}

val Context.notificationManager: NotificationManager get() = requireNotNull(getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager?)

val Context.locationManager: LocationManager get() = requireNotNull(getSystemService(Context.LOCATION_SERVICE) as? LocationManager?)

/**
 * @return true if CompanionDeviceManager API is present
 */
fun Context.hasCompanionDeviceApi(): Boolean =
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
        packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)
    else false

/**
 * @return true if the device has a GPS receiver
 */
fun Context.hasGps(): Boolean = locationManager.allProviders.contains(LocationManager.GPS_PROVIDER)

/**
 * @return true if the device has a GPS receiver and it is disabled (location turned off)
 */
fun Context.gpsDisabled(): Boolean =
    if (hasGps()) !locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) else false

/**
 * return the text string of the permissions missing
 */
val Context.permissionMissing: String
    get() = if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
        getString(R.string.permission_missing)
    } else {
        getString(R.string.permission_missing_31)
    }

/**
 * return a list of the permissions we don't have
 */
fun Context.getMissingPermissions(perms: List<String>): Array<String> = perms.filter {
    ContextCompat.checkSelfPermission(
        this,
        it
    ) != PackageManager.PERMISSION_GRANTED
}.toTypedArray()

/**
 * Bluetooth permissions (or empty if we already have what we need)
 */
fun Context.getBluetoothPermissions(): Array<String> {
    val perms = mutableListOf<String>()

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        perms.add(Manifest.permission.BLUETOOTH_SCAN)
        perms.add(Manifest.permission.BLUETOOTH_CONNECT)
    } else if (!hasCompanionDeviceApi()) {
        perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    return getMissingPermissions(perms)
}

/** @return true if the user already has Bluetooth connect permission */
fun Context.hasBluetoothPermission() = getBluetoothPermissions().isEmpty()

/**
 * Camera permission (or empty if we already have what we need)
 */
fun Context.getCameraPermissions(): Array<String> {
    val perms = mutableListOf(Manifest.permission.CAMERA)

    return getMissingPermissions(perms)
}

/** @return true if the user already has camera permission */
fun Context.hasCameraPermission() = getCameraPermissions().isEmpty()

/**
 * Location permission (or empty if we already have what we need)
 */
fun Context.getLocationPermissions(): Array<String> {
    val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)

    return getMissingPermissions(perms)
}

/** @return true if the user already has location permission */
fun Context.hasLocationPermission() = getLocationPermissions().isEmpty()

/**
 * A list of missing background location permissions (or empty if we already have what we need)
 */
fun Context.getBackgroundPermissions(): Array<String> {
    val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)

    if (android.os.Build.VERSION.SDK_INT >= 29) // only added later
        perms.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    return getMissingPermissions(perms)
}

/** @return true if the user already has background location permission */
fun Context.hasBackgroundPermission() = getBackgroundPermissions().isEmpty()
