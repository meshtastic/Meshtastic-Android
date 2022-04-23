package com.geeksville.mesh.repository.radio

import android.content.Context
import com.geeksville.android.Logging
import com.geeksville.mesh.android.usbManager
import com.geeksville.mesh.repository.usb.SerialConnection
import com.geeksville.mesh.repository.usb.SerialConnectionListener
import com.geeksville.mesh.repository.usb.UsbRepository
import com.hoho.android.usbserial.driver.UsbSerialDriver
import java.util.concurrent.atomic.AtomicReference

/**
 * An interface that assumes we are talking to a meshtastic device via USB serial
 */
class SerialInterface(
    service: RadioInterfaceService,
    private val usbRepository: UsbRepository,
    private val address: String) :
    StreamInterface(service), Logging {
    companion object : Logging, InterfaceFactory('s') {
        override fun createInterface(
            context: Context,
            service: RadioInterfaceService,
            usbRepository: UsbRepository,
            rest: String
        ): IRadioInterface = SerialInterface(service, usbRepository, rest)

        init {
            registerFactory()
        }

        /**
         * according to https://stackoverflow.com/questions/12388914/usb-device-access-pop-up-suppression/15151075#15151075
         * we should never ask for USB permissions ourselves, instead we should rely on the external dialog printed by the system.  If
         * we do that the system will remember we have accesss
         */
        const val assumePermission = false

        fun toInterfaceName(deviceName: String) = "s$deviceName"

        override fun addressValid(
            context: Context,
            usbRepository: UsbRepository,
            rest: String
        ): Boolean {
            usbRepository.serialDevicesWithDrivers.value.filterValues {
                assumePermission || context.usbManager.hasPermission(it.device)
            }
            findSerial(usbRepository, rest)?.let { d ->
                return assumePermission || context.usbManager.hasPermission(d.device)
            }
            return false
        }

        private fun findSerial(usbRepository: UsbRepository, rest: String): UsbSerialDriver? {
            val deviceMap = usbRepository.serialDevicesWithDrivers.value
            return if (deviceMap.containsKey(rest)) {
                deviceMap[rest]!!
            } else {
                deviceMap.map { (_, driver) -> driver }.firstOrNull()
            }
        }
    }

    private var connRef = AtomicReference<SerialConnection?>()

    init {
        connect()
    }

    override fun onDeviceDisconnect(waitForStopped: Boolean) {
        connRef.get()?.close(waitForStopped)
        super.onDeviceDisconnect(waitForStopped)
    }

    override fun connect() {
        val device = findSerial(usbRepository, address)
        if (device == null) {
            errormsg("Can't find device")
        } else {
            info("Opening $device")
            val onConnect: () -> Unit = {  super.connect() }
            usbRepository.createSerialConnection(device, object : SerialConnectionListener {
                override fun onMissingPermission() {
                    errormsg("Need permissions for port")
                }

                override fun onConnected() {
                    onConnect.invoke()
                }

                override fun onDataReceived(bytes: ByteArray) {
                    debug("Received ${bytes.size} byte(s)")
                    bytes.forEach(::readChar)
                }

                override fun onDisconnected(thrown: Exception?) {
                    thrown?.let { e ->
                        errormsg("Serial error: $e")
                    }
                    debug("$device disconnected")
                    onDeviceDisconnect(false)
                }
            }).also { conn ->
                connRef.set(conn)
                conn.connect()
            }
        }
    }

    override fun sendBytes(p: ByteArray) {
        connRef.get()?.sendBytes(p)
    }
}