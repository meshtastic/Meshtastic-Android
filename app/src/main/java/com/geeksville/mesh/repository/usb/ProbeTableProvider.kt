package com.geeksville.mesh.repository.usb

import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.ProbeTable
import com.hoho.android.usbserial.driver.UsbSerialProber
import dagger.Reusable
import javax.inject.Inject
import javax.inject.Provider

/**
 * Creates a probe table for the USB driver.  This augments the default device-to-driver
 * mappings with additional known working configurations.  See this package's README for
 * more info.
 */
@Reusable
class ProbeTableProvider @Inject constructor() : Provider<ProbeTable> {
    override fun get(): ProbeTable {
        return UsbSerialProber.getDefaultProbeTable().apply {
            // RAK 4631:
            addProduct(9114, 32809, CdcAcmSerialDriver::class.java)
            // LilyGo TBeam v1.1:
            addProduct(6790, 21972, CdcAcmSerialDriver::class.java)
        }
    }
}