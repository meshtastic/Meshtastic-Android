/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package com.geeksville.mesh.repository.radio

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapConcat
import no.nordicsemi.kotlin.ble.client.RemoteCharacteristic
import no.nordicsemi.kotlin.ble.client.RemoteService
import no.nordicsemi.kotlin.ble.core.WriteType
import org.meshtastic.core.ble.MeshtasticBleConstants.FROMNUM_CHARACTERISTIC
import org.meshtastic.core.ble.MeshtasticBleConstants.FROMRADIOSYNC_CHARACTERISTIC
import org.meshtastic.core.ble.MeshtasticBleConstants.FROMRADIO_CHARACTERISTIC
import org.meshtastic.core.ble.MeshtasticBleConstants.LOGRADIO_CHARACTERISTIC
import org.meshtastic.core.ble.MeshtasticBleConstants.TORADIO_CHARACTERISTIC

class MeshtasticRadioServiceImpl(private val remoteService: RemoteService) : MeshtasticRadioProfile.State {

    private val toRadioCharacteristic: RemoteCharacteristic =
        remoteService.characteristics.first { it.uuid == TORADIO_CHARACTERISTIC }
    private val fromRadioCharacteristic: RemoteCharacteristic =
        remoteService.characteristics.first { it.uuid == FROMRADIO_CHARACTERISTIC }
    private val fromRadioSyncCharacteristic: RemoteCharacteristic? =
        remoteService.characteristics.firstOrNull { it.uuid == FROMRADIOSYNC_CHARACTERISTIC }
    private val fromNumCharacteristic: RemoteCharacteristic? =
        if (fromRadioSyncCharacteristic == null) {
            remoteService.characteristics.first { it.uuid == FROMNUM_CHARACTERISTIC }
        } else {
            null
        }
    private val logRadioCharacteristic: RemoteCharacteristic =
        remoteService.characteristics.first { it.uuid == LOGRADIO_CHARACTERISTIC }

    init {
        require(toRadioCharacteristic.isWritable()) { "TORADIO must be writable" }
        require(fromRadioCharacteristic.isReadable()) { "FROMRADIO must be readable" }
        fromRadioSyncCharacteristic?.let { require(it.isSubscribable()) { "FROMRADIOSYNC must be subscribable" } }
        fromNumCharacteristic?.let { require(it.isSubscribable()) { "FROMNUM must be subscribable" } }
        require(logRadioCharacteristic.isSubscribable()) { "LOGRADIO must be subscribable" }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Suppress("TooGenericExceptionCaught")
    override val fromRadio: Flow<ByteArray> =
        if (fromRadioSyncCharacteristic != null) {
            fromRadioSyncCharacteristic.subscribe()
        } else {
            // Legacy path: Whenever notified by fromNum, read from fromRadio characteristic
            fromNumCharacteristic!!.subscribe().flatMapConcat { countBytes ->
                // The byte from FROMNUM notification is the number of packets waiting
                val count = if (countBytes.isNotEmpty()) countBytes[0].toUByte().toInt() else 1
                kotlinx.coroutines.flow.flow {
                    repeat(count) {
                        try {
                            emit(fromRadioCharacteristic.read())
                        } catch (e: Exception) {
                            co.touchlab.kermit.Logger.e(e) { "BLE: Failed to read from FROMRADIO" }
                        }
                    }
                }
            }
        }

    override val logRadio: Flow<ByteArray> = logRadioCharacteristic.subscribe()

    override suspend fun sendToRadio(packet: ByteArray) {
        toRadioCharacteristic.write(packet, WriteType.WITHOUT_RESPONSE)
    }
}
