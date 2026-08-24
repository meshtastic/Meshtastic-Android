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

import org.meshtastic.core.ui.util.PermissionUiState

internal class AndroidIntroPermissions(
    private val bluetoothState: PermissionUiState,
    private val locationState: PermissionUiState,
    private val notificationState: PermissionUiState?,
) : IntroPermissions {
    override val bluetooth: IntroPermissionState =
        object : IntroPermissionState {
            override val isGranted: Boolean
                get() = bluetoothState.isGranted

            override fun launchRequest() = bluetoothState.request()
        }

    override val location: IntroPermissionState =
        object : IntroPermissionState {
            override val isGranted: Boolean
                get() = locationState.isGranted

            override fun launchRequest() = locationState.request()
        }

    override val notification: IntroPermissionState? =
        notificationState?.let { state ->
            object : IntroPermissionState {
                override val isGranted: Boolean
                    get() = state.isGranted

                override fun launchRequest() = state.request()
            }
        }
}
