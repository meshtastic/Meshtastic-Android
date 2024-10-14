package com.geeksville.mesh.repository.radio

import android.hardware.usb.UsbManager
import com.geeksville.mesh.repository.usb.UsbRepository
import com.hoho.android.usbserial.driver.UsbSerialDriver
import javax.inject.Inject

/**
 * Serial/USB interface backend implementation.
 */
class SerialInterfaceSpec @Inject constructor(
    private val factory: SerialInterfaceFactory,
    private val usbManager: dagger.Lazy<UsbManager>,
    private val usbRepository: UsbRepository,
) : InterfaceSpec<SerialInterface> {
    override fun createInterface(rest: String): SerialInterface {
        return factory.create(rest)
    }

    override fun addressValid(
        rest: String
    ): Boolean {
        usbRepository.serialDevicesWithDrivers.value.filterValues {
            usbManager.get().hasPermission(it.device)
        }
        findSerial(rest)?.let { d ->
            return usbManager.get().hasPermission(d.device)
        }
        return false
    }

    internal fun findSerial(rest: String): UsbSerialDriver? {
        val deviceMap = usbRepository.serialDevicesWithDrivers.value
        return if (deviceMap.containsKey(rest)) {
            deviceMap[rest]!!
        } else {
            deviceMap.map { (_, driver) -> driver }.firstOrNull()
        }
    }
}
