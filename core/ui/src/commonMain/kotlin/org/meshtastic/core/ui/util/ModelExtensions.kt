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
package org.meshtastic.core.ui.util

import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.model.TracerouteMapAvailability
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.traceroute_endpoint_missing
import org.meshtastic.core.resources.traceroute_map_no_data

fun TracerouteMapAvailability.toMessageRes(): StringResource? = when (this) {
    TracerouteMapAvailability.Ok -> null
    TracerouteMapAvailability.MissingEndpoints -> Res.string.traceroute_endpoint_missing
    TracerouteMapAvailability.NoMappableNodes -> Res.string.traceroute_map_no_data
}
