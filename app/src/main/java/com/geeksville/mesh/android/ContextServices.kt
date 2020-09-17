package com.geeksville.mesh.android

import android.bluetooth.BluetoothManager
import android.content.Context
import android.hardware.usb.UsbManager

/**
 * @return null on platforms without a BlueTooth driver (i.e. the emulator)
 */
val Context.bluetoothManager: BluetoothManager? get() = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager?

val Context.usbManager: UsbManager get() = requireNotNull(getSystemService(Context.USB_SERVICE) as? UsbManager) { "USB_SERVICE is not available"}
