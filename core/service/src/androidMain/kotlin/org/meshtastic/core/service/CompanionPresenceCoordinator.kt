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

import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.meshtastic.core.ble.CompanionAssociationRepository
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keeps Companion Device Manager presence observation pointed at exactly one radio: the currently selected BLE device,
 * and only while it holds an association. Selecting a different radio (or disassociating) retires the old registration,
 * so `MeshCompanionDeviceService` never receives events the presence policy would discard anyway.
 *
 * [start]ed once from `Application.onCreate` (the repo's eager-init pattern — deliberately not `createdAtStart`, see
 * `AppFunctionsModule`), which also serves as the re-assertion the platform needs: presence registration must be
 * re-declared on every process start because it does not reliably survive reboots.
 *
 * Constructed with the bare address flow rather than `RadioInterfaceService` so tests exercise the reconciliation with
 * nothing but a `MutableStateFlow` and a recording repository fake.
 */
class CompanionPresenceCoordinator(
    private val selectedAddressFlow: StateFlow<String?>,
    private val repository: CompanionAssociationRepository,
    private val scope: CoroutineScope,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) {

    private val started = AtomicBoolean(false)
    private var observedMac: String? = null

    /** Begins reconciling presence observation with the selection; idempotent. No-op before API 31. */
    fun start() {
        if (sdkInt < Build.VERSION_CODES.S || !started.compareAndSet(false, true)) return
        scope.launch {
            combine(selectedAddressFlow, repository.associationsRevision) { address, _ ->
                CompanionAssociationRepository.bleMacFromFullAddress(address)?.takeIf {
                    repository.hasAssociationFor(it)
                }
            }
                .distinctUntilChanged()
                .collect { desired -> reconcile(desired) }
        }
    }

    private fun reconcile(desired: String?) {
        val current = observedMac
        if (current != null && !current.equals(desired, ignoreCase = true)) {
            repository.stopObservingPresence(current)
        }
        if (desired != null && !desired.equals(current, ignoreCase = true)) {
            repository.startObservingPresence(desired)
        }
        observedMac = desired
    }
}
