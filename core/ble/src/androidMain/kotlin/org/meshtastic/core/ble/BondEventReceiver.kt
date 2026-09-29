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
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Logs Bluetooth bond broadcasts for the radios the app bonds to or connects to, so field data can show how often bonds
 * are lost and what follows. It changes nothing about bonding or reconnection.
 *
 * The receiver registers on the first [watch] made while BLUETOOTH_CONNECT is granted, and stays registered until
 * [scope] is cancelled.
 */
internal class BondEventReceiver(
    private val context: Context,
    private val scope: CoroutineScope,
    private val connectingAddress: () -> String? = { ActiveBleConnection.active?.address },
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) {
    private val watched: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val registered = AtomicBoolean(false)

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val event = intent.toBondEvent() ?: return
                if (!isRadio(event.address)) return
                val line = event.toLogLine(sdkLabel())
                Logger.log(line.severity, Logger.tag, null, line.message)
            }
        }

    /** Marks [address] as a radio whose bond events are logged, registering the receiver if it can now. */
    fun watch(address: String) {
        watched += address.uppercase()
        if (!registered.get() && hasConnectPermission() && registered.compareAndSet(false, true)) {
            // Undispatched so the receiver is registered before a bond() that follows can broadcast.
            scope.launch(start = CoroutineStart.UNDISPATCHED) { receiveUntilCancelled() }
        }
    }

    private suspend fun receiveUntilCancelled() {
        val filter = IntentFilter().apply { bondEventActions(sdkInt).forEach(::addAction) }
        // The Bluetooth app sends these under its own uid, which a NOT_EXPORTED receiver refuses. All three are
        // protected broadcasts, so exporting admits no other sender.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        try {
            awaitCancellation()
        } finally {
            context.unregisterReceiver(receiver)
        }
    }

    private fun isRadio(address: String): Boolean {
        val normalized = address.uppercase()
        return normalized in watched || normalized == connectingAddress()?.uppercase()
    }

    private fun hasConnectPermission(): Boolean = sdkInt < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
        PackageManager.PERMISSION_GRANTED
}

internal fun Intent.toBondEvent(): BondEvent? {
    val address =
        IntentCompat.getParcelableExtra(this, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)?.address
            ?: return null
    return when (action) {
        BluetoothDevice.ACTION_BOND_STATE_CHANGED ->
            BondEvent.BondStateChanged(
                address = address,
                previous = getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR),
                current = getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR),
            )

        BluetoothDevice.ACTION_KEY_MISSING ->
            BondEvent.KeyMissing(
                address = address,
                lossReason =
                if (hasExtra(BluetoothDevice.EXTRA_BOND_LOSS_REASON)) {
                    getIntExtra(BluetoothDevice.EXTRA_BOND_LOSS_REASON, BluetoothDevice.ERROR)
                } else {
                    null
                },
            )

        BluetoothDevice.ACTION_ENCRYPTION_CHANGE ->
            BondEvent.EncryptionChange(
                address = address,
                encrypted = getBooleanExtra(BluetoothDevice.EXTRA_ENCRYPTION_ENABLED, false),
                status = getIntExtra(BluetoothDevice.EXTRA_ENCRYPTION_STATUS, BluetoothDevice.ERROR),
            )

        else -> null
    }
}

/** `36.1` style from API 36, where `SDK_INT_FULL` carries the minor release; the plain API level before it. */
private fun sdkLabel(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
    val full = Build.VERSION.SDK_INT_FULL
    "${Build.getMajorSdkVersion(full)}.${Build.getMinorSdkVersion(full)}"
} else {
    Build.VERSION.SDK_INT.toString()
}
