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
import com.juul.kable.Peripheral
import com.juul.kable.WriteType
import com.juul.kable.characteristicOf
import com.juul.kable.writeWithoutResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import org.meshtastic.core.ble.MeshtasticBleConstants.FROMNUM_CHARACTERISTIC
import org.meshtastic.core.ble.MeshtasticBleConstants.FROMRADIOSYNC_CHARACTERISTIC
import org.meshtastic.core.ble.MeshtasticBleConstants.FROMRADIO_CHARACTERISTIC
import org.meshtastic.core.ble.MeshtasticBleConstants.LOGRADIO_CHARACTERISTIC
import org.meshtastic.core.ble.MeshtasticBleConstants.SERVICE_UUID
import org.meshtastic.core.ble.MeshtasticBleConstants.TORADIO_CHARACTERISTIC
import kotlin.uuid.Uuid

class KableMeshtasticRadioProfile(private val peripheral: Peripheral) : MeshtasticRadioProfile {

    private val toRadio = characteristicOf(SERVICE_UUID, TORADIO_CHARACTERISTIC)
    private val fromRadioChar = characteristicOf(SERVICE_UUID, FROMRADIO_CHARACTERISTIC)
    private val fromRadioSync = characteristicOf(SERVICE_UUID, FROMRADIOSYNC_CHARACTERISTIC)
    private val fromNum = characteristicOf(SERVICE_UUID, FROMNUM_CHARACTERISTIC)
    private val logRadioChar = characteristicOf(SERVICE_UUID, LOGRADIO_CHARACTERISTIC)

    private val triggerDrain = MutableSharedFlow<Unit>(extraBufferCapacity = 64)

    init {
        val svc = peripheral.services.value?.find { it.serviceUuid == SERVICE_UUID }
        Logger.i {
            "KableMeshtasticRadioProfile init. Discovered characteristics: ${svc?.characteristics?.map {
                it.characteristicUuid
            }}"
        }
    }

    private fun hasCharacteristic(uuid: Uuid): Boolean = peripheral.services.value?.any { svc ->
        svc.serviceUuid == SERVICE_UUID && svc.characteristics.any { it.characteristicUuid == uuid }
    } == true

    // Using observe() for fromRadioSync or legacy read loop for fromRadio
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override val fromRadio: Flow<ByteArray> = channelFlow {
        // Try to observe FROMRADIOSYNC if available. If it fails, fallback to FROMNUM/FROMRADIO.
        // This mirrors the robust fallback logic originally established in the legacy Android Nordic implementation.
        launch {
            try {
                if (hasCharacteristic(FROMRADIOSYNC_CHARACTERISTIC)) {
                    peripheral.observe(fromRadioSync).collect { send(it) }
                } else {
                    error("fromRadioSync missing")
                }
            } catch (e: Exception) {
                // Fallback to legacy
                launch {
                    if (hasCharacteristic(FROMNUM_CHARACTERISTIC)) {
                        peripheral.observe(fromNum).collect { triggerDrain.tryEmit(Unit) }
                    }
                }
                triggerDrain.collect {
                    var keepReading = true
                    while (keepReading) {
                        try {
                            if (!hasCharacteristic(FROMRADIO_CHARACTERISTIC)) {
                                keepReading = false
                                continue
                            }
                            val packet = peripheral.read(fromRadioChar)
                            if (packet.isEmpty()) keepReading = false else send(packet)
                        } catch (e: Exception) {
                            keepReading = false
                        }
                    }
                }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override val logRadio: Flow<ByteArray> = channelFlow {
        try {
            if (hasCharacteristic(LOGRADIO_CHARACTERISTIC)) {
                peripheral.observe(logRadioChar).collect { send(it) }
            }
        } catch (e: Exception) {
            // logRadio is optional, ignore if not found
        }
    }

    private val toRadioWriteType: WriteType by lazy {
        val svc = peripheral.services.value?.find { it.serviceUuid == SERVICE_UUID }
        val char = svc?.characteristics?.find { it.characteristicUuid == TORADIO_CHARACTERISTIC }

        if (char?.properties?.writeWithoutResponse == true) {
            WriteType.WithoutResponse
        } else {
            WriteType.WithResponse
        }
    }

    override suspend fun sendToRadio(packet: ByteArray) {
        peripheral.write(toRadio, packet, toRadioWriteType)
        triggerDrain.tryEmit(Unit)
    }
}
