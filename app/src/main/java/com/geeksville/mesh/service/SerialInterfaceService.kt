package com.geeksville.mesh.service

import com.google.android.things.pio.PeripheralManager
import com.google.android.things.pio.UartDevice
import com.google.android.things.pio.UartDeviceCallback

class SerialInterfaceService : InterfaceService() {
    companion object {
        fun findPorts(): List<String> {
            val manager = PeripheralManager.getInstance()
            return manager.uartDeviceList
        }

        private val START1 = 0x94.toByte()
        private val START2 = 0xc3.toByte()
    }

    private var uart: UartDevice? = null

    /** The index of the next byte we are hoping to receive */
    private var rxPtr = 0

    private val callback = object : UartDeviceCallback {
        override fun onUartDeviceDataAvailable(p0: UartDevice): Boolean {

            return uart != null // keep reading until our device goes away
        }

        override fun onUartDeviceError(uart: UartDevice, error: Int) {
            super.onUartDeviceError(uart, error)
        }
    }

    override fun handleSendToRadio(p: ByteArray) {
        uart?.apply {
            val header = ByteArray(4)
            header[0] = START1
            header[1] = START2
            header[2] = (p.size shr 8).toByte()
            header[3] = (p.size and 0xff).toByte()
            write(header, header.size)
            write(p, p.size)
            // flush(UartDevice.FLUSH_OUT) - I don't think we need to stall for htis
        }
    }

    /*
    private fun readerLoop() {
        val scratch = ByteArray(1)
        var ptr = 0
        while (true) { // FIXME wait for someone to ask us to exit, and catch continuation exception
            uart?.apply {
                read(scratch, 1)
                when (ptr) {
                    0 ->
                }
            }
        }
    } */


    override fun onCreate() {
        super.onCreate()

        val port = findPorts()[0]
        val manager = PeripheralManager.getInstance()
        uart = manager.openUartDevice(port)
        uart?.apply {
            setBaudrate(921600)
            registerUartDeviceCallback(callback)
        }
    }

    override fun onDestroy() {
        uart?.close()
        uart = null
        super.onDestroy()
    }
}