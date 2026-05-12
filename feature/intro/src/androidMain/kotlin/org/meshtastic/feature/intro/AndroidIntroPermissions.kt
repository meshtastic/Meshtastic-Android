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

import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted

@OptIn(ExperimentalPermissionsApi::class)
internal class AndroidIntroPermissions(
    private val bluetoothState: MultiplePermissionsState,
    private val locationState: MultiplePermissionsState,
    private val notificationState: PermissionState?,
) : IntroPermissions {
    override val bluetooth: IntroPermissionState =
        object : IntroPermissionState {
            override val isGranted: Boolean
                get() = bluetoothState.allPermissionsGranted

            override fun launchRequest() = bluetoothState.launchMultiplePermissionRequest()
        }

    override val location: IntroPermissionState =
        object : IntroPermissionState {
            override val isGranted: Boolean
                get() = locationState.allPermissionsGranted

            override fun launchRequest() = locationState.launchMultiplePermissionRequest()
        }

    override val notification: IntroPermissionState? =
        notificationState?.let { state ->
            object : IntroPermissionState {
                override val isGranted: Boolean
                    get() = state.status.isGranted

                override fun launchRequest() = state.launchPermissionRequest()
            }
        }
}
