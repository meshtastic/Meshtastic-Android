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

package com.geeksville.mesh.repository.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import co.touchlab.kermit.Logger
import com.geeksville.mesh.util.exceptionReporter
import com.geeksville.mesh.util.getParcelableExtraCompat
import javax.inject.Inject

/** A helper class to call onChanged when bluetooth is enabled or disabled or when permissions are changed. */
class UsbBroadcastReceiver @Inject constructor(private val usbRepository: UsbRepository) : BroadcastReceiver() {
    // Can be used for registering
    internal val intentFilter
        get() =
            IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            }

    override fun onReceive(context: Context, intent: Intent) = exceptionReporter {
        val device: UsbDevice? = intent.getParcelableExtraCompat(UsbManager.EXTRA_DEVICE)
        val deviceName: String = device?.deviceName ?: "unknown"

        when (intent.action) {
            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                Logger.d { "USB device '$deviceName' was detached" }
                usbRepository.refreshState()
            }
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                Logger.d { "USB device '$deviceName' was attached" }
                usbRepository.refreshState()
            }
            UsbManager.EXTRA_PERMISSION_GRANTED -> {
                Logger.d { "USB device '$deviceName' was granted permission" }
                usbRepository.refreshState()
            }
        }
    }
}
