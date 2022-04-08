package com.geeksville.mesh.service

import android.content.Context
import android.hardware.usb.UsbManager
import com.geeksville.android.Logging
import com.geeksville.mesh.android.usbManager
import com.geeksville.mesh.repository.usb.UsbRepository
import com.geeksville.util.ignoreException
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.util.SerialInputOutputManager


/**
 * An interface that assumes we are talking to a meshtastic device via USB serial
 */
class SerialInterface(
    service: RadioInterfaceService,
    private val usbRepository: UsbRepository,
    private val address: String) :
    StreamInterface(service), Logging, SerialInputOutputManager.Listener {
    companion object : Logging, InterfaceFactory('s') {
        override fun createInterface(
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
            deviceMap.forEach { (path, _) ->
                debug("Found serial port: $path")
            }
            return if (deviceMap.containsKey(rest)) {
                deviceMap[rest]!!
            } else {
                deviceMap.map { (_, driver) -> driver }.firstOrNull()
            }
        }
    }

    private var uart: UsbSerialDriver? = null
    private var ioManager: SerialInputOutputManager? = null

    init {
        connect()
    }

    /** Tell MeshService our device has gone away, but wait for it to come back
     *
     * @param waitForStopped if true we should wait for the manager to finish - must be false if called from inside the manager callbacks
     *  */
    override fun onDeviceDisconnect(waitForStopped: Boolean) {
        ignoreException {
            ioManager?.let {
                debug("USB device disconnected, but it might come back")
                it.stop()

                // Allow a short amount of time for the manager to quit (so the port can be cleanly closed)
                if (waitForStopped) {
                    val msecSleep = 50L
                    var numTries = 1000 / msecSleep
                    while (it.state != SerialInputOutputManager.State.STOPPED && numTries > 0) {
                        debug("Waiting for USB manager to stop...")
                        Thread.sleep(msecSleep)
                        numTries -= 1
                    }
                }

                ioManager = null
            }
        }

        ignoreException {
            uart?.apply {
                ports[0].close() // This will cause the reader thread to exit

                uart = null
            }
        }

        super.onDeviceDisconnect(waitForStopped)
    }

    override fun connect() {
        val manager = service.getSystemService(Context.USB_SERVICE) as UsbManager
        val device = findSerial(usbRepository, address)

        if (device != null) {
            info("Opening $device")
            val connection =
                manager.openDevice(device.device) // This can fail with "Control Transfer failed" if port was aleady open
            if (connection == null) {
                // FIXME add UsbManager.requestPermission(device, ..) handling to activity
                errormsg("Need permissions for port")
            } else {
                val port = device.ports[0] // Most devices have just one port (port 0)

                port.open(connection)
                port.setParameters(921600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                uart = device

                debug("Starting serial reader thread")
                val io = SerialInputOutputManager(port, this)
                io.readTimeout = 200 // To save battery we only timeout ever so often
                ioManager = io

                val thread = Thread(io)
                thread.isDaemon = true
                thread.priority = Thread.MAX_PRIORITY
                thread.name = "serial reader"
                thread.start() // No need to keep reference to thread around, we quit by asking the ioManager to quit

                // Now tell clients they can (finally use the api)
                super.connect()
            }
        } else {
            errormsg("Can't find device")
        }
    }

    override fun sendBytes(p: ByteArray) {
        ioManager?.apply {
            writeAsync(p)
        }
    }

    /**
     * Called when [SerialInputOutputManager.run] aborts due to an error.
     */
    override fun onRunError(e: java.lang.Exception) {
        errormsg("Serial error: $e")

        onDeviceDisconnect(false)
    }

    /**
     * Called when new incoming data is available.
     */
    override fun onNewData(data: ByteArray) {
        data.forEach(::readChar)
    }
}