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
@file:Suppress(
    // Every js("...") snippet references its Kotlin parameters inside the JS source, which detekt cannot see.
    "UnusedParameter",
)

package org.meshtastic.core.network.repository

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import org.meshtastic.core.network.serial.JsSerial
import org.meshtastic.core.network.serial.JsSerialPort
import org.meshtastic.core.network.serial.WebSerialPortRegistry
import org.meshtastic.core.network.serial.productId
import org.meshtastic.core.network.serial.vendorId
import org.meshtastic.core.network.serial.webSerialOrNull

/**
 * [SerialDevicePresence] from Web Serial's `connect`/`disconnect` events on `navigator.serial`, which fire only for
 * ports this page has been granted. Each event re-reads `getPorts()`, so the set holds the granted ports that are
 * plugged in now, keyed by [WebSerialPortRegistry] id.
 *
 * A replugged USB device comes back as a new port object. When exactly one absent id has its USB vendor and product,
 * the new object takes that id over, so the selected address survives the replug and transport recovery can find it.
 */
class WebSerialDevicePresence(private val registry: WebSerialPortRegistry, scope: CoroutineScope) :
    SerialDevicePresence {
    private val keys = MutableStateFlow<Set<String>>(emptySet())
    override val deviceKeys: StateFlow<Set<String>> = keys.asStateFlow()

    private val refreshes =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        val serial = webSerialOrNull()
        if (serial != null) {
            addSerialListener(serial, "connect") { event ->
                eventPort(event)?.let(::adoptReplug)
                refreshes.tryEmit(Unit)
            }
            addSerialListener(serial, "disconnect") { refreshes.tryEmit(Unit) }
            // Sequential, so a slow getPorts() cannot land after a newer one and publish a stale set.
            refreshes.onStart { emit(Unit) }.onEach { refresh() }.launchIn(scope)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun refresh() {
        try {
            val granted = registry.refreshGrantedPorts()
            keys.value = granted.filter { registry.get(it.id)?.let(::isConnected) == true }.map { it.id }.toSet()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Logger.w(e) { "Web Serial getPorts() failed" }
        }
    }

    private fun adoptReplug(port: JsSerialPort) {
        val known = registry.knownPorts()
        if (known.values.any { it === port } || port.vendorId() == 0) return
        val present = keys.value
        val candidates = known.filter { (id, old) ->
            id !in present && old.vendorId() == port.vendorId() && old.productId() == port.productId()
        }
        candidates.keys.singleOrNull()?.let { id -> registry.rebind(id, port) }
    }
}

private fun addSerialListener(serial: JsSerial, type: String, listener: (JsAny) -> Unit) {
    js("serial.addEventListener(type, listener)")
}

/** The port a `connect` event fired for, which it bubbles up to `navigator.serial` from. */
private fun eventPort(event: JsAny): JsSerialPort? = js("event.target && event.target.getInfo ? event.target : null")

/** `SerialPort.connected` where the browser has it; older Chromium lists only plugged-in ports anyway. */
private fun isConnected(port: JsSerialPort): Boolean = js("port.connected !== false")
