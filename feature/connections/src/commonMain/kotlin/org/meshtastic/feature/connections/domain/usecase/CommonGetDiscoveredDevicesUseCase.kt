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
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.demo_mode
import org.meshtastic.feature.connections.model.DeviceListEntry
import org.meshtastic.feature.connections.model.DiscoveredDevices
import org.meshtastic.feature.connections.model.GetDiscoveredDevicesUseCase

@Single
class CommonGetDiscoveredDevicesUseCase(
    private val recentAddressesDataSource: RecentAddressesDataSource,
    private val nodeRepository: NodeRepository,
    private val databaseManager: DatabaseManager,
    private val usbScanner: UsbScanner? = null,
) : GetDiscoveredDevicesUseCase {
    private val suffixLength = 4

    override fun invoke(showMock: Boolean): Flow<DiscoveredDevices> {
        val nodeDb = nodeRepository.nodeDBbyNum
        val usbFlow = usbScanner?.scanUsbDevices() ?: kotlinx.coroutines.flow.flowOf(emptyList())

        return combine(nodeDb, recentAddressesDataSource.recentAddresses, usbFlow) { db, recentList, usbList ->
            val recentTcpForUi =
                recentList
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
