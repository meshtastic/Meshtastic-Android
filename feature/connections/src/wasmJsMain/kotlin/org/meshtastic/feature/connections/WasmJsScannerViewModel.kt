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

import org.jetbrains.compose.resources.StringResource
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.network.repository.NetworkRepository
import org.meshtastic.core.repository.RadioController
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioPrefs
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.web_bluetooth_unsupported
import org.meshtastic.core.resources.web_serial_unsupported
import org.meshtastic.core.resources.web_transports_unsupported
import org.meshtastic.core.ui.util.webBluetoothAvailable
import org.meshtastic.core.ui.util.webSerialAvailable
import org.meshtastic.feature.connections.data.PendingFirmwareRecoverySource
import org.meshtastic.feature.connections.data.RecentAddressesSource
import org.meshtastic.feature.connections.domain.usecase.WasmJsUsbScanner
import org.meshtastic.feature.connections.model.GetDiscoveredDevicesUseCase

/**
 * The web [ScannerViewModel]: Web Bluetooth and Web Serial. Each pane needs both the transport factory to support it
 * and this browser to have the API behind it, so Firefox and Safari never reach a picker that cannot open.
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
    dispatchers: CoroutineDispatchers,
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
    bleScanner = bleScanner,
    bluetoothSupported = DeviceType.BLE in radioInterfaceService.supportedDeviceTypes && webBluetoothAvailable(),
    usbSupported = DeviceType.USB in radioInterfaceService.supportedDeviceTypes && webSerialAvailable(),
    networkSupported = DeviceType.TCP in radioInterfaceService.supportedDeviceTypes,
    usbScanner = usbScanner,
) {
    override val unsupportedTransportNotice: StringResource? =
        when {
            !webBluetoothAvailable() && !webSerialAvailable() -> Res.string.web_transports_unsupported
            !webBluetoothAvailable() -> Res.string.web_bluetooth_unsupported
            !webSerialAvailable() -> Res.string.web_serial_unsupported
            else -> null
        }
}
