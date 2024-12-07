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