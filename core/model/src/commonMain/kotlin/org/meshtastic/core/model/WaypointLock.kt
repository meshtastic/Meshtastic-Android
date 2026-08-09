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

import org.meshtastic.proto.Waypoint

/**
 * Whether this waypoint is locked to a single node. A locked waypoint (`locked_to != 0`) may only be edited or removed
 * mesh-wide by the node it is locked to; an unlocked waypoint (`locked_to == 0`) is editable by anyone.
 */
val Waypoint.isLocked: Boolean
    get() = locked_to != 0

/**
 * Whether [nodeNum] is permitted to edit this waypoint or broadcast a change/removal of it: true when the waypoint is
 * unlocked, or locked to exactly [nodeNum]. A null [nodeNum] (our own identity not yet known) can only modify unlocked
 * waypoints.
 *
 * Single source of truth for the `locked_to` permission check. Callers pass the identity being checked:
 * - map edit/delete gates pass our own node number, so the creator can always manage a waypoint locked to themselves;
 * - the inbound-packet validator passes the packet's sender, so a locked waypoint is only accepted from its owner.
 *
 * The lock owner must be a real node number. Writing a placeholder (e.g. `1`) would lock a waypoint to a phantom node
 * that no real device can match, silently blocking even the creator — see issue #6343.
 */
fun Waypoint.isModifiableBy(nodeNum: Int?): Boolean = locked_to == 0 || locked_to == nodeNum
