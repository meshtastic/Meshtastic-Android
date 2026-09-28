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

import android.bluetooth.BluetoothGatt
import android.os.Build
import co.touchlab.kermit.Logger
import com.juul.kable.Peripheral
import kotlinx.coroutines.flow.StateFlow

@Suppress("ReturnCount")
internal actual fun Peripheral.refreshGattCache(): Boolean {
    // Direct 2-hop reflection on Kable 0.45.0 internals.
    // Path: BluetoothDeviceAndroidPeripheral.connection (MutableStateFlow<Connection?>) → .value → Connection.gatt
    // Re-verify field names on Kable version bumps.
    val gatt =
        try {
            extractBluetoothGatt()
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logRefreshFailure("gatt-unreachable peripheral=${javaClass.name}", e)
            return false
        }
            ?: run {
                logRefreshFailure("gatt-unreachable peripheral=${javaClass.name}")
                return false
            }
    return try {
        val refreshMethod = gatt.javaClass.getDeclaredMethod("refresh").apply { isAccessible = true }
        val refreshed = refreshMethod.invoke(gatt) as? Boolean ?: false
        Logger.i {
            "$REFRESH_LOG_LABEL outcome=${if (refreshed) "refreshed" else "refused"} sdk=${Build.VERSION.SDK_INT}"
        }
        refreshed
    } catch (e: NoSuchMethodException) {
        // The hidden-API blocklist hides the method rather than throwing on access, so this is how a block shows up.
        logRefreshFailure("hidden-api-blocked", e)
        false
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        logRefreshFailure("invoke-failed", e)
        false
    }
}

/** A stable label, so the field logs show when refresh() stops working and on which API level. */
private const val REFRESH_LOG_LABEL = "gatt-cache-refresh"

private fun logRefreshFailure(outcome: String, cause: Throwable? = null) {
    Logger.w(cause) { "$REFRESH_LOG_LABEL outcome=$outcome sdk=${Build.VERSION.SDK_INT}" }
}

/**
 * Extracts the [BluetoothGatt] from Kable's internal object graph via a 2-hop field lookup.
 *
 * Hop 1: `Peripheral` → field `"connection"` (`MutableStateFlow<Connection?>`) → `.value` → `Connection` Hop 2:
 * `Connection` → field `"gatt"` → `BluetoothGatt`
 *
 * Returns `null` if either hop fails (e.g., Kable internals changed between versions).
 */
@Suppress("ReturnCount")
private fun Peripheral.extractBluetoothGatt(): BluetoothGatt? {
    // Hop 1: Read "connection" field from the concrete Peripheral class (BluetoothDeviceAndroidPeripheral)
    val connectionField = readDeclaredField("connection") ?: return null
    val connectionStateFlow = connectionField as? StateFlow<*> ?: return null
    val connection = connectionStateFlow.value ?: return null

    // Hop 2: Read "gatt" field from the Connection class
    return connection.readDeclaredFieldAs<BluetoothGatt>("gatt")
}

private fun Any.readDeclaredField(name: String): Any? {
    var clazz: Class<*>? = javaClass
    while (clazz != null && clazz != Any::class.java) {
        try {
            val field = clazz.getDeclaredField(name)
            field.isAccessible = true
            return field.get(this)
        } catch (_: NoSuchFieldException) {
            clazz = clazz.superclass
        }
    }
    return null
}

private inline fun <reified T> Any.readDeclaredFieldAs(name: String): T? = readDeclaredField(name) as? T
