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
package org.meshtastic.feature.firmware.ota.dfu

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.meshtastic.core.ble.BleConnection
import org.meshtastic.core.ble.BleConnectionState
import org.meshtastic.core.ble.BleDevice
import org.meshtastic.core.ble.BleService
import org.meshtastic.core.model.util.anonymize
import org.meshtastic.feature.firmware.ota.calculateMacPlusOne
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/** Timings both DFU transports share. */
internal object DfuTimings {
    val CONNECT_TIMEOUT = 15.seconds
    val COMMAND_TIMEOUT = 30.seconds

    /** Settle after the Control Point CCCD write is confirmed, before the first command. */
    val SUBSCRIPTION_SETTLE = 500.milliseconds

    /** Bound on a whole firmware stream, the longest operation either transport runs. */
    val STREAM_TIMEOUT = 15.minutes
}

/**
 * Finds the DFU-mode device at [address] or MAC+1 with [scan], connects, and subscribes to [controlPoint] in
 * [serviceUuid], forwarding each notification to [onNotification]. [ready] runs in the same profile once the
 * subscription has settled. Throws [DfuException.ConnectionFailed] when the device is not found or will not connect.
 *
 * @param notFoundHint Appended to the not-found error, for protocol-specific advice.
 */
@Suppress("LongParameterList")
internal suspend fun BleConnection.connectToDfuControlPoint(
    address: String,
    tag: String,
    serviceUuid: Uuid,
    controlPoint: Uuid,
    logScope: CoroutineScope,
    scan: suspend (predicate: (BleDevice) -> Boolean) -> BleDevice?,
    onNotification: (ByteArray) -> Unit,
    notFoundHint: String = "",
    ready: suspend CoroutineScope.(BleService) -> Unit = {},
): BleDevice {
    val targetAddresses = setOf(address, calculateMacPlusOne(address))
    val shown = targetAddresses.map { it.anonymize() }
    Logger.i { "$tag: Scanning for DFU mode device at $shown..." }
    val device =
        scan { it.address in targetAddresses }
            ?: throw DfuException.ConnectionFailed("DFU mode device not found (tried $shown).$notFoundHint")

    Logger.i { "$tag: Found DFU mode device at ${device.address.anonymize()}, connecting..." }
    connectionState.onEach { Logger.d { "$tag: Connection state → $it" } }.launchIn(logScope)
    if (connectAndAwait(device, DfuTimings.CONNECT_TIMEOUT) is BleConnectionState.Disconnected) {
        throw DfuException.ConnectionFailed("Failed to connect to DFU device ${device.address.anonymize()}")
    }

    profile(serviceUuid) { service ->
        // launchIn(this) runs on the connection scope, so the subscription outlives this block.
        val subscribed = CompletableDeferred<Unit>()
        service
            .observe(service.characteristic(controlPoint)) {
                Logger.d { "$tag: Control Point subscribed" }
                subscribed.complete(Unit)
            }
            .onEach(onNotification)
            .catch { e ->
                if (!subscribed.isCompleted) subscribed.completeExceptionally(e)
                Logger.e(e) { "$tag: Control Point notification error" }
            }
            .launchIn(this)
        subscribed.await()
        delay(DfuTimings.SUBSCRIPTION_SETTLE)
        ready(service)
        Logger.i { "$tag: Connected and ready (${device.address.anonymize()})" }
    }
    return device
}
