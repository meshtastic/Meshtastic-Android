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
 * The stable CoT UID for [this] node, e.g. `MESHTASTIC-A1B2C3D4`.
 *
 * ATAK identifies a contact by UID, so this must stay stable across broadcasts or every refresh spawns a duplicate
 * marker. Derived from the node number rather than the user record because [Node.user] can be empty or change.
 *
 * Upper-case hex is load-bearing, not cosmetic — see [MESH_NODE_UID_PREFIX]. Deliberately not built from
 * [NodeAddress.numToDefaultId], which formats `!%08x` in lower case for display.
 */
internal fun Node.cotUid(): String =
    MESH_NODE_UID_PREFIX + num.toUInt().toString(TAK_HEX_RADIX).uppercase().padStart(MESH_NODE_UID_HEX_WIDTH, '0')

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
 * Telemetry summary carried in the CoT `<remarks>` element.
 *
 * Field labels, order, and precision match Apple's `createCoTFromNode` so an operator reading a contact's remarks sees
 * the same text regardless of which phone bridged it.
 *
 * **Deliberate divergence:** Apple suppresses a field when its value is zero (`if voltage > 0`, `if rssi != 0`, …).
 * This repo treats zero as a real reading — 0 dB SNR and 0 dBm RSSI are genuine measurements, and 0% battery is exactly
 * the value an operator most needs to see — so absence is detected via nullability and the SNR/RSSI sentinels
 * ([Node.snrOrNull] / [Node.rssiOrNull]) instead. Apple also substitutes 100% for an unreported battery; omitting it is
 * preferred over reporting a figure the node never sent. `Air Util Tx` has no Apple counterpart and is additive.
 */
internal fun Node.cotRemarks(): String? {
    val parts = buildList {
        batteryLevel?.let { add("Battery: $it%") }
        voltage?.let { add("Voltage: ${NumberFormatter.format(it, VOLTAGE_DECIMALS)}V") }
        deviceMetrics.channel_utilization?.let { add("Chan Util: ${NumberFormatter.format(it, 1)}%") }
        deviceMetrics.air_util_tx?.let { add("Air Util Tx: ${NumberFormatter.format(it, 1)}%") }
        rssiOrNull?.let { add("RSSI: $it dBm") }
        snrOrNull?.let { add("SNR: ${NumberFormatter.format(it, 1)} dB") }
    }
    return parts.joinToString(" | ").ifEmpty { null }
}

/** Apple prints voltage with two decimals (`%.2f`) where every other telemetry field uses one. */
private const val VOLTAGE_DECIMALS = 2

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
