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
package org.meshtastic.feature.connections.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.meshtastic.core.common.database.DatabaseManager
import org.meshtastic.core.common.util.normalizeAddress
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.ConnectionIdentity
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.feature.connections.model.DeviceListEntry

/**
 * The node number each radio reported as its own, by transport address: the live handshake for the connected radio, the
 * last associated handshake for every other. A radio's MAC or advertised name never stands in for it, since firmware
 * 2.8 derives the number from the public key and a 16-bit name suffix collides with other nodes.
 */
internal class LocalNodeNums(private val recorded: Map<String, Int>, private val live: ConnectionIdentity?) {
    fun live(fullAddress: String): Int? = live?.takeIf { sameAddress(it.address, fullAddress) }?.nodeNum

    fun recorded(fullAddress: String): Int? = recorded[normalizeAddress(fullAddress)]

    fun forAddress(fullAddress: String): Int? = live(fullAddress) ?: recorded(fullAddress)

    private fun sameAddress(a: String, b: String) = normalizeAddress(a) == normalizeAddress(b)
}

internal fun localNodeNums(databaseManager: DatabaseManager, nodeManager: NodeManager): Flow<LocalNodeNums> =
    combine(databaseManager.localNodeNums, nodeManager.connectionIdentity, ::LocalNodeNums)

/**
 * Labels each entry with the node that is that radio, or none. A serial address usually names a port or a USB vendor
 * and product id, not a radio, so such an entry takes only the live session's node: a recorded number may belong to
 * another radio that used the same port.
 */
internal fun List<DeviceListEntry>.withLocalNodes(nodeDb: Map<Int, Node>, nums: LocalNodeNums): List<DeviceListEntry> =
    map { entry ->
        val liveOnly = entry is DeviceListEntry.Usb && !entry.addressNamesRadio
        val num = if (liveOnly) nums.live(entry.fullAddress) else nums.forAddress(entry.fullAddress)
        entry.copy(node = num?.let(nodeDb::get))
    }
