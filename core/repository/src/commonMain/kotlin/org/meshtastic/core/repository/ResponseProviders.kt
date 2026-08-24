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

import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.core.model.service.TracerouteResponse

/**
 * Read-only provider of traceroute response state.
 *
 * Inject in ViewModels that display traceroute results. The write side ([ServiceRepository.setTracerouteResponse]) is
 * restricted to handlers.
 */
interface TracerouteResponseProvider {
    /** The most recent traceroute result, or null if none pending. */
    val tracerouteResponse: StateFlow<TracerouteResponse?>

    /** Clears the current traceroute response (consumed by UI after display). */
    fun clearTracerouteResponse()
}

/**
 * Read-only provider of neighbor info response state.
 *
 * Inject in ViewModels that display neighbor info results.
 */
interface NeighborInfoResponseProvider {
    /** The most recent neighbor info response (formatted string), or null. */
    val neighborInfoResponse: StateFlow<String?>

    /** Clears the current neighbor info response. */
    fun clearNeighborInfoResponse()
}
