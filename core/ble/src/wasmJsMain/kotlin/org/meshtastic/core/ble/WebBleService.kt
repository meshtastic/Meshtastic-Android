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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.uuid.Uuid

/**
 * [BleService] implementation wrapping one resolved [JsBluetoothRemoteGATTService] (Web Bluetooth), caching every
 * characteristic discovered on it, keyed by [Uuid].
 *
 * `BleService.hasCharacteristic()` is not `suspend`, but Web Bluetooth's `getCharacteristic()`/`getCharacteristics()`
 * are async — so every characteristic this service could ever be asked about must be resolved up front via [resolve],
 * before the service is handed to a caller's `setup` block. [WebBleConnection.profile] does exactly that, mirroring how
 * Kable's [KableBleConnection] (`nonWebMain`) waits for `peripheral.services` before handing out its own [BleService].
 */
internal class WebBleService(
    private val jsService: JsBluetoothRemoteGATTService,
    private val resolvedCharacteristics: Map<Uuid, JsBluetoothRemoteGATTCharacteristic>,
) : BleService {

    override fun hasCharacteristic(characteristic: BleCharacteristic): Boolean =
        resolvedCharacteristics.containsKey(characteristic.uuid)

    override fun discoveredCharacteristicUuids(): List<Uuid> = resolvedCharacteristics.keys.toList()

    override fun observe(characteristic: BleCharacteristic): Flow<ByteArray> = observe(characteristic) {}

    @Suppress("TooGenericExceptionCaught")
    override fun observe(characteristic: BleCharacteristic, onSubscription: suspend () -> Unit): Flow<ByteArray> =
        callbackFlow {
            val jsChar = resolvedCharacteristics[characteristic.uuid]
            if (jsChar == null) {
                close()
                return@callbackFlow
            }
            try {
                jsChar.startNotificationsSuspend()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                close(e)
                return@callbackFlow
            }
            val removeListener =
                jsChar.addListener("characteristicvaluechanged") { trySend(jsChar.currentValueAsByteArray()) }
            onSubscription()
            awaitClose { removeListener() }
        }

    override suspend fun read(characteristic: BleCharacteristic): ByteArray {
        val jsChar = resolvedCharacteristic(characteristic)
        return retryBleOperation(tag = "WebBle") { jsChar.readValueAsByteArray() }
    }

    override fun preferredWriteType(characteristic: BleCharacteristic): BleWriteType {
        val jsChar = resolvedCharacteristics[characteristic.uuid] ?: return BleWriteType.WITH_RESPONSE
        return if (jsChar.properties.writeWithoutResponse) BleWriteType.WITHOUT_RESPONSE else BleWriteType.WITH_RESPONSE
    }

    override suspend fun write(characteristic: BleCharacteristic, data: ByteArray, writeType: BleWriteType) {
        val jsChar = resolvedCharacteristic(characteristic)
        retryBleOperation(tag = "WebBle") {
            when (writeType) {
                BleWriteType.WITH_RESPONSE -> jsChar.writeWithResponse(data)
                BleWriteType.WITHOUT_RESPONSE -> jsChar.writeWithoutResponseSuspend(data)
            }
        }
    }

    private fun resolvedCharacteristic(characteristic: BleCharacteristic): JsBluetoothRemoteGATTCharacteristic =
        resolvedCharacteristics[characteristic.uuid]
            ?: error("Characteristic not discovered on this service: ${characteristic.uuid}")

    companion object {
        /**
         * Resolves every characteristic Web Bluetooth reports for [jsService] (unfiltered — see
         * [JsBluetoothRemoteGATTService.getCharacteristics]) and builds a [WebBleService] over the result.
         *
         * A characteristic whose UUID fails to parse as a [Uuid] is skipped rather than failing discovery outright;
         * that would only happen for a non-conformant peripheral, and Kable's own discovery is similarly tolerant of
         * unrecognised entries.
         */
        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        suspend fun resolve(jsService: JsBluetoothRemoteGATTService): WebBleService {
            val resolved = mutableMapOf<Uuid, JsBluetoothRemoteGATTCharacteristic>()
            for (jsChar in jsService.getAllCharacteristics()) {
                try {
                    resolved[Uuid.parse(jsChar.uuid)] = jsChar
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.w(e) { "Skipping characteristic with unparsable UUID '${jsChar.uuid}'" }
                }
            }
            return WebBleService(jsService, resolved)
        }
    }
}
