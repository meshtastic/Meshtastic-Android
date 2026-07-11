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
package org.meshtastic.feature.firmware

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.StringResource
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.common.di.ApplicationCoroutineScope
import org.meshtastic.core.common.state.ExcludedModulesUnlock
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.database.entity.FirmwareReleaseType
import org.meshtastic.core.datastore.BootloaderWarningDataSource
import org.meshtastic.core.datastore.FirmwareRecoveryDataSource
import org.meshtastic.core.datastore.model.PendingFirmwareRecovery
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.util.anonymize
import org.meshtastic.core.repository.DeviceHardwareRepository
import org.meshtastic.core.repository.FirmwareReleaseRepository
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioController
import org.meshtastic.core.repository.RadioPrefs
import org.meshtastic.core.repository.isBle
import org.meshtastic.core.repository.isSerial
import org.meshtastic.core.repository.isTcp
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.UiText
import org.meshtastic.core.resources.firmware_recovery_ble_failed
import org.meshtastic.core.resources.firmware_update_archive_missing_target
import org.meshtastic.core.resources.firmware_update_battery_low
import org.meshtastic.core.resources.firmware_update_context_changed
import org.meshtastic.core.resources.firmware_update_copying
import org.meshtastic.core.resources.firmware_update_extracting
import org.meshtastic.core.resources.firmware_update_failed
import org.meshtastic.core.resources.firmware_update_filename_unavailable
import org.meshtastic.core.resources.firmware_update_flashing
import org.meshtastic.core.resources.firmware_update_invalid_local_file_detail
import org.meshtastic.core.resources.firmware_update_method_ble
import org.meshtastic.core.resources.firmware_update_method_usb
import org.meshtastic.core.resources.firmware_update_method_wifi
import org.meshtastic.core.resources.firmware_update_missing_target
import org.meshtastic.core.resources.firmware_update_no_device
import org.meshtastic.core.resources.firmware_update_node_info_missing
import org.meshtastic.core.resources.firmware_update_requires_bin
import org.meshtastic.core.resources.firmware_update_requires_ota_zip
import org.meshtastic.core.resources.firmware_update_requires_uf2
import org.meshtastic.core.resources.firmware_update_unknown_error
import org.meshtastic.core.resources.firmware_update_unknown_hardware
import org.meshtastic.core.resources.firmware_update_unsupported_update_method
import org.meshtastic.core.resources.unknown

private const val DEVICE_DETACH_TIMEOUT = 30_000L
private const val VERIFY_TIMEOUT = 60_000L
private const val VERIFY_DELAY = 2000L
private const val MIN_BATTERY_LEVEL = 10
private const val LOCAL_RELEASE_ID = "local"

private val BLUETOOTH_ADDRESS_REGEX = Regex("([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}")

/**
 * ViewModel driving the firmware update screen. Coordinates release checking, file retrieval, transport-specific update
 * execution, and post-update device verification.
 */
