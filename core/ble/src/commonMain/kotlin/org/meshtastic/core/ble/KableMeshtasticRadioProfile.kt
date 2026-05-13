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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import org.meshtastic.core.ble.MeshtasticBleConstants.FROMNUM_CHARACTERISTIC
import org.meshtastic.core.ble.MeshtasticBleConstants.FROMRADIO_CHARACTERISTIC
import org.meshtastic.core.ble.MeshtasticBleConstants.LOGRADIO_CHARACTERISTIC
import org.meshtastic.core.ble.MeshtasticBleConstants.TORADIO_CHARACTERISTIC
import kotlin.time.Duration.Companion.milliseconds

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

    /**
     * Cached preferred write type for [toRadio]. Resolved once at construction so the hot send path doesn't have to
     * walk the discovered services list on every packet.
     */
    private val toRadioWriteType: BleWriteType = service.preferredWriteType(toRadio)

    companion object {
        private val TRANSIENT_RETRY_DELAY = 500.milliseconds
    }

    private val subscriptionReady = CompletableDeferred<Unit>()

    /**
     * Latched signal: a single buffered slot collapses bursts of drain triggers into one pending poll. Capacity 1 with
     * DROP_OLDEST means we never block writers and never let stale drain requests pile up.
     */
    private val triggerDrain =
        MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override val fromRadio: Flow<ByteArray> = channelFlow {
        launch {
            if (service.hasCharacteristic(fromNum)) {
                service
                    .observe(fromNum) {
                        Logger.d { "FROMNUM CCCD written — notifications enabled" }
                        subscriptionReady.complete(Unit)
                    }
                    .collect { triggerDrain.tryEmit(Unit) }
            } else {
                subscriptionReady.complete(Unit)
            }
        }
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
                    delay(TRANSIENT_RETRY_DELAY)
                }
            }
        }
    }

    override val logRadio: Flow<ByteArray> =
        if (service.hasCharacteristic(logRadioChar)) {
            service.observe(logRadioChar).catch { e ->
                if (e is CancellationException) throw e
                // logRadio is optional — log at debug for diagnostics but don't surface to callers.
                Logger.d(e) { "logRadio observation failure suppressed" }
            }
        } else {
            emptyFlow()
        }

    override suspend fun sendToRadio(packet: ByteArray) {
        service.write(toRadio, packet, toRadioWriteType)
        triggerDrain.tryEmit(Unit)
    }

    override fun requestDrain() {
        triggerDrain.tryEmit(Unit)
    }

    override suspend fun awaitSubscriptionReady() {
        subscriptionReady.await()
    }
}
