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
package org.meshtastic.app.repository.radio

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
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

    private val triggerDrain = MutableSharedFlow<Unit>(extraBufferCapacity = 64)

    init {
        require(toRadioCharacteristic.isWritable()) { "TORADIO must be writable" }
        require(fromRadioCharacteristic.isReadable()) { "FROMRADIO must be readable" }
        fromRadioSyncCharacteristic?.let { require(it.isSubscribable()) { "FROMRADIOSYNC must be subscribable" } }
        fromNumCharacteristic?.let { require(it.isSubscribable()) { "FROMNUM must be subscribable" } }
        require(logRadioCharacteristic.isSubscribable()) { "LOGRADIO must be subscribable" }
    }

    override val fromRadio: Flow<ByteArray> =
        if (fromRadioSyncCharacteristic != null) {
            fromRadioSyncCharacteristic.subscribe()
        } else {
            // Legacy path: drain fromRadio characteristic when notified or after write
            channelFlow {
                launch { fromNumCharacteristic!!.subscribe().collect { triggerDrain.tryEmit(Unit) } }

                triggerDrain.collect {
                    var keepReading = true
                    while (keepReading) {
                        try {
                            val packet = fromRadioCharacteristic.read()
                            if (packet.isEmpty()) {
                                keepReading = false
                            } else {
                                send(packet)
                            }
                        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                            co.touchlab.kermit.Logger.e(e) { "BLE: Failed to read from FROMRADIO" }
                            keepReading = false
                        }
                    }
                }
            }
        }

    override val logRadio: Flow<ByteArray> = logRadioCharacteristic.subscribe()

    override suspend fun sendToRadio(packet: ByteArray) {
        toRadioCharacteristic.write(packet, WriteType.WITHOUT_RESPONSE)
        if (fromRadioSyncCharacteristic == null) {
            triggerDrain.tryEmit(Unit)
        }
    }
}
