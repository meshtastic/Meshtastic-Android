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

import android.os.Build
import co.touchlab.kermit.Logger
import com.juul.kable.AndroidPeripheral
import com.juul.kable.ExperimentalKableApi
import com.juul.kable.NotConnectedException
import com.juul.kable.Peripheral

@OptIn(ExperimentalKableApi::class)
internal actual fun Peripheral.refreshGattCache(): Boolean {
    val androidPeripheral =
        this as? AndroidPeripheral
            ?: run {
                logRefreshFailure("not-android peripheral=${javaClass.name}")
                return false
            }
    return try {
        val refreshed = androidPeripheral.refreshGattCache()
        Logger.i {
            "$REFRESH_LOG_LABEL outcome=${if (refreshed) "refreshed" else "refused"} sdk=${Build.VERSION.SDK_INT}"
        }
        refreshed
    } catch (e: NotConnectedException) {
        logRefreshFailure("not-connected", e)
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
