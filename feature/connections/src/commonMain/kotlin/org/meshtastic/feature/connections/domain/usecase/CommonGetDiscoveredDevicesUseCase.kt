/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
import org.jetbrains.compose.resources.getString
import org.koin.core.annotation.Single
import org.meshtastic.core.common.database.DatabaseManager
import org.meshtastic.core.datastore.RecentAddressesDataSource
import org.meshtastic.core.network.repository.DiscoveredService
import org.meshtastic.core.network.repository.NetworkRepository
import org.meshtastic.core.network.repository.NetworkRepository.Companion.toAddressString
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.demo_mode
import org.meshtastic.core.resources.meshtastic
import org.meshtastic.feature.connections.model.DeviceListEntry
import org.meshtastic.feature.connections.model.DiscoveredDevices
import org.meshtastic.feature.connections.model.GetDiscoveredDevicesUseCase

@Single
class CommonGetDiscoveredDevicesUseCase(
    private val recentAddressesDataSource: RecentAddressesDataSource,
    private val nodeRepository: NodeRepository,
    private val databaseManager: DatabaseManager,
    private val networkRepository: NetworkRepository,
    private val usbScanner: UsbScanner? = null,
) : GetDiscoveredDevicesUseCase {
    private val suffixLength = 4

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    override fun invoke(showMock: Boolean): Flow<DiscoveredDevices> {
        val nodeDb = nodeRepository.nodeDBbyNum
        val usbFlow = usbScanner?.scanUsbDevices() ?: kotlinx.coroutines.flow.flowOf(emptyList())

        val processedTcpFlow =
            combine(networkRepository.resolvedList, recentAddressesDataSource.recentAddresses) {
                    tcpServices,
                    recentList,
                ->
                val recentMap = recentList.associateBy({ it.address }) { it.name }
                tcpServices
                    .map { service ->
                        val address = "t${service.toAddressString()}"
                        val txtRecords = service.txt
                        val shortNameBytes = txtRecords["shortname"]
                        val idBytes = txtRecords["id"]

                        val shortName =
                            shortNameBytes?.let { it.decodeToString() }
                                ?: runCatching { getString(Res.string.meshtastic) }.getOrDefault("Meshtastic")
                        val deviceId = idBytes?.let { it.decodeToString() }?.replace("!", "")
                        var displayName = recentMap[address] ?: shortName
                        if (deviceId != null && (displayName.split("_").none { it == deviceId })) {
                            displayName += "_$deviceId"
                        }
                        DeviceListEntry.Tcp(displayName, address)
                    }
                    .sortedBy { it.name }
            }

        return combine(
            nodeDb,
            processedTcpFlow,
            networkRepository.resolvedList,
            recentAddressesDataSource.recentAddresses,
            usbFlow,
        ) { db, processedTcp, resolved, recentList, usbList ->
            val discoveredTcpForUi =
                processedTcp.map { entry ->
                    val matchingNode =
                        if (databaseManager.hasDatabaseFor(entry.fullAddress)) {
                            val resolvedService = resolved.find { "t${it.toAddressString()}" == entry.fullAddress }
                            val deviceId = resolvedService?.txt?.get("id")?.let { it.decodeToString() }
                            db.values.find { node ->
                                node.user.id == deviceId || (deviceId != null && node.user.id == "!$deviceId")
                            }
                        } else {
                            null
                        }
                    entry.copy(node = matchingNode)
                }

            val discoveredTcpAddresses = processedTcp.map { it.fullAddress }.toSet()

            val recentTcpForUi =
                recentList
                    .filterNot { discoveredTcpAddresses.contains(it.address) }
                    .map { DeviceListEntry.Tcp(it.name, it.address) }
                    .map { entry ->
                        val matchingNode =
                            if (databaseManager.hasDatabaseFor(entry.fullAddress)) {
                                val suffix = entry.name.split("_").lastOrNull()?.lowercase()
                                db.values.find { node ->
                                    suffix != null &&
                                        suffix.length >= suffixLength &&
                                        node.user.id.lowercase().endsWith(suffix)
                                }
                            } else {
                                null
                            }
                        entry.copy(node = matchingNode)
                    }
                    .sortedBy { it.name }

            DiscoveredDevices(
                discoveredTcpDevices = discoveredTcpForUi,
                recentTcpDevices = recentTcpForUi,
                usbDevices =
                usbList +
                    if (showMock) {
                        val demoModeLabel =
                            runCatching { getString(Res.string.demo_mode) }.getOrDefault("Demo Mode")
                        listOf(DeviceListEntry.Mock(demoModeLabel))
                    } else {
                        emptyList()
                    },
            )
        }
    }
}
