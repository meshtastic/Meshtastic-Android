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
package org.meshtastic.core.ble

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.uuid.Uuid

/**
 * [BleConnection] implementation using the raw Web Bluetooth API (`navigator.bluetooth`), named and shaped to resemble
 * Kable's own [KableBleConnection] (`nonWebMain`) vocabulary — connect/disconnect/profile over a [BleService] — so this
 * can eventually be proposed upstream as Kable's JS target rather than maintained as a permanent, unrelated fork. See
 * `WebBluetoothApi.kt`'s KDoc for the JS-interop layer this builds on.
 *
 * Web Bluetooth has no equivalent of Kable's `autoConnect`/advertisement-based reconnect: a [WebBleDevice] handle only
 * stays valid for the lifetime of the page (or until the OS revokes the grant), and `gatt.connect()` is the only
 * reconnection primitive. There is also no platform GATT-status code on disconnect — `gattserverdisconnected` carries
 * no reason, so every remote disconnect maps to [DisconnectReason.RemoteDisconnect], never a more specific code the way
 * Kable's `KableStateMapping` distinguishes timeout/encryption/etc. failures on Android/iOS.
 */
class WebBleConnection(private val scope: CoroutineScope) : BleConnection {

    private var jsDevice: JsBluetoothDevice? = null
    private var jsServer: JsBluetoothRemoteGATTServer? = null
    private var removeDisconnectListener: (() -> Unit)? = null

    private val _deviceFlow = MutableStateFlow<BleDevice?>(null)
    override val deviceFlow: StateFlow<BleDevice?> = _deviceFlow.asStateFlow()

    override val device: BleDevice?
        get() = _deviceFlow.value

    private val _connectionState =
        MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected(DisconnectReason.Unknown))
    override val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    @Suppress("TooGenericExceptionCaught")
    override suspend fun connect(device: BleDevice) {
        val webDevice = device as? WebBleDevice ?: error("Unsupported BleDevice type: ${device::class}")
        cleanUp()

        _connectionState.value = BleConnectionState.Connecting
        webDevice.updateState(BleConnectionState.Connecting)

        val gatt =
            webDevice.jsDevice.gatt
                ?: run {
                    val failure = BleConnectionState.Disconnected(DisconnectReason.ConnectionFailed)
                    _connectionState.value = failure
                    webDevice.updateState(failure)
                    error("Device '${webDevice.address}' does not expose a GATT server")
                }

        val server =
            try {
                retryBleOperation(tag = "WebBle") { gatt.connectSuspend() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val failure = BleConnectionState.Disconnected(DisconnectReason.ConnectionFailed)
                _connectionState.value = failure
                webDevice.updateState(failure)
                throw e
            }

        jsDevice = webDevice.jsDevice
        jsServer = server
        removeDisconnectListener =
            webDevice.jsDevice.addListener("gattserverdisconnected") {
                // gattserverdisconnected carries no reason code — RemoteDisconnect is the honest, coarsest-common
                // label; see class KDoc. Fired from a raw JS callback so the state update must hop back onto the
                // connection's own scope rather than emitting a StateFlow value directly from here.
                val disconnected = BleConnectionState.Disconnected(DisconnectReason.RemoteDisconnect)
                webDevice.updateState(disconnected)
                scope.launch {
                    _connectionState.value = disconnected
                    _deviceFlow.value = null
                }
            }

        _deviceFlow.value = webDevice
        webDevice.updateState(BleConnectionState.Connected)
        _connectionState.value = BleConnectionState.Connected
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override suspend fun connectAndAwait(device: BleDevice, timeout: Duration): BleConnectionState = try {
        withTimeout(timeout) {
            connect(device)
            BleConnectionState.Connected
        }
    } catch (_: TimeoutCancellationException) {
        BleConnectionState.Disconnected(DisconnectReason.Timeout)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        BleConnectionState.Disconnected(DisconnectReason.ConnectionFailed)
    }

    override suspend fun disconnect() = withContext(NonCancellable) {
        _connectionState.value = BleConnectionState.Disconnected(DisconnectReason.LocalDisconnect)
        (device as? WebBleDevice)?.updateState(BleConnectionState.Disconnected(DisconnectReason.LocalDisconnect))
        cleanUp()
        _deviceFlow.value = null
    }

    override suspend fun <T> profile(
        serviceUuid: Uuid,
        timeout: Duration,
        setup: suspend CoroutineScope.(BleService) -> T,
    ): T {
        val server = jsServer ?: error("Not connected")
        return withTimeout(timeout) {
            val jsService =
                retryBleOperation(tag = "WebBle") { server.getPrimaryServiceSuspend(serviceUuid.toString()) }
            val service = WebBleService.resolve(jsService)
            coroutineScope { setup(service) }
        }
    }

    // Web Bluetooth exposes no MTU/write-length negotiation API — callers already fall back to
    // DEFAULT_BLE_WRITE_VALUE_LENGTH when this is null, which is the honest answer here.
    override fun maximumWriteValueLength(writeType: BleWriteType): Int? = null

    // requestHighConnectionPriority/requestBalancedConnectionPriority/invalidateServiceCache all keep the
    // BleConnection interface's default `false` — Web Bluetooth exposes no connection-priority or GATT-cache
    // control API, so there is nothing platform-specific to override here.

    /** Tears down the current GATT connection and listener, if any. Safe to call when already disconnected. */
    @Suppress("TooGenericExceptionCaught")
    private fun cleanUp() {
        removeDisconnectListener?.invoke()
        removeDisconnectListener = null
        val server = jsServer
        if (server != null && server.connected) {
            try {
                server.disconnect()
            } catch (e: Exception) {
                Logger.w(e) { "Failed to disconnect GATT server during cleanup" }
            }
        }
        jsServer = null
        jsDevice = null
    }
}
