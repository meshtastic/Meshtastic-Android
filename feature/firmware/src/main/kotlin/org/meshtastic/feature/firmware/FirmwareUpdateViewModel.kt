/*
 * Copyright (c) 2025 Meshtastic LLC
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

package org.meshtastic.feature.firmware

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.meshtastic.core.data.repository.DeviceHardwareRepository
import org.meshtastic.core.data.repository.FirmwareReleaseRepository
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.database.entity.FirmwareReleaseType
import org.meshtastic.core.datastore.BootloaderWarningDataSource
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.prefs.radio.RadioPrefs
import org.meshtastic.core.prefs.radio.isBle
import org.meshtastic.core.prefs.radio.isSerial
import org.meshtastic.core.prefs.radio.isTcp
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.firmware_update_copying
import org.meshtastic.core.strings.firmware_update_extracting
import org.meshtastic.core.strings.firmware_update_failed
import org.meshtastic.core.strings.firmware_update_flashing
import org.meshtastic.core.strings.firmware_update_method_ble
import org.meshtastic.core.strings.firmware_update_method_usb
import org.meshtastic.core.strings.firmware_update_method_wifi
import org.meshtastic.core.strings.firmware_update_no_device
import org.meshtastic.core.strings.firmware_update_starting_dfu
import org.meshtastic.core.strings.firmware_update_unknown_hardware
import org.meshtastic.core.strings.firmware_update_updating
import org.meshtastic.core.strings.unknown
import java.io.File
import javax.inject.Inject

private const val DFU_RECONNECT_PREFIX = "x"
private const val PERCENT_MAX_VALUE = 100f
private const val DEVICE_DETACH_TIMEOUT = 30_000L

private val BLUETOOTH_ADDRESS_REGEX = Regex("([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}")

@HiltViewModel
@Suppress("LongParameterList")
class FirmwareUpdateViewModel
@Inject
constructor(
    private val firmwareReleaseRepository: FirmwareReleaseRepository,
    private val deviceHardwareRepository: DeviceHardwareRepository,
    private val nodeRepository: NodeRepository,
    private val serviceRepository: ServiceRepository,
    private val radioPrefs: RadioPrefs,
    @ApplicationContext private val context: Context,
    private val bootloaderWarningDataSource: BootloaderWarningDataSource,
    private val firmwareUpdateManager: FirmwareUpdateManager,
    private val dfuManager: DfuManager,
    private val usbManager: UsbManager,
    private val fileHandler: FirmwareFileHandler,
) : ViewModel() {

    private val _state = MutableStateFlow<FirmwareUpdateState>(FirmwareUpdateState.Idle)
    val state: StateFlow<FirmwareUpdateState> = _state.asStateFlow()

    private val _selectedReleaseType = MutableStateFlow(FirmwareReleaseType.STABLE)
    val selectedReleaseType: StateFlow<FirmwareReleaseType> = _selectedReleaseType.asStateFlow()

    private var updateJob: Job? = null
    private var tempFirmwareFile: File? = null

    init {
        // Cleanup potential leftovers
        viewModelScope.launch {
            tempFirmwareFile = cleanupTemporaryFiles(fileHandler, tempFirmwareFile)
            checkForUpdates()
            observeDfuProgress()
        }
    }

    override fun onCleared() {
        super.onCleared()
        tempFirmwareFile = cleanupTemporaryFiles(fileHandler, tempFirmwareFile)
    }

    fun setReleaseType(type: FirmwareReleaseType) {
        _selectedReleaseType.value = type
        checkForUpdates()
    }

    fun checkForUpdates() {
        updateJob?.cancel()
        updateJob =
            viewModelScope.launch {
                _state.value = FirmwareUpdateState.Checking
                runCatching {
                    val ourNode = nodeRepository.ourNodeInfo.value
                    val address = radioPrefs.devAddr?.drop(1)
                    if (address == null || ourNode == null) {
                        _state.value = FirmwareUpdateState.Error(getString(Res.string.firmware_update_no_device))
                        return@launch
                    }
                    getDeviceHardware(ourNode)?.let { deviceHardware ->
                        firmwareReleaseRepository.getReleaseFlow(
                            _selectedReleaseType.value,
                        ).collectLatest { release ->
                            val dismissed = bootloaderWarningDataSource.isDismissed(address)
                            val firmwareUpdateMethod =
                                if (radioPrefs.isSerial()) {
                                    FirmwareUpdateMethod.Usb
                                } else if (radioPrefs.isBle()) {
                                    FirmwareUpdateMethod.Ble
                                } else if (radioPrefs.isTcp()) {
                                    FirmwareUpdateMethod.Wifi
                                } else {
                                    FirmwareUpdateMethod.Unknown
                                }
                            _state.value =
                                FirmwareUpdateState.Ready(
                                    release = release,
                                    deviceHardware = deviceHardware,
                                    address = address,
                                    showBootloaderWarning =
                                    deviceHardware.requiresBootloaderUpgradeForOta == true &&
                                        !dismissed &&
                                        radioPrefs.isBle(),
                                    updateMethod = firmwareUpdateMethod,
                                    currentFirmwareVersion = ourNode.metadata?.firmwareVersion,
                                )
                        }
                    }
                }
                    .onFailure { e ->
                        if (e is CancellationException) throw e
                        Logger.e(e) { "Error checking for updates" }
                        _state.value = FirmwareUpdateState.Error(e.message ?: "Unknown error")
                    }
            }
    }

    fun startUpdate() {
        val currentState = _state.value as? FirmwareUpdateState.Ready ?: return
        val release = currentState.release ?: return

        updateJob?.cancel()
        updateJob =
            viewModelScope.launch {
                tempFirmwareFile =
                    firmwareUpdateManager.startUpdate(
                        release = release,
                        hardware = currentState.deviceHardware,
                        address = currentState.address,
                        updateState = { _state.value = it },
                    )
            }
    }

    fun saveDfuFile(uri: Uri) {
        val currentState = _state.value as? FirmwareUpdateState.AwaitingFileSave ?: return
        val firmwareFile = currentState.uf2File
        val sourceUri = currentState.sourceUri

        viewModelScope.launch {
            try {
                _state.value = FirmwareUpdateState.Processing(getString(Res.string.firmware_update_copying))
                if (firmwareFile != null) {
                    fileHandler.copyFileToUri(firmwareFile, uri)
                } else if (sourceUri != null) {
                    fileHandler.copyUriToUri(sourceUri, uri)
                }

                _state.value = FirmwareUpdateState.Processing(getString(Res.string.firmware_update_flashing))
                withTimeoutOrNull(DEVICE_DETACH_TIMEOUT) { usbManager.deviceDetachFlow().first() }
                    ?: Logger.w { "Timed out waiting for device to detach, assuming success" }

                _state.value = FirmwareUpdateState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(e) { "Error saving DFU file" }
                _state.value = FirmwareUpdateState.Error(e.message ?: getString(Res.string.firmware_update_failed))
            } finally {
                cleanupTemporaryFiles(fileHandler, tempFirmwareFile)
            }
        }
    }

    fun startUpdateFromFile(uri: Uri) {
        val currentState = _state.value as? FirmwareUpdateState.Ready ?: return
        if (currentState.updateMethod is FirmwareUpdateMethod.Ble && !isValidBluetoothAddress(currentState.address)) {
            return
        }

        updateJob?.cancel()
        updateJob =
            viewModelScope.launch {
                try {
                    _state.value = FirmwareUpdateState.Processing(getString(Res.string.firmware_update_extracting))
                    val extension = if (currentState.updateMethod is FirmwareUpdateMethod.Ble) ".zip" else ".uf2"
                    val extractedFile = fileHandler.extractFirmware(uri, currentState.deviceHardware, extension)

                    tempFirmwareFile = extractedFile
                    val firmwareUri = if (extractedFile != null) Uri.fromFile(extractedFile) else uri

                    tempFirmwareFile =
                        firmwareUpdateManager.startUpdate(
                            release =
                            FirmwareRelease(id = "local", title = "Local File", zipUrl = "", releaseNotes = ""),
                            hardware = currentState.deviceHardware,
                            address = currentState.address,
                            updateState = { _state.value = it },
                            firmwareUri = firmwareUri,
                        )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.e(e) { "Error starting update from file" }
                    _state.value = FirmwareUpdateState.Error(e.message ?: "Local update failed")
                }
            }
    }

    fun dismissBootloaderWarningForCurrentDevice() {
        val currentState = _state.value as? FirmwareUpdateState.Ready ?: return
        viewModelScope.launch {
            bootloaderWarningDataSource.dismiss(currentState.address)
            _state.value = currentState.copy(showBootloaderWarning = false)
        }
    }

    private suspend fun observeDfuProgress() {
        dfuManager.progressFlow().flowOn(Dispatchers.Main).collect { dfuState ->
            when (dfuState) {
                is DfuInternalState.Progress -> {
                    val msg = getString(Res.string.firmware_update_updating, "${dfuState.percent}")
                    _state.value = FirmwareUpdateState.Updating(dfuState.percent / PERCENT_MAX_VALUE, msg)
                }

                is DfuInternalState.Error -> {
                    _state.value = FirmwareUpdateState.Error("DFU Error: ${dfuState.message}")
                    tempFirmwareFile = cleanupTemporaryFiles(fileHandler, tempFirmwareFile)
                }

                is DfuInternalState.Completed -> {
                    _state.value = FirmwareUpdateState.Success
                    serviceRepository.meshService?.setDeviceAddress("$DFU_RECONNECT_PREFIX${dfuState.address}")
                    tempFirmwareFile = cleanupTemporaryFiles(fileHandler, tempFirmwareFile)
                }

                is DfuInternalState.Aborted -> {
                    _state.value = FirmwareUpdateState.Error("DFU Aborted")
                    tempFirmwareFile = cleanupTemporaryFiles(fileHandler, tempFirmwareFile)
                }

                is DfuInternalState.Starting -> {
                    val msg = getString(Res.string.firmware_update_starting_dfu)
                    _state.value = FirmwareUpdateState.Processing(msg)
                }
            }
        }
    }

    private suspend fun getDeviceHardware(ourNode: org.meshtastic.core.database.model.Node): DeviceHardware? {
        val hwModel = ourNode.user.hwModel?.number
        return if (hwModel != null) {
            deviceHardwareRepository.getDeviceHardwareByModel(hwModel).getOrElse {
                _state.value =
                    FirmwareUpdateState.Error(getString(Res.string.firmware_update_unknown_hardware, hwModel))
                null
            }
        } else {
            _state.value = FirmwareUpdateState.Error("Node user information is missing.")
            null
        }
    }
}

private fun cleanupTemporaryFiles(fileHandler: FirmwareFileHandler, tempFirmwareFile: File?): File? {
    runCatching {
        tempFirmwareFile?.takeIf { it.exists() }?.delete()
        fileHandler.cleanupAllTemporaryFiles()
    }
        .onFailure { e -> Logger.w(e) { "Failed to cleanup temp files" } }
    return null
}

private fun isValidBluetoothAddress(address: String?): Boolean =
    address != null && BLUETOOTH_ADDRESS_REGEX.matches(address)

private fun FirmwareReleaseRepository.getReleaseFlow(type: FirmwareReleaseType): Flow<FirmwareRelease?> = when (type) {
    FirmwareReleaseType.STABLE -> stableRelease
    FirmwareReleaseType.ALPHA -> alphaRelease
}

sealed class FirmwareUpdateMethod(val description: StringResource) {
    object Usb : FirmwareUpdateMethod(Res.string.firmware_update_method_usb)

    object Ble : FirmwareUpdateMethod(Res.string.firmware_update_method_ble)

    object Wifi : FirmwareUpdateMethod(Res.string.firmware_update_method_wifi)

    object Unknown : FirmwareUpdateMethod(Res.string.unknown)
}
