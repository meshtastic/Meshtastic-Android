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
package org.meshtastic.core.takserver

import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.util.onlineTimeThreshold

// Converts entries of the Meshtastic node database into CoT contacts for connected TAK clients.
//
// This is the local mesh -> ATAK visualization path. Nothing produced here crosses the mesh, so none of it is subject
// to the LoRa MTU or the TAKPacket wire format — that is TAKMeshIntegration's concern.

/**
 * True when [this] node should be presented to TAK clients as a contact.
 *
 * Mirrors Meshtastic-Apple's filter: the node must have identified itself, been heard recently, and hold a usable
 * position. [ourNodeNum] is excluded because ATAK already renders the operator's own position as self; when it is null
 * the local node number is not yet known and nothing is excluded.
 */
internal fun Node.isEligibleForCot(ourNodeNum: Int?, lastHeardThreshold: Int = onlineTimeThreshold()): Boolean =
    num != ourNodeNum && !isUnknownUser && lastHeard > lastHeardThreshold && validPosition != null

/**
 * The stable CoT UID for [this] node, e.g. `MESHTASTIC-a1b2c3d4`.
 *
 * ATAK identifies a contact by UID, so this must stay stable across broadcasts or every refresh spawns a duplicate
 * marker. Derived from the node number rather than the user record because [Node.user] can be empty or change.
 */
internal fun Node.cotUid(): String = MESH_NODE_UID_PREFIX + NodeAddress.numToDefaultId(num).removePrefix("!")

/**
 * The ATAK callsign for [this] node — `"SHORT - Long Name"`, matching Apple. Falls back through the names that are
 * actually populated so a partially-identified node never renders as a blank contact.
 */
internal fun Node.cotCallsign(): String {
    val short = user.short_name.trim()
    val long = user.long_name.trim()
    return when {
        short.isNotEmpty() && long.isNotEmpty() -> "$short - $long"
        short.isNotEmpty() -> short
        long.isNotEmpty() -> long
        else -> NodeAddress.numToDefaultId(num)
    }
}

/**
 * Telemetry summary carried in the CoT `<remarks>` element, as Apple does.
 *
 * Every field is omitted when the node has not reported it. Zero is a real reading for all of these, so absence is
 * detected via nullability and the SNR/RSSI sentinels ([Node.snrOrNull] / [Node.rssiOrNull]) rather than by treating 0
 * as missing.
 */
internal fun Node.cotRemarks(): String? {
    val parts = buildList {
        batteryLevel?.let { add("Battery: $it%") }
        voltage?.let { add("Voltage: ${NumberFormatter.format(it, 1)}V") }
        deviceMetrics.channel_utilization?.let { add("ChUtil: ${NumberFormatter.format(it, 1)}%") }
        deviceMetrics.air_util_tx?.let { add("AirUtilTx: ${NumberFormatter.format(it, 1)}%") }
        snrOrNull?.let { add("SNR: ${NumberFormatter.format(it, 1)}dB") }
        rssiOrNull?.let { add("RSSI: ${it}dBm") }
    }
    return parts.joinToString(" | ").ifEmpty { null }
}

/**
 * Build the CoT event representing [this] node.
 *
 * Callers are expected to have filtered with [isEligibleForCot] first; a node without a valid position still converts,
 * but yields a 0/0 point that ATAK will place in the Gulf of Guinea.
 */
internal fun Node.toCoTMessage(): CoTMessage = user.toCoTMessage(
    position = validPosition,
    team = MESH_NODE_TAK_TEAM,
    role = DEFAULT_TAK_ROLE_NAME,
    battery = batteryLevel ?: DEFAULT_TAK_BATTERY,
    uid = cotUid(),
    callsign = cotCallsign(),
    staleMinutes = MESH_NODE_STALE_MINUTES,
    remarks = cotRemarks(),
)
