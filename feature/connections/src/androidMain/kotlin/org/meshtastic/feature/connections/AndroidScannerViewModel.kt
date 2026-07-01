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

import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.ble.BluetoothRepository
import org.meshtastic.core.datastore.FirmwareRecoveryDataSource
import org.meshtastic.core.datastore.RecentAddressesDataSource
import org.meshtastic.core.model.util.anonymize
import org.meshtastic.core.network.repository.NetworkRepository
import org.meshtastic.core.network.repository.UsbRepository
import org.meshtastic.core.repository.RadioController
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioPrefs
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.bonding_failed_permissions
import org.meshtastic.core.resources.bonding_failed_retry
import org.meshtastic.core.resources.usb_permission_denied
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
    firmwareRecoveryDataSource: FirmwareRecoveryDataSource,
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
    firmwareRecoveryDataSource,
    bleScanner,
) {
    override fun requestBonding(entry: DeviceListEntry.Ble) {
        Logger.i { "Starting bonding for ${entry.device.address.anonymize}" }
        viewModelScope.launch {
            @Suppress("TooGenericExceptionCaught")
            val armTransport =
                try {
                    bluetoothRepository.bond(entry.device)
                    Logger.i { "Bonding complete for ${entry.device.address.anonymize}, selecting device..." }
                    true
                } catch (ex: SecurityException) {
                    // No BLUETOOTH_CONNECT permission — connecting would fail the same way, so surface the
                    // error and do not arm the transport.
                    Logger.w(ex) { "Bonding failed for ${entry.device.address.anonymize} Permissions not granted" }
                    serviceRepository.setErrorMessage(
                        text = getString(Res.string.bonding_failed_permissions),
                        severity = Severity.Warn,
                    )
                    false
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    if (bluetoothRepository.isBonded(entry.device.address)) {
                        Logger.w(ex) {
                            "Bonding did not complete cleanly for ${entry.device.address.anonymize}, " +
                                "but Android now reports it bonded; selecting device"
                        }
                        true
                    } else {
                        Logger.w(ex) {
                            "Bonding did not complete cleanly for ${entry.device.address.anonymize}; " +
                                "waiting for an explicit retry"
                        }
                        serviceRepository.setErrorMessage(
                            text = getString(Res.string.bonding_failed_retry),
                            severity = Severity.Warn,
                        )
                        false
                    }
                }
            if (armTransport) {
                changeDeviceAddress(entry.fullAddress)
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
                    serviceRepository.setErrorMessage(
                        text = getString(Res.string.usb_permission_denied),
                        severity = Severity.Warn,
                    )
                }
            }
            .launchIn(viewModelScope)
    }
}
