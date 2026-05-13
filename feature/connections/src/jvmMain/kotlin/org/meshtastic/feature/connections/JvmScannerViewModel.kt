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

import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.datastore.RecentAddressesDataSource
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.network.repository.NetworkRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioPrefs
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.feature.connections.model.GetDiscoveredDevicesUseCase

/**
 * Desktop/JVM [ScannerViewModel] registration.
 *
 * On Desktop, the base [ScannerViewModel] is used directly. The default [requestBonding] connects without explicit
 * bonding since the OS Bluetooth stack handles pairing during the GATT connection.
 */
@KoinViewModel(binds = [ScannerViewModel::class])
@Suppress("LongParameterList")
class JvmScannerViewModel(
    serviceRepository: ServiceRepository,
    radioController: RadioController,
    radioInterfaceService: RadioInterfaceService,
    radioPrefs: RadioPrefs,
    recentAddressesDataSource: RecentAddressesDataSource,
    getDiscoveredDevicesUseCase: GetDiscoveredDevicesUseCase,
    networkRepository: NetworkRepository,
    dispatchers: org.meshtastic.core.di.CoroutineDispatchers,
    uiPrefs: UiPrefs,
    bleScanner: org.meshtastic.core.ble.BleScanner? = null,
) : ScannerViewModel(
    serviceRepository,
    radioController,
    radioInterfaceService,
    radioPrefs,
    recentAddressesDataSource,
    getDiscoveredDevicesUseCase,
    networkRepository,
    dispatchers,
    uiPrefs,
    bleScanner,
)
