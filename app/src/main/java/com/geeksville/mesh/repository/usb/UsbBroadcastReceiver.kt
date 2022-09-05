package com.geeksville.mesh.repository.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.util.exceptionReporter
import javax.inject.Inject

/**
 * A helper class to call onChanged when bluetooth is enabled or disabled or when permissions are
 * changed.
 */
class UsbBroadcastReceiver @Inject constructor(
    private val usbRepository: UsbRepository
) : BroadcastReceiver(), Logging {
    // Can be used for registering
    internal val intentFilter get() = IntentFilter().apply {
        addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
    }

    override fun onReceive(context: Context, intent: Intent) = exceptionReporter {
        val deviceName: String = intent.getParcelableExtra<UsbDevice?>(UsbManager.EXTRA_DEVICE)?.deviceName ?: "unknown"
        when (intent.action) {
            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                debug("USB device '$deviceName' was detached")
                usbRepository.refreshState()
            }
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                debug("USB device '$deviceName' was attached")
                usbRepository.refreshState()
            }
            UsbManager.EXTRA_PERMISSION_GRANTED -> {
                debug("USB device '$deviceName' was granted permission")
                usbRepository.refreshState()
            }
        }
    }
}