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
package org.meshtastic.feature.intro

/** JVM/Desktop stub: permissions are always granted (desktop doesn't need BLE/location onboarding). */
internal object JvmIntroPermissions : IntroPermissions {
    private val grantedState =
        object : IntroPermissionState {
            override val isGranted: Boolean = true

            override fun launchRequest() = Unit
        }

    override val bluetooth: IntroPermissionState = grantedState
    override val location: IntroPermissionState = grantedState
    override val notification: IntroPermissionState = grantedState
}

/** JVM/Desktop stub: settings navigation is a no-op. */
internal object JvmIntroSettingsNavigator : IntroSettingsNavigator {
    override fun openAppSettings() = Unit

    override fun openCriticalAlertsSettings() = Unit
}
