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

import androidx.lifecycle.ViewModel
import androidx.navigation3.runtime.NavKey
import org.koin.core.annotation.KoinViewModel

/** ViewModel for the app introduction flow. */
@KoinViewModel
class IntroViewModel : ViewModel() {

    /**
     * Determines the next navigation key based on the current key and the state of permissions. The flow hierarchy is:
     * Core Connection -> Shared Location -> Notifications -> Done.
     *
     * @param bluetoothRequiresLocation true on API < 31, where BLE scanning is gated by ACCESS_FINE_LOCATION rather
     *   than the Android 12 "Nearby devices" permissions. There the Bluetooth screen has *already asked for the
     *   location permission*, so following it with a Location screen asks the user for the same system permission twice
     *   in a row — and a user who declines both has spent both of Android's allowed denials before ever seeing the app,
     *   landing on USER_FIXED with no dialog available again. The Bluetooth screen covers both uses on those releases,
     *   so the second ask is dropped rather than duplicated.
     */
    fun getNextKey(
        currentKey: NavKey,
        allPermissionsGranted: Boolean,
        bluetoothRequiresLocation: Boolean = false,
    ): NavKey? = when (currentKey) {
        is Welcome -> Bluetooth
        is Bluetooth -> if (bluetoothRequiresLocation) Notifications else Location
        is Location -> Notifications
        is Notifications -> if (allPermissionsGranted) CriticalAlerts else null
        is CriticalAlerts -> null
        else -> null
    }
}
