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
package org.meshtastic.core.ble.niimbot

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import org.koin.core.annotation.Single
import org.meshtastic.core.ble.BleCharacteristic
import org.meshtastic.core.ble.BleConnection
import org.meshtastic.core.ble.BleConnectionFactory
import org.meshtastic.core.ble.BleConnectionState
import org.meshtastic.core.ble.BleDevice
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.ble.BleService
import org.meshtastic.core.ble.BleServiceInfo
import org.meshtastic.core.ble.BleWriteType
import org.meshtastic.core.ble.DEFAULT_BLE_WRITE_VALUE_LENGTH
import org.meshtastic.core.common.util.safeCatching
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/** The Bluetooth base UUID's tail: a service that ends with it is a standard one, never the print channel. */
private const val STANDARD_UUID_SUFFIX = "-0000-1000-8000-00805f9b34fb"

/** The label layouts are drawn for the D110's 96-dot head, so only that model is offered. */
private const val PRINTER_NAME_PREFIX = "D110"

fun isNiimbotPrinterName(name: String?): Boolean = name?.startsWith(PRINTER_NAME_PREFIX) == true

/** The service and characteristic the printer talks on. */
data class NiimbotChannel(val service: Uuid, val characteristic: Uuid)

/**
 * Finds the print channel the way MultiMote/niimbluelib does: the one characteristic on a vendor service that both
 * notifies and takes writes without response.
 */
fun niimbotChannel(services: List<BleServiceInfo>): NiimbotChannel? = services
    .filterNot { it.uuid.toString().endsWith(STANDARD_UUID_SUFFIX) }
    .firstNotNullOfOrNull { service ->
        service.characteristics
            .firstOrNull { it.notifies && it.writesWithoutResponse }
            ?.let { NiimbotChannel(service.uuid, it.uuid) }
    }

/** Creates a [NiimbotPrinter] that lives in the caller's scope. */
@Single
class NiimbotPrinterFactory(private val scanner: BleScanner, private val connectionFactory: BleConnectionFactory) {
    fun create(scope: CoroutineScope): NiimbotPrinter = NiimbotPrinter(scanner, connectionFactory, scope)
}

/**
 * A Niimbot D110 label printer over Bluetooth LE, on a connection of its own: nothing here reads or changes the radio's
 * connection.
 */
class NiimbotPrinter(private val scanner: BleScanner, connectionFactory: BleConnectionFactory, scope: CoroutineScope) {
    private val connection: BleConnection = connectionFactory.createAuxiliary(scope, TAG)
    private var link: NiimbotLink? = null

    val connectionState: StateFlow<BleConnectionState>
        get() = connection.connectionState

    /** Printers advertising nearby. Ends after [timeout]. */
    fun scan(timeout: Duration = SCAN_TIMEOUT): Flow<BleDevice> =
        scanner.scan(timeout).filter { isNiimbotPrinterName(it.name) }

    /** Connects and opens the print channel, or throws the [NiimbotException] that says why not. */
    suspend fun connect(device: BleDevice) {
        link = null
        link =
            try {
                openLink(device)
            } catch (_: TimeoutCancellationException) {
                // The connection's own bound on discovery ran out, which is the printer not answering.
                null
            } ?: throw NiimbotException.NoResponse(NiimbotRequest.CONNECT)
    }

    private suspend fun openLink(device: BleDevice): NiimbotLink? {
        if (connection.connectAndAwait(device, CONNECT_TIMEOUT) !is BleConnectionState.Connected) return null
        val channel =
            niimbotChannel(connection.discoveredServices(DISCOVERY_TIMEOUT))
                ?: throw NiimbotException.Unsupported(NiimbotRequest.CONNECT)
        return connection.profile(channel.service, DISCOVERY_TIMEOUT) { service ->
            open(service, BleCharacteristic(channel.characteristic))
        }
    }

    suspend fun print(bitmap: LabelBitmap) {
        val open = link ?: throw NiimbotException.NoResponse(NiimbotRequest.CONNECT)
        NiimbotProtocol(open).print(bitmap)
    }

    suspend fun disconnect() {
        link = null
        connection.disconnect()
    }

    private suspend fun CoroutineScope.open(service: BleService, characteristic: BleCharacteristic): NiimbotLink {
        val packets = Channel<NiimbotPacket>(Channel.UNLIMITED)
        val parser = NiimbotPacketParser()
        val subscribed = CompletableDeferred<Unit>()
        service
            .observe(characteristic) { subscribed.complete(Unit) }
            .onEach { bytes -> parser.append(bytes).forEach { packets.trySend(it) } }
            .catch { subscribed.completeExceptionally(NiimbotException.NoResponse(NiimbotRequest.CONNECT)) }
            .onCompletion { packets.close() }
            .launchIn(this)
        subscribed.await()
        return BleNiimbotLink(connection, service, characteristic, packets)
    }

    private class BleNiimbotLink(
        private val connection: BleConnection,
        private val service: BleService,
        private val characteristic: BleCharacteristic,
        override val received: ReceiveChannel<NiimbotPacket>,
    ) : NiimbotLink {
        override suspend fun send(packet: NiimbotPacket) {
            val bytes = packet.toBytes()
            val chunk =
                maxOf(
                    DEFAULT_BLE_WRITE_VALUE_LENGTH,
                    connection.maximumWriteValueLength(BleWriteType.WITHOUT_RESPONSE) ?: 0,
                )
            var offset = 0
            while (offset < bytes.size) {
                val end = minOf(offset + chunk, bytes.size)
                safeCatching {
                    service.write(characteristic, bytes.copyOfRange(offset, end), BleWriteType.WITHOUT_RESPONSE)
                }
                    .getOrElse { throw NiimbotException.NoResponse(packet.type) }
                offset = end
            }
        }
    }

    private companion object {
        const val TAG = "NiimbotPrinter"
        val SCAN_TIMEOUT = 30.seconds
        val CONNECT_TIMEOUT = 10.seconds
        val DISCOVERY_TIMEOUT = 10.seconds
    }
}
