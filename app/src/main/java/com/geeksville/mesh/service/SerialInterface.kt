package com.geeksville.mesh.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.geeksville.android.Logging
import com.geeksville.mesh.android.usbManager
import com.geeksville.util.exceptionReporter
import com.geeksville.util.ignoreException
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager


/**
 * An interface that assumes we are talking to a meshtastic device via USB serial
 */
class SerialInterface(service: RadioInterfaceService, private val address: String) :
    StreamInterface(service), Logging, SerialInputOutputManager.Listener {
    companion object : Logging, InterfaceFactory('s') {
        override fun createInterface(
            service: RadioInterfaceService,
            rest: String
        ): IRadioInterface = SerialInterface(service, rest)

        init {
            registerFactory()
        }

        /**
         * according to https://stackoverflow.com/questions/12388914/usb-device-access-pop-up-suppression/15151075#15151075
         * we should never ask for USB permissions ourselves, instead we should rely on the external dialog printed by the system.  If
         * we do that the system will remember we have accesss
         */
        const val assumePermission = true

        fun toInterfaceName(deviceName: String) = "s$deviceName"

        fun findDrivers(context: Context): List<UsbSerialDriver> {
            val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(context.usbManager)
            val devices = drivers.map { it.device }
            devices.forEach { d ->
                debug("Found serial port ${d.deviceName}")
            }
            return drivers
        }

        override fun addressValid(context: Context, rest: String): Boolean {
            findSerial(context, rest)?.let { d ->
                return assumePermission || context.usbManager.hasPermission(d.device)
            }
            return false
        }

        private fun findSerial(context: Context, rest: String): UsbSerialDriver? {
            val drivers = findDrivers(context)

            return if (drivers.isEmpty())
                null
            else  // Open a connection to the first available driver.
                drivers[0] // FIXME, instead we should find by name
        }
    }

    private var uart: UsbSerialDriver? = null
    private var ioManager: SerialInputOutputManager? = null

    private var usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = exceptionReporter {

            if (UsbManager.ACTION_USB_DEVICE_DETACHED == intent.action) {
                debug("A USB device was detached")
                val device: UsbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)!!
                if (uart?.device == device)
                    onDeviceDisconnect(true)
            }

            if (UsbManager.ACTION_USB_DEVICE_ATTACHED == intent.action) {
                debug("attaching USB")
                val device: UsbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)!!
                if (assumePermission || context.usbManager.hasPermission(device)) {
                    // reinit the port from scratch and reopen
                    onDeviceDisconnect(true)
                    connect()
                } else {
                    warn("We don't have permissions for this USB device")
                }
            }
        }
    }

    init {
        val filter = IntentFilter()
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        service.registerReceiver(usbReceiver, filter)

        connect()
    }

    override fun close() {
        service.unregisterReceiver(usbReceiver)
        super.close()
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
        val device = findSerial(service, address)

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