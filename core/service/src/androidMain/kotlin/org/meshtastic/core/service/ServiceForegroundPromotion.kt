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

package org.meshtastic.core.service

import android.app.Notification
import android.app.Service
import androidx.core.app.ServiceCompat
import co.touchlab.kermit.Logger
import org.meshtastic.core.repository.SERVICE_NOTIFY_ID

/**
 * Promotes [this] service to the foreground, returning whether it actually got there.
 *
 * The boolean is the whole point. Every caller of `startForegroundService()` leaves ActivityManager holding a
 * pending-start watchdog against the service; letting a failure return quietly is what turns a recoverable
 * `ForegroundServiceStartNotAllowedException` into a fatal `ForegroundServiceDidNotStartInTimeException` five seconds
 * later. A false result obliges the caller to stop the service immediately.
 *
 * `IllegalStateException` is the supertype of the API 31+ `ForegroundServiceStartNotAllowedException` and of the API
 * 34+ invalid/missing-type exceptions, so catching it covers all of them without an API-gated class reference.
 */
internal fun Service.startForegroundSafely(notification: Notification, foregroundServiceType: Int): Boolean = try {
    ServiceCompat.startForeground(this, SERVICE_NOTIFY_ID, notification, foregroundServiceType)
    true
} catch (ex: IllegalStateException) {
    Logger.e(ex) { "Foreground start not allowed by the OS" }
    false
} catch (ex: SecurityException) {
    retryWithoutRefusedType(ex, notification, foregroundServiceType)
} catch (ex: IllegalArgumentException) {
    // AOSP surfaces a refused type as SecurityException, but a type/manifest mismatch (or OEM variance in how the
    // refusal is reported) arrives as IllegalArgumentException. connectedDevice is always manifest-declared, so the
    // same fallback applies.
    retryWithoutRefusedType(ex, notification, foregroundServiceType)
} catch (ex: Exception) {
    Logger.e(ex) { "Error starting foreground service" }
    false
}

/**
 * Handles a refused foreground-service type ([SecurityException] on AOSP, [IllegalArgumentException] on a type/manifest
 * mismatch) by dropping back to `connectedDevice` alone, which carries no while-in-use restriction and is always
 * declared in the manifest. Only the additive `location` type can be refused this way, so when it was not requested
 * there is nothing left to give up.
 */
private fun Service.retryWithoutRefusedType(
    ex: RuntimeException,
    notification: Notification,
    requestedType: Int,
): Boolean {
    val fallbackType = ForegroundStartPolicy.fallbackForegroundServiceType()
    if (requestedType == fallbackType) {
        Logger.e(ex) { "Foreground start refused with no additive type to drop" }
        return false
    }
    Logger.w(ex) { "Foreground start refused with type $requestedType, retrying as connectedDevice" }
    return retryForegroundAsConnectedDevice(notification, fallbackType)
}

private fun Service.retryForegroundAsConnectedDevice(notification: Notification, fallbackType: Int): Boolean = try {
    ServiceCompat.startForeground(this, SERVICE_NOTIFY_ID, notification, fallbackType)
    true
} catch (retryEx: Exception) {
    Logger.e(retryEx) { "Failed to start foreground service even after retry" }
    false
}
