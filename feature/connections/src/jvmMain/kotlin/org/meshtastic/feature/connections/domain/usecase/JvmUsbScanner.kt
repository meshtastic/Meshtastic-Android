package org.meshtastic.feature.connections.domain.usecase

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import org.koin.core.annotation.Single
import org.meshtastic.core.network.SerialTransport
import org.meshtastic.feature.connections.model.DeviceListEntry
import org.meshtastic.feature.connections.model.JvmUsbDeviceData
import kotlin.coroutines.coroutineContext

@Single
class JvmUsbScanner : UsbScanner {
    override fun scanUsbDevices(): Flow<List<DeviceListEntry.Usb>> = flow {
        while (coroutineContext.isActive) {
            val ports = SerialTransport.getAvailablePorts().map { portName ->
                DeviceListEntry.Usb(
                    usbData = JvmUsbDeviceData(portName),
                    name = portName,
                    fullAddress = "s$portName",
                    bonded = true, // Desktop serial ports don't need Android USB permission bonding
                    node = null
                )
            }
            emit(ports)
            delay(2000L) // Poll every 2 seconds
        }
    }
}
