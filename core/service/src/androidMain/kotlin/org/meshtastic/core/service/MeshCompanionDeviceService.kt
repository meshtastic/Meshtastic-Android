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

import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.companion.DevicePresenceEvent
import android.os.Build
import androidx.annotation.RequiresApi
import co.touchlab.kermit.Logger
import org.koin.android.ext.android.inject
import org.meshtastic.core.ble.CompanionAssociationRepository
import org.meshtastic.core.model.util.anonymize
import org.meshtastic.core.repository.ConnectionStateProvider
import org.meshtastic.core.repository.RadioInterfaceService

/**
 * System-bound Companion Device Manager presence service (Phase 2 of CDM adoption).
 *
 * Once `CompanionAssociationRepository.startObservingPresence` registers the selected radio, the platform binds this
 * service — reviving the process if necessary — when that radio's BLE presence changes. A device-appeared event starts
 * [MeshService] so the transport reconnects to the persisted address (`MeshServiceOrchestrator.start()` connects on its
 * own; no scan is involved — the transport builds its Peripheral straight from the MAC). Device-disappeared events are
 * deliberately unhandled: presence-driven teardown (Phase 3) waits until field data shows appeared-events are
 * trustworthy across OEMs, because a torn-down service that never restarts is strictly worse than today's idle one.
 *
 * The decision lives in [CompanionPresencePolicy]; this class only adapts the three generations of platform callback
 * (String on 31–32, [AssociationInfo] on 33–35, [DevicePresenceEvent] on 36+) onto it. The action is idempotent —
 * `onStartCommand` is re-entrant by design — so an OS that delivers an event through more than one callback generation
 * cannot cause harm.
 */
@RequiresApi(Build.VERSION_CODES.S)
class MeshCompanionDeviceService : CompanionDeviceService() {

    private val radioInterfaceService: RadioInterfaceService by inject()
    private val connectionStateProvider: ConnectionStateProvider by inject()
    private val companionAssociationRepository: CompanionAssociationRepository by inject()

    // API 33-35 entry point.
    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        handleAppeared(associationInfo.deviceMacAddress?.toString())
    }

    // API 31-32 entry point.
    @Deprecated("Platform entry point on API 31-32 only")
    override fun onDeviceAppeared(address: String) {
        handleAppeared(address)
    }

    // API 36+ entry point. Not calling super keeps the deprecated callbacks from firing a second time.
    override fun onDevicePresenceEvent(event: DevicePresenceEvent) {
        when (event.event) {
            DevicePresenceEvent.EVENT_BLE_APPEARED ->
                handleAppeared(companionAssociationRepository.macForAssociationId(event.associationId))

            else -> Logger.d { "Ignoring companion presence event ${event.event}" }
        }
    }

    private fun handleAppeared(mac: String?) {
        if (!CompanionPresencePolicy.shouldStartOnAppeared(mac, selectedBleMac(), connectionState())) return
        Logger.i { "Companion radio ${mac?.anonymize} appeared; starting MeshService" }
        MeshService.startService(this, ServiceStartTrigger.CompanionDevicePresent, hasCompanionAssociation = true)
    }

    private fun selectedBleMac(): String? =
        CompanionAssociationRepository.bleMacFromFullAddress(radioInterfaceService.currentDeviceAddressFlow.value)

    private fun connectionState() = connectionStateProvider.connectionState.value
}
