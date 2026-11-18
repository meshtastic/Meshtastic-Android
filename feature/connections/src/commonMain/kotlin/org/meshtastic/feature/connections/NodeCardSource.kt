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
package org.meshtastic.feature.connections

import kotlinx.coroutines.flow.Flow
import org.meshtastic.feature.connections.model.NodeStatusUi
import org.meshtastic.feature.connections.model.host.Bearer

/**
 * Where the node card reads a node-kmp node from: one kind of node the app can be connected to. The card shows the
 * first source that [claims] the current device address.
 */
interface NodeCardSource {
    /** True when [address], the current device address, reaches a node this source reads. */
    fun claims(address: String): Boolean

    /** The card for the node at [address]; null while there is none to show. */
    fun status(address: String): Flow<NodeStatusUi?>

    /** Switch [bearer] in the node's stored transport section. */
    suspend fun setBearer(address: String, bearer: Bearer, on: Boolean)

    /** Switch one extra LoRa radio by itself, under LoRa's switch. */
    suspend fun setExtraRadio(address: String, name: String, on: Boolean): ExtraRadioSwitch

    /** Re-read permission and hardware state after a grant or an attach. */
    fun refreshRequirements() = Unit

    /** Ask the platform for the attached LoRa stick's USB permission. */
    fun requestUsbPermission() = Unit
}

/** What became of an extra radio switch, as the user is told it. */
enum class ExtraRadioSwitch {
    /** Made and kept. */
    KEPT,

    /** Made, but a restart will forget it. */
    NOT_KEPT,

    /** Not made. */
    FAILED,
}
