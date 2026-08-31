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
package org.meshtastic.core.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * The minimal seam [org.meshtastic.core.service.MeshServiceOrchestrator] needs onto the TAK server integration —
 * folding `TAKServerManager.isRunning` and `TAKMeshIntegration.start`/`stop` (core:takserver) into one interface so a
 * platform with no TAK support (e.g. wasmJs, where core:takserver's TLS listener is a permanent browser-sandbox
 * impossibility) can supply a real no-op without core:service depending on core:takserver at all.
 */
interface TakServerIntegration {
    /** Whether the TAK server + mesh bridge are currently running. */
    val isRunning: StateFlow<Boolean>

    /** Start the TAK server and the mesh<->CoT bridge on [scope]. */
    fun start(scope: CoroutineScope)

    /** Stop the TAK server and the mesh<->CoT bridge. */
    fun stop()
}
