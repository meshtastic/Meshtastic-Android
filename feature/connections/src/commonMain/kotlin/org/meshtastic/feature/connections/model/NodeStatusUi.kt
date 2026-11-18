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

import org.meshtastic.feature.connections.model.host.Bearer
import org.meshtastic.feature.connections.model.host.ExtraRadio
import org.meshtastic.feature.connections.model.host.NodePhase
import org.meshtastic.feature.connections.model.host.PrimaryRadio

/** The in-process node as the status card shows it; [sensors] is null on a host with no sensor bus. */
data class NodeStatusUi(
    val phase: NodePhase,
    val nodeId: String?,
    val longName: String?,
    val shortName: String?,
    val peerCount: Int,
    val failure: String?,
    val settingsReset: Boolean,
    val bearers: List<BearerRowUi>,
    val sensors: SensorsUi? = null,
    val extraRadioFailure: String? = null,
)

/**
 * One bearer row; [switchable] says whether the card offers it a switch (see [Bearer.switchable]). [extra] marks a LoRa
 * radio the host runs beside its first and is null for the bearer itself. Such a radio has a switch of its own under
 * the LoRa bearer's.
 */
data class BearerRowUi(
    val bearer: Bearer,
    val enabled: Boolean,
    val switchable: Boolean,
    val state: BearerRowState,
    val received: Long,
    val sent: Long,
    val relayed: Long,
    val extra: ExtraRadioUi? = null,
    /** What the LoRa bearer's own radio is, when the host says; null on every other row. */
    val primary: PrimaryRadio? = null,
) {
    /** The extra radio's number, 2 for `lora-2`, or null for the bearer itself. */
    val radio: Int?
        get() = extra?.number

    /** An extra radio that is off only because LoRa is, while its own switch still reads on. */
    val offWithLora: Boolean
        get() = extra != null && state == BearerRowState.Off && extra.switchedOn && !extra.loraOn
}

/**
 * An extra LoRa radio's part of its row. [details] is what the host says the radio is, null where it describes none.
 * [switchedOn] is the radio's own switch and [loraOn] the LoRa bearer's, the master: the card disables the radio's
 * switch while LoRa is off.
 */
data class ExtraRadioUi(
    val name: String,
    val number: Int,
    val details: ExtraRadio?,
    val switchedOn: Boolean,
    val loraOn: Boolean,
) {
    val id: String?
        get() = details?.id?.trim()?.takeIf { it.isNotEmpty() }

    private val described: List<ExtraRadioPart>
        get() {
            val radio = details ?: return emptyList()
            return listOfNotNull(
                ExtraRadioPart.KIND,
                ExtraRadioPart.ID.takeIf { id != null },
                ExtraRadioPart.ROLE,
                if (radio.duplex) ExtraRadioPart.TRANSMITS else ExtraRadioPart.RECEIVE_ONLY,
                ExtraRadioPart.BRIDGES.takeIf { radio.bridge },
            )
        }

    /** How many parts the card may leave out to keep the row short. */
    val droppable: Int
        get() = DROP_ORDER.count { it in described }

    /** The description in reading order, with the [dropped] least important parts left out. */
    fun parts(dropped: Int = 0): List<ExtraRadioPart> {
        val all = described
        return all - DROP_ORDER.filter { it in all }.take(dropped).toSet()
    }

    private companion object {
        // The kind, what the radio listens to and that it transmits always stay.
        val DROP_ORDER = listOf(ExtraRadioPart.RECEIVE_ONLY, ExtraRadioPart.ID, ExtraRadioPart.BRIDGES)
    }
}

/** One piece of an extra radio's description. */
enum class ExtraRadioPart {
    KIND,
    ID,
    ROLE,
    RECEIVE_ONLY,
    TRANSMITS,
    BRIDGES,
}

sealed interface BearerRowState {
    data object Off : BearerRowState

    /** LoRa with its device ready and no region set: the region is what turns it on. */
    data object RegionUnset : BearerRowState

    data object Ready : BearerRowState

    data object Active : BearerRowState

    data class Unavailable(val reason: String) : BearerRowState

    data class NeedsPermission(val permission: String) : BearerRowState

    data class NeedsUsbPermission(val device: String) : BearerRowState

    data class NeedsHardware(val what: String) : BearerRowState

    data class Unsupported(val reason: String) : BearerRowState
}

/** The extra radio named [name] as its row shows it, or null for a name that numbers no extra radio. */
internal fun extraRadioUi(name: String, details: ExtraRadio?, off: Set<String>, loraOn: Boolean): ExtraRadioUi? =
    extraRadioNumber(name)?.let { ExtraRadioUi(name, it, details, switchedOn = name !in off, loraOn = loraOn) }

/** The number in an extra radio's transport name, 2 for `lora-2`, or null for a name of another shape. */
internal fun extraRadioNumber(name: String): Int? =
    name.substringAfterLast('-', missingDelimiterValue = "").toIntOrNull()?.takeIf { it > 1 }

/** The card's header from the node's fields; node-kmp's `NodeStatus` has no public constructor, so tests build here. */
@Suppress("LongParameterList")
internal fun nodeStatusUi(
    phase: NodePhase,
    nodeNum: Long?,
    longName: String?,
    shortName: String?,
    peerCount: Int,
    failure: String?,
    settingsReset: Boolean,
    bearers: List<BearerRowUi>,
    extraRadioFailure: String? = null,
): NodeStatusUi = NodeStatusUi(
    phase = phase,
    nodeId = nodeNum?.let { nodeId(it) },
    longName = longName,
    shortName = shortName,
    peerCount = peerCount,
    failure = failure,
    settingsReset = settingsReset,
    bearers = bearers,
    // The store can fail on a host with no extra radio, where there is nothing to say it about.
    extraRadioFailure = extraRadioFailure?.takeIf { bearers.any { row -> row.extra != null } },
)

private const val NODE_ID_DIGITS = 8
private const val HEX = 16

internal fun nodeId(nodeNum: Long): String = "!" + nodeNum.toString(HEX).padStart(NODE_ID_DIGITS, '0')
