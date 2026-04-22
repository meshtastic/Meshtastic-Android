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
import kotlinx.coroutines.flow.flowOf
import org.meshtastic.core.common.database.DatabaseManager
import org.meshtastic.core.common.util.safeCatchingAll
import org.meshtastic.core.datastore.RecentAddressesDataSource
import org.meshtastic.core.network.repository.DiscoveredService
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.demo_mode
import org.meshtastic.core.resources.getStringSuspend
import org.meshtastic.core.resources.meshtastic
import org.meshtastic.feature.connections.model.DeviceListEntry
import org.meshtastic.feature.connections.model.DiscoveredDevices
import org.meshtastic.feature.connections.model.GetDiscoveredDevicesUseCase

/**
 * Platform-agnostic implementation of [GetDiscoveredDevicesUseCase].
 *
 * Intentionally NOT annotated `@Single` in common source: on Android, the richer
 * [org.meshtastic.feature.connections.domain.usecase.AndroidGetDiscoveredDevicesUseCase] is the canonical binding, and
 * a common `@Single` here would silently override it (last-write-wins), producing an empty USB list. Each non-Android
 * target registers its own `@Single` wrapper (see `JvmGetDiscoveredDevicesUseCase`).
 */
open class CommonGetDiscoveredDevicesUseCase(
    private val recentAddressesDataSource: RecentAddressesDataSource,
    private val nodeRepository: NodeRepository,
    private val databaseManager: DatabaseManager,
    private val usbScanner: UsbScanner? = null,
) : GetDiscoveredDevicesUseCase {

    override fun invoke(showMock: Boolean, resolvedList: Flow<List<DiscoveredService>>): Flow<DiscoveredDevices> {
        val nodeDb = nodeRepository.nodeDBbyNum
        val usbFlow = usbScanner?.scanUsbDevices() ?: flowOf(emptyList())

        return combine(nodeDb, resolvedList, recentAddressesDataSource.recentAddresses, usbFlow) {
                db,
                resolved,
                recentList,
                usbList,
            ->
            val defaultName = safeCatchingAll { getStringSuspend(Res.string.meshtastic) }.getOrDefault("Meshtastic")
            val processedTcp = processTcpServices(resolved, recentList, defaultName)
            val discoveredTcpAddresses = processedTcp.mapTo(mutableSetOf()) { it.fullAddress }

            val discoveredTcpForUi = matchDiscoveredTcpNodes(processedTcp, db, resolved, databaseManager)
            val recentTcpForUi = buildRecentTcpEntries(recentList, discoveredTcpAddresses, db, databaseManager)

            val mockEntries = buildList {
                if (showMock) {
                    val label = safeCatchingAll { getStringSuspend(Res.string.demo_mode) }.getOrDefault("Demo Mode")
                    add(DeviceListEntry.Mock(label))
                }
            }

            DiscoveredDevices(
                discoveredTcpDevices = discoveredTcpForUi,
                recentTcpDevices = recentTcpForUi,
                usbDevices = usbList + mockEntries,
            )
        }
    }
}
