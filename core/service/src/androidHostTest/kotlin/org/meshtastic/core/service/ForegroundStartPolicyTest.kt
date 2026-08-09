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
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Rules for [ForegroundStartPolicy].
 *
 * These cases encode the two platform gates that produced the 2.8.0 foreground-service failures: the Android 12+
 * background-start restriction, and the Android 14+ while-in-use restriction on the `location` service type.
 */
class ForegroundStartPolicyTest {

    private companion object {
        const val ANDROID_10 = 29
        const val ANDROID_11 = 30
        const val ANDROID_12 = 31
        const val ANDROID_13 = 33
        const val ANDROID_14 = 34
        const val ANDROID_17 = 37

        const val CONNECTED_DEVICE = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        const val LOCATION = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION

        /** Platform levels that enforce the background foreground-service-start restriction. */
        val RESTRICTED_LEVELS = listOf(ANDROID_12, ANDROID_13, ANDROID_14, ANDROID_17)
    }

    // region background-start restriction

    @Test
    fun `before Android 12 every trigger may start in the background`() {
        ServiceStartTrigger.entries.forEach { trigger ->
            assertTrue(
                ForegroundStartPolicy.isForegroundStartAllowed(trigger, appInForeground = false, sdkInt = ANDROID_11),
                "$trigger should be allowed pre-Android-12",
            )
        }
    }

    @Test
    fun `a user-visible start is allowed on every platform`() {
        RESTRICTED_LEVELS.forEach { sdk ->
            assertTrue(
                ForegroundStartPolicy.isForegroundStartAllowed(
                    ServiceStartTrigger.UserInterface,
                    appInForeground = true,
                    sdkInt = sdk,
                ),
                "UserInterface should be allowed on API $sdk",
            )
        }
    }

    @Test
    fun `boot completed keeps its exemption even though the process is not yet visible`() {
        RESTRICTED_LEVELS.forEach { sdk ->
            assertTrue(
                ForegroundStartPolicy.isForegroundStartAllowed(
                    ServiceStartTrigger.BootCompleted,
                    appInForeground = false,
                    sdkInt = sdk,
                ),
                "BootCompleted should be allowed on API $sdk",
            )
        }
    }

    @Test
    fun `a backgrounded device-address change is refused`() {
        RESTRICTED_LEVELS.forEach { sdk ->
            assertFalse(
                ForegroundStartPolicy.isForegroundStartAllowed(
                    ServiceStartTrigger.DeviceAddressChanged,
                    appInForeground = false,
                    sdkInt = sdk,
                ),
                "DeviceAddressChanged should be refused while backgrounded on API $sdk",
            )
        }
    }

    @Test
    fun `a device-address change while the app is visible is allowed`() {
        RESTRICTED_LEVELS.forEach { sdk ->
            assertTrue(
                ForegroundStartPolicy.isForegroundStartAllowed(
                    ServiceStartTrigger.DeviceAddressChanged,
                    appInForeground = true,
                    sdkInt = sdk,
                ),
                "DeviceAddressChanged should be allowed while visible on API $sdk",
            )
        }
    }

    // endregion

    // region service type selection

    @Test
    fun `no service type is claimed before Android 10`() {
        assertEquals(
            0,
            ForegroundStartPolicy.foregroundServiceType(
                hasLocationPermission = true,
                appInForeground = true,
                sdkInt = 28,
            ),
        )
    }

    @Test
    fun `connectedDevice alone is claimed without location permission`() {
        assertEquals(
            CONNECTED_DEVICE,
            ForegroundStartPolicy.foregroundServiceType(
                hasLocationPermission = false,
                appInForeground = true,
                sdkInt = ANDROID_17,
            ),
        )
    }

