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
import org.meshtastic.core.common.util.safeCatchingAll
import org.meshtastic.core.datastore.model.RecentAddress
import org.meshtastic.core.model.Node
import org.meshtastic.core.network.repository.DiscoveredService
import org.meshtastic.core.network.repository.NetworkRepository.Companion.toAddressString
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.demo_mode
import org.meshtastic.core.resources.demo_mode_replay
import org.meshtastic.core.resources.getStringSuspend
import org.meshtastic.feature.connections.model.DeviceListEntry

// Shared helpers for TCP device discovery logic used by both CommonGetDiscoveredDevicesUseCase and the Android variant.

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
        // mDNS can resolve two services to one host:port (e.g. re-announce before the old record expires). The device
        // list keys on fullAddress, and duplicate LazyColumn keys are a hard crash.
        .distinctBy { it.fullAddress }
        .sortedBy { it.name }
}

/**
 * Labels each discovered TCP entry with the node that is that radio: the live session's node when it is connected, else
 * the node whose id the radio announces in its mDNS TXT record. Only a radio that announces no id falls back to the
 * node number it reported at its last handshake, since the address may now belong to another radio.
 */
internal fun matchDiscoveredTcpNodes(
    entries: List<DeviceListEntry.Tcp>,
    nodeDb: Map<Int, Node>,
    resolvedServices: List<DiscoveredService>,
    databaseManager: DatabaseManager,
    nums: LocalNodeNums,
): List<DeviceListEntry.Tcp> = entries.map { entry ->
    val resolvedService = resolvedServices.find { "t${it.toAddressString()}" == entry.fullAddress }
    val deviceId = resolvedService?.txt?.get("id")?.decodeToString()
    val identified =
        when {
            deviceId == null -> nums.recorded(entry.fullAddress)?.let(nodeDb::get)

            databaseManager.hasDatabaseFor(entry.fullAddress) ->
                nodeDb.values.find { node -> node.user.id == deviceId || node.user.id == "!$deviceId" }

            else -> null
        }
    // A connected radio is its live node or, until that node's NodeInfo arrives, no node: any other match names a
    // different radio.
    val live = nums.live(entry.fullAddress)
    entry.copy(node = if (live != null) nodeDb[live] else identified)
}

/**
 * Builds the "recent TCP devices" list by filtering out currently discovered addresses and labelling each entry with
 * the node recorded for its address.
 */
internal fun buildRecentTcpEntries(
    recentAddresses: List<RecentAddress>,
    discoveredAddresses: Set<String>,
    nodeDb: Map<Int, Node>,
    nums: LocalNodeNums,
): List<DeviceListEntry> = recentAddresses
    .filterNot { discoveredAddresses.contains(it.address) }
    .map { DeviceListEntry.Tcp(it.name, it.address) }
    // The persisted list is not guaranteed unique: only add() dedupes, and legacy blobs bypass it. The device list
    // keys on fullAddress, and duplicate LazyColumn keys are a hard crash.
    .distinctBy { it.fullAddress }
    .withLocalNodes(nodeDb, nums)
    .sortedBy { it.name }

/**
 * The virtual devices offered behind the Demo Mode gate, shared so the common and Android discovery paths cannot
 * diverge on visibility policy.
 *
 * [showReplay] is nested inside [showMock]: replay is an extra entry *within* Demo Mode, never a device on its own. It
 * additionally requires the build to carry the capture asset (`RadioLinks.isReplayTransportAvailable`) — without it the
 * replay transport falls back to the plain mock, so the entry would advertise something it cannot do.
 */
internal suspend fun virtualDeviceEntries(showMock: Boolean, showReplay: Boolean): List<DeviceListEntry> {
    if (!showMock) return emptyList()
    val mock =
        DeviceListEntry.Mock(safeCatchingAll { getStringSuspend(Res.string.demo_mode) }.getOrDefault("Demo Mode"))
    val replay =
        if (showReplay) {
            val label = safeCatchingAll {
                getStringSuspend(Res.string.demo_mode_replay)
            }
                .getOrDefault("Demo Mode (Replay)")
            DeviceListEntry.Replay(label)
        } else {
            null
        }
    return listOfNotNull(mock, replay)
}
