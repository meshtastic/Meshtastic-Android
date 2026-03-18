package org.meshtastic.feature.connections.model

/** JVM-specific implementation of [UsbDeviceData] wrapping the serial port name. */
data class JvmUsbDeviceData(val portName: String) : UsbDeviceData
