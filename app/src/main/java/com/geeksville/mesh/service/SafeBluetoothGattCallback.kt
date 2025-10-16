/*
 * Copyright (c) 2024-2025 Meshtastic LLC
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

package com.geeksville.mesh.service

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.os.Build
import com.geeksville.mesh.logAssert
import com.geeksville.mesh.util.exceptionReporter
import timber.log.Timber

/**
 * Our customized GattCallback.
 *
 * It is in its own class to keep SafeBluetooth smaller.
 */
@Suppress("TooManyFunctions")
internal class SafeBluetoothGattCallback(private val safeBluetooth: SafeBluetooth) : BluetoothGattCallback() {

    private val workQueue = safeBluetooth.workQueue

    override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) = exceptionReporter {
        Timber.i("new bluetooth connection state $newState, status $status")

        when (newState) {
            BluetoothProfile.STATE_CONNECTED -> {
                safeBluetooth.state =
                    newState // we only care about connected/disconnected - not the transitional states

                // If autoconnect is on and this connect attempt failed, hopefully some future attempt will
                // succeed
                if (status != BluetoothGatt.GATT_SUCCESS && safeBluetooth.autoReconnect) {
                    Timber.e("Connect attempt failed $status, not calling connect completion handler...")
                } else {
                    workQueue.completeWork(status, Unit)
                }
            }

            BluetoothProfile.STATE_DISCONNECTED -> {
                if (safeBluetooth.gatt == null) {
                    Timber.e("No gatt: ignoring connection state $newState, status $status")
                } else if (safeBluetooth.isClosing) {
                    Timber.i("Got disconnect because we are shutting down, closing gatt")
                    safeBluetooth.gatt = null
                    g.close() // Finish closing our gatt here
                } else {
                    // cancel any queued ops if we were already connected
                    val oldstate = safeBluetooth.state
                    safeBluetooth.state = newState
                    if (oldstate == BluetoothProfile.STATE_CONNECTED) {
                        Timber.i("Lost connection - aborting current work: ${workQueue.currentWork}")

                        // If autoReconnect is true and we are in a state where reconnecting makes sense
                        if (
                            safeBluetooth.autoReconnect &&
                            (workQueue.currentWork == null || workQueue.currentWork?.isConnect() == true)
                        ) {
                            safeBluetooth.dropAndReconnect()
                        } else {
                            safeBluetooth.lostConnection("lost connection")
                        }
                    } else if (status == 133) {
                        // We were not previously connected and we just failed with our non-auto connection
                        // attempt.  Therefore we now need
                        // to do an autoconnection attempt.  When that attempt succeeds/fails the normal
                        // callbacks will be called

                        // Note: To workaround https://issuetracker.google.com/issues/36995652
                        // Always call BluetoothDevice#connectGatt() with autoConnect=false
                        // (the race condition does not affect that case). If that connection times out
                        // you will get a callback with status=133. Then call BluetoothGatt#connect()
                        // to initiate a background connection.
                        if (safeBluetooth.autoReconnect) {
                            Timber.w("Failed on non-auto connect, falling back to auto connect attempt")
                            safeBluetooth.closeGatt() // Close the old non-auto connection
                            safeBluetooth.lowLevelConnect(true)
                        }
                    } else if (status == 147) {
                        Timber.i("got 147, calling lostConnection()")
                        safeBluetooth.lostConnection("code 147")
                    }

                    if (status == 257) { // mystery error code when phone is hung
                        // throw Exception("Mystery bluetooth failure - debug me")
                        safeBluetooth.restartBle()
                    }
                }
            }
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        // For testing lie and claim failure
        workQueue.completeWork(status, Unit)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
        workQueue.completeWork(status, characteristic)
    }

    // API 33+ callback with value parameter (overload for modern Android)
    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int,
    ) {
        // Store value in characteristic for compatibility with existing code
        // Note: This is safe because we clone the value before using it
        @Suppress("DEPRECATION")
        characteristic.value = value
        workQueue.completeWork(status, characteristic)
    }

    override fun onReliableWriteCompleted(gatt: BluetoothGatt, status: Int) {
        workQueue.completeWork(status, Unit)
    }

    override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
        val reliable = safeBluetooth.currentReliableWrite
        if (reliable != null) {
            @Suppress("DEPRECATION")
            val charValue = characteristic.value
            if (!charValue.contentEquals(reliable)) {
                Timber.e("A reliable write failed!")
                gatt.abortReliableWrite()
                workQueue.completeWork(
                    SafeBluetooth.STATUS_RELIABLE_WRITE_FAILED,
                    characteristic,
                ) // skanky code to indicate failure
            } else {
                logAssert(gatt.executeReliableWrite())
                // After this execute reliable completes - we can continue with normal operations (see
                // onReliableWriteCompleted)
            }
        } else {
            // Just a standard write - do the normal flow
            workQueue.completeWork(status, characteristic)
        }
    }

    override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
        // Alas, passing back an Int mtu isn't working and since I don't really care what MTU
        // the device was willing to let us have I'm just punting and returning Unit
        if (workQueue.isSettingMtu) workQueue.completeWork(status, Unit) else Timber.e("Ignoring bogus onMtuChanged")
    }

    /** Callback triggered as a result of a remote characteristic notification. */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        safeBluetooth.notifyHandlers[characteristic.uuid]?.let { handler ->
            exceptionReporter { handler(characteristic) }
        } ?: Timber.w("Received notification from $characteristic, but no handler registered")
    }

    // API 33+ callback with value parameter (overload for modern Android)
    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
    ) {
        // Store value in characteristic for compatibility with existing code
        @Suppress("DEPRECATION")
        characteristic.value = value
        onCharacteristicChanged(gatt, characteristic)
    }

    /** Callback indicating the result of a descriptor write operation. */
    override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
        workQueue.completeWork(status, descriptor)
    }

    /** Callback reporting the result of a descriptor read operation. */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onDescriptorRead(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
        workQueue.completeWork(status, descriptor)
    }

    // API 33+ callback with value parameter for descriptor read (overload for modern Android)
    override fun onDescriptorRead(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int,
        value: ByteArray,
    ) {
        // Store value in descriptor for compatibility with existing code
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            @Suppress("DEPRECATION")
            descriptor.value = value
        }
        workQueue.completeWork(status, descriptor)
    }

    // Added: callback for remote RSSI reads
    override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
        workQueue.completeWork(status, rssi)
    }
}
