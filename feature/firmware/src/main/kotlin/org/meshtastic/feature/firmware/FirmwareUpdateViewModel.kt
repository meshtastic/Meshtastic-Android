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
package org.meshtastic.feature.firmware

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
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
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.datastore.BootloaderWarningDataSource
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.prefs.radio.RadioPrefs
import org.meshtastic.core.prefs.radio.isBle
import org.meshtastic.core.prefs.radio.isSerial
import org.meshtastic.core.prefs.radio.isTcp
import org.meshtastic.core.service.ConnectionState
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.firmware_update_battery_low
import org.meshtastic.core.strings.firmware_update_copying
import org.meshtastic.core.strings.firmware_update_dfu_aborted
import org.meshtastic.core.strings.firmware_update_dfu_error
import org.meshtastic.core.strings.firmware_update_disconnecting
import org.meshtastic.core.strings.firmware_update_enabling_dfu
import org.meshtastic.core.strings.firmware_update_extracting
import org.meshtastic.core.strings.firmware_update_failed
import org.meshtastic.core.strings.firmware_update_flashing
import org.meshtastic.core.strings.firmware_update_local_failed
import org.meshtastic.core.strings.firmware_update_method_ble
import org.meshtastic.core.strings.firmware_update_method_usb
import org.meshtastic.core.strings.firmware_update_method_wifi
import org.meshtastic.core.strings.firmware_update_no_device
import org.meshtastic.core.strings.firmware_update_node_info_missing
import org.meshtastic.core.strings.firmware_update_starting_dfu
import org.meshtastic.core.strings.firmware_update_unknown_error
import org.meshtastic.core.strings.firmware_update_unknown_hardware
import org.meshtastic.core.strings.firmware_update_updating
import org.meshtastic.core.strings.firmware_update_validating
import org.meshtastic.core.strings.unknown
import java.io.File
import javax.inject.Inject

private const val DFU_RECONNECT_PREFIX = "x"
private const val PERCENT_MAX_VALUE = 100f
private const val DEVICE_DETACH_TIMEOUT = 30_000L
private const val VERIFY_TIMEOUT = 60_000L
private const val VERIFY_DELAY = 2000L
private const val MIN_BATTERY_LEVEL = 10
private const val KIB_DIVISOR = 1024f
private const val MILLIS_PER_SECOND = 1000L

private val BLUETOOTH_ADDRESS_REGEX = Regex("([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}")

