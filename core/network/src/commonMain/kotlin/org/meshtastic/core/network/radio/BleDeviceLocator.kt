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
@file:Suppress("TooGenericExceptionCaught")

package org.meshtastic.core.network.radio

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.meshtastic.core.ble.BleDevice
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.ble.BluetoothRepository
import org.meshtastic.core.ble.MeshtasticBleConstants.SERVICE_UUID
import org.meshtastic.core.model.RadioNotConnectedException
import org.meshtastic.core.model.util.anonymize
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val SCAN_RETRY_COUNT = 3
private val SCAN_RETRY_DELAY = 1.seconds

/**
 * Bounded scan duration used by both discovery paths in [BleDeviceLocator.findDevice]:
 * - Bonded devices get one address-filtered scan before falling back to the bonded handle.
 * - Non-bonded retries each use this duration.
 *
 * Keeping the bonded path to one scanner registration is important on Android, which throttles applications that start
 * BLE scans too frequently. A single 5s window still covers multiple advertising intervals for typical power-save slots
 * (~1–2s each), resolves immediately when the target advertises, and avoids consuming two scan starts per reconnect. If
 * the scan misses, [BleDeviceLocator.findDevice] falls back to the bonded handle and `attemptConnection` keeps that
 * patient `autoConnect` path bounded through `CONNECTION_TIMEOUT`.
 */
internal val SCAN_TIMEOUT = 5.seconds

/**
 * Locates the BLE device for one transport address: bonded-handle lookup with a single fresh-advertisement scan, or
 * retried bounded scans for non-bonded addresses.
 *
 * Extracted from [BleRadioTransport] (unchanged behavior) so the transport class stays within detekt's LargeClass
 * budget while sibling reconnect fixes continue to grow it.
 */
internal class BleDeviceLocator(
    private val scanner: BleScanner,
    private val bluetoothRepository: BluetoothRepository,
    private val address: String,
) {
    /** Robustly finds the device. Checks bonded devices, preferring a fresh scan result when available. */
    @Suppress("ReturnCount")
    internal suspend fun findDevice(): BleDevice {
        val bondedDevice =
            bluetoothRepository.state.value.bondedDevices.firstOrNull { it.address.equals(address, ignoreCase = true) }

        if (bondedDevice != null) {
            // Use one bounded, address-filtered scan. Splitting this into a short scan plus an escalated scan consumed
            // two Android scanner registrations per reconnect and could hit SCAN_FAILED_SCANNING_TOO_FREQUENTLY when a
            // user switched devices while the reconnect policy and the Connections screen were also scanning.
            Logger.i { "[${address.anonymize()}] Bonded device found; scanning once for a fresh advertisement" }
            scanForFreshDevice(SCAN_TIMEOUT)?.let {
                Logger.i { "[${address.anonymize()}] Fresh advertisement found; using scanned device" }
                return it
            }

            // If the scan misses, fall back to the bonded handle. Bonded-only devices have no fresh advertisement, so
            // Kable uses autoConnect=true and Android can patiently wait for the device to advertise again.
            // This remains bounded by CONNECTION_TIMEOUT in connectAndAwait(), after which BleReconnectPolicy owns
            // retry/backoff.
            Logger.w {
                "[${address.anonymize()}] No fresh advertisement within $SCAN_TIMEOUT; " +
                    "falling back to bonded handle for bounded autoConnect"
            }
            return bondedDevice
        }

        // Non-bonded path: preserve existing retry behavior (SCAN_RETRY_COUNT attempts at SCAN_TIMEOUT).
        Logger.i { "[${address.anonymize()}] Device not found in bonded list, scanning" }
        repeat(SCAN_RETRY_COUNT) { attempt ->
            scanForFreshDevice(SCAN_TIMEOUT)?.let {
                return it
            }
            if (attempt < SCAN_RETRY_COUNT - 1) {
                delay(SCAN_RETRY_DELAY)
            }
        }
        throw RadioNotConnectedException("Device not found at address ${address.anonymize()}")
    }

    /**
     * Performs a single BLE scan attempt for the selected [address] and returns the first matching [BleDevice], or null
     * if the scan times out or fails.
     *
     * One scan attempt only — no retry, no backoff. Both bonded and non-bonded paths in [findDevice] share this
     * primitive so retry policy stays centralized:
     * - Bonded: one address-filtered [SCAN_TIMEOUT] attempt before [findDevice] returns the bonded handle.
     * - Non-bonded: [SCAN_RETRY_COUNT] attempts at [SCAN_TIMEOUT] with [SCAN_RETRY_DELAY] between attempts.
     *
     * The outer [withTimeoutOrNull] is binding: the scanner receives [timeout] as a hint, but this coroutine resumes on
     * its own schedule regardless of when (or whether) the scanner honors it.
     *
     * [CancellationException] is rethrown — coroutine cancellation must never be swallowed.
     */
    internal suspend fun scanForFreshDevice(timeout: Duration): BleDevice? = try {
        withTimeoutOrNull(timeout) {
            // Pass both service UUID and address; the scanner picks whichever filter the platform can honour
            // (address natively on Android, service UUID elsewhere) and narrows to the address itself.
            scanner.scan(timeout = timeout, serviceUuid = SERVICE_UUID, address = address).first {
                it.address.equals(address, ignoreCase = true)
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logger.v(e) { "[${address.anonymize()}] Scan failed (timeout=$timeout)" }
        null
    }
}
