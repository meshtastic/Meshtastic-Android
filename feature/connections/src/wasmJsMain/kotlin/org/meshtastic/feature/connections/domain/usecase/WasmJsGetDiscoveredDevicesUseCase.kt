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

import org.koin.core.annotation.Single
import org.meshtastic.core.common.database.DatabaseManager
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.feature.connections.data.RecentAddressesSource
import org.meshtastic.feature.connections.model.GetDiscoveredDevicesUseCase

/**
 * wasmJs binding for [GetDiscoveredDevicesUseCase] — same shape as [JvmGetDiscoveredDevicesUseCase], minus USB (no
 * [UsbScanner] arg: WebUSB hot-plug scanning is a future pass, not this v0 slice; `usbDevices` in the resulting
 * [org.meshtastic.feature.connections.model.DiscoveredDevices] is always empty). Found via an actual browser load
 * (`NoDefinitionFoundException` for [GetDiscoveredDevicesUseCase], since [CommonGetDiscoveredDevicesUseCase] is
 * deliberately un-annotated in commonMain — see its own KDoc), not a compile-time check.
 */
@Single(binds = [GetDiscoveredDevicesUseCase::class])
class WasmJsGetDiscoveredDevicesUseCase(
    recentAddressesDataSource: RecentAddressesSource,
    nodeRepository: NodeRepository,
    databaseManager: DatabaseManager,
) : CommonGetDiscoveredDevicesUseCase(recentAddressesDataSource, nodeRepository, databaseManager)
