/*
 * Copyright (c) 2024 Meshtastic LLC
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

package com.geeksville.mesh.repository.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.app.PendingIntentCompat
import com.geeksville.mesh.util.registerReceiverCompat
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
            if (ACTION_USB_PERMISSION == intent.action) {
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                trySend(granted)
                close()
            }
        }
    }
    val permissionIntent = PendingIntentCompat.getBroadcast(
        context,
        0,
        Intent(ACTION_USB_PERMISSION).apply { `package` = context.packageName },
        0,
        true
    )
    val filter = IntentFilter(ACTION_USB_PERMISSION)
    context.registerReceiverCompat(receiver, filter)
    requestPermission(device, permissionIntent)

    awaitClose { context.unregisterReceiver(receiver) }
}
