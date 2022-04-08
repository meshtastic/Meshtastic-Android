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