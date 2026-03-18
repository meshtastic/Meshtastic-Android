package org.meshtastic.feature.connections.domain.usecase

import kotlinx.coroutines.flow.Flow
import org.meshtastic.feature.connections.model.DeviceListEntry

/** Platform-specific scanner for USB/Serial devices. */
interface UsbScanner {
    fun scanUsbDevices(): Flow<List<DeviceListEntry.Usb>>
}
