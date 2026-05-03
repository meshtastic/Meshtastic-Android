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
import org.meshtastic.core.ble.buildPeripheralForSavedAddress
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.repository.RadioPrefs
import org.meshtastic.core.service.SdkClientLifecycle
import org.meshtastic.sdk.AutoReconnectConfig
import org.meshtastic.sdk.RadioClient
import org.meshtastic.sdk.storage.sqldelight.SqlDelightStorageProvider
import org.meshtastic.sdk.transport.ble.BleTransport

/**
 * Holds the active [RadioClient] and orchestrates connect/disconnect lifecycle.
 *
 * This is the SDK integration point for the POC. The [RadioClient] is exposed as a [StateFlow]
 * so ViewModels and the service can react to connection changes with `flatMapLatest`.
 *
 * **Thread-safety:** [rebuildAndConnect] is serialized by a [Mutex] so concurrent calls
 * (e.g., radio address change during an active connection attempt) queue safely.
 *
 * **Scope:** The provider owns a long-lived [CoroutineScope] for connection management.
 * It is NOT tied to any screen lifecycle — callers use [WhileSubscribed][kotlinx.coroutines.flow.SharingStarted]
 * on derived StateFlows for screen-scoped work.
 *
 * SDK gap F tracked here: [BleTransport] requires a Kable [Peripheral][com.juul.kable.Peripheral]
 * rather than accepting a MAC address string directly. [buildPeripheralForSavedAddress] bridges
 * that gap on the Android side until the SDK adds a convenience factory.
 */
@Single(binds = [SdkClientLifecycle::class])
class RadioClientProvider(
    private val context: Context,
    private val radioPrefs: RadioPrefs,
) : SdkClientLifecycle {
    private val _client = MutableStateFlow<RadioClient?>(null)

    /** Active [RadioClient], or `null` when disconnected or between connections. */
    val client: StateFlow<RadioClient?> = _client.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    /**
     * Tear down the existing client (if any) and build + connect a new one using the
     * current saved radio address from [RadioPrefs].
     *
     * If [RadioPrefs.devAddr] is not a BLE address this call is a no-op (other transport
     * types will be added in follow-up work).
     *
     * Callers that cannot suspend should use [rebuildAndConnectAsync].
     */
    suspend fun rebuildAndConnect() = mutex.withLock {
        val rawAddress = radioPrefs.devAddr.value ?: run {
            Logger.w { "RadioClientProvider: no saved device address — skipping connect" }
            return@withLock
        }

        // Only BLE is wired for this POC
        val interfaceChar = rawAddress.firstOrNull()
        if (InterfaceId.forIdChar(interfaceChar ?: ' ') != InterfaceId.BLUETOOTH) {
            Logger.w { "RadioClientProvider: non-BLE transport not yet wired ($rawAddress)" }
            return@withLock
        }

        val macAddress = rawAddress.substring(1) // strip leading 'x'

        // Clear first so observers see null during teardown
        val old = _client.value
        _client.value = null
        old?.let {
            runCatching { it.disconnect() }.onFailure { e -> Logger.w(e) { "disconnect old client" } }
        }

        Logger.i { "RadioClientProvider: building new client for $macAddress" }

        val peripheral = buildPeripheralForSavedAddress(macAddress)
        val transport = BleTransport(peripheral, macAddress)

        val newClient = RadioClient.Builder()
            .transport(transport)
            .storage(SqlDelightStorageProvider(baseDir = context.filesDir.absolutePath))
            .autoReconnect(AutoReconnectConfig()) // enabled=true; Disabled is SDK default — must opt in
            .build()

        _client.value = newClient
        newClient.connect()

        Logger.i { "RadioClientProvider: client connected" }
    }

    /** Fire-and-forget version of [rebuildAndConnect] for non-suspending call sites. */
    fun rebuildAndConnectAsync() {
        scope.launch {
            runCatching { rebuildAndConnect() }
                .onFailure { e -> Logger.e(e) { "RadioClientProvider: connect failed" } }
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
}
