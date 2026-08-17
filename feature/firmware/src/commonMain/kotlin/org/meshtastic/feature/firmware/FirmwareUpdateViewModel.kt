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
import org.meshtastic.core.common.state.FirmwareMaintenanceLock
import org.meshtastic.core.common.state.HiddenFeaturesUnlock
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
import org.meshtastic.core.repository.NodeRestartTracker
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.RadioController
import org.meshtastic.core.repository.RadioPrefs
import org.meshtastic.core.repository.isBle
import org.meshtastic.core.repository.isSerial
import org.meshtastic.core.repository.isTcp
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.UiText
import org.meshtastic.core.resources.firmware_maintenance_cdc_unblock_failed
import org.meshtastic.core.resources.firmware_maintenance_copy_failed
import org.meshtastic.core.resources.firmware_maintenance_wrong_destination
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
import org.meshtastic.core.resources.firmware_update_retrieval_failed
import org.meshtastic.core.resources.firmware_update_unknown_error
import org.meshtastic.core.resources.firmware_update_unknown_hardware
import org.meshtastic.core.resources.firmware_update_unsupported_update_method
import org.meshtastic.core.resources.unknown

// Bench measurement (Wio Tracker L1, 2 MB UF2): consume + reboot can outlast 30 s by a hair.
private const val DEVICE_DETACH_TIMEOUT = 60_000L