@HiltViewModel
@Suppress("LongParameterList", "TooManyFunctions")
class FirmwareUpdateViewModel
@Inject
constructor(
    private val firmwareReleaseRepository: FirmwareReleaseRepository,
    private val deviceHardwareRepository: DeviceHardwareRepository,
    private val nodeRepository: NodeRepository,
    private val serviceRepository: ServiceRepository,
    private val radioPrefs: RadioPrefs,
    private val bootloaderWarningDataSource: BootloaderWarningDataSource,
    private val firmwareUpdateManager: FirmwareUpdateManager,
    private val usbManager: UsbManager,
    private val fileHandler: FirmwareFileHandler,
) : ViewModel() {

    private val _state = MutableStateFlow<FirmwareUpdateState>(FirmwareUpdateState.Idle)
    val state: StateFlow<FirmwareUpdateState> = _state.asStateFlow()

    private val _selectedReleaseType = MutableStateFlow(FirmwareReleaseType.STABLE)
    val selectedReleaseType: StateFlow<FirmwareReleaseType> = _selectedReleaseType.asStateFlow()

    private val _selectedRelease = MutableStateFlow<FirmwareRelease?>(null)
    val selectedRelease: StateFlow<FirmwareRelease?> = _selectedRelease.asStateFlow()

    private val _deviceHardware = MutableStateFlow<DeviceHardware?>(null)
    val deviceHardware = _deviceHardware.asStateFlow()

    private val _currentFirmwareVersion = MutableStateFlow<String?>(null)
    val currentFirmwareVersion = _currentFirmwareVersion.asStateFlow()

    private var updateJob: Job? = null
    private var tempFirmwareFile: File? = null
    private var originalDeviceAddress: String? = null

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

    fun cancelUpdate() {
        updateJob?.cancel()
        _state.value = FirmwareUpdateState.Idle
        checkForUpdates()
    }

    @Suppress("LongMethod")
    fun checkForUpdates() {
        updateJob?.cancel()
        updateJob =
            viewModelScope.launch {
                _state.value = FirmwareUpdateState.Checking
                runCatching {
                    val ourNode = nodeRepository.myNodeInfo.value
                    val address = radioPrefs.devAddr?.drop(1)
                    if (address == null || ourNode == null) {
                        _state.value = FirmwareUpdateState.Error(getString(Res.string.firmware_update_no_device))
                        return@launch
                    }
                    getDeviceHardware(ourNode)?.let { deviceHardware ->
                        _deviceHardware.value = deviceHardware
                        _currentFirmwareVersion.value = ourNode.firmwareVersion

                        val releaseFlow =
                            if (_selectedReleaseType.value == FirmwareReleaseType.LOCAL) {
                                kotlinx.coroutines.flow.flowOf(null)
                            } else {
                                firmwareReleaseRepository.getReleaseFlow(_selectedReleaseType.value)
                            }

                        releaseFlow.collectLatest { release ->
                            _selectedRelease.value = release
                            val dismissed = bootloaderWarningDataSource.isDismissed(address)
                            val firmwareUpdateMethod =
                                when {
                                    radioPrefs.isSerial() -> {
                                        // ESP32 Serial updates are not supported from the app yet.
                                        if (deviceHardware.isEsp32Arc) {
                                            FirmwareUpdateMethod.Unknown
                                        } else {
                                            FirmwareUpdateMethod.Usb
                                        }
                                    }

                                    radioPrefs.isBle() -> FirmwareUpdateMethod.Ble
                                    radioPrefs.isTcp() -> FirmwareUpdateMethod.Wifi
                                    else -> FirmwareUpdateMethod.Unknown
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
                                    currentFirmwareVersion = ourNode.firmwareVersion,
                                )
                        }
                    }
                }
                    .onFailure { e ->
                        if (e is CancellationException) throw e
                        Logger.e(e) { "Error checking for updates" }
                        val unknownError = getString(Res.string.firmware_update_unknown_error)
                        _state.value = FirmwareUpdateState.Error(e.message ?: unknownError)
                    }
            }
    }

    fun startUpdate() {
        val currentState = _state.value as? FirmwareUpdateState.Ready ?: return
        val release = currentState.release ?: return
        originalDeviceAddress = currentState.address

        viewModelScope.launch {
            if (checkBatteryLevel()) {
                updateJob?.cancel()
                updateJob =
                    viewModelScope.launch {
                        try {
                            tempFirmwareFile =
                                firmwareUpdateManager.startUpdate(
                                    release = release,
                                    hardware = currentState.deviceHardware,
                                    address = currentState.address,
                                    updateState = { _state.value = it },
                                )

                            if (_state.value is FirmwareUpdateState.Success) {
                                verifyUpdateResult(originalDeviceAddress)
                            }
                        } catch (e: CancellationException) {
                            Logger.i { "Firmware update cancelled" }
                            _state.value = FirmwareUpdateState.Idle
                            checkForUpdates()
                            throw e
                        } catch (e: Exception) {
                            val failedMsg = getString(Res.string.firmware_update_failed)
                            _state.value = FirmwareUpdateState.Error(e.message ?: failedMsg)
                        }
                    }
            }
        }
    }

    fun saveDfuFile(uri: Uri) {
        val currentState = _state.value as? FirmwareUpdateState.AwaitingFileSave ?: return
        val firmwareFile = currentState.uf2File
        val sourceUri = currentState.sourceUri

        viewModelScope.launch {
            try {
                val copyingMsg = getString(Res.string.firmware_update_copying)
                _state.value = FirmwareUpdateState.Processing(ProgressState(copyingMsg))
                if (firmwareFile != null) {
                    fileHandler.copyFileToUri(firmwareFile, uri)
                } else if (sourceUri != null) {
                    fileHandler.copyUriToUri(sourceUri, uri)
                }

                val flashingMsg = getString(Res.string.firmware_update_flashing)
                _state.value = FirmwareUpdateState.Processing(ProgressState(flashingMsg))
                withTimeoutOrNull(DEVICE_DETACH_TIMEOUT) { usbManager.deviceDetachFlow().first() }
                    ?: Logger.w { "Timed out waiting for device to detach, assuming success" }

                verifyUpdateResult(originalDeviceAddress)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(e) { "Error saving DFU file" }
                val failedMsg = getString(Res.string.firmware_update_failed)
                _state.value = FirmwareUpdateState.Error(e.message ?: failedMsg)
            } finally {
                cleanupTemporaryFiles(fileHandler, tempFirmwareFile)
            }
        }
    }

    fun startUpdateFromFile(uri: Uri) {
        val currentState = _state.value as? FirmwareUpdateState.Ready ?: return
        if (currentState.updateMethod is FirmwareUpdateMethod.Ble && !isValidBluetoothAddress(currentState.address)) {
            viewModelScope.launch {
                val noDeviceMsg = getString(Res.string.firmware_update_no_device)
                _state.value = FirmwareUpdateState.Error(noDeviceMsg)
            }
            return
        }
        originalDeviceAddress = currentState.address

        updateJob?.cancel()
        updateJob =
            viewModelScope.launch {
                try {
                    val extractingMsg = getString(Res.string.firmware_update_extracting)
                    _state.value = FirmwareUpdateState.Processing(ProgressState(extractingMsg))
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

                    if (_state.value is FirmwareUpdateState.Success) {
                        verifyUpdateResult(originalDeviceAddress)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.e(e) { "Error starting update from file" }
                    val failedMsg = getString(Res.string.firmware_update_local_failed)
                    _state.value = FirmwareUpdateState.Error(e.message ?: failedMsg)
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
        firmwareUpdateManager.dfuProgressFlow().flowOn(Dispatchers.Main).collect { dfuState ->
            when (dfuState) {
                is DfuInternalState.Progress -> handleDfuProgress(dfuState)

                is DfuInternalState.Error -> {
                    val errorMsg = getString(Res.string.firmware_update_dfu_error, dfuState.message ?: "")
                    _state.value = FirmwareUpdateState.Error(errorMsg)
                    tempFirmwareFile = cleanupTemporaryFiles(fileHandler, tempFirmwareFile)
                }

                is DfuInternalState.Completed -> {
                    tempFirmwareFile = cleanupTemporaryFiles(fileHandler, tempFirmwareFile)
                    verifyUpdateResult(originalDeviceAddress)
                }

                is DfuInternalState.Aborted -> {
                    val abortedMsg = getString(Res.string.firmware_update_dfu_aborted)
                    _state.value = FirmwareUpdateState.Error(abortedMsg)
                    tempFirmwareFile = cleanupTemporaryFiles(fileHandler, tempFirmwareFile)
                }

                is DfuInternalState.Starting -> {
                    val msg = getString(Res.string.firmware_update_starting_dfu)
                    _state.value = FirmwareUpdateState.Processing(ProgressState(msg))
                }

                is DfuInternalState.EnablingDfuMode -> {
                    val msg = getString(Res.string.firmware_update_enabling_dfu)
                    _state.value = FirmwareUpdateState.Processing(ProgressState(msg))
                }

                is DfuInternalState.Validating -> {
                    val msg = getString(Res.string.firmware_update_validating)
                    _state.value = FirmwareUpdateState.Processing(ProgressState(msg))
                }

                is DfuInternalState.Disconnecting -> {
                    val msg = getString(Res.string.firmware_update_disconnecting)
                    _state.value = FirmwareUpdateState.Processing(ProgressState(msg))
                }

                else -> {} // ignore connected/disconnected for UI noise
            }
        }
    }

    private fun handleDfuProgress(dfuState: DfuInternalState.Progress) {
        val progress = dfuState.percent / PERCENT_MAX_VALUE
        val percentText = "${dfuState.percent}%"

        // Nordic DFU speed is in Bytes/ms. Convert to KiB/s.
        val speedBytesPerSec = dfuState.speed * MILLIS_PER_SECOND
        val speedKib = speedBytesPerSec / KIB_DIVISOR

        // Calculate ETA
        val totalBytes = tempFirmwareFile?.length() ?: 0L
        val etaText =
            if (totalBytes > 0 && speedBytesPerSec > 0 && dfuState.percent > 0) {
                val remainingBytes = totalBytes * (1f - progress)
                val etaSeconds = remainingBytes / speedBytesPerSec
                ", ETA: ${etaSeconds.toInt()}s"
            } else {
                ""
            }

        val partInfo =
            if (dfuState.partsTotal > 1) {
                " (Part ${dfuState.currentPart}/${dfuState.partsTotal})"
            } else {
                ""
            }

        val metrics =
            if (dfuState.speed > 0) {
                String.format(java.util.Locale.US, "%.1f KiB/s%s%s", speedKib, etaText, partInfo)
            } else {
                partInfo
            }
        viewModelScope.launch {
            val statusMsg =
                getString(Res.string.firmware_update_updating, "").replace(Regex(":?\\s*%1\\\$s%?"), "").trim()
            val details = "$percentText ($metrics)"
            _state.value = FirmwareUpdateState.Updating(ProgressState(statusMsg, progress, details))
        }
    }

    private suspend fun verifyUpdateResult(address: String?) {
        _state.value = FirmwareUpdateState.Verifying

        // Trigger a fresh connection attempt by MeshService
        address?.let { currentAddr ->
            Logger.i { "Post-update: Requesting MeshService to reconnect to $currentAddr" }
            serviceRepository.meshService?.setDeviceAddress("$DFU_RECONNECT_PREFIX$currentAddr")
        }

        // Wait for device to reconnect and settle
        val result =
            withTimeoutOrNull(VERIFY_TIMEOUT) {
                // Wait for both Connected state and node info to be present
                serviceRepository.connectionState.first { it is ConnectionState.Connected }
                nodeRepository.ourNodeInfo.filterNotNull().first()
                delay(VERIFY_DELAY) // Extra buffer for initial config sync
                true
            }

        if (result == null) {
            Logger.w { "Post-update verification timed out for $address" }
            _state.value = FirmwareUpdateState.VerificationFailed
        } else {
            _state.value = FirmwareUpdateState.Success
        }
    }

    private suspend fun checkBatteryLevel(): Boolean {
        val node = nodeRepository.ourNodeInfo.value ?: return true
        val level = node.batteryLevel ?: 1
        val isBatteryLow = level in 1..MIN_BATTERY_LEVEL

        if (isBatteryLow) {
            val batteryLowMsg = getString(Res.string.firmware_update_battery_low, level)
            _state.value = FirmwareUpdateState.Error(batteryLowMsg)
        }
        return !isBatteryLow
    }

    private suspend fun getDeviceHardware(ourNode: MyNodeEntity): DeviceHardware? {
        val nodeInfo = nodeRepository.ourNodeInfo.value
        val hwModelInt = nodeInfo?.user?.hw_model?.value
        val target = ourNode.pioEnv

        return if (hwModelInt != null) {
            deviceHardwareRepository.getDeviceHardwareByModel(hwModelInt, target).getOrElse {
                _state.value =
                    FirmwareUpdateState.Error(getString(Res.string.firmware_update_unknown_hardware, hwModelInt))
                null
            }
        } else {
            val nodeInfoMissing = getString(Res.string.firmware_update_node_info_missing)
            _state.value = FirmwareUpdateState.Error(nodeInfoMissing)
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
    FirmwareReleaseType.LOCAL -> kotlinx.coroutines.flow.flowOf(null)
}

sealed class FirmwareUpdateMethod(val description: StringResource) {
    object Usb : FirmwareUpdateMethod(Res.string.firmware_update_method_usb)

    object Ble : FirmwareUpdateMethod(Res.string.firmware_update_method_ble)

    object Wifi : FirmwareUpdateMethod(Res.string.firmware_update_method_wifi)

    object Unknown : FirmwareUpdateMethod(Res.string.unknown)
}
