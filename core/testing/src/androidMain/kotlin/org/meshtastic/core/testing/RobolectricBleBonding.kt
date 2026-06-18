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
package org.meshtastic.core.testing

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Looper
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowBluetoothDevice

/**
 * Reusable Robolectric helpers for driving Android Bluetooth bonding logic in `androidHostTest` source sets.
 *
 * These let a unit test exercise the real `AndroidBluetoothRepository.bond()` (and any future BLE bonding code) without
 * an emulator and without a production seam. They rely on two Robolectric behaviors (verified against Robolectric
 * 4.16.x):
 * - [android.bluetooth.BluetoothAdapter.getRemoteDevice] caches the returned [BluetoothDevice] by address in a static
 *   map, so the shadow configured here is the *same* instance production code reads when it calls
 *   `getRemoteDevice(mac)` internally.
 * - [ShadowBluetoothDevice.createBond] calls `checkForBluetoothConnectPermission()` first, so tests must call
 *   [grantBluetoothConnectPermission] or `createBond()` throws [SecurityException] instead of returning a value.
 *
 * Isolation note: because the device cache is static and survives across tests in the same JVM, give each test a
 * distinct MAC so bond-state cannot bleed between tests.
 */
object RobolectricBleBonding {

    /** A syntactically valid BLE MAC for tests that don't care about the specific address. */
    const val TEST_BLE_MAC: String = "AA:BB:CC:DD:EE:FF"

    private val application: Application
        get() = RuntimeEnvironment.getApplication()

    /**
     * The default adapter Robolectric exposes; production resolves the same one via
     * [android.bluetooth.BluetoothManager].
     */
    private val adapter: BluetoothAdapter
        get() = BluetoothAdapter.getDefaultAdapter()

    /** Grant the runtime permissions [ShadowBluetoothDevice.createBond] checks, so it returns instead of throwing. */
    fun grantBluetoothConnectPermission() {
        shadowOf(application)
            .grantPermissions(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    }

    /**
     * The (address-cached) [ShadowBluetoothDevice] for [mac] — the same instance production's `getRemoteDevice` sees.
     */
    fun deviceShadow(mac: String = TEST_BLE_MAC): ShadowBluetoothDevice = shadowOf(adapter.getRemoteDevice(mac))

    /**
     * Configure the cached device for [mac]: its [bondState] and what `createBond()` returns.
     *
     * Defaulting [createBondReturns] to `false` reproduces the #5849 entry condition (createBond() returned false →
     * re-check bondState). Note Robolectric's `createBond()` sets bondState to BONDED only when [createBondReturns] is
     * `true`; with `false` the [bondState] you set here is what the production re-check reads.
     */
    fun primeBond(
        mac: String = TEST_BLE_MAC,
        bondState: Int = BluetoothDevice.BOND_NONE,
        createBondReturns: Boolean = false,
    ): ShadowBluetoothDevice = deviceShadow(mac).apply {
        setBondState(bondState)
        setCreatedBond(createBondReturns)
    }

    /**
     * Broadcast `ACTION_BOND_STATE_CHANGED` for [mac] and pump the main looper so a runtime-registered
     * [android.content.BroadcastReceiver] (e.g. the one `bond()` registers) is delivered synchronously.
     */
    fun sendBondStateChanged(mac: String, newState: Int, previousState: Int) {
        val intent =
            Intent(BluetoothDevice.ACTION_BOND_STATE_CHANGED).apply {
                putExtra(BluetoothDevice.EXTRA_DEVICE, adapter.getRemoteDevice(mac))
                putExtra(BluetoothDevice.EXTRA_BOND_STATE, newState)
                putExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, previousState)
            }
        application.sendBroadcast(intent)
        shadowOf(Looper.getMainLooper()).idle()
    }
}
