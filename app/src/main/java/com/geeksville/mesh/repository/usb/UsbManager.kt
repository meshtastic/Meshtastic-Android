package com.geeksville.mesh.repository.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.geeksville.mesh.util.PendingIntentCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val ACTION_USB_PERMISSION = "com.geeksville.mesh.USB_PERMISSION"

internal fun UsbManager.requestPermission(
    context: Context,
    device: UsbDevice,
): Flow<Boolean> = callbackFlow {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            trySend(granted)
            close()
        }
    }
    val permissionIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(ACTION_USB_PERMISSION),
        PendingIntentCompat.FLAG_MUTABLE
    )
    val filter = IntentFilter(ACTION_USB_PERMISSION)
    context.registerReceiver(receiver, filter)
    requestPermission(device, permissionIntent)

    awaitClose { context.unregisterReceiver(receiver) }
}
