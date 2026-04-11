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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import org.meshtastic.core.ble.MeshtasticBleConstants.FROMNUM_CHARACTERISTIC
import org.meshtastic.core.ble.MeshtasticBleConstants.FROMRADIO_CHARACTERISTIC
import org.meshtastic.core.ble.MeshtasticBleConstants.LOGRADIO_CHARACTERISTIC
import org.meshtastic.core.ble.MeshtasticBleConstants.TORADIO_CHARACTERISTIC

/**
 * [MeshtasticRadioProfile] implementation using Kable BLE characteristics.
 *
 * Uses the standard Meshtastic BLE protocol: FROMNUM notifications trigger polling reads on the FROMRADIO
 * characteristic. The firmware gates FROMNUM notifications behind `STATE_SEND_PACKETS`, so during the config handshake
 * we seed the drain trigger to poll proactively.
 */
class KableMeshtasticRadioProfile(private val service: BleService) : MeshtasticRadioProfile {

    private val toRadio = service.characteristic(TORADIO_CHARACTERISTIC)
    private val fromRadioChar = service.characteristic(FROMRADIO_CHARACTERISTIC)
    private val fromNum = service.characteristic(FROMNUM_CHARACTERISTIC)
    private val logRadioChar = service.characteristic(LOGRADIO_CHARACTERISTIC)

    companion object {
        private const val TRANSIENT_RETRY_DELAY_MS = 500L
    }

    // replay = 1: a seed emission placed here before the collector starts is replayed to the
    // collector immediately on subscription. This is what drives the initial FROMRADIO poll
    // during the config-handshake phase, where the firmware suppresses FROMNUM notifications
    // (it only emits them in STATE_SEND_PACKETS). Without the initial replay the entire config
    // stream would be silently skipped.
    private val triggerDrain =
        MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override val fromRadio: Flow<ByteArray> = channelFlow {
        launch {
            // Wire up FROMNUM notifications for steady-state packet delivery.
            launch {
                if (service.hasCharacteristic(fromNum)) {
                    service.observe(fromNum).collect { triggerDrain.tryEmit(Unit) }
                }
            }
            // Seed the replay buffer so the collector below starts draining immediately.
            // The firmware does NOT send FROMNUM notifications during the config handshake
            // (it gates them on STATE_SEND_PACKETS). Without this seed the entire config
            // stream would never be read.
            triggerDrain.tryEmit(Unit)
            triggerDrain.collect {
                var keepReading = true
                while (keepReading) {
                    try {
                        if (!service.hasCharacteristic(fromRadioChar)) {
                            keepReading = false
                            continue
                        }
                        val packet = service.read(fromRadioChar)
                        if (packet.isEmpty()) keepReading = false else send(packet)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Logger.w(e) { "FROMRADIO read error, pausing before next drain trigger" }
                        keepReading = false
                        // Don't permanently stop — the next triggerDrain emission will retry.
                        delay(TRANSIENT_RETRY_DELAY_MS)
                    }
                }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override val logRadio: Flow<ByteArray> = channelFlow {
        try {
            if (service.hasCharacteristic(logRadioChar)) {
                service.observe(logRadioChar).collect { send(it) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // logRadio is optional, ignore if not found
        }
    }

    override suspend fun sendToRadio(packet: ByteArray) {
        service.write(toRadio, packet, service.preferredWriteType(toRadio))
        triggerDrain.tryEmit(Unit)
    }

    override fun requestDrain() {
        triggerDrain.tryEmit(Unit)
    }
}
