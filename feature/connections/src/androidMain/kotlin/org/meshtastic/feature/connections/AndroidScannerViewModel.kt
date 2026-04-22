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
package org.meshtastic.feature.connections

import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.ble.BluetoothRepository
import org.meshtastic.core.datastore.RecentAddressesDataSource
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.model.util.anonymize
import org.meshtastic.core.network.repository.NetworkRepository
import org.meshtastic.core.network.repository.UsbRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioPrefs
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.feature.connections.model.AndroidUsbDeviceData
import org.meshtastic.feature.connections.model.DeviceListEntry
import org.meshtastic.feature.connections.model.GetDiscoveredDevicesUseCase

@KoinViewModel(binds = [ScannerViewModel::class])
@Suppress("LongParameterList", "TooManyFunctions")
class AndroidScannerViewModel(
    serviceRepository: ServiceRepository,
    radioController: RadioController,
    radioInterfaceService: RadioInterfaceService,
    radioPrefs: RadioPrefs,
    recentAddressesDataSource: RecentAddressesDataSource,
    getDiscoveredDevicesUseCase: GetDiscoveredDevicesUseCase,
    networkRepository: NetworkRepository,
    dispatchers: org.meshtastic.core.di.CoroutineDispatchers,
    private val bluetoothRepository: BluetoothRepository,
    private val usbRepository: UsbRepository,
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
) {
    override fun requestBonding(entry: DeviceListEntry.Ble) {
        Logger.i { "Starting bonding for ${entry.device.address.anonymize}" }
        viewModelScope.launch {
            @Suppress("TooGenericExceptionCaught")
            try {
                bluetoothRepository.bond(entry.device)
                Logger.i { "Bonding complete for ${entry.device.address.anonymize}, selecting device..." }
                changeDeviceAddress(entry.fullAddress)
            } catch (ex: SecurityException) {
                Logger.w(ex) { "Bonding failed for ${entry.device.address.anonymize} Permissions not granted" }
                serviceRepository.setErrorMessage(
                    text = "Bonding failed: ${ex.message} Permissions not granted",
                    severity = Severity.Warn,
                )
            } catch (ex: Exception) {
                // Bonding is often flaky and can fail for many reasons (timeout, user cancel, etc)
                val message = ex.message ?: ""
                if (message.contains("Received bond state changed 11")) {
                    // This is a known issue where bonding is still in progress, ignore as error
                    Logger.d { "Bonding still in progress for ${entry.device.address.anonymize}" }
                } else {
                    Logger.w(ex) { "Bonding failed for ${entry.device.address.anonymize}" }
                    serviceRepository.setErrorMessage(text = "Bonding failed: ${ex.message}", severity = Severity.Warn)
                }
            }
        }
    }

    override fun requestPermission(entry: DeviceListEntry.Usb) {
        val usbData = entry.usbData as? AndroidUsbDeviceData ?: return
        usbRepository
            .requestPermission(usbData.driver.device)
            .onEach { granted ->
                if (granted) {
                    Logger.i { "User approved USB access" }
                    changeDeviceAddress(entry.fullAddress)
                } else {
                    Logger.e { "USB permission denied for device ${entry.address}" }
                }
            }
            .launchIn(viewModelScope)
    }
}
