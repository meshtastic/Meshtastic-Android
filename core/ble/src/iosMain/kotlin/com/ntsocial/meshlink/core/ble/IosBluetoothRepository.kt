/*
 * NTsocial MeshLink original work and modifications:
 * Copyright (c) 2026 LiberaNt LLC
 *
 * Meshtastic Android-derived portions, where present:
 * Copyright (c) 2026 Meshtastic LLC
 *
 * Developed and/or modified for NTsocial MeshLink in 2026.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
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
@file:Suppress("DEPRECATION")

package com.ntsocial.meshlink.core.ble

import co.touchlab.kermit.Logger
import com.juul.kable.Bluetooth
import com.juul.kable.NotConnectedException
import com.juul.kable.Peripheral
import com.juul.kable.PeripheralBuilder
import com.juul.kable.Reason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.core.annotation.Single
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/** iOS Bluetooth state and pairing semantics backed by Kable/CoreBluetooth. */
@Single(binds = [BluetoothRepository::class])
class IosBluetoothRepository(private val loggingConfig: BleLoggingConfig) : BluetoothRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(BluetoothState())
    override val state: StateFlow<BluetoothState> = _state.asStateFlow()
    private val pairingMutex = Mutex()

    @Volatile private var stateJob: Job? = null

    /** Process-local proof that the protected FROMNUM subscription completed for this peripheral. */
    @Volatile private var pairingVerifiedAddresses: Set<String> = emptySet()

    init {
        refreshState()
    }

    override fun refreshState() {
        initializePlatformBle()
        if (stateJob?.isActive == true) return
        stateJob =
            scope.launch {
                Bluetooth.availability.collect { availability ->
                    _state.value =
                        when (availability) {
                            Bluetooth.Availability.Available -> _state.value.copy(hasPermissions = true, enabled = true)

                            is Bluetooth.Availability.Unavailable ->
                                _state.value.copy(
                                    hasPermissions =
                                    when (availability.reason) {
                                        Reason.Unauthorized -> false
                                        Reason.Unknown -> _state.value.hasPermissions
                                        else -> true
                                    },
                                    enabled = false,
                                )
                        }
                }
            }
    }

    override fun isValid(bleAddress: String): Boolean = runCatching { Uuid.parse(bleAddress) }.isSuccess

    // CoreBluetooth does not expose a public bond database. A valid UUID is only an address, never proof of pairing.
    override fun isBonded(address: String): Boolean = normalizedAddress(address) in pairingVerifiedAddresses

    /** Reconstructs, verifies, and stages a saved CoreBluetooth peripheral without waiting for an advertisement. */
    override suspend fun prepareKnownDevice(address: String): BleDevice? {
        val normalized = normalizedAddress(address)
        if (normalized.isEmpty()) return null

        state.value.bondedDevices
            .firstOrNull { normalizedAddress(it.address) == normalized }
            ?.let {
                return it
            }

        val candidate = MeshtasticBleDevice(address = normalized, reconnectByIdentifier = true)
        return try {
            bond(candidate)
            // Another same-address pairing attempt may have won while bond() waited for the mutex. Return the
            // exact device instance that owns the staged peripheral so transport take/discard remains identity-safe.
            state.value.bondedDevices.firstOrNull { normalizedAddress(it.address) == normalized } ?: candidate
        } catch (pairing: BlePairingException) {
            if (pairing.failure == BlePairingFailure.DEVICE_NOT_FOUND && pairing.cause is NoSuchElementException) {
                null
            } else {
                throw pairing
            }
        }
    }

    override suspend fun discardPreparedDevice(device: BleDevice) {
        val prepared = discardPlatformPreparedPeripheral(device) ?: return
        closePeripheral(prepared.peripheral)
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun bond(device: BleDevice) {
        val meshtasticDevice =
            device as? MeshtasticBleDevice
                ?: throw BlePairingException(
                    failure = BlePairingFailure.PLATFORM_FAILURE,
                    message = "Unsupported Bluetooth peripheral",
                )
        val normalized = normalizedAddress(device.address)
        if (normalized.isEmpty()) {
            throw BlePairingException(
                failure = BlePairingFailure.DEVICE_NOT_FOUND,
                message = "The Bluetooth peripheral identifier is invalid",
            )
        }

        pairingMutex.withLock {
            if (normalized in pairingVerifiedAddresses) return@withLock

            fun PeripheralBuilder.configurePairingPeripheral() {
                logging { applyConfig(loggingConfig, identifier = "ios-pairing") }
                observationExceptionHandler { cause -> throw cause }
                platformConfig(device) { false }
            }

            initializePlatformBle()
            fun createPairingPeripheral(): Peripheral = try {
                meshtasticDevice.advertisement?.let { advertisement ->
                    Peripheral(advertisement) { configurePairingPeripheral() }
                } ?: createPeripheral(device.address) { configurePairingPeripheral() }
            } catch (missing: NoSuchElementException) {
                throw BlePairingException(
                    failure = BlePairingFailure.DEVICE_NOT_FOUND,
                    message = "The saved Bluetooth peripheral is no longer available",
                    cause = missing,
                )
            }

            var peripheral = createPairingPeripheral()
            var stagedForTransport = false
            try {
                val preparedConnectionScope =
                    try {
                        preparePairingPeripheral(
                            peripheral = peripheral,
                            timeout =
                            if (meshtasticDevice.reconnectByIdentifier) {
                                IOS_RESTORED_PERIPHERAL_PROBE_TIMEOUT
                            } else {
                                IOS_NATIVE_PAIRING_TIMEOUT
                            },
                        )
                    } catch (timeout: TimeoutCancellationException) {
                        if (!meshtasticDevice.reconnectByIdentifier) throw timeout
                        peripheral = retryReconstructedPeripheral(normalized, peripheral, ::createPairingPeripheral)
                        preparePairingPeripheral(peripheral, IOS_NATIVE_PAIRING_TIMEOUT)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (pairing: BlePairingException) {
                        throw pairing
                    } catch (error: Exception) {
                        if (!meshtasticDevice.reconnectByIdentifier) throw error
                        peripheral =
                            retryReconstructedPeripheral(normalized, peripheral, ::createPairingPeripheral, error)
                        preparePairingPeripheral(peripheral, IOS_NATIVE_PAIRING_TIMEOUT)
                    }

                val previous =
                    replacePlatformPreparedPeripheral(
                        address = normalized,
                        prepared = PlatformPreparedPeripheral(meshtasticDevice, peripheral, preparedConnectionScope),
                    )
                stagedForTransport = true
                previous?.peripheral?.let { closePeripheral(it) }
                pairingVerifiedAddresses = pairingVerifiedAddresses + normalized
                _state.update { current ->
                    current.copy(
                        bondedDevices =
                        current.bondedDevices.filterNot { normalizedAddress(it.address) == normalized } +
                            meshtasticDevice,
                    )
                }
                schedulePreparedPeripheralExpiry(meshtasticDevice)
            } catch (timeout: TimeoutCancellationException) {
                if (stagedForTransport) discardPreparedDevice(meshtasticDevice)
                throw BlePairingException(
                    failure = BlePairingFailure.TIMED_OUT,
                    message = "Bluetooth pairing timed out. Confirm the node PIN and try again.",
                    cause = timeout,
                )
            } catch (cancelled: CancellationException) {
                if (stagedForTransport) discardPreparedDevice(meshtasticDevice)
                throw cancelled
            } catch (pairing: BlePairingException) {
                if (stagedForTransport) discardPreparedDevice(meshtasticDevice)
                throw pairing
            } catch (error: Exception) {
                if (stagedForTransport) discardPreparedDevice(meshtasticDevice)
                throw BlePairingException(
                    failure = BlePairingFailure.AUTHENTICATION_FAILED,
                    message = "Bluetooth pairing did not complete. Confirm the node PIN and try again.",
                    cause = error,
                )
            } finally {
                if (!stagedForTransport) closePeripheral(peripheral)
            }
        }
    }

    private fun schedulePreparedPeripheralExpiry(device: MeshtasticBleDevice) {
        scope.launch {
            delay(IOS_PREPARED_PERIPHERAL_LEASE_TIMEOUT)
            discardPreparedDevice(device)
        }
    }

    private suspend fun retryReconstructedPeripheral(
        address: String,
        stalePeripheral: Peripheral,
        createFreshPeripheral: () -> Peripheral,
        cause: Exception? = null,
    ): Peripheral {
        // Kable 0.42 reconstructs an OS-restored, already-connected CBPeripheral with a logical Disconnected state and
        // then waits for a didConnect callback that CoreBluetooth does not resend. Cancelling this bounded probe makes
        // the same Kable CentralManager release that stale link; a fresh wrapper can establish a normal session.
        Logger.w(cause) {
            "[$address] Saved iOS peripheral could not publish a verified connect transition; " +
                "releasing restored link before retry"
        }
        Logger.i { "ios_ble_recovery stage=retire_probe" }
        closePeripheral(stalePeripheral)
        delay(IOS_RESTORED_PERIPHERAL_SETTLE_DELAY)
        return createFreshPeripheral().also { Logger.i { "ios_ble_recovery stage=fresh_wrapper" } }
    }

    private suspend fun preparePairingPeripheral(
        peripheral: Peripheral,
        timeout: kotlin.time.Duration,
    ): CoroutineScope = withTimeout(timeout) {
        Logger.i { "ios_ble_recovery stage=connect_begin timeoutMs=${timeout.inWholeMilliseconds}" }
        val connectionScope = peripheral.connect()
        Logger.i { "ios_ble_recovery stage=connect_complete" }
        val service = KableBleService(peripheral, MeshtasticBleConstants.SERVICE_UUID)
        val fromNum = service.characteristic(MeshtasticBleConstants.FROMNUM_CHARACTERISTIC)
        if (!service.hasCharacteristic(fromNum)) {
            throw BlePairingException(
                failure = BlePairingFailure.PLATFORM_FAILURE,
                message = "The node does not expose the Meshtastic pairing characteristic",
            )
        }

        val subscriptionReady = CompletableDeferred<Unit>()
        val observationJob = launch {
            service
                .observe(fromNum) { subscriptionReady.complete(Unit) }
                .catch { error ->
                    if (error is CancellationException) throw error
                    subscriptionReady.completeExceptionally(error)
                    throw error
                }
                .collect()
        }
        try {
            // The encrypted CCCD write triggers and authoritatively completes native iOS pairing.
            subscriptionReady.await()
            Logger.i { "ios_ble_recovery stage=subscription_ready" }
        } finally {
            observationJob.cancelAndJoin()
        }
        connectionScope
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun closePeripheral(peripheral: Peripheral) = withContext(NonCancellable) {
        try {
            peripheral.disconnect()
        } catch (_: NotConnectedException) {
            // The attempt may already have disconnected after a rejected or cancelled PIN.
        } catch (_: Exception) {
            // close() below remains authoritative for releasing CoreBluetooth resources.
        }
        try {
            peripheral.close()
        } catch (_: Exception) {
            // Nothing else can be recovered after the temporary peripheral is closed.
        }
    }

    private fun normalizedAddress(address: String): String =
        runCatching { Uuid.parse(address).toString().lowercase() }.getOrDefault("")

    private companion object {
        val IOS_NATIVE_PAIRING_TIMEOUT = 90.seconds
        val IOS_RESTORED_PERIPHERAL_PROBE_TIMEOUT = 5.seconds
        val IOS_RESTORED_PERIPHERAL_SETTLE_DELAY = 1.seconds
        val IOS_PREPARED_PERIPHERAL_LEASE_TIMEOUT = 30.seconds
    }
}
