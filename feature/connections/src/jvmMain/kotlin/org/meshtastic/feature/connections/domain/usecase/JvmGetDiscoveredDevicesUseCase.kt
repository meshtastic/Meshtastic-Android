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

import org.koin.core.annotation.Single
import org.meshtastic.core.common.database.DatabaseManager
import org.meshtastic.core.datastore.RecentAddressesDataSource
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.feature.connections.model.GetDiscoveredDevicesUseCase

/**
 * JVM/Desktop binding for [org.meshtastic.feature.connections.model.GetDiscoveredDevicesUseCase].
 *
 * The common use-case body lives in [CommonGetDiscoveredDevicesUseCase] (un-annotated, so it does not collide with the
 * Android impl). This thin subclass registers it with Koin only for JVM/Desktop targets, where [JvmUsbScanner] supplies
 * the USB data source.
 *
 * The explicit `binds` is required because Koin annotations only infer interface bindings from directly-implemented
 * interfaces — the [GetDiscoveredDevicesUseCase] interface is implemented on the parent
 * [CommonGetDiscoveredDevicesUseCase], which the annotation processor does not walk.
 */
@Single(binds = [GetDiscoveredDevicesUseCase::class])
class JvmGetDiscoveredDevicesUseCase(
    recentAddressesDataSource: RecentAddressesDataSource,
    nodeRepository: NodeRepository,
    databaseManager: DatabaseManager,
    usbScanner: UsbScanner? = null,
) : CommonGetDiscoveredDevicesUseCase(recentAddressesDataSource, nodeRepository, databaseManager, usbScanner)
