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
package org.meshtastic.core.model

import kotlinx.coroutines.flow.StateFlow

/**
 * Minimal interface exposing radio-config packet response state.
 * Used by feature/connections to observe config-loading progress without
 * depending on the full RadioConfigViewModel in feature/settings.
 */
interface RadioConfigStateProvider {
    /** Current packet response state (loading/success/error/empty). */
    val packetResponseState: StateFlow<ResponseState<Boolean>>

    /** Route name associated with the pending config request (e.g. "LORA"). */
    val pendingRouteName: StateFlow<String>

    /** Initiate a config load for the given route name. */
    fun requestConfigLoad(routeName: String)

    /** Clear the packet response, resetting to [ResponseState.Empty]. */
    fun clearPacketResponse()
}
