/*
 * Copyright (c) 2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.meshtastic.feature.firmware.ota

import co.touchlab.kermit.Logger
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.SocketAddress
import io.ktor.network.sockets.aSocket
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.readByteArray
import org.meshtastic.core.common.util.ioDispatcher
import org.meshtastic.core.common.util.safeCatching

/**
 * Listens for the ESP32 OTA loader's UDP discovery broadcast so the host can learn the device's post-reboot IP.
 *
 * After rebooting into OTA mode the loader emits a UDP broadcast to `255.255.255.255:[port]` every ~1 second. DHCP may
 * assign a different IP in OTA mode than in normal operation, so connecting to the previously-known IP can fail; this
 * discovery resolves the actual address.
 */
internal object WifiOtaDiscovery {
    /**
     * Listens for the OTA loader's UDP discovery broadcast on [port] and returns the sender's IP address. Returns
     * `null` on timeout, bind failure, or any other receive error — callers fall back to the original IP.
     */
    suspend fun discoverOtaDevice(
        port: Int = WifiOtaTransport.DEFAULT_PORT,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): String? = withContext(ioDispatcher) {
        // NOTE: No MulticastLock acquired — that is an Android-only API and this is commonMain. All-ones
        // limited broadcasts (255.255.255.255) are typically delivered without it; if a specific device filters
        // them, add an expect/actual multicast-lock wrapper and acquire it for the duration of [receive].
        safeCatching {
            // ponytail: var accumulator + loop-on-condition. withTimeoutOrNull's block type comes from its
            // terminal expression; an infinite `while(true){...}` whose body ends in Logger.d (Unit) makes
            // the whole block Unit, which clashes with any explicit <T> param. Accumulating into `discovered`
            // gives the block a concrete String? terminal and lets inference handle the rest.
            withTimeoutOrNull(timeoutMs) {
                val selector = SelectorManager(ioDispatcher)
                var discovered: String? = null
                try {
                    val socket = aSocket(selector).udp().bind(InetSocketAddress("0.0.0.0", port))
                    try {
                        Logger.i { "WiFi OTA: Listening for OTA device discovery broadcast on port $port" }
                        while (discovered == null) {
                            val datagram = socket.receive()
                            val candidate = senderHost(datagram.address)
                            val payload = datagram.packet.readByteArray()
                            if (candidate != null && isOtaDiscoveryBeacon(payload)) {
                                Logger.i { "WiFi OTA: Discovered OTA device broadcast" }
                                discovered = candidate
                            } else {
                                Logger.d { "WiFi OTA: Ignoring non-Meshtastic OTA discovery datagram" }
                            }
                        }
                    } finally {
                        socket.close()
                    }
                } finally {
                    selector.close()
                }
                discovered
            }
        }
            .getOrNull()
    }

    @Suppress("ReturnCount") // early-out paths for different address shapes
    internal fun senderHost(address: SocketAddress): String? {
        val sock = address as? InetSocketAddress ?: return null
        // InetSocketAddress.hostname may trigger a reverse-DNS lookup; use only the raw IPv4 bytes returned by
        // resolveAddress() (4 bytes for IPv4 — the only shape ESP32 OTA loader broadcasts).
        val bytes = sock.resolveAddress()
        if (bytes != null && bytes.size == IPV4_ADDRESS_BYTES) {
            return bytes.joinToString(".") { (it.toInt() and BYTE_MASK).toString() }
        }
        return null
    }

    internal fun isOtaDiscoveryBeacon(payload: ByteArray): Boolean {
        val message = payload.decodeToString().trim()
        return OTA_DISCOVERY_PATTERN.matches(message)
    }

    // esp32-unified-ota broadcasts "<deviceName> <version>", where deviceName is "Meshtastic_" plus 4 MAC hex digits.
    private val OTA_DISCOVERY_PATTERN = Regex("""^Meshtastic_[0-9A-Fa-f]{4}\s+\S{1,32}$""")
    private const val IPV4_ADDRESS_BYTES = 4
    private const val BYTE_MASK = 0xFF
    private const val DEFAULT_TIMEOUT_MS = 3_000L
}
