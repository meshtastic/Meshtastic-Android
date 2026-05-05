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
package org.meshtastic.desktop.radio

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.meshtastic.core.data.radio.RadioClientAccessor
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.repository.RadioPrefs
import org.meshtastic.core.service.SdkClientLifecycle
import org.meshtastic.sdk.AutoReconnectConfig
import org.meshtastic.sdk.RadioClient
import org.meshtastic.sdk.RadioTransport
import org.meshtastic.sdk.storage.sqldelight.SqlDelightStorageProvider
import org.meshtastic.sdk.transport.serial.JvmSerialPorts
import org.meshtastic.sdk.transport.tcp.TcpTransport

/**
 * Desktop (JVM) implementation of [RadioClientAccessor].
 *
 * Supports BLE (Kable JVM — macOS/Windows/Linux), TCP, and Serial (jSerialComm) transports.
 * Storage uses file-system backed SqlDelightStorageProvider with a platform-appropriate data dir.
 *
 * Registered manually in [desktopPlatformStubsModule] — do NOT add @Single to avoid
 * double-registration with the @ComponentScan in DesktopDiModule.
 */
class DesktopRadioClientProvider(
    private val radioPrefs: RadioPrefs,
) : RadioClientAccessor, SdkClientLifecycle {

    private val _client = MutableStateFlow<RadioClient?>(null)
    override val client: StateFlow<RadioClient?> = _client.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    /**
     * Tear down the existing client (if any) and build + connect a new one using the current
     * saved radio address from [RadioPrefs].
     *
     * Supports BLE (`x` prefix), TCP (`t` prefix, format `tHOST:PORT`), and Serial (`s` prefix).
     */
    suspend fun rebuildAndConnect() = mutex.withLock {
        val rawAddress = radioPrefs.devAddr.value
            ?: run {
                Logger.w { "DesktopRadioClientProvider: no saved device address — skipping connect" }
                return@withLock
            }

        val interfaceChar = rawAddress.firstOrNull() ?: run {
            Logger.w { "DesktopRadioClientProvider: empty address — skipping connect" }
            return@withLock
        }
        val addressPayload = rawAddress.substring(1)

        val transport: RadioTransport = when (InterfaceId.forIdChar(interfaceChar)) {
            InterfaceId.BLUETOOTH -> {
                // BLE on Desktop requires a Kable Peripheral (obtained via Scanner in the connections UI).
                // Direct MAC-address construction is Android-only. Desktop BLE is handled by the
                // connections feature via DesktopRadioTransportFactory; skip SDK client for BLE for now.
                Logger.w { "DesktopRadioClientProvider: BLE not yet supported via SDK — use connections UI" }
                return@withLock
            }

            InterfaceId.TCP -> {
                val (host, port) = parseTcpAddress(addressPayload)
                Logger.i { "DesktopRadioClientProvider: building TCP transport for $host:$port" }
                TcpTransport(host, port)
            }

            InterfaceId.SERIAL -> {
                Logger.i { "DesktopRadioClientProvider: building Serial transport for $addressPayload" }
                JvmSerialPorts.open(addressPayload)
            }

            InterfaceId.MOCK, InterfaceId.NOP, null -> {
                Logger.w { "DesktopRadioClientProvider: unsupported transport '$interfaceChar' ($rawAddress)" }
                return@withLock
            }
        }

        val old = _client.value
        _client.value = null
        old?.let { runCatching { it.disconnect() }.onFailure { e -> Logger.w(e) { "disconnect old" } } }

        val newClient = RadioClient.Builder()
            .transport(transport)
            .storage(SqlDelightStorageProvider(baseDir = storageDir()))
            .autoReconnect(AutoReconnectConfig())
            .build()

        _client.value = newClient
        newClient.connect()

        Logger.i { "DesktopRadioClientProvider: connected via ${InterfaceId.forIdChar(interfaceChar)}" }
    }

    override fun rebuildAndConnectAsync() {
        scope.launch {
            runCatching { rebuildAndConnect() }
                .onFailure { e -> Logger.e(e) { "DesktopRadioClientProvider: connect failed" } }
        }
    }

    override fun disconnect() {
        scope.launch {
            mutex.withLock {
                val c = _client.value ?: return@withLock
                _client.value = null
                runCatching { c.disconnect() }.onFailure { e -> Logger.w(e) { "disconnect" } }
            }
        }
    }

    companion object {
        private const val DEFAULT_TCP_PORT = 4403

        private fun parseTcpAddress(payload: String): Pair<String, Int> {
            val parts = payload.split(":")
            val host = parts[0]
            val port = parts.getOrNull(1)?.toIntOrNull() ?: DEFAULT_TCP_PORT
            return host to port
        }

        /** Platform-appropriate storage directory for SDK state (channels, nodeDB, etc.). */
        private fun storageDir(): String {
            val os = System.getProperty("os.name", "").lowercase()
            val home = System.getProperty("user.home", ".")
            return when {
                os.contains("mac") -> "$home/Library/Application Support/Meshtastic/sdk"
                os.contains("win") -> "${System.getenv("APPDATA") ?: home}/Meshtastic/sdk"
                else -> "${System.getenv("XDG_DATA_HOME") ?: "$home/.local/share"}/meshtastic/sdk"
            }
        }
    }
}
