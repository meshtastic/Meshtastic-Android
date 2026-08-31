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
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.network.repository.NetworkRepository
import org.meshtastic.core.repository.RadioController
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioPrefs
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.feature.connections.data.PendingFirmwareRecoverySource
import org.meshtastic.feature.connections.data.RecentAddressesSource
import org.meshtastic.feature.connections.domain.usecase.WasmJsUsbScanner
import org.meshtastic.feature.connections.model.GetDiscoveredDevicesUseCase

/**
 * wasmJs [ScannerViewModel] registration — same shape as [JvmScannerViewModel]. Unlike Jvm/Android, `bleScanner` and
 * `usbScanner` are NOT optional here: `CoreBleWasmJsModule` (webApp/di/WebKoinModule.kt) always provides a real Web
 * Bluetooth-backed [BleScanner] (this repo's core:ble wasmJs pass), and [WasmJsUsbScanner] always provides a real Web
 * Serial-backed one, so this platform has real BLE and USB scanning, just no TCP discovery. Found via an actual browser
 * load, not a compile-time check — same as [WasmJsGetDiscoveredDevicesUseCase]'s own KDoc explains.
 */
@KoinViewModel(binds = [ScannerViewModel::class])
@Suppress("LongParameterList")
class WasmJsScannerViewModel(
    serviceRepository: ServiceRepository,
    radioController: RadioController,
    radioInterfaceService: RadioInterfaceService,
    radioPrefs: RadioPrefs,
    recentAddressesDataSource: RecentAddressesSource,
    getDiscoveredDevicesUseCase: GetDiscoveredDevicesUseCase,
    networkRepository: NetworkRepository,
    dispatchers: org.meshtastic.core.di.CoroutineDispatchers,
    uiPrefs: UiPrefs,
    firmwareRecoveryDataSource: PendingFirmwareRecoverySource,
    bleScanner: BleScanner,
    usbScanner: WasmJsUsbScanner,
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
    firmwareRecoveryDataSource,
    bleScanner,
    usbScanner,
)
