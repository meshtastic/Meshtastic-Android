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

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.nordicsemi.android.dfu.DfuProgressListenerAdapter
import no.nordicsemi.android.dfu.DfuServiceInitiator
import no.nordicsemi.android.dfu.DfuServiceListenerHelper
import no.nordicsemi.kotlin.ble.client.android.CentralManager
import no.nordicsemi.kotlin.ble.core.ConnectionState
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.compose.resources.getString
import org.meshtastic.core.data.repository.DeviceHardwareRepository
import org.meshtastic.core.data.repository.FirmwareReleaseRepository
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.database.entity.FirmwareReleaseType
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.firmware_update_extracting
import org.meshtastic.core.strings.firmware_update_failed
import org.meshtastic.core.strings.firmware_update_invalid_address
import org.meshtastic.core.strings.firmware_update_no_device
import org.meshtastic.core.strings.firmware_update_not_found_in_release
import org.meshtastic.core.strings.firmware_update_starting_dfu
import org.meshtastic.core.strings.firmware_update_starting_service
import org.meshtastic.core.strings.firmware_update_unknown_hardware
import org.meshtastic.core.strings.firmware_update_updating
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.inject.Inject

private const val NO_DEVICE_SELECTED = "n"
private const val DFU_RECONNECT_PREFIX = "x"
private const val DOWNLOAD_BUFFER_SIZE = 8192
private const val PERCENT_MAX_VALUE = 100f

private const val SCAN_TIMEOUT = 2000L

private const val PACKETS_BEFORE_PRN = 8

private val BLUETOOTH_ADDRESS_REGEX = Regex("([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}")

/**
 * ViewModel responsible for managing the firmware update process for Meshtastic devices.
 *
 * It handles checking for updates, downloading firmware artifacts, extracting compatible firmware, and initiating the
 * Device Firmware Update (DFU) process over Bluetooth.
 */
