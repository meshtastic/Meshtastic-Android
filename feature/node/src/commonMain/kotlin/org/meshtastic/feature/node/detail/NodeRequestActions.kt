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
package org.meshtastic.feature.node.detail

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.TelemetryType

/** Interface for high-level node request actions (e.g., requesting user info, position, telemetry). */
interface NodeRequestActions {
    val lastTracerouteTime: StateFlow<Long?>
    val lastRequestNeighborTimes: StateFlow<Map<Int, Long>>

    fun requestUserInfo(scope: CoroutineScope, destNum: Int, longName: String)

    fun requestNeighborInfo(scope: CoroutineScope, destNum: Int, longName: String)

    fun requestPosition(
        scope: CoroutineScope,
        destNum: Int,
        longName: String,
        position: Position = Position(0.0, 0.0, 0),
    )

    fun requestTelemetry(scope: CoroutineScope, destNum: Int, longName: String, type: TelemetryType)

    fun requestTraceroute(scope: CoroutineScope, destNum: Int, longName: String)
}
