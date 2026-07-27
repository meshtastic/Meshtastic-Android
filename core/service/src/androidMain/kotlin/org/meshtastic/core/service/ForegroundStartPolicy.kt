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
package org.meshtastic.core.service

import android.content.pm.ServiceInfo
import android.os.Build

/**
 * Decides whether starting [MeshService] as a foreground service is legal, and which foreground service types it may
 * claim.
 *
 * Deliberately pure: every platform fact arrives as a parameter so the rules can be unit tested without a device. The
 * Android probes that supply those facts live in `ForegroundStartContext.kt`.
 *
 * Two independent platform gates are modelled here.
 * 1. **Background-start restriction (Android 12+).** `startForegroundService()` throws
 *    `ForegroundServiceStartNotAllowedException` unless the caller is in an allowlisted context. Covered by
 *    [isForegroundStartAllowed].
 * 2. **While-in-use type restriction (Android 14+).** A `location` foreground service cannot be *started* from the
 *    background without `ACCESS_BACKGROUND_LOCATION`, which this app does not declare; attempting it throws
 *    `SecurityException`. `connectedDevice` carries no such restriction. Covered by [foregroundServiceType].
 */
object ForegroundStartPolicy {

    /** Android 12 (API 31) introduced the background foreground-service start restriction. */
    private const val SDK_BACKGROUND_START_RESTRICTED = Build.VERSION_CODES.S

    /** Android 10 (API 29) introduced foreground service types. */
    private const val SDK_SERVICE_TYPES = Build.VERSION_CODES.Q

    /** Android 14 (API 34) began enforcing while-in-use restrictions on the `location` type at start time. */
    private const val SDK_WHILE_IN_USE_ENFORCED = Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    /**
     * Returns true when `Context.startForegroundService()` is expected to succeed for [trigger].
     *
     * Returning false is not a failure to recover from — it means the platform would reject the start, so the app must
     * not make the call at all. The service is picked up by the next legitimate trigger, which in practice is the user
     * opening the app ([ServiceStartTrigger.UserInterface]).
     *
     * @param appInForeground whether this process currently holds a user-visible component.
     * @param sdkInt the running platform API level.
     */
    fun isForegroundStartAllowed(
        trigger: ServiceStartTrigger,
        appInForeground: Boolean,
        sdkInt: Int = Build.VERSION.SDK_INT,
    ): Boolean = when {
        // Before Android 12 there is no background-start restriction at all.
        sdkInt < SDK_BACKGROUND_START_RESTRICTED -> true

        // An activity moving to started is the canonical "transitions from a user-visible state" exemption, and
        // BOOT_COMPLETED / MY_PACKAGE_REPLACED are named exemptions. Neither depends on current process importance:
        // at boot the process is not yet user-visible, which is precisely what the exemption exists for.
        trigger == ServiceStartTrigger.UserInterface -> true

        trigger == ServiceStartTrigger.BootCompleted -> true

        // No exemption of its own — legal only while the app is genuinely still in the foreground.
        else -> appInForeground
    }

    /**
     * Returns the foreground service type bitmask to pass to `startForeground()`.
     *
     * `connectedDevice` is always claimed: it is the type that actually describes the radio link, it is not subject to
     * while-in-use restrictions, and its prerequisite (`BLUETOOTH_CONNECT`) is declared. `location` is additive and is
     * claimed only when it can be held legally, because requesting it in a state the platform forbids throws
     * `SecurityException` and takes the whole start down with it.
     *
     * @param hasLocationPermission whether `ACCESS_FINE_LOCATION` is granted.
     * @param appInForeground whether this process currently holds a user-visible component.
     * @param sdkInt the running platform API level.
     */
    fun foregroundServiceType(
        hasLocationPermission: Boolean,
        appInForeground: Boolean,
        sdkInt: Int = Build.VERSION.SDK_INT,
    ): Int {
        if (sdkInt < SDK_SERVICE_TYPES) return 0

        val canClaimLocation =
            hasLocationPermission &&
                // From Android 14 the location type is while-in-use restricted at start time. The app does not declare
                // ACCESS_BACKGROUND_LOCATION, so a background start can never hold it.
                (sdkInt < SDK_WHILE_IN_USE_ENFORCED || appInForeground)

        return if (canClaimLocation) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        }
    }

    /**
     * The fallback type used when [foregroundServiceType] is still rejected at runtime. Always safe to claim when the
     * service may run at all.
     */
    fun fallbackForegroundServiceType(sdkInt: Int = Build.VERSION.SDK_INT): Int =
        if (sdkInt < SDK_SERVICE_TYPES) 0 else ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
}
