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

import org.meshtastic.core.model.ConnectionState

/**
 * Decides how `MeshCompanionDeviceService` reacts to Companion Device Manager presence events.
 *
 * Deliberately pure, in the mold of [ForegroundStartPolicy]: every platform fact arrives as a parameter so the rules
 * can be unit tested without a device. Presence events arrive for *any* associated radio, so the rule first insists the
 * event is about the currently selected one — other associations are simply older radios the user still owns.
 */
object CompanionPresencePolicy {

    /**
     * Whether a device-appeared event should start [MeshService]: the appeared radio is the selected one and there is
     * no live connection to preserve. Starting while `Connecting` is deliberately allowed — `onStartCommand` is
     * re-entrant by design (`MainActivity.onStart()` re-issues it on every visit), and the appeared event may be the
     * first sign the radio is actually reachable again.
     *
     * @param appearedMac the MAC the presence event names, if resolvable.
     * @param selectedBleMac the MAC of the currently selected BLE radio, or null when the selection is not BLE.
     * @param connectionState the app-wide connection state.
     */
    fun shouldStartOnAppeared(
        appearedMac: String?,
        selectedBleMac: String?,
        connectionState: ConnectionState,
    ): Boolean = isSelectedRadio(appearedMac, selectedBleMac) && connectionState !is ConnectionState.Connected

    private fun isSelectedRadio(eventMac: String?, selectedBleMac: String?): Boolean =
        eventMac != null && selectedBleMac != null && eventMac.equals(selectedBleMac, ignoreCase = true)
}