@HiltViewModel
@Suppress("LongParameterList")
class FirmwareUpdateViewModel
@Inject
constructor(
    private val firmwareReleaseRepository: FirmwareReleaseRepository,
    private val deviceHardwareRepository: DeviceHardwareRepository,
    private val nodeRepository: NodeRepository,
    private val centralManager: CentralManager,
    client: OkHttpClient,
    private val serviceRepository: ServiceRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow<FirmwareUpdateState>(FirmwareUpdateState.Idle)
    val state: StateFlow<FirmwareUpdateState> = _state.asStateFlow()

    private val _selectedReleaseType = MutableStateFlow(FirmwareReleaseType.STABLE)
    val selectedReleaseType: StateFlow<FirmwareReleaseType> = _selectedReleaseType.asStateFlow()

    private var updateJob: Job? = null
    private val fileHandler = FirmwareFileHandler(context, client)
    private var tempFirmwareFile: File? = null

    init {
        checkForUpdates()

        // Start listening to DFU events immediately
        viewModelScope.launch { observeDfuProgress() }
    }

    override fun onCleared() {
        super.onCleared()
        cleanupTemporaryFiles()
    }

    /** Sets the desired [FirmwareReleaseType] (e.g., ALPHA, STABLE) and triggers a new update check. */
    fun setReleaseType(type: FirmwareReleaseType) {
        _selectedReleaseType.value = type
        checkForUpdates()
    }

    /**
     * Initiates a check for available firmware updates based on the selected release type.
     *
     * Validates the current device connection and hardware before fetching release information. Updates [state] to
     * [FirmwareUpdateState.Checking], then [FirmwareUpdateState.Ready] or [FirmwareUpdateState.Error].
     */
    fun checkForUpdates() {
        updateJob?.cancel()
        updateJob =
            viewModelScope.launch {
                _state.value = FirmwareUpdateState.Checking

                runCatching {
                    val validationResult = validateDeviceAndConnection()

                    if (validationResult == null) {
                        // Validation failed, state is already set to Error inside validateDeviceAndConnection
                        return@launch
                    }

                    val (ourNode, _, address) = validationResult
                    val deviceHardware = getDeviceHardware(ourNode) ?: return@launch

                    firmwareReleaseRepository.getReleaseFlow(_selectedReleaseType.value).collectLatest { release ->
                        _state.value = FirmwareUpdateState.Ready(release, deviceHardware, address)
                    }
                }
                    .onFailure { e ->
                        if (e is CancellationException) throw e
                        Timber.e(e)
                        _state.value = FirmwareUpdateState.Error(e.message ?: "Unknown error")
                    }
            }
    }

    /**
     * Starts the firmware update process using the currently identified release.
     * 1. Downloads the firmware zip from the release URL.
     * 2. Extracts the correct firmware image for the connected device hardware.
     * 3. Initiates the DFU process.
     */
    @Suppress("TooGenericExceptionCaught")
    fun startUpdate() {
        val currentState = _state.value as? FirmwareUpdateState.Ready ?: return
        val (release, hardware, address) = currentState

        if (release == null || !isValidBluetoothAddress(address)) return

        updateJob?.cancel()
        updateJob =
            viewModelScope.launch {
                try {
                    // 1. Download
                    _state.value = FirmwareUpdateState.Downloading(0f)

                    var firmwareFile: File? = null

                    // Try direct download of the specific device firmware
                    val version = release.id.removePrefix("v")
                    // We prefer platformioTarget because it matches the build artifact naming
                    // convention (lower-case with hyphens).
                    // hwModelSlug often uses underscores and uppercase
                    // (e.g. TRACKER_T1000_E vs tracker-t1000-e).
                    val target = hardware.platformioTarget.ifEmpty { hardware.hwModelSlug }
                    val filename = "firmware-$target-$version.zip"
                    val directUrl = "https://meshtastic.github.io/firmware-$version/$filename"

                    if (fileHandler.checkUrlExists(directUrl)) {
                        try {
                            firmwareFile =
                                fileHandler.downloadFile(directUrl, "firmware_direct.zip") { progress ->
                                    _state.value = FirmwareUpdateState.Downloading(progress)
                                }
                        } catch (e: Exception) {
                            Timber.w(e, "Direct download failed, falling back to release zip")
                        }
                    }

                    if (firmwareFile == null) {
                        val zipUrl = getDeviceFirmwareUrl(release.zipUrl, hardware.architecture)

                        val downloadedZip =
                            fileHandler.downloadFile(zipUrl, "firmware_release.zip") { progress ->
                                _state.value = FirmwareUpdateState.Downloading(progress)
                            }

                        // Note: Current API does not provide checksums, so we rely on content-length
                        // checks during download and integrity checks during extraction.

                        // 2. Extract
                        _state.value = FirmwareUpdateState.Processing(getString(Res.string.firmware_update_extracting))
                        val extracted = fileHandler.extractFirmware(downloadedZip, hardware)

                        if (extracted == null) {
                            val msg = getString(Res.string.firmware_update_not_found_in_release, hardware.displayName)
                            _state.value = FirmwareUpdateState.Error(msg)
                            return@launch
                        }
                        firmwareFile = extracted
                    }

                    tempFirmwareFile = firmwareFile
                    initiateDfu(address, hardware, firmwareFile!!)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e)
                    _state.value = FirmwareUpdateState.Error(e.message ?: getString(Res.string.firmware_update_failed))
                }
            }
    }

    /**
     * Starts a firmware update using a local file provided via [Uri].
     *
     * Copies the content to a temporary file and initiates the DFU process.
     */
    @Suppress("TooGenericExceptionCaught")
    fun startUpdateFromFile(uri: Uri) {
        val currentState = _state.value as? FirmwareUpdateState.Ready ?: return
        val (_, hardware, address) = currentState

        if (!isValidBluetoothAddress(address)) return

        updateJob?.cancel()
        updateJob =
            viewModelScope.launch {
                try {
                    _state.value = FirmwareUpdateState.Processing(getString(Res.string.firmware_update_extracting))
                    val localFile = fileHandler.copyUriToFile(uri)
                    tempFirmwareFile = localFile

                    initiateDfu(address, hardware, localFile)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e)
                    _state.value = FirmwareUpdateState.Error(e.message ?: "Local update failed")
                }
            }
    }

    /**
     * Configures the DFU service and starts the update.
     *
     * @param address The Bluetooth address of the target device.
     * @param deviceHardware The hardware definition of the target device.
     * @param firmwareFile The local file containing the firmware image.
     */
    private fun initiateDfu(address: String, deviceHardware: DeviceHardware, firmwareFile: File) {
        viewModelScope.launch {
            _state.value = FirmwareUpdateState.Processing(getString(Res.string.firmware_update_starting_service))

            serviceRepository.meshService?.setDeviceAddress(NO_DEVICE_SELECTED)

            DfuServiceInitiator(address)
                .disableResume()
                .setDeviceName(deviceHardware.displayName)
                .setForceScanningForNewAddressInLegacyDfu(true)
                .setForeground(true)
                .setKeepBond(true)
                .setPacketsReceiptNotificationsEnabled(true)
                .setPacketsReceiptNotificationsValue(PACKETS_BEFORE_PRN)
                .setScanTimeout(SCAN_TIMEOUT)
                .setUnsafeExperimentalButtonlessServiceInSecureDfuEnabled(true)
                .setZip(Uri.fromFile(firmwareFile))
                .start(context, FirmwareDfuService::class.java)
        }
    }

    /**
     * Bridges the callback-based DfuServiceListenerHelper to a Kotlin Flow. This decouples the listener implementation
     * from the ViewModel state.
     */
    private suspend fun observeDfuProgress() {
        dfuProgressFlow(context)
            .flowOn(Dispatchers.Main) // Listener Helper typically needs main thread for registration
            .collect { dfuState ->
                when (dfuState) {
                    is DfuInternalState.Progress -> {
                        val msg = getString(Res.string.firmware_update_updating, "${dfuState.percent}")
                        _state.value = FirmwareUpdateState.Updating(dfuState.percent / PERCENT_MAX_VALUE, msg)
                    }
                    is DfuInternalState.Error -> {
                        _state.value = FirmwareUpdateState.Error("DFU Error: ${dfuState.message}")
                        cleanupTemporaryFiles()
                    }
                    is DfuInternalState.Completed -> {
                        _state.value = FirmwareUpdateState.Success
                        serviceRepository.meshService?.setDeviceAddress("$DFU_RECONNECT_PREFIX${dfuState.address}")
                        cleanupTemporaryFiles()
                    }
                    is DfuInternalState.Aborted -> {
                        _state.value = FirmwareUpdateState.Error("DFU Aborted")
                        cleanupTemporaryFiles()
                    }
                    is DfuInternalState.Starting -> {
                        val msg = getString(Res.string.firmware_update_starting_dfu)
                        _state.value = FirmwareUpdateState.Processing(msg)
                    }
                }
            }
    }

    private fun cleanupTemporaryFiles() {
        runCatching {
            tempFirmwareFile?.takeIf { it.exists() }?.delete()
            fileHandler.cleanupAllTemporaryFiles()
        }
            .onFailure { e -> Timber.w(e, "Failed to cleanup temp files") }
        tempFirmwareFile = null
    }

    private data class ValidationResult(
        val node: org.meshtastic.core.database.model.Node,
        val peripheral: no.nordicsemi.kotlin.ble.client.android.Peripheral,
        val address: String,
    )

    /**
     * Validates that a Meshtastic device is known (in Node DB), connected via Bluetooth, and has a valid Bluetooth
     * address.
     */
    private suspend fun validateDeviceAndConnection(): ValidationResult? {
        val ourNode = nodeRepository.ourNodeInfo.value
        val connectedPeripheral =
            centralManager.getBondedPeripherals().firstOrNull { it.state.value == ConnectionState.Connected }
        val address = connectedPeripheral?.address

        return if (ourNode != null && connectedPeripheral != null && address != null) {
            if (isValidBluetoothAddress(address)) {
                ValidationResult(ourNode, connectedPeripheral, address)
            } else {
                _state.value = FirmwareUpdateState.Error(getString(Res.string.firmware_update_invalid_address, address))
                null
            }
        } else {
            _state.value = FirmwareUpdateState.Error(getString(Res.string.firmware_update_no_device))
            null
        }
    }

    private suspend fun getDeviceHardware(ourNode: org.meshtastic.core.database.model.Node): DeviceHardware? {
        val hwModel = ourNode.user.hwModel?.number

        return if (hwModel != null) {
            val deviceHardware = deviceHardwareRepository.getDeviceHardwareByModel(hwModel).getOrNull()
            if (deviceHardware != null) {
                deviceHardware
            } else {
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

private fun getDeviceFirmwareUrl(url: String, targetArch: String): String {
    // Architectures ordered by length descending to handle substrings like esp32-s3 vs esp32
    val knownArchs = listOf("esp32-s3", "esp32-c3", "esp32-c6", "nrf52840", "rp2040", "stm32", "esp32")

    for (arch in knownArchs) {
        if (url.contains(arch, ignoreCase = true)) {
            // Replace the found architecture with the target architecture
            // We use replacement to preserve the rest of the URL structure (version, server, etc.)
            return url.replace(arch, targetArch, ignoreCase = true)
        }
    }

    return url
}

/** Internal state representation for the DFU process flow. */
private sealed interface DfuInternalState {
    data class Starting(val address: String) : DfuInternalState

    data class Progress(val address: String, val percent: Int) : DfuInternalState

    data class Completed(val address: String) : DfuInternalState

    data class Aborted(val address: String) : DfuInternalState

    data class Error(val address: String, val message: String?) : DfuInternalState
}

private fun isValidBluetoothAddress(address: String?): Boolean =
    address != null && BLUETOOTH_ADDRESS_REGEX.matches(address)

private fun FirmwareReleaseRepository.getReleaseFlow(type: FirmwareReleaseType): Flow<FirmwareRelease?> = when (type) {
    FirmwareReleaseType.STABLE -> stableRelease
    FirmwareReleaseType.ALPHA -> alphaRelease
}

/** Converts Nordic DFU callbacks to a cold Flow. Automatically registers/unregisters the listener. */
private fun dfuProgressFlow(context: Context): Flow<DfuInternalState> = callbackFlow {
    val listener =
        object : DfuProgressListenerAdapter() {
            override fun onDfuProcessStarting(deviceAddress: String) {
                trySend(DfuInternalState.Starting(deviceAddress))
            }

            override fun onProgressChanged(
                deviceAddress: String,
                percent: Int,
                speed: Float,
                avgSpeed: Float,
                currentPart: Int,
                partsTotal: Int,
            ) {
                trySend(DfuInternalState.Progress(deviceAddress, percent))
            }

            override fun onDfuCompleted(deviceAddress: String) {
                trySend(DfuInternalState.Completed(deviceAddress))
            }

            override fun onDfuAborted(deviceAddress: String) {
                trySend(DfuInternalState.Aborted(deviceAddress))
            }

            override fun onError(deviceAddress: String, error: Int, errorType: Int, message: String?) {
                trySend(DfuInternalState.Error(deviceAddress, message))
            }
        }

    DfuServiceListenerHelper.registerProgressListener(context, listener)
    awaitClose { DfuServiceListenerHelper.unregisterProgressListener(context, listener) }
}

/**
 * Helper class to handle file operations related to firmware updates, such as downloading, copying from URI, and
 * extracting specific files from Zip archives.
 */
private class FirmwareFileHandler(private val context: Context, private val client: OkHttpClient) {
    fun cleanupAllTemporaryFiles() {
        // Use cacheDir directly with File constructor for cleaner paths
        File(context.cacheDir, "firmware_release.zip").deleteIfExists()
        File(context.cacheDir, "firmware_direct.zip").deleteIfExists()
        File(context.cacheDir, "local_update.zip").deleteIfExists()
    }

    @Suppress("TooGenericExceptionCaught")
    private fun File.deleteIfExists() {
        try {
            if (exists()) delete()
        } catch (e: Exception) {
            Timber.w(e, "Failed to delete file: $name")
        }
    }

    suspend fun checkUrlExists(url: String): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).head().build()
        try {
            client.newCall(request).execute().use { response -> response.isSuccessful }
        } catch (e: IOException) {
            Timber.w(e, "Failed to check URL existence: $url")
            false
        }
    }

    suspend fun copyUriToFile(uri: Uri): File = withContext(Dispatchers.IO) {
        val inputStream =
            context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open content URI")

        val targetFile = File(context.cacheDir, "local_update.zip")

        inputStream.use { input -> FileOutputStream(targetFile).use { output -> input.copyTo(output) } }
        targetFile
    }

    suspend fun downloadFile(url: String, fileName: String, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) throw IOException("Download failed: ${response.code}")

            val body = response.body ?: throw IOException("Empty response body")
            val contentLength = body.contentLength()
            val targetFile = File(context.cacheDir, fileName)

            body.byteStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                    var bytesRead: Int
                    var totalBytesRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        // Check for coroutine cancellation during heavy IO loops
                        if (!isActive) throw CancellationException("Download cancelled")

                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead

                        if (contentLength > 0) {
                            onProgress(totalBytesRead.toFloat() / contentLength)
                        }
                    }
                    // Basic integrity check
                    if (contentLength != -1L && totalBytesRead != contentLength) {
                        throw IOException("Incomplete download: expected $contentLength bytes, got $totalBytesRead")
                    }
                }
            }
            targetFile
        }

    suspend fun extractFirmware(zipFile: File, hardware: DeviceHardware): File? = withContext(Dispatchers.IO) {
        val target = hardware.platformioTarget.ifEmpty { hardware.hwModelSlug }
        if (target.isEmpty()) return@withContext null

        val targetLowerCase = target.lowercase()
        val matchingEntries = mutableListOf<Pair<ZipEntry, File>>()

        ZipInputStream(zipFile.inputStream()).use { zipInput ->
            var entry = zipInput.nextEntry
            while (entry != null) {
                val name = entry.name.lowercase()
                if (!entry.isDirectory && isValidFirmwareFile(name, targetLowerCase)) {
                    val outFile = File(context.cacheDir, File(name).name)
                    // We extract to verify it's a valid zip entry payload
                    FileOutputStream(outFile).use { output -> zipInput.copyTo(output) }
                    matchingEntries.add(entry to outFile)
                }
                entry = zipInput.nextEntry
            }
        }
        // Best match heuristic: prefer shortest filename (e.g. 'tbeam' matches 'tbeam-s3', but 'tbeam' is shorter)
        // This prevents flashing 'tbeam-s3' firmware onto a 'tbeam' device if both are present.
        matchingEntries.minByOrNull { it.first.name.length }?.second
    }

    /**
     * Checks if a filename matches the target device. Enforces stricter matching to avoid substring false positives
     * (e.g. "tbeam" matching "tbeam-s3").
     */
    private fun isValidFirmwareFile(filename: String, target: String): Boolean {
        val regex = Regex(".*[\\-_]${Regex.escape(target)}[\\-_\\.].*")
        return filename.endsWith(".zip") &&
            filename.contains(target) &&
            (regex.matches(filename) || filename.startsWith("$target-") || filename.startsWith("$target."))
    }
}