@Suppress("LongParameterList", "TooManyFunctions", "LargeClass")
@KoinViewModel
class FirmwareUpdateViewModel(
    private val firmwareReleaseRepository: FirmwareReleaseRepository,
    private val deviceHardwareRepository: DeviceHardwareRepository,
    private val nodeRepository: NodeRepository,
    private val radioController: RadioController,
    private val radioPrefs: RadioPrefs,
    private val bootloaderWarningDataSource: BootloaderWarningDataSource,
    private val firmwareRecoveryDataSource: FirmwareRecoveryDataSource,
    private val firmwareUpdateManager: FirmwareUpdateManager,
    private val usbManager: FirmwareUsbManager,
    private val fileHandler: FirmwareFileHandler,
    private val applicationScope: ApplicationCoroutineScope,
    excludedModulesUnlock: ExcludedModulesUnlock,
) : ViewModel() {

    private val _state = MutableStateFlow<FirmwareUpdateState>(FirmwareUpdateState.Idle)
    val state: StateFlow<FirmwareUpdateState> = _state.asStateFlow()

    val connectionState = radioController.connectionState

    /** The excluded-modules easter egg also unlocks the nightly preview channel, like the web flasher's konami code. */
    val nightlyUnlocked: StateFlow<Boolean> = excludedModulesUnlock.unlocked

    private val _selectedReleaseType = MutableStateFlow(FirmwareReleaseType.STABLE)
    val selectedReleaseType: StateFlow<FirmwareReleaseType> = _selectedReleaseType.asStateFlow()

    private val _selectedRelease = MutableStateFlow<FirmwareRelease?>(null)
    val selectedRelease: StateFlow<FirmwareRelease?> = _selectedRelease.asStateFlow()

    private val _deviceHardware = MutableStateFlow<DeviceHardware?>(null)
    val deviceHardware = _deviceHardware.asStateFlow()

    private val _currentFirmwareVersion = MutableStateFlow<String?>(null)
    val currentFirmwareVersion = _currentFirmwareVersion.asStateFlow()

    private val _pendingLocalFirmwareFile = MutableStateFlow<PendingLocalFirmwareFile?>(null)
    val pendingLocalFirmwareFile: StateFlow<PendingLocalFirmwareFile?> = _pendingLocalFirmwareFile.asStateFlow()

    private var updateJob: Job? = null
    private var prepareJob: Job? = null
    private var tempFirmwareFile: FirmwareArtifact? = null
    private var pendingLocalFirmwareArtifact: FirmwareArtifact? = null
    private var originalDeviceAddress: String? = null

    /** Set when [checkForUpdates] enters recovery mode (disconnected + a saved record); consumed by [startUpdate]. */
    private var pendingRecovery: PendingFirmwareRecovery? = null

    init {
        // Cleanup potential leftovers
        viewModelScope.launch {
            tempFirmwareFile = cleanupTemporaryFiles(fileHandler, tempFirmwareFile)
            checkForUpdates()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCleared() {
        super.onCleared()
        prepareJob?.cancel()
        prepareJob = null
        // viewModelScope is already cancelled when onCleared() runs, so launch cleanup on the
        // application-wide scope (SupervisorJob + ioDispatcher). ATOMIC start + NonCancellable
        // context keeps cleanup running even if something tries to cancel it mid-flight.
        val pendingArtifact = pendingLocalFirmwareArtifact
        pendingLocalFirmwareArtifact = null
        applicationScope.launch(start = CoroutineStart.ATOMIC) {
            withContext(NonCancellable) {
                tempFirmwareFile = cleanupTemporaryFiles(fileHandler, tempFirmwareFile)
                if (pendingArtifact != null && pendingArtifact != tempFirmwareFile) {
                    cleanupTemporaryFiles(fileHandler, pendingArtifact)
                }
                safeCatching { fileHandler.cleanupAllTemporaryFiles() }
                    .onFailure { Logger.w(it) { "Failed to cleanup remaining temp files" } }
            }
        }
    }

    fun setReleaseType(type: FirmwareReleaseType) {
        _selectedReleaseType.value = type
        checkForUpdates()
    }

    fun cancelUpdate() {
        updateJob?.cancel()
        clearPendingLocalFirmwareFile()
        _state.value = FirmwareUpdateState.Idle
        checkForUpdates()
    }

    @Suppress("LongMethod")
    fun checkForUpdates() {
        updateJob?.cancel()
        clearPendingLocalFirmwareFile()
        updateJob =
            viewModelScope.launch {
                _state.value = FirmwareUpdateState.Checking
                safeCatching {
                    val ourNode = nodeRepository.myNodeInfo.value
                    val address = radioPrefs.devAddr.value?.drop(1)
                    if (address == null || ourNode == null) {
                        // Not connected: offer to re-flash a device stranded in bootloader mode if we saved a
                        // recovery record when its (now-interrupted) update was triggered. Otherwise, no device.
                        enterRecoveryModeOrError()
                        return@launch
                    }
                    val deviceHardware = getDeviceHardware(ourNode) ?: return@launch
                    _deviceHardware.value = deviceHardware
                    _currentFirmwareVersion.value = ourNode.firmwareVersion

                    val releaseFlow =
                        if (_selectedReleaseType.value == FirmwareReleaseType.LOCAL) {
                            flowOf(null)
                        } else {
                            firmwareReleaseRepository.getReleaseFlow(_selectedReleaseType.value)
                        }
                    releaseFlow.collectLatest { release ->
                        _selectedRelease.value = release

                        val dismissed = bootloaderWarningDataSource.isDismissed(address)
                        val firmwareUpdateMethod =
                            when {
                                radioPrefs.isSerial() -> {
                                    // Serial OTA is not yet supported for ESP32 — only nRF52/RP2040 UF2.
                                    if (deviceHardware.isEsp32Arc) {
                                        FirmwareUpdateMethod.Unknown
                                    } else {
                                        FirmwareUpdateMethod.Usb
                                    }
                                }

                                radioPrefs.isBle() -> FirmwareUpdateMethod.Ble

                                radioPrefs.isTcp() -> {
                                    // WiFi OTA is ESP32-only; nRF52/RP2040 have no TCP update path.
                                    if (deviceHardware.isEsp32Arc) {
                                        FirmwareUpdateMethod.Wifi
                                    } else {
                                        FirmwareUpdateMethod.Unknown
                                    }
                                }

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
                    .onFailure { e ->
                        Logger.e(e) { "Error checking for updates" }
                        val unknownError = UiText.Resource(Res.string.firmware_update_unknown_error)
                        _state.value =
                            FirmwareUpdateState.Error(
                                if (e.message != null) UiText.DynamicString(e.message!!) else unknownError,
                            )
                    }
            }
    }

    /**
     * Disconnected entry point: if a firmware update was interrupted and left a device stranded in bootloader mode,
     * rebuild a recovery-flavored [FirmwareUpdateState.Ready] from the saved record so the user can re-flash it without
     * first reconnecting (the bootloader exposes no mesh service to connect to). No record ⇒ the usual "no device".
     */
    private suspend fun enterRecoveryModeOrError() {
        val recovery = firmwareRecoveryDataSource.pending.first()
        if (recovery == null) {
            clearDeviceMetadata()
            _state.value = FirmwareUpdateState.Error(UiText.Resource(Res.string.firmware_update_no_device))
            return
        }
        pendingRecovery = recovery
        val hardware =
            deviceHardwareRepository.getDeviceHardwareByModel(recovery.hwModel, recovery.pioEnv).getOrElse {
                clearDeviceMetadata()
                _state.value =
                    FirmwareUpdateState.Error(
                        UiText.Resource(Res.string.firmware_update_unknown_hardware, recovery.hwModel),
                    )
                null
            } ?: return

        _deviceHardware.value = hardware
        _currentFirmwareVersion.value = null
        val type =
            runCatching { FirmwareReleaseType.valueOf(recovery.releaseType) }.getOrDefault(FirmwareReleaseType.STABLE)
        _selectedReleaseType.value = type

        firmwareReleaseRepository.getReleaseFlow(type).collectLatest { release ->
            _selectedRelease.value = release
            _state.value =
                FirmwareUpdateState.Ready(
                    release = release,
                    deviceHardware = hardware,
                    address = recovery.fullAddress.drop(1),
                    showBootloaderWarning = false,
                    updateMethod = FirmwareUpdateMethod.Ble,
                    currentFirmwareVersion = null,
                    isRecovery = true,
                )
        }
    }

    fun startUpdate() {
        val currentState = _state.value as? FirmwareUpdateState.Ready ?: return
        val release = currentState.release ?: return
        if (currentState.isRecovery) {
            startRecoveryUpdate(currentState, release)
        } else {
            startNormalUpdate(currentState, release)
        }
    }

    private fun startNormalUpdate(currentState: FirmwareUpdateState.Ready, release: FirmwareRelease) {
        originalDeviceAddress = radioPrefs.devAddr.value

        viewModelScope.launch {
            if (checkBatteryLevel()) {
                updateJob?.cancel()
                updateJob =
                    viewModelScope.launch {
                        try {
                            // Persist a recovery record before flashing so a stranded bootloader (interrupted upload,
                            // app closed, missed reconnect) can be re-flashed later while disconnected.
                            maybeRecordRecovery(currentState)
                            tempFirmwareFile =
                                firmwareUpdateManager.startUpdate(
                                    release = release,
                                    hardware = currentState.deviceHardware,
                                    address = currentState.address,
                                    updateState = { _state.value = it },
                                )

                            when (val finalState = _state.value) {
                                is FirmwareUpdateState.Success ->
                                    verifyUpdateResult(originalDeviceAddress, finalState.wasLowSpeedTransfer)

                                // USB/UF2 path intentionally pauses here: the UI launches the file picker and
                                // saveDfuFile() resumes the flow. Leave the state intact (tempFirmwareFile holds
                                // the artifact for cleanup after the copy completes).
                                is FirmwareUpdateState.AwaitingFileSave -> Unit

                                is FirmwareUpdateState.Error -> {
                                    tempFirmwareFile = cleanupTemporaryFiles(fileHandler, tempFirmwareFile)
                                }

                                else -> {
                                    // Defense-in-depth: handler returned without setting a terminal state
                                    Logger.w { "Firmware update returned without terminal state: ${_state.value}" }
                                    _state.value =
                                        FirmwareUpdateState.Error(UiText.Resource(Res.string.firmware_update_failed))
                                    tempFirmwareFile = cleanupTemporaryFiles(fileHandler, tempFirmwareFile)
                                }
                            }
                        } catch (e: CancellationException) {
                            Logger.w(e) { "Firmware update cancelled — cause: ${e.cause} message: ${e.message}" }
                            _state.value = FirmwareUpdateState.Idle
                            checkForUpdates()
                            throw e
                        } catch (e: Exception) {
                            Logger.e(e) { "Firmware update failed" }
                            _state.value = FirmwareUpdateState.Error(UiText.Resource(Res.string.firmware_update_failed))
                        }
                    }
            }
        }
    }

    /**
     * Persist a [PendingFirmwareRecovery] for the current BLE nRF-DFU update, so an interrupted flash that strands the
     * device in bootloader mode can be recovered later. Scoped to BLE + non-ESP32 + a re-fetchable release channel
     * (STABLE/ALPHA); ESP32 OTA and local-file flashes are intentionally not recoverable in this flow.
     */
    private suspend fun maybeRecordRecovery(state: FirmwareUpdateState.Ready) {
        val type = _selectedReleaseType.value
        val recoverable =
            state.updateMethod is FirmwareUpdateMethod.Ble &&
                !state.deviceHardware.isEsp32Arc &&
                type != FirmwareReleaseType.LOCAL
        if (!recoverable) return
        val fullAddress = radioPrefs.devAddr.value
        val pioEnv = nodeRepository.myNodeInfo.value?.pioEnv
        if (fullAddress == null || pioEnv == null) return
        firmwareRecoveryDataSource.set(
            PendingFirmwareRecovery(
                fullAddress = fullAddress,
                hwModel = state.deviceHardware.hwModel,
                pioEnv = pioEnv,
                releaseType = type.name,
                deviceName = radioPrefs.devName.value ?: state.deviceHardware.displayName,
            ),
        )
    }

    /**
     * Re-flash a device stranded in bootloader mode. Routes straight to BLE DFU (the device is disconnected, so the
     * connection-type dispatch can't run) and reuses the same verify/cleanup tail as a normal update.
     */
    private fun startRecoveryUpdate(currentState: FirmwareUpdateState.Ready, release: FirmwareRelease) {
        originalDeviceAddress = pendingRecovery?.fullAddress
        updateJob?.cancel()
        updateJob =
            viewModelScope.launch {
                try {
                    tempFirmwareFile =
                        firmwareUpdateManager.recoverDfuDevice(
                            release = release,
                            hardware = currentState.deviceHardware,
                            address = currentState.address,
                            updateState = { _state.value = it },
                        )

                    when (val finalState = _state.value) {
                        is FirmwareUpdateState.Success ->
                            verifyUpdateResult(originalDeviceAddress, finalState.wasLowSpeedTransfer)

                        is FirmwareUpdateState.Error -> {
                            // BLE re-flash of a stranded device failed. A stock nRF bootloader can't reliably finish
                            // an interrupted OTA update over the air, so point the user at USB serial-DFU recovery
                            // rather than surfacing the low-level connection error.
                            _state.value =
                                FirmwareUpdateState.Error(UiText.Resource(Res.string.firmware_recovery_ble_failed))
                            tempFirmwareFile = cleanupTemporaryFiles(fileHandler, tempFirmwareFile)
                        }

                        else -> {
                            Logger.w { "Firmware recovery returned without terminal state: ${_state.value}" }
                            _state.value =
                                FirmwareUpdateState.Error(UiText.Resource(Res.string.firmware_recovery_ble_failed))
                            tempFirmwareFile = cleanupTemporaryFiles(fileHandler, tempFirmwareFile)
                        }
                    }
                } catch (e: CancellationException) {
                    Logger.w(e) { "Firmware recovery cancelled" }
                    _state.value = FirmwareUpdateState.Idle
                    checkForUpdates()
                    throw e
                } catch (e: Exception) {
                    Logger.e(e) { "Firmware recovery failed" }
                    _state.value = FirmwareUpdateState.Error(UiText.Resource(Res.string.firmware_recovery_ble_failed))
                }
            }
    }

    fun saveDfuFile(uri: CommonUri) {
        val currentState = _state.value as? FirmwareUpdateState.AwaitingFileSave ?: return
        val firmwareArtifact = currentState.uf2Artifact

        viewModelScope.launch {
            try {
                _state.value =
                    FirmwareUpdateState.Processing(ProgressState(UiText.Resource(Res.string.firmware_update_copying)))
                fileHandler.copyToUri(firmwareArtifact, uri)

                _state.value =
                    FirmwareUpdateState.Processing(ProgressState(UiText.Resource(Res.string.firmware_update_flashing)))
                withTimeoutOrNull(DEVICE_DETACH_TIMEOUT) { usbManager.deviceDetachFlow().first() }
                    ?: Logger.w { "Timed out waiting for device to detach, assuming success" }

                verifyUpdateResult(originalDeviceAddress)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(e) { "Error saving DFU file" }
                _state.value = FirmwareUpdateState.Error(UiText.Resource(Res.string.firmware_update_failed))
            } finally {
                cleanupTemporaryFiles(fileHandler, tempFirmwareFile)
            }
        }
    }

    fun prepareLocalFirmwareFile(uri: CommonUri) {
        val currentState = _state.value as? FirmwareUpdateState.Ready ?: return
        clearPendingLocalFirmwareFile()
        prepareJob =
            viewModelScope.launch {
                try {
                    val fileName =
                        safeCatching { fileHandler.getDisplayName(uri)?.takeIf { it.isNotBlank() } }
                            .getOrElse { e ->
                                Logger.w(e) { "Failed to resolve local firmware filename" }
                                null
                            }

                    // State may have changed during the suspend call (e.g. cancelUpdate, checkForUpdates).
                    // Do not write errors or reopen the confirmation dialog for a stale selection.
                    when {
                        _state.value != currentState -> Unit

                        fileName == null ->
                            _state.value =
                                FirmwareUpdateState.Error(
                                    UiText.Resource(Res.string.firmware_update_filename_unavailable),
                                )

                        else -> {
                            val resolution = resolveLocalFirmwareFile(uri, fileName, currentState)
                            if (_state.value != currentState) {
                                cleanupResolvedLocalFirmwareFile(resolution)
                            } else {
                                applyLocalFirmwareResolution(resolution, currentState)
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.e(e) { "Error preparing local firmware file" }
                    if (_state.value == currentState) {
                        _state.value = FirmwareUpdateState.Error(UiText.Resource(Res.string.firmware_update_failed))
                    }
                }
            }
    }

    fun confirmLocalFirmwareFile() {
        val pendingSelection = _pendingLocalFirmwareFile.value ?: return
        val pendingArtifact = pendingLocalFirmwareArtifact
        _pendingLocalFirmwareFile.value = null
        pendingLocalFirmwareArtifact = null
        val currentState = _state.value as? FirmwareUpdateState.Ready
        if (currentState != null) {
            val validation = validatePendingLocalFirmwareFile(pendingSelection, currentState)
            if (validation is LocalFirmwareFileValidation.Invalid) {
                _state.value =
                    FirmwareUpdateState.Error(
                        localFirmwareValidationError(validation.reason, pendingSelection.fileName, currentState),
                    )
                cleanupPendingLocalFirmwareArtifact(pendingArtifact)
            } else {
                startUpdateFromFile(pendingSelection.uri, pendingArtifact)
            }
        } else {
            cleanupPendingLocalFirmwareArtifact(pendingArtifact)
        }
    }

    fun dismissLocalFirmwareFile() {
        clearPendingLocalFirmwareFile()
    }

    private suspend fun resolveLocalFirmwareFile(
        uri: CommonUri,
        fileName: String,
        state: FirmwareUpdateState.Ready,
    ): LocalFirmwareResolution {
        val validation = validateLocalFirmwareFileName(fileName, state.deviceHardware, state.updateMethod)
        return when (validation) {
            LocalFirmwareFileValidation.Valid -> LocalFirmwareResolution.Resolved(uri = uri, fileName = fileName)

            is LocalFirmwareFileValidation.Invalid ->
                if (shouldTryLocalFirmwareBundle(fileName, validation.reason)) {
                    resolveLocalFirmwareBundle(uri, fileName, state, validation.reason)
                } else {
                    LocalFirmwareResolution.Invalid(reason = validation.reason, fileName = fileName)
                }
        }
    }

    private suspend fun resolveLocalFirmwareBundle(
        uri: CommonUri,
        fileName: String,
        state: FirmwareUpdateState.Ready,
        fallbackReason: LocalFirmwareFileValidationReason,
    ): LocalFirmwareResolution {
        val payloadExtension = localFirmwarePayloadExtension(state.deviceHardware, state.updateMethod)
        return if (payloadExtension == null) {
            LocalFirmwareResolution.Invalid(reason = fallbackReason, fileName = fileName)
        } else {
            val extractingState =
                FirmwareUpdateState.Processing(ProgressState(UiText.Resource(Res.string.firmware_update_extracting)))
            _state.value = extractingState
            try {
                val extractedArtifact = extractLocalFirmwareArchive(uri, fileName, state, payloadExtension)
                if (extractedArtifact == null) {
                    LocalFirmwareResolution.Invalid(
                        reason = LocalFirmwareFileValidationReason.MissingArchiveFirmware,
                        fileName = fileName,
                    )
                } else {
                    validateExtractedLocalFirmware(extractedArtifact, fileName, state)
                }
            } finally {
                if (_state.value == extractingState) {
                    _state.value = state
                }
            }
        }
    }

    private suspend fun extractLocalFirmwareArchive(
        uri: CommonUri,
        fileName: String,
        state: FirmwareUpdateState.Ready,
        payloadExtension: String,
    ): FirmwareArtifact? {
        val preferredFilenames =
            preferredLocalFirmwareArchiveFilenames(fileName, state.deviceHardware, state.updateMethod)
        for (preferredFilename in preferredFilenames) {
            safeCatching { fileHandler.extractFirmware(uri, state.deviceHardware, payloadExtension, preferredFilename) }
                .onFailure { e -> Logger.w(e) { "Failed to extract preferred local firmware $preferredFilename" } }
                .getOrNull()
                ?.let {
                    return it
                }
        }
        return safeCatching { fileHandler.extractFirmware(uri, state.deviceHardware, payloadExtension) }
            .getOrElse { e ->
                Logger.w(e) { "Failed to extract local firmware archive" }
                null
            }
    }

    private fun validateExtractedLocalFirmware(
        extractedArtifact: FirmwareArtifact,
        fallbackFileName: String,
        state: FirmwareUpdateState.Ready,
    ): LocalFirmwareResolution {
        val extractedFileName = extractedArtifact.fileName
        val extractedValidation =
            extractedFileName?.let { validateLocalFirmwareFileName(it, state.deviceHardware, state.updateMethod) }
                ?: LocalFirmwareFileValidation.Invalid(LocalFirmwareFileValidationReason.MissingArchiveFirmware)
        return if (extractedValidation == LocalFirmwareFileValidation.Valid && extractedFileName != null) {
            LocalFirmwareResolution.Resolved(
                uri = extractedArtifact.uri,
                fileName = extractedFileName,
                temporaryArtifact = extractedArtifact.takeIf { it.isTemporary },
            )
        } else {
            cleanupPendingLocalFirmwareArtifact(extractedArtifact)
            LocalFirmwareResolution.Invalid(
                reason = (extractedValidation as LocalFirmwareFileValidation.Invalid).reason,
                fileName = extractedFileName ?: fallbackFileName,
            )
        }
    }

    private fun applyLocalFirmwareResolution(resolution: LocalFirmwareResolution, state: FirmwareUpdateState.Ready) {
        when (resolution) {
            is LocalFirmwareResolution.Invalid ->
                _state.value =
                    FirmwareUpdateState.Error(
                        localFirmwareValidationError(resolution.reason, resolution.fileName, state),
                    )

            is LocalFirmwareResolution.Resolved -> {
                pendingLocalFirmwareArtifact = resolution.temporaryArtifact
                _pendingLocalFirmwareFile.value =
                    PendingLocalFirmwareFile(
                        uri = resolution.uri,
                        fileName = resolution.fileName,
                        deviceName = state.deviceHardware.displayName,
                        platformioTarget = state.deviceHardware.effectiveTarget,
                        updateMethod = state.updateMethod,
                        address = state.address,
                    )
            }
        }
    }

    private fun cleanupResolvedLocalFirmwareFile(resolution: LocalFirmwareResolution) {
        if (resolution is LocalFirmwareResolution.Resolved) {
            cleanupPendingLocalFirmwareArtifact(resolution.temporaryArtifact)
        }
    }

    private fun clearPendingLocalFirmwareFile() {
        prepareJob?.cancel()
        prepareJob = null
        val artifact = pendingLocalFirmwareArtifact
        _pendingLocalFirmwareFile.value = null
        pendingLocalFirmwareArtifact = null
        cleanupPendingLocalFirmwareArtifact(artifact)
    }

    private fun cleanupPendingLocalFirmwareArtifact(artifact: FirmwareArtifact?) {
        artifact
            ?.takeIf { it.isTemporary }
            ?.let {
                viewModelScope.launch {
                    safeCatching { fileHandler.deleteFile(it) }
                        .onFailure { e -> Logger.w(e) { "Failed to cleanup pending local firmware file" } }
                }
            }
    }

    private fun startUpdateFromFile(uri: CommonUri, pendingArtifact: FirmwareArtifact? = null) {
        val currentState = _state.value as? FirmwareUpdateState.Ready ?: return
        if (currentState.updateMethod is FirmwareUpdateMethod.Ble && !isValidBluetoothAddress(currentState.address)) {
            _state.value = FirmwareUpdateState.Error(UiText.Resource(Res.string.firmware_update_no_device))
            cleanupPendingLocalFirmwareArtifact(pendingArtifact)
            return
        }
        originalDeviceAddress = radioPrefs.devAddr.value

        updateJob?.cancel()
        updateJob =
            viewModelScope.launch {
                try {
                    val updateArtifact =
                        firmwareUpdateManager.startUpdate(
                            release = FirmwareRelease(id = LOCAL_RELEASE_ID, zipUrl = "", releaseNotes = ""),
                            hardware = currentState.deviceHardware,
                            address = currentState.address,
                            updateState = { _state.value = it },
                            firmwareUri = uri,
                        )
                    tempFirmwareFile = updateArtifact?.takeIf { it.isTemporary } ?: pendingArtifact
                    // If the handler created its own temp copy (e.g. ESP32 importFromUri),
                    // clean up the extracted bundle artifact to prevent a leak.
                    if (pendingArtifact != null && pendingArtifact != tempFirmwareFile) {
                        cleanupTemporaryFiles(fileHandler, pendingArtifact)
                    }

                    when (val finalState = _state.value) {
                        is FirmwareUpdateState.Success ->
                            verifyUpdateResult(originalDeviceAddress, finalState.wasLowSpeedTransfer)

                        // USB/UF2 path pauses here for the user to pick a save location; saveDfuFile() resumes it.
                        is FirmwareUpdateState.AwaitingFileSave -> Unit

                        is FirmwareUpdateState.Error -> {
                            tempFirmwareFile = cleanupTemporaryFiles(fileHandler, tempFirmwareFile)
                        }

                        else -> {
                            Logger.w { "Firmware update returned without terminal state: ${_state.value}" }
                            _state.value = FirmwareUpdateState.Error(UiText.Resource(Res.string.firmware_update_failed))
                            tempFirmwareFile = cleanupTemporaryFiles(fileHandler, tempFirmwareFile)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.e(e) { "Error starting update from file" }
                    _state.value = FirmwareUpdateState.Error(UiText.Resource(Res.string.firmware_update_failed))
                    tempFirmwareFile = cleanupTemporaryFiles(fileHandler, tempFirmwareFile ?: pendingArtifact)
                }
            }
    }

    private fun localFirmwareValidationError(
        reason: LocalFirmwareFileValidationReason,
        fileName: String,
        state: FirmwareUpdateState.Ready,
    ): UiText = when (reason) {
        LocalFirmwareFileValidationReason.MissingArchiveFirmware ->
            UiText.Resource(
                Res.string.firmware_update_archive_missing_target,
                fileName,
                state.deviceHardware.displayName,
                state.deviceHardware.effectiveTarget,
            )

        LocalFirmwareFileValidationReason.MissingTarget ->
            UiText.Resource(Res.string.firmware_update_missing_target)

        LocalFirmwareFileValidationReason.UnsupportedUpdateMethod ->
            UiText.Resource(Res.string.firmware_update_unsupported_update_method)

        LocalFirmwareFileValidationReason.RequiresOtaZip ->
            UiText.Resource(Res.string.firmware_update_requires_ota_zip, state.deviceHardware.displayName)

        LocalFirmwareFileValidationReason.RequiresBin ->
            UiText.Resource(Res.string.firmware_update_requires_bin, state.deviceHardware.displayName)

        LocalFirmwareFileValidationReason.RequiresUf2 ->
            UiText.Resource(Res.string.firmware_update_requires_uf2, state.deviceHardware.displayName)

        LocalFirmwareFileValidationReason.ConfirmationContextChanged ->
            UiText.Resource(Res.string.firmware_update_context_changed)

        LocalFirmwareFileValidationReason.TargetMismatch ->
            UiText.Resource(
                Res.string.firmware_update_invalid_local_file_detail,
                fileName,
                state.deviceHardware.displayName,
                state.deviceHardware.effectiveTarget,
            )
    }

    fun dismissBootloaderWarningForCurrentDevice() {
        val currentState = _state.value as? FirmwareUpdateState.Ready ?: return
        viewModelScope.launch {
            bootloaderWarningDataSource.dismiss(currentState.address)
            _state.value = currentState.copy(showBootloaderWarning = false)
        }
    }

    private suspend fun verifyUpdateResult(address: String?, wasLowSpeedTransfer: Boolean = false) {
        _state.value = FirmwareUpdateState.Verifying

        // Trigger a fresh connection attempt by MeshService using the original prefixed address.
        //
        // For USB/serial, do NOT force this: a USB node re-enumerates several times through the bootloader over
        // ~20s, so a one-shot setDeviceAddress fires into an enumeration gap, fails ("Serial device not found"),
        // and lands the transport in Disconnected — which preempts the dedicated USB auto-recovery in
        // SharedRadioInterfaceService (observeUsbRecoveryTriggers), since that only arms from DeviceSleep. Leaving
        // the transport in DeviceSleep lets that recovery reconnect the moment the device re-attaches on its new
        // (stable-keyed) address. BLE/TCP have no such hot-plug recovery, so they still need the explicit re-address.
        address?.let { fullAddr ->
            if (radioPrefs.isSerial()) {
                Logger.i { "Post-update: leaving USB reconnect to USB auto-recovery for ${fullAddr.anonymize}" }
            } else {
                Logger.i { "Post-update: Requesting MeshService to reconnect to ${fullAddr.anonymize}" }
                // GATT cache invalidation is only needed for BLE reconnects — the device
                // reboots into a different GATT profile on the same MAC address. TCP/USB
                // don't have this problem, and leaving a stale BLE-only request around
                // could trigger an unnecessary refresh on a later BLE connection.
                if (isBluetoothInterfaceAddress(fullAddr)) {
                    Logger.d { "Post-update: Requesting GATT cache invalidation before BLE reconnect" }
                    radioController.requestGattCacheInvalidationOnNextConnect()
                }
                radioController.setDeviceAddress(fullAddr)
            }
        }

        // Wait for device to reconnect and settle
        val result =
            withTimeoutOrNull(VERIFY_TIMEOUT) {
                // Wait for both Connected state and node info to be present
                connectionState.first { it is ConnectionState.Connected }
                nodeRepository.ourNodeInfo.filterNotNull().first()
                delay(VERIFY_DELAY) // Extra buffer for initial config sync
                true
            }

        if (result == null) {
            Logger.w { "Post-update verification timed out for $address" }
            _state.value = FirmwareUpdateState.VerificationFailed
        } else {
            // Device is back and healthy — retire any recovery record (covers both normal and recovery updates).
            pendingRecovery = null
            firmwareRecoveryDataSource.clear()
            _state.value = FirmwareUpdateState.Success(wasLowSpeedTransfer)
        }
    }

    private suspend fun checkBatteryLevel(): Boolean {
        val node = nodeRepository.ourNodeInfo.value ?: return true
        val level = node.batteryLevel ?: 1
        val isBatteryLow = level in 1..MIN_BATTERY_LEVEL

        if (isBatteryLow) {
            _state.value = FirmwareUpdateState.Error(UiText.Resource(Res.string.firmware_update_battery_low, level))
        }
        return !isBatteryLow
    }

    private suspend fun getDeviceHardware(ourNode: MyNodeInfo): DeviceHardware? {
        val nodeInfo = nodeRepository.ourNodeInfo.value
        val hwModelInt = nodeInfo?.user?.hw_model?.value
        val target = ourNode.pioEnv

        return if (hwModelInt != null) {
            deviceHardwareRepository.getDeviceHardwareByModel(hwModelInt, target).getOrElse {
                clearDeviceMetadata()
                _state.value =
                    FirmwareUpdateState.Error(UiText.Resource(Res.string.firmware_update_unknown_hardware, hwModelInt))
                null
            }
        } else {
            clearDeviceMetadata()
            _state.value = FirmwareUpdateState.Error(UiText.Resource(Res.string.firmware_update_node_info_missing))
            null
        }
    }

    private fun clearDeviceMetadata() {
        _selectedRelease.value = null
        _deviceHardware.value = null
        _currentFirmwareVersion.value = null
    }
}

private sealed interface LocalFirmwareResolution {
    data class Resolved(val uri: CommonUri, val fileName: String, val temporaryArtifact: FirmwareArtifact? = null) :
        LocalFirmwareResolution

    data class Invalid(val reason: LocalFirmwareFileValidationReason, val fileName: String) : LocalFirmwareResolution
}

private fun shouldTryLocalFirmwareBundle(fileName: String, reason: LocalFirmwareFileValidationReason): Boolean {
    val normalizedFileName = fileName.substringAfterLast('/').substringAfterLast('\\').lowercase()
    return normalizedFileName.endsWith(".zip") &&
        when (reason) {
            LocalFirmwareFileValidationReason.RequiresOtaZip,
            LocalFirmwareFileValidationReason.RequiresBin,
            LocalFirmwareFileValidationReason.RequiresUf2,
            -> true

            LocalFirmwareFileValidationReason.MissingArchiveFirmware,
            LocalFirmwareFileValidationReason.MissingTarget,
            LocalFirmwareFileValidationReason.TargetMismatch,
            LocalFirmwareFileValidationReason.ConfirmationContextChanged,
            LocalFirmwareFileValidationReason.UnsupportedUpdateMethod,
            -> false
        }
}

private suspend fun cleanupTemporaryFiles(
    fileHandler: FirmwareFileHandler,
    tempFirmwareFile: FirmwareArtifact?,
): FirmwareArtifact? {
    safeCatching { tempFirmwareFile?.takeIf { it.isTemporary }?.let { fileHandler.deleteFile(it) } }
        .onFailure { e -> Logger.w(e) { "Failed to cleanup temp files" } }
    return null
}

private fun isValidBluetoothAddress(address: String?): Boolean =
    address != null && BLUETOOTH_ADDRESS_REGEX.matches(address)

private fun isBluetoothInterfaceAddress(address: String): Boolean =
    address.startsWith(InterfaceId.BLUETOOTH.id) || address.startsWith("!")

private fun FirmwareReleaseRepository.getReleaseFlow(type: FirmwareReleaseType): Flow<FirmwareRelease?> = when (type) {
    FirmwareReleaseType.STABLE -> stableRelease
    FirmwareReleaseType.ALPHA -> alphaRelease
    FirmwareReleaseType.NIGHTLY -> nightlyRelease
    FirmwareReleaseType.LOCAL -> flowOf(null)
}

/** The transport mechanism used to deliver firmware to the device, determined by the active radio connection. */
sealed class FirmwareUpdateMethod(val description: StringResource) {
    data object Usb : FirmwareUpdateMethod(Res.string.firmware_update_method_usb)

    data object Ble : FirmwareUpdateMethod(Res.string.firmware_update_method_ble)

    data object Wifi : FirmwareUpdateMethod(Res.string.firmware_update_method_wifi)

    data object Unknown : FirmwareUpdateMethod(Res.string.unknown)
}
