package com.geeksville.mesh.android

import android.Manifest
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.geeksville.mesh.service.BluetoothInterface

/**
 * @return null on platforms without a BlueTooth driver (i.e. the emulator)
 */
val Context.bluetoothManager: BluetoothManager? get() = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager?

val Context.usbManager: UsbManager get() = requireNotNull(getSystemService(Context.USB_SERVICE) as? UsbManager?) { "USB_SERVICE is not available"}

val Context.notificationManager: NotificationManager get() = requireNotNull(getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager?)

/**
 * return a list of the permissions we don't have
 */
fun Context.getMissingPermissions(perms: List<String>) = perms.filter {
    ContextCompat.checkSelfPermission(
        this,
        it
    ) != PackageManager.PERMISSION_GRANTED
}

/**
 * Bluetooth connect permissions (or empty if we already have what we need)
 */
fun Context.getConnectPermissions(): List<String> {
    val perms = mutableListOf<String>()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        perms.add(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        perms.add(Manifest.permission.BLUETOOTH)
    }

    return getMissingPermissions(perms)
}

/** @return true if the user already has Bluetooth connect permission */
fun Context.hasConnectPermission() = getConnectPermissions().isEmpty()

/**
 * Bluetooth scan/discovery permissions (or empty if we already have what we need)
 */
fun Context.getScanPermissions(): List<String> {
    val perms = mutableListOf<String>()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        perms.add(Manifest.permission.BLUETOOTH_SCAN)
    } else if (!BluetoothInterface.hasCompanionDeviceApi(this)) {
        perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
        perms.add(Manifest.permission.BLUETOOTH_ADMIN)
    }

    return getMissingPermissions(perms)
}

/** @return true if the user already has Bluetooth scan/discovery permission */
fun Context.hasScanPermission() = getScanPermissions().isEmpty()

/**
 * Camera permission (or empty if we already have what we need)
 */
fun Context.getCameraPermissions(): List<String> {
    val perms = mutableListOf(Manifest.permission.CAMERA)

    return getMissingPermissions(perms)
}

/** @return true if the user already has camera permission */
fun Context.hasCameraPermission() = getCameraPermissions().isEmpty()

/**
 * Location permission (or empty if we already have what we need)
 */
fun Context.getLocationPermissions(): List<String> {
    val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)

    return getMissingPermissions(perms)
}

/** @return true if the user already has location permission */
fun Context.hasLocationPermission() = getLocationPermissions().isEmpty()

/**
 * A list of missing background location permissions (or empty if we already have what we need)
 */
fun Context.getBackgroundPermissions(): List<String> {
    val perms = mutableListOf<String>()

    if (Build.VERSION.SDK_INT >= 29) // only added later
        perms.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    return getMissingPermissions(perms)
}

/** @return true if the user already has background location permission */
fun Context.hasBackgroundPermission() = getBackgroundPermissions().isEmpty()