/** How long the post-update permission preflight waits for the updated firmware to enumerate. */
private const val USB_REATTACH_PERMISSION_WAIT = 30_000L
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
    private val firmwareRetriever: FirmwareRetriever,
    private val firmwareMaintenanceLock: FirmwareMaintenanceLock,
    private val applicationScope: ApplicationCoroutineScope,
    private val hiddenFeaturesUnlock: HiddenFeaturesUnlock,
    private val analytics: PlatformAnalytics,
    private val nodeRestartTracker: NodeRestartTracker,
) : ViewModel() {

    private val _state = MutableStateFlow<FirmwareUpdateState>(FirmwareUpdateState.Idle)
    val state: StateFlow<FirmwareUpdateState> = _state.asStateFlow()

    val connectionState = radioController.connectionState

    /** The version-row easter egg also unlocks the nightly preview channel, like the web flasher's konami code. */
    val nightlyUnlocked: StateFlow<Boolean> = hiddenFeaturesUnlock.unlocked

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

    /** Remaining legs of an in-flight USB maintenance sequence, head first. Empty outside a maintenance flow. */
    private var pendingUsbPasses: List<UsbFileSavePass> = emptyList()

    /** Hardware the running maintenance sequence targets; needed to choose images once a volume is read. */
    private var maintenanceHardware: DeviceHardware? = null

    /**
     * True once an erase or bootloader image has been written, which is the point the device stops having a working
     * application. From then on failures re-offer the pass instead of surfacing a dead end.
     */
    private var destructiveWriteDone = false

    /**
     * Set by [startUpdate] when the user opted into a wipe on a transport that cannot erase flash (BLE/WiFi). Consumed
     * by [verifyUpdateResult]: the factory reset is only sent once the updated firmware is back up and verified — a
     * device we cannot confirm updated is never wiped.
     */
    private var pendingFactoryResetAfterUpdate = false

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
        // A maintenance sequence's lock must not outlive this ViewModel: the user may abandon the flow between
        // passes (e.g. navigating away after the erase leg but before picking the firmware save location), and a
        // leaked lock would permanently suppress the radio transport's auto-reconnect for the rest of the app
        // session — see startUsbMaintenance/advancePastPass. A no-op if no sequence was in flight.
        firmwareMaintenanceLock.release()
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
                                maintenance =
                                usbMaintenanceGate(
                                    hardware = deviceHardware,
                                    updateMethod = firmwareUpdateMethod,
                                    hasRelease = release != null,
                                ),
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
        // A nightly recovery record can only exist if the user had unlocked the hidden channel and deliberately
        // flashed nightly before the interruption; re-assert the (process-scoped) unlock so the recovery UI can
        // show and re-fetch that channel instead of leaving the stranded device unrecoverable.
        if (type == FirmwareReleaseType.NIGHTLY) {
            hiddenFeaturesUnlock.unlock()
        }
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

    /**
     * Emitted from every path that begins a flash, so the RUM action counts local-file sideloads alongside release
     * updates. The method label is mapped explicitly because [FirmwareUpdateMethod] is obfuscated in release builds.
     */
    private fun trackUpdateStart(state: FirmwareUpdateState.Ready, releaseId: String, wipeDevice: Boolean = false) {
        val updateMethod =
            when (state.updateMethod) {
                FirmwareUpdateMethod.Usb -> "usb"
                FirmwareUpdateMethod.Ble -> "ble"
                FirmwareUpdateMethod.Wifi -> "wifi"
                FirmwareUpdateMethod.Unknown -> "unknown"
            }
        analytics.trackAction(
            "firmware_update_start",
            mapOf(
                "update_method" to updateMethod,
                "is_recovery" to state.isRecovery,
                "release_version" to releaseId,
                "wipe_device" to wipeDevice,
            ),
        )
    }

    fun startUpdate(wipeDevice: Boolean = false) {
        val currentState = _state.value as? FirmwareUpdateState.Ready ?: return
        val release = currentState.release ?: return
        // Every update starts from a clean slate: an opt-in from an earlier, failed attempt must not carry over.
        pendingFactoryResetAfterUpdate = false
        trackUpdateStart(currentState, release.id, wipeDevice)
        when {
            currentState.isRecovery -> startRecoveryUpdate(currentState, release)

            // Over USB a wipe is a physical flash erase: route into the maintenance sequence, which
            // erases via the UF2 image and then installs this release as its terminal pass.
            wipeDevice && currentState.updateMethod is FirmwareUpdateMethod.Usb ->
                startUsbMaintenance(UsbMaintenanceRequest.FactoryErase)

            // BLE/WiFi cannot touch flash directly; the equivalent is a factory reset issued once the
            // updated firmware is back up and verified.
            wipeDevice -> {
                pendingFactoryResetAfterUpdate = true
                startNormalUpdate(currentState, release)
            }

            else -> startNormalUpdate(currentState, release)
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
     * (STABLE/ALPHA/NIGHTLY); ESP32 OTA and local-file flashes are intentionally not recoverable in this flow. A
     * NIGHTLY record re-asserts the hidden-features unlock on recovery (see [enterRecoveryModeOrError]).
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

    // ── USB maintenance (factory erase / bootloader upgrade) ────────────────────────────────────────
    // Factory erase has no standalone entry point: it is reached through startUpdate(wipeDevice = true)
    // on the USB path, so a wipe always ends with this release installed.

    fun startBootloaderUpgrade() = startUsbMaintenance(UsbMaintenanceRequest.BootloaderUpgrade)

    @Suppress("ReturnCount") // preconditions; each missing one must abort before the device is rebooted
    private fun startUsbMaintenance(request: UsbMaintenanceRequest) {
        val currentState = _state.value as? FirmwareUpdateState.Ready ?: return
        val release = currentState.release ?: return

        // Defence in depth: the screen already disables a refused erase and hides an unmapped bootloader upgrade,
        // but never reboot a device into DFU for a request the gate would not have offered — that costs the user a
        // pointless reboot cycle before the write-time check in chooseMaintenanceImage refuses it anyway.
        val gate = currentState.maintenance
        // A hidden gate carries no refusal (show=false, eraseRefusal=null), so the per-request checks below
        // would sail past it — refuse outright before anything can reboot the device.
        if (!gate.show) {
            _state.value =
                FirmwareUpdateState.Error(usbMaintenanceRefusalMessage(UsbMaintenanceRefusal.UnsupportedArchitecture))
            return
        }
        when (request) {
            UsbMaintenanceRequest.FactoryErase ->
                gate.eraseRefusal?.let {
                    _state.value = FirmwareUpdateState.Error(usbMaintenanceRefusalMessage(it))
                    return
                }

            UsbMaintenanceRequest.BootloaderUpgrade ->
                if (!gate.showBootloaderUpgrade) {
                    _state.value =
                        FirmwareUpdateState.Error(usbMaintenanceRefusalMessage(UsbMaintenanceRefusal.UnknownBoardId))
                    return
                }
        }

        originalDeviceAddress = radioPrefs.devAddr.value
        maintenanceHardware = currentState.deviceHardware
        destructiveWriteDone = false

        viewModelScope.launch {
            // Battery check comes first: an abort here has touched nothing, and acquiring before it would leak
            // the lock (nothing on this path releases it), suppressing transport recovery until the screen closes.
            if (!checkBatteryLevel()) return@launch
            // Held until the sequence finishes or fails. Without it the environmental-recovery listeners restart
            // the radio transport mid-sequence and bind it to the erase firmware's bare CDC port.
            firmwareMaintenanceLock.acquire()
            updateJob?.cancel()
            updateJob =
                viewModelScope.launch {
                    try {
                        pendingUsbPasses =
                            performUsbMaintenance(
                                request = request,
                                release = release,
                                hardware = currentState.deviceHardware,
                                radioController = radioController,
                                nodeRepository = nodeRepository,
                                updateState = { _state.value = it },
                                retrieveUsbFirmware = firmwareRetriever::retrieveUsbFirmware,
                            )
                        // The firmware image is the last pass, so it is also what must be cleaned up if the flow dies.
                        tempFirmwareFile =
                            pendingUsbPasses.filterIsInstance<UsbFileSavePass.Prepared>().lastOrNull()?.artifact
                    } catch (e: CancellationException) {
                        throw e
                    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                        Logger.e(e) { "USB maintenance preparation failed" }
                        _state.value = FirmwareUpdateState.Error(UiText.Resource(Res.string.firmware_update_failed))
                    } finally {
                        // Preparation that produced no passes never reached the device; hand the transport back.
                        if (pendingUsbPasses.isEmpty()) firmwareMaintenanceLock.release()
                    }
                }
        }
    }

    /**
     * Resumes a maintenance sequence once the user has pointed at the device's UF2 volume.
     *
     * Distinct from [saveDfuFile] because the URI kinds differ: this takes a *tree* URI, which grants the sibling
     * access needed to read `INFO_UF2.TXT` and vet the volume before writing. Each pass re-picks, because the device
     * re-enumerates between passes and the previous grant no longer refers to the mounted volume.
     */
    @Suppress("ReturnCount") // preconditions guarding a destructive write
    fun writeMaintenancePass(treeUri: CommonUri) {
        val currentState = _state.value as? FirmwareUpdateState.AwaitingFileSave ?: return
        val pass = pendingUsbPasses.firstOrNull() ?: return
        if (pass.step != currentState.step) return
        val hardware = maintenanceHardware ?: return

        viewModelScope.launch {
            try {
                // Capture the ports present before the write so the erase image's port can be told from pre-existing
                // ones.
                val portsBefore = usbManager.serialPortKeys()
                val result = usbPassWriter(portsBefore).write(pass, treeUri, hardware) { _state.value = it }
                handlePassResult(pass, result)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Logger.e(e) { "Writing ${pass.step} failed" }
                reofferOrFail(pass, UiText.Resource(Res.string.firmware_update_failed))
            }
        }
    }

    private suspend fun handlePassResult(pass: UsbFileSavePass, result: UsbPassResult) = when (result) {
        UsbPassResult.Written -> advancePastPass(pass)

        is UsbPassResult.Refused -> reofferOrFail(pass, usbMaintenanceRefusalMessage(result.reason))

        UsbPassResult.ImageDownloadFailed ->
            reofferOrFail(pass, UiText.Resource(Res.string.firmware_update_retrieval_failed))

        UsbPassResult.CopyFailed ->
            reofferOrFail(pass, UiText.Resource(Res.string.firmware_maintenance_copy_failed))

        UsbPassResult.WriteDidNotLand ->
            reofferOrFail(pass, UiText.Resource(Res.string.firmware_maintenance_wrong_destination))

        UsbPassResult.CdcUnblockFailed ->
            reofferOrFail(pass, UiText.Resource(Res.string.firmware_maintenance_cdc_unblock_failed))
    }

    private suspend fun advancePastPass(pass: UsbFileSavePass) {
        if (pass.step.isDestructive) destructiveWriteDone = true
        pendingUsbPasses = pendingUsbPasses.drop(1)

        val next = pendingUsbPasses.firstOrNull()
        if (next == null) {
            // Sequence complete: hand the device back before verifying, so the normal reconnect can run.
            firmwareMaintenanceLock.release()
            verifyUpdateResult(originalDeviceAddress)
        } else {
            _state.value = next.toAwaitingFileSave()
        }
    }

    /**
     * Re-offers the same pass with an explanation, or fails outright when nothing destructive has happened yet.
     *
     * Once an erase or bootloader image has been written the device has no application, so dropping the user on an
     * error screen is the worst available outcome — the flow keeps offering the pass until it succeeds or they leave
     * deliberately.
     */
    private fun reofferOrFail(pass: UsbFileSavePass, message: UiText) {
        if (destructiveWriteDone) {
            // Still mid-sequence — hold the lock, the user is being asked to retry this pass.
            _state.value = pass.toAwaitingFileSave(retryMessage = message)
        } else {
            firmwareMaintenanceLock.release()
            _state.value = FirmwareUpdateState.Error(message)
        }
    }

    private fun usbPassWriter(portsBefore: Set<String>) = UsbPassWriter(
        fileHandler = fileHandler,
        retrieveMaintenanceUf2 = { asset, onProgress ->
            firmwareRetriever.retrieveMaintenanceUf2(asset, onProgress)
        },
        awaitDeviceDetach = { timeout ->
            withTimeoutOrNull(timeout) { usbManager.deviceDetachFlow().first() } != null
        },
        unblockCdc = { wait, hold -> usbManager.unblockCdcPort(portsBefore, wait, hold) },
    )

    fun saveDfuFile(uri: CommonUri) {
        val currentState = _state.value as? FirmwareUpdateState.AwaitingFileSave ?: return
        // A maintenance pass carries no artifact — it goes through writeMaintenancePass with a tree URI instead.
        val firmwareArtifact = currentState.uf2Artifact ?: return

        viewModelScope.launch {
            try {
                _state.value =
                    FirmwareUpdateState.Processing(ProgressState(UiText.Resource(Res.string.firmware_update_copying)))
                fileHandler.copyToUri(firmwareArtifact, uri)

                _state.value =
                    FirmwareUpdateState.Processing(ProgressState(UiText.Resource(Res.string.firmware_update_flashing)))
                withTimeoutOrNull(DEVICE_DETACH_TIMEOUT) { usbManager.deviceDetachFlow().first() }
                    ?: Logger.w { "Timed out waiting for device to detach, assuming success" }

                // All writes are done once the device detached. Release before verifying: for serial,
                // verification relies on SharedRadioInterfaceService's USB auto-recovery to reconnect the
                // radio — which is exactly what the lock suppresses. (The release in finally is a no-op then.)
                firmwareMaintenanceLock.release()
                verifyUpdateResult(originalDeviceAddress)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(e) { "Error saving DFU file" }
                _state.value = FirmwareUpdateState.Error(UiText.Resource(Res.string.firmware_update_failed))
            } finally {
                cleanupTemporaryFiles(fileHandler, tempFirmwareFile)
                // This is also the terminal pass of a USB maintenance sequence when the FromVolume leg has already
                // completed: the Prepared firmware artifact is saved through this pre-existing single-pass path
                // rather than writeMaintenancePass/advancePastPass, so releasing here is the only place that
                // sequence's lock gets freed. A no-op for a plain single-pass update, which never acquires the lock.
                firmwareMaintenanceLock.release()
                pendingUsbPasses = emptyList()
                maintenanceHardware = null
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
        // Local-file installs never wipe; drop any opt-in left over from an earlier, failed attempt.
        pendingFactoryResetAfterUpdate = false
        if (currentState.updateMethod is FirmwareUpdateMethod.Ble && !isValidBluetoothAddress(currentState.address)) {
            _state.value = FirmwareUpdateState.Error(UiText.Resource(Res.string.firmware_update_no_device))
            cleanupPendingLocalFirmwareArtifact(pendingArtifact)
            return
        }
        trackUpdateStart(currentState, LOCAL_RELEASE_ID)
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
        // Consume the wipe opt-in up front: whatever this verification concludes, it must never leak into a
        // later update flow that did not opt in (e.g. a retry via a local file).
        val factoryResetAfterVerify = pendingFactoryResetAfterUpdate
        pendingFactoryResetAfterUpdate = false
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
                Logger.i { "Post-update: preflighting the USB permission grant for ${fullAddr.anonymize}" }
                // The reboot gave the device a new USB identity, and Android scopes permission grants to the
                // identity — without a fresh grant the auto-recovery fails with SecurityException and a healthy
                // update lands on VerificationFailed. Ask now, while the user is still watching. On success,
                // reconnect explicitly: auto-recovery's attach trigger already fired (and failed) while the
                // permission dialog was still up, and it does not retry on a grant. At this point the device is
                // enumerated and the grant is held, so the one-shot setDeviceAddress cannot land in the
                // enumeration gap the bare-USB path avoids.
                if (usbManager.ensureSerialPermission(USB_REATTACH_PERMISSION_WAIT)) {
                    Logger.i {
                        "Post-update: USB permission confirmed, reconnecting explicitly to ${fullAddr.anonymize}"
                    }
                    radioController.setDeviceAddress(fullAddr)
                } else {
                    Logger.w { "Post-update USB permission preflight did not complete; relying on auto-recovery" }
                }
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
            Logger.w { "Post-update verification timed out for ${address.anonymize()}" }
            // The opted-in wipe is deliberately skipped: a device we could not verify is never wiped.
            _state.value = FirmwareUpdateState.VerificationFailed
        } else {
            // Device is back and healthy — retire any recovery record (covers both normal and recovery updates).
            pendingRecovery = null
            firmwareRecoveryDataSource.clear()
            val wiped = factoryResetAfterVerify && sendPostUpdateFactoryReset()
            _state.value = FirmwareUpdateState.Success(wasLowSpeedTransfer, deviceWasWiped = wiped)
        }
    }

    /**
     * Sends the factory reset the user opted into alongside a BLE/WiFi update, once the updated firmware is verified.
     *
     * @return true when the reset was actually sent — the Success screen only claims a wipe that happened.
     */
    private suspend fun sendPostUpdateFactoryReset(): Boolean {
        val nodeNum = nodeRepository.myNodeInfo.value?.myNodeNum
        if (nodeNum == null) {
            Logger.w { "Post-update factory reset skipped: local node number unknown after reconnect" }
            return false
        }
        return try {
            // The reset reboots the device and drops the transport; mark it expected so the UI shows
            // "restarting" instead of a surprise disconnect (same treatment as the settings reset).
            nodeRestartTracker.expectRestart()
            radioController.factoryReset(nodeNum, radioController.generatePacketId())
            // The phone-side node DB describes a configuration that no longer exists.
            nodeRepository.clearNodeDB()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Logger.e(e) { "Post-update factory reset failed" }
            false
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
