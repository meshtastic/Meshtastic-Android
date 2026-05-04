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
package org.meshtastic.app.radio

import android.content.Context
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
import org.koin.core.annotation.Single
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.repository.RadioPrefs
import org.meshtastic.core.service.SdkClientLifecycle
import org.meshtastic.sdk.AutoReconnectConfig
import org.meshtastic.sdk.RadioClient
import org.meshtastic.sdk.RadioTransport
import org.meshtastic.sdk.storage.sqldelight.SqlDelightStorageProvider
import org.meshtastic.sdk.transport.ble.BleTransport
import org.meshtastic.sdk.transport.serial.AndroidSerialPorts
import org.meshtastic.sdk.transport.tcp.TcpTransport

/**
 * Holds the active [RadioClient] and orchestrates connect/disconnect lifecycle.
 *
 * This is the SDK integration point for the POC. The [RadioClient] is exposed as a [StateFlow] so ViewModels and the
 * service can react to connection changes with `flatMapLatest`.
 */
@Single(binds = [SdkClientLifecycle::class])
class RadioClientProvider(private val context: Context, private val radioPrefs: RadioPrefs) : SdkClientLifecycle {
    private val _client = MutableStateFlow<RadioClient?>(null)

    /** Active [RadioClient], or `null` when disconnected or between connections. */
    val client: StateFlow<RadioClient?> = _client.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    /**
     * Tear down the existing client (if any) and build + connect a new one using the current saved radio address from
     * [RadioPrefs].
     *
     * Supports BLE (`x` prefix), TCP (`t` prefix, format `tHOST:PORT`), and Serial (`s` prefix, format `sPORTNAME`).
     */
    suspend fun rebuildAndConnect() = mutex.withLock {
        val rawAddress =
            radioPrefs.devAddr.value
                ?: run {
                    Logger.w { "RadioClientProvider: no saved device address — skipping connect" }
                    return@withLock
                }

        val interfaceChar = rawAddress.firstOrNull() ?: run {
            Logger.w { "RadioClientProvider: empty address — skipping connect" }
            return@withLock
        }
        val addressPayload = rawAddress.substring(1) // strip leading interface char

        val transport: RadioTransport = when (InterfaceId.forIdChar(interfaceChar)) {
            InterfaceId.BLUETOOTH -> {
                Logger.i { "RadioClientProvider: building BLE transport for $addressPayload" }
                BleTransport(addressPayload) { autoConnectIf { true } }
            }

            InterfaceId.TCP -> {
                val (host, port) = parseTcpAddress(addressPayload)
                Logger.i { "RadioClientProvider: building TCP transport for $host:$port" }
                TcpTransport(host, port)
            }

            InterfaceId.SERIAL -> {
                Logger.i { "RadioClientProvider: building Serial transport for $addressPayload" }
                AndroidSerialPorts.init(context)
                AndroidSerialPorts.open(addressPayload)
            }

            InterfaceId.MOCK, InterfaceId.NOP, null -> {
                Logger.w { "RadioClientProvider: unsupported transport type '$interfaceChar' ($rawAddress)" }
                return@withLock
            }
        }

        // Clear first so observers see null during teardown
        val old = _client.value
        _client.value = null
        old?.let { runCatching { it.disconnect() }.onFailure { e -> Logger.w(e) { "disconnect old client" } } }

        val newClient =
            RadioClient.Builder()
                .transport(transport)
                .storage(SqlDelightStorageProvider(baseDir = context.filesDir.absolutePath))
                .autoReconnect(AutoReconnectConfig()) // enabled=true; Disabled is SDK default — must opt in
                .build()

        _client.value = newClient
        newClient.connect()

        Logger.i { "RadioClientProvider: client connected via ${InterfaceId.forIdChar(interfaceChar)}" }
    }

    /** Fire-and-forget version of [rebuildAndConnect] for non-suspending call sites. */
    fun rebuildAndConnectAsync() {
        scope.launch {
            runCatching { rebuildAndConnect() }.onFailure { e -> Logger.e(e) { "RadioClientProvider: connect failed" } }
        }
    }

    /** Disconnect and clear the active client. */
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

        /** Parse "host:port" or "host" (uses default port 4403). */
        private fun parseTcpAddress(payload: String): Pair<String, Int> {
            val parts = payload.split(":")
            val host = parts[0]
            val port = parts.getOrNull(1)?.toIntOrNull() ?: DEFAULT_TCP_PORT
            return host to port
        }
    }
}
