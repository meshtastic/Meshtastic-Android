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
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.meshtastic.core.ble.MeshtasticBleConstants.FROMNUM_CHARACTERISTIC
import org.meshtastic.core.ble.MeshtasticBleConstants.FROMRADIO_CHARACTERISTIC
import org.meshtastic.core.ble.MeshtasticBleConstants.LOGRADIO_CHARACTERISTIC
import org.meshtastic.core.ble.MeshtasticBleConstants.TORADIO_CHARACTERISTIC
import kotlin.time.Duration.Companion.milliseconds

/**
 * [MeshtasticRadioProfile] implementation for Web Bluetooth, following the same FROMNUM-notification-triggers-a-
 * FROMRADIO-drain protocol shape as `KableMeshtasticRadioProfile` (`nonWebMain`) — see that file's KDoc for the
 * protocol itself, which is unchanged here; only the underlying transport differs.
 *
 * **Accepted, named gap vs. the Kable-based platforms:** this implementation does not replicate
 * `BleExceptionClassifier.isSessionFatalBleException()`'s "distinguish a session-fatal GATT error from a transient one,
 * and propagate the former so the transport layer reconnects" logic. Web Bluetooth's JS exceptions carry no comparable
 * GATT status code to classify on, so every read/write failure here is treated the same way — logged and retried via
 * [retryBleOperation], never specially rethrown for reconnection. A broken web session is instead caught by
 * [WebBleConnection]'s `gattserverdisconnected` listener at the connection layer, one level up. This is a coarser
 * disconnect-detection story than the Kable-based platforms have today; it is an accepted gap for this first pass, not
 * a bug to chase down here.
 */
class WebMeshtasticRadioProfile(private val service: BleService) : MeshtasticRadioProfile {

    private val toRadio = service.characteristic(TORADIO_CHARACTERISTIC)
    private val fromRadioChar = service.characteristic(FROMRADIO_CHARACTERISTIC)
    private val fromNum = service.characteristic(FROMNUM_CHARACTERISTIC)
    private val logRadioChar = service.characteristic(LOGRADIO_CHARACTERISTIC)

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
                try {
                    service
                        .observe(fromNum) {
                            Logger.d { "FROMNUM notifications enabled" }
                            subscriptionReady.complete(Unit)
                        }
                        .collect { triggerDrain.tryEmit(Unit) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    subscriptionReady.completeExceptionally(e)
                    throw e
                }
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
                    // See class KDoc: no session-fatal/transient distinction on web — every failure is treated as
                    // transient and retried on the next drain trigger.
                    Logger.w(e) { "FROMRADIO read error, pausing before next drain trigger" }
                    keepReading = false
                    delay(TRANSIENT_RETRY_DELAY)
                }
            }
        }
    }

    override val logRadio: Flow<ByteArray> = flow {
        if (!service.hasCharacteristic(logRadioChar)) return@flow
        emitAll(
            service.observe(logRadioChar).catch { e ->
                if (e is CancellationException) throw e
                Logger.d(e) { "logRadio observation failure suppressed" }
            },
        )
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
