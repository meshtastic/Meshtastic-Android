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

import android.app.Application
import android.content.Context
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.ProbeTable
import com.hoho.android.usbserial.driver.UsbSerialProber
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface UsbRepositoryModule {
    companion object {
        @Provides
        fun provideUsbManager(application: Application): UsbManager? =
            application.getSystemService(Context.USB_SERVICE) as UsbManager?

        @Provides
        fun provideProbeTable(provider: ProbeTableProvider): ProbeTable = provider.get()

        @Provides
        fun provideUsbSerialProber(probeTable: ProbeTable): UsbSerialProber = UsbSerialProber(probeTable)
    }
}