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

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.di.CoroutineDispatchers
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val BOND_TIMEOUT = 30.seconds
private val BOND_STATE_POLL_INTERVAL = 500.milliseconds

// Fixed two-poll grace for slow BOND_NONE -> BOND_BONDING transitions after createBond() returns true.
// Tune this or track observed transition latency if a specific OEM needs a longer window.
private val CREATED_BOND_NONE_GRACE = BOND_STATE_POLL_INTERVAL + BOND_STATE_POLL_INTERVAL
internal const val BOND_FAILED_OR_REJECTED_MESSAGE = "Bonding failed or rejected"

/** Android implementation of [BluetoothRepository]. */
@Suppress("TooManyFunctions")
@Single
class AndroidBluetoothRepository(
    private val context: Context,
    private val dispatchers: CoroutineDispatchers,
    @Named("ProcessLifecycle") private val processLifecycle: Lifecycle,
) : BluetoothRepository {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private val _state = MutableStateFlow(BluetoothState(hasPermissions = hasBluetoothPermissions()))
    override val state: StateFlow<BluetoothState> = _state.asStateFlow()

    private val deviceCache = mutableMapOf<String, MeshtasticBleDevice>()

    init {
        processLifecycle.coroutineScope.launch(dispatchers.default) { updateBluetoothState() }
    }

    private fun hasBluetoothPermissions(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val hasConnect =
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        val hasScan =
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
        hasConnect && hasScan
    } else {
        // Pre-Android 12: classic Bluetooth permissions are install-time.
        true
    }

    override fun refreshState() {
        processLifecycle.coroutineScope.launch(dispatchers.default) { updateBluetoothState() }
    }

    override fun isValid(bleAddress: String): Boolean = BluetoothAdapter.checkBluetoothAddress(bleAddress)

    @Suppress("TooGenericExceptionThrown", "TooGenericExceptionCaught", "SwallowedException")
    @SuppressLint("MissingPermission")
    override suspend fun bond(device: BleDevice) {
        val macAddress = device.address
        val remoteDevice =
            bluetoothAdapter?.getRemoteDevice(macAddress) ?: throw Exception("Bluetooth adapter unavailable")

        if (remoteDevice.bondState == android.bluetooth.BluetoothDevice.BOND_BONDED) {
            updateBluetoothState()
            return
        }

        try {
            val bonded =
                withTimeoutOrNull(BOND_TIMEOUT) {
                    val result = CompletableDeferred<Unit>()
                    val receiver = createBondReceiver(macAddress, result)

                    val filter =
                        android.content.IntentFilter(android.bluetooth.BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                    ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

                    try {
                        val start = startOrObserveBond(remoteDevice, result)
                        awaitBondResult(remoteDevice, result, start)
                    } finally {
                        unregisterBondReceiver(receiver)
                    }
                    // Reaching here means the suspended bond wait completed before BOND_TIMEOUT.
                    true
                } ?: (remoteDevice.bondState == android.bluetooth.BluetoothDevice.BOND_BONDED)

            if (!bonded) {
                throw Exception("Timed out waiting for bonding to complete")
            }
        } finally {
            updateBluetoothState()
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException", "ReturnCount")
    @SuppressLint("MissingPermission")
    override suspend fun removeBond(address: String): Boolean {
        val remoteDevice = bluetoothAdapter?.getRemoteDevice(address)
        if (remoteDevice == null || remoteDevice.bondState == android.bluetooth.BluetoothDevice.BOND_NONE) {
            return false
        }
        return try {
            // removeBond() is a public-but-hidden BluetoothDevice API (no SDK stub); reflection is the standard access
            // path used across the Android BLE/DFU ecosystem (incl. Nordic's DFU library).
            val removed = remoteDevice.javaClass.getMethod("removeBond").invoke(remoteDevice) as? Boolean ?: false
            Logger.i { "removeBond($address) -> $removed" }
            removed
        } catch (e: Exception) {
            Logger.w(e) { "removeBond($address) reflection failed" }
            false
        } finally {
            updateBluetoothState()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    @SuppressLint("MissingPermission")
    private fun startOrObserveBond(
        remoteDevice: android.bluetooth.BluetoothDevice,
        result: CompletableDeferred<Unit>,
    ): BondWaitStart {
        var start = BondWaitStart()
        try {
            if (!result.isCompleted) {
                if (remoteDevice.bondState == android.bluetooth.BluetoothDevice.BOND_BONDED) {
                    result.complete(Unit)
                } else if (remoteDevice.createBond()) {
                    start = BondWaitStart(createdBond = true)
                } else {
                    // createBond() returns false when a bond is already in flight, triggered by a GATT
                    // operation hitting a secured characteristic, or already established.
                    // ACTION_BOND_STATE_CHANGED is unreliable on some devices (see Kable #111), so
                    // re-check bondState directly rather than failing the whole flow.
                    when (remoteDevice.bondState) {
                        android.bluetooth.BluetoothDevice.BOND_BONDED -> {
                            result.complete(Unit)
                        }

                        android.bluetooth.BluetoothDevice.BOND_BONDING -> {
                            // Bond already in progress; leave the receiver registered to resolve it on
                            // the terminal BOND_BONDED / BOND_NONE transition instead of treating this
                            // as a failure.
                            Logger.d { "createBond() returned false but bonding is already in progress" }
                            start = BondWaitStart(bondingObserved = true)
                        }

                        else -> {
                            result.completeExceptionally(Exception("Failed to initiate bonding"))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            result.completeExceptionally(e)
        }
        return start
    }

    private data class BondWaitStart(val bondingObserved: Boolean = false, val createdBond: Boolean = false)

    @SuppressLint("MissingPermission")
    private suspend fun awaitBondResult(
        remoteDevice: android.bluetooth.BluetoothDevice,
        result: CompletableDeferred<Unit>,
        start: BondWaitStart,
    ) {
        var bondingWasInFlight = start.bondingObserved
        // createBond() can return true before Android reports BOND_BONDING. Tolerate two polled
        // BOND_NONE samples (polls 1-2), then fail on the third persistent BOND_NONE.
        var createdBondNoneGraceRemaining =
            if (start.createdBond) {
                CREATED_BOND_NONE_GRACE
            } else {
                Duration.ZERO
            }

        while (!result.isCompleted) {
            val completedFromReceiver =
                withTimeoutOrNull(BOND_STATE_POLL_INTERVAL) {
                    result.await()
                    true
                } == true

            if (!completedFromReceiver) {
                when (remoteDevice.bondState) {
                    android.bluetooth.BluetoothDevice.BOND_BONDED -> {
                        result.complete(Unit)
                    }

                    android.bluetooth.BluetoothDevice.BOND_BONDING -> {
                        // Once polling observes BOND_BONDING, a later BOND_NONE is terminal. Keep this
                        // defensive for any path that observes in-flight bonding outside the start state.
                        bondingWasInFlight = true
                        createdBondNoneGraceRemaining = Duration.ZERO
                    }

                    android.bluetooth.BluetoothDevice.BOND_NONE -> {
                        // Invariant: if start.createdBond is false, startOrObserveBond either completed
                        // result or observed BOND_BONDING, which is represented by bondingWasInFlight.
                        val pollFailureDetails =
                            "bond poll failed: bondingWasInFlight=$bondingWasInFlight " +
                                "createdBond=${start.createdBond} graceRemaining=$createdBondNoneGraceRemaining"
                        when {
                            bondingWasInFlight -> {
                                Logger.d { pollFailureDetails }
                                result.completeExceptionally(Exception(BOND_FAILED_OR_REJECTED_MESSAGE))
                            }

                            createdBondNoneGraceRemaining > Duration.ZERO -> {
                                createdBondNoneGraceRemaining -= BOND_STATE_POLL_INTERVAL
                            }

                            start.createdBond -> {
                                Logger.d { pollFailureDetails }
                                result.completeExceptionally(Exception(BOND_FAILED_OR_REJECTED_MESSAGE))
                            }
                        }
                    }
                }
            }
        }
        result.await()
    }

    @Suppress("TooGenericExceptionThrown")
    private fun createBondReceiver(
        macAddress: String,
        result: CompletableDeferred<Unit>,
    ): android.content.BroadcastReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context, intent: android.content.Intent) {
            if (intent.action != android.bluetooth.BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
            val d =
                androidx.core.content.IntentCompat.getParcelableExtra(
                    intent,
                    android.bluetooth.BluetoothDevice.EXTRA_DEVICE,
                    android.bluetooth.BluetoothDevice::class.java,
                )
            if (d?.address?.equals(macAddress, ignoreCase = true) != true) return

            val state =
                intent.getIntExtra(
                    android.bluetooth.BluetoothDevice.EXTRA_BOND_STATE,
                    android.bluetooth.BluetoothDevice.ERROR,
                )
            val prevState =
                intent.getIntExtra(
                    android.bluetooth.BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE,
                    android.bluetooth.BluetoothDevice.ERROR,
                )

            if (state == android.bluetooth.BluetoothDevice.BOND_BONDED) {
                result.complete(Unit)
            } else if (
                state == android.bluetooth.BluetoothDevice.BOND_NONE &&
                prevState == android.bluetooth.BluetoothDevice.BOND_BONDING
            ) {
                result.completeExceptionally(Exception(BOND_FAILED_OR_REJECTED_MESSAGE))
            }
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun unregisterBondReceiver(receiver: android.content.BroadcastReceiver) {
        try {
            context.unregisterReceiver(receiver)
        } catch (ignored: Exception) {}
    }

    internal suspend fun updateBluetoothState() {
        val enabled = bluetoothAdapter?.isEnabled == true
        var hasPermissions = hasBluetoothPermissions()
        val bondedDevices =
            if (hasPermissions) {
                try {
                    getBondedAppPeripherals()
                } catch (e: SecurityException) {
                    Logger.w(e) { "SecurityException accessing bonded devices. Missing BLUETOOTH_CONNECT?" }
                    hasPermissions = false
                    emptyList()
                }
            } else {
                emptyList()
            }

        val newState = BluetoothState(hasPermissions = hasPermissions, enabled = enabled, bondedDevices = bondedDevices)

        _state.emit(newState)
        Logger.d { "Detected our bluetooth access=$newState" }
    }

    @SuppressLint("MissingPermission")
    private fun getBondedAppPeripherals(): List<BleDevice> {
        val bonded = bluetoothAdapter?.bondedDevices ?: return emptyList()
        val bondedAddresses = bonded.mapTo(mutableSetOf()) { it.address }
        // Evict entries for devices that are no longer bonded and update names in case the
        // user renamed the device in firmware since the cache was populated.
        deviceCache.keys.retainAll(bondedAddresses)
        return bonded.map { device ->
            val cached = deviceCache.getOrPut(device.address) { MeshtasticBleDevice(device.address, device.name) }
            // If the name changed (firmware rename, etc.), replace the cached entry and return the new one.
            if (cached.name != device.name) {
                val updated = MeshtasticBleDevice(device.address, device.name)
                deviceCache[device.address] = updated
                updated
            } else {
                cached
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun isBonded(address: String): Boolean = try {
        bluetoothAdapter?.bondedDevices?.any { it.address.equals(address, ignoreCase = true) } ?: false
    } catch (e: SecurityException) {
        Logger.w(e) { "SecurityException checking bonded devices. Missing BLUETOOTH_CONNECT?" }
        false
    }
}
