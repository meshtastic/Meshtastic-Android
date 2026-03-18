package org.meshtastic.core.network

import org.meshtastic.core.repository.RadioTransport

/**
 * JVM-specific implementation of [RadioTransport] using jSerialComm.
 * Currently a stub for testing architecture.
 */
class SerialTransport : RadioTransport {
    
    override fun handleSendToRadio(p: ByteArray) {
        // Stub implementation
    }

    override fun keepAlive() {
        // Stub implementation
    }

    override fun close() {
        // Stub implementation
    }
}
