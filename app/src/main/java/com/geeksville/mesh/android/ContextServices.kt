package com.geeksville.mesh.android

import android.Manifest
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat

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