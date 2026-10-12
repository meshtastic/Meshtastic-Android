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
package org.meshtastic.feature.connections.model

import org.meshtastic.node.host.Bearer
import org.meshtastic.node.host.NodePhase
import org.meshtastic.node.host.NodeStatus
import org.meshtastic.node.host.Requirement
import org.meshtastic.node.transport.TransportAvailability

/** The in-process node as the status card shows it. */
data class NodeStatusUi(
    val phase: NodePhase,
    val nodeId: String?,
    val longName: String?,
    val shortName: String?,
    val peerCount: Int,
    val failure: String?,
    val settingsReset: Boolean,
    val bearers: List<BearerRowUi>,
)

/** One bearer row; [switchable] says whether the card offers it a switch (see [Bearer.switchable]). */
data class BearerRowUi(
    val bearer: Bearer,
    val enabled: Boolean,
    val switchable: Boolean,
    val state: BearerRowState,
    val received: Long,
    val sent: Long,
    val relayed: Long,
)

sealed interface BearerRowState {
    data object Off : BearerRowState

    data object Ready : BearerRowState

    data object Active : BearerRowState

    data class Unavailable(val reason: String) : BearerRowState

    data class NeedsPermission(val permission: String) : BearerRowState

    data class NeedsUsbPermission(val device: String) : BearerRowState

    data class NeedsHardware(val what: String) : BearerRowState

    data class Unsupported(val reason: String) : BearerRowState
}

fun NodeStatus.toUi(): NodeStatusUi = NodeStatusUi(
    phase = phase,
    nodeId = nodeNum?.let { nodeId(it) },
    longName = longName,
    shortName = shortName,
    peerCount = peerCount,
    failure = failure,
    settingsReset = settingsReset,
    bearers =
    Bearer.entries.filter { it.onCard }.mapNotNull { bearer ->
        val status = bearers[bearer] ?: return@mapNotNull null
        bearerRow(
            bearer = bearer,
            enabled = status.enabled,
            availability = status.availability,
            requirement = status.requirement,
            received = status.counters.rx,
            sent = status.counters.tx,
            relayed = status.counters.relayed,
        )
    },
)

/** A requirement outranks the availability: the user can act on it, while availability only reports. */
@Suppress("LongParameterList")
internal fun bearerRow(
    bearer: Bearer,
    enabled: Boolean,
    availability: TransportAvailability?,
    requirement: Requirement,
    received: Long,
    sent: Long,
    relayed: Long,
): BearerRowUi = BearerRowUi(
    bearer = bearer,
    enabled = enabled,
    switchable = bearer.switchable,
    state =
    when (requirement) {
        is Requirement.NeedsPermission -> BearerRowState.NeedsPermission(requirement.permission)

        is Requirement.NeedsUsbPermission -> BearerRowState.NeedsUsbPermission(requirement.device)

        is Requirement.NeedsHardware -> BearerRowState.NeedsHardware(requirement.what)

        is Requirement.Unsupported -> BearerRowState.Unsupported(requirement.reason)

        Requirement.Ready ->
            when {
                !enabled -> BearerRowState.Off

                availability is TransportAvailability.Active -> BearerRowState.Active

                availability is TransportAvailability.Unavailable ->
                    BearerRowState.Unavailable(availability.reason)

                availability is TransportAvailability.NeedsPermission ->
                    BearerRowState.NeedsPermission(availability.permission)

                else -> BearerRowState.Ready
            }
    },
    received = received,
    sent = sent,
    relayed = relayed,
)

private const val NODE_ID_DIGITS = 8
private const val HEX = 16

private fun nodeId(nodeNum: Long): String = "!" + nodeNum.toString(HEX).padStart(NODE_ID_DIGITS, '0')
