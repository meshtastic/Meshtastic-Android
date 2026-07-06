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
package org.meshtastic.core.model.geofence

import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.isFromLocal
import org.meshtastic.proto.Waypoint

/**
 * Collapse a raw waypoint-packet list into the set of currently active waypoints, keyed by waypoint id.
 *
 * `PacketRepository.getWaypoints()` is a row-PER-TRANSMISSION firehose: every re-broadcast or edit inserts a new
 * `packet` row (keyed on the random MeshPacket transmission id, not the semantic waypoint id). Consumers must normalise
 * — latest transmission wins, expired waypoints dropped — or they will see duplicate/stale geofences and keep alerting
 * on waypoints the user can no longer see. Both the map UI and the geofence engine go through here so they cannot
 * drift. Rows are ordered oldest-first, so `associateBy` keeps the newest copy per id.
 */
fun List<DataPacket>.activeWaypointPackets(nowSeconds: Long): Map<Int, DataPacket> = filter { it.waypoint != null }
    .associateBy { it.waypoint!!.id }
    .filterValues {
        val expire = it.waypoint?.expire ?: 0
        expire == 0 || expire.toLong() > nowSeconds
    }

/**
 * The geofences whose crossings THIS device should raise notifications for: waypoints we created ([isFromLocal]) plus
 * any the user has explicitly opted into ([optedInIds]). Foreign geofences are excluded by default because waypoints
 * are mesh-broadcast — every receiver stores them, so without this gate everyone in range would alert on the creator's
 * crossings. Only waypoints that actually request crossing notifications ([notifiesOnCrossing]) survive.
 */
fun Collection<DataPacket>.geofencesToMonitor(myNodeNum: Int?, optedInIds: Set<Int>): List<Waypoint> =
    filter { it.isFromLocal(myNodeNum) || (it.waypoint?.id in optedInIds) }
        .mapNotNull { it.waypoint }
        .filter { it.notifiesOnCrossing }