    @Test
    fun `location is claimed in the background before Android 14`() {
        listOf(ANDROID_10, ANDROID_12, ANDROID_13).forEach { sdk ->
            assertEquals(
                CONNECTED_DEVICE or LOCATION,
                ForegroundStartPolicy.foregroundServiceType(
                    hasLocationPermission = true,
                    appInForeground = false,
                    sdkInt = sdk,
                ),
                "location should still be claimable in the background on API $sdk",
            )
        }
    }

    @Test
    fun `location is dropped for a background start from Android 14`() {
        // The app does not declare ACCESS_BACKGROUND_LOCATION, so claiming the location type here would throw
        // SecurityException and take the whole foreground start with it.
        listOf(ANDROID_14, ANDROID_17).forEach { sdk ->
            assertEquals(
                CONNECTED_DEVICE,
                ForegroundStartPolicy.foregroundServiceType(
                    hasLocationPermission = true,
                    appInForeground = false,
                    sdkInt = sdk,
                ),
                "location must be dropped for a background start on API $sdk",
            )
        }
    }

    @Test
    fun `location is claimed for a foreground start on Android 14 and later`() {
        listOf(ANDROID_14, ANDROID_17).forEach { sdk ->
            assertEquals(
                CONNECTED_DEVICE or LOCATION,
                ForegroundStartPolicy.foregroundServiceType(
                    hasLocationPermission = true,
                    appInForeground = true,
                    sdkInt = sdk,
                ),
                "location should be claimable while visible on API $sdk",
            )
        }
    }

    @Test
    fun `connectedDevice is always claimed when types are supported`() {
        listOf(ANDROID_10, ANDROID_12, ANDROID_13, ANDROID_14, ANDROID_17).forEach { sdk ->
            listOf(true, false).forEach { hasLocation ->
                listOf(true, false).forEach { visible ->
                    val types =
                        ForegroundStartPolicy.foregroundServiceType(
                            hasLocationPermission = hasLocation,
                            appInForeground = visible,
                            sdkInt = sdk,
                        )
                    assertEquals(
                        CONNECTED_DEVICE,
                        types and CONNECTED_DEVICE,
                        "connectedDevice missing on API $sdk (location=$hasLocation, visible=$visible)",
                    )
                }
            }
        }
    }

    // endregion

    // region fallback type

    @Test
    fun `the fallback type is connectedDevice only`() {
        assertEquals(CONNECTED_DEVICE, ForegroundStartPolicy.fallbackForegroundServiceType(sdkInt = ANDROID_17))
        assertEquals(0, ForegroundStartPolicy.fallbackForegroundServiceType(sdkInt = 28))
    }

    @Test
    fun `the fallback never claims a while-in-use restricted type`() {
        listOf(ANDROID_10, ANDROID_12, ANDROID_14, ANDROID_17).forEach { sdk ->
            val fallback = ForegroundStartPolicy.fallbackForegroundServiceType(sdkInt = sdk)
            assertEquals(0, fallback and LOCATION, "fallback must not include location on API $sdk")
        }
    }

    // endregion

    /**
     * The invariant behind the crash: any start the policy permits must also be startable with the types the policy
     * hands out. A permitted background start that still asks for `location` on Android 14+ would throw
     * `SecurityException` inside the service, which is exactly the failure this policy exists to prevent.
     */
    @Test
    fun `a permitted background start never asks for a while-in-use restricted type`() {
        RESTRICTED_LEVELS.forEach { sdk ->
            ServiceStartTrigger.entries
                .filter { ForegroundStartPolicy.isForegroundStartAllowed(it, appInForeground = false, sdkInt = sdk) }
                .forEach { trigger ->
                    val types =
                        ForegroundStartPolicy.foregroundServiceType(
                            hasLocationPermission = true,
                            appInForeground = false,
                            sdkInt = sdk,
                        )
                    if (sdk >= ANDROID_14) {
                        assertEquals(
                            0,
                            types and LOCATION,
                            "$trigger on API $sdk is permitted in the background but claims location",
                        )
                    }
                }
        }
    }
}
