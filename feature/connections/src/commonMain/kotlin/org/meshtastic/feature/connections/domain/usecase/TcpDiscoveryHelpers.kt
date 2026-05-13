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

import org.meshtastic.core.common.database.DatabaseManager
import org.meshtastic.core.datastore.model.RecentAddress
import org.meshtastic.core.model.Node
import org.meshtastic.core.network.repository.DiscoveredService
import org.meshtastic.core.network.repository.NetworkRepository.Companion.toAddressString
import org.meshtastic.feature.connections.model.DeviceListEntry

private const val SUFFIX_LENGTH = 4

/**
 * Shared helpers for TCP device discovery logic used by both [CommonGetDiscoveredDevicesUseCase] and the
 * Android-specific variant.
 */

/** Converts a list of [DiscoveredService] into [DeviceListEntry.Tcp] with display names derived from TXT records. */
internal fun processTcpServices(
    tcpServices: List<DiscoveredService>,
    recentAddresses: List<RecentAddress>,
    defaultShortName: String = "Meshtastic",
): List<DeviceListEntry.Tcp> {
    val recentMap = recentAddresses.associateBy({ it.address }) { it.name }
    return tcpServices
        .map { service ->
            val address = "t${service.toAddressString()}"
            val txtRecords = service.txt
            val shortNameBytes = txtRecords["shortname"]
            val idBytes = txtRecords["id"]

            val shortName = shortNameBytes?.decodeToString() ?: defaultShortName
            val deviceId = idBytes?.decodeToString()?.replace("!", "")
            var displayName = recentMap[address] ?: shortName
            if (deviceId != null && displayName.split("_").none { it == deviceId }) {
                displayName += "_$deviceId"
            }
            DeviceListEntry.Tcp(displayName, address)
        }
        .sortedBy { it.name }
}

/** Matches each discovered TCP entry to a [Node] from the database using its mDNS device ID. */
internal fun matchDiscoveredTcpNodes(
    entries: List<DeviceListEntry.Tcp>,
    nodeDb: Map<Int, Node>,
    resolvedServices: List<DiscoveredService>,
    databaseManager: DatabaseManager,
): List<DeviceListEntry.Tcp> = entries.map { entry ->
    val matchingNode =
        if (databaseManager.hasDatabaseFor(entry.fullAddress)) {
            val resolvedService = resolvedServices.find { "t${it.toAddressString()}" == entry.fullAddress }
            val deviceId = resolvedService?.txt?.get("id")?.decodeToString()
            nodeDb.values.find { node ->
                node.user.id == deviceId || (deviceId != null && node.user.id == "!$deviceId")
            }
        } else {
            null
        }
    entry.copy(node = matchingNode)
}

/**
 * Builds the "recent TCP devices" list by filtering out currently discovered addresses and matching each entry to a
 * [Node] by name suffix.
 */
internal fun buildRecentTcpEntries(
    recentAddresses: List<RecentAddress>,
    discoveredAddresses: Set<String>,
    nodeDb: Map<Int, Node>,
    databaseManager: DatabaseManager,
): List<DeviceListEntry.Tcp> = recentAddresses
    .filterNot { discoveredAddresses.contains(it.address) }
    .map { DeviceListEntry.Tcp(it.name, it.address) }
    .map { entry ->
        entry.copy(node = findNodeByNameSuffix(entry.name, entry.fullAddress, nodeDb, databaseManager))
    }
    .sortedBy { it.name }

/**
 * Finds a [Node] matching the last `_`-delimited segment of [displayName], if a local database exists for the given
 * [fullAddress]. Used by both TCP recent-device matching and Android USB device matching to avoid duplicated
 * suffix-lookup logic.
 */
internal fun findNodeByNameSuffix(
    displayName: String,
    fullAddress: String,
    nodeDb: Map<Int, Node>,
    databaseManager: DatabaseManager,
): Node? {
    val suffix = displayName.split("_").lastOrNull()?.lowercase()
    return if (!databaseManager.hasDatabaseFor(fullAddress) || suffix == null || suffix.length < SUFFIX_LENGTH) {
        null
    } else {
        nodeDb.values.find { it.user.id.lowercase().endsWith(suffix) }
    }
}
