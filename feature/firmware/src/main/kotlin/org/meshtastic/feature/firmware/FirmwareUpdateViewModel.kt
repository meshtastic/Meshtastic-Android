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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import no.nordicsemi.android.dfu.DfuProgressListenerAdapter
import no.nordicsemi.android.dfu.DfuServiceInitiator
import no.nordicsemi.android.dfu.DfuServiceListenerHelper
import okhttp3.OkHttpClient
import okhttp3.Request
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
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.firmware_update_copying
import org.meshtastic.core.strings.firmware_update_extracting
import org.meshtastic.core.strings.firmware_update_failed
import org.meshtastic.core.strings.firmware_update_flashing
import org.meshtastic.core.strings.firmware_update_no_device
import org.meshtastic.core.strings.firmware_update_not_found_in_release
import org.meshtastic.core.strings.firmware_update_rebooting
import org.meshtastic.core.strings.firmware_update_starting_dfu
import org.meshtastic.core.strings.firmware_update_starting_service
import org.meshtastic.core.strings.firmware_update_unknown_hardware
import org.meshtastic.core.strings.firmware_update_updating
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
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
private const val REBOOT_DELAY = 5000L
private const val DEVICE_DETACH_TIMEOUT = 30_000L

private val BLUETOOTH_ADDRESS_REGEX = Regex("([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}")

/**
 * ViewModel responsible for managing the firmware update process for Meshtastic devices.
 *
 * It handles checking for updates, downloading firmware artifacts, extracting compatible firmware, and initiating the
 * Device Firmware Update (DFU) process over Bluetooth.
 */
@HiltViewModel
@Suppress("LongParameterList", "TooManyFunctions")
class FirmwareUpdateViewModel
@Inject
constructor(
    private val firmwareReleaseRepository: FirmwareReleaseRepository,
    private val deviceHardwareRepository: DeviceHardwareRepository,
    private val nodeRepository: NodeRepository,
    client: OkHttpClient,
    private val serviceRepository: ServiceRepository,
    private val radioPrefs: RadioPrefs,
    @ApplicationContext private val context: Context,
    private val bootloaderWarningDataSource: BootloaderWarningDataSource,
) : ViewModel() {

    private val _state = MutableStateFlow<FirmwareUpdateState>(FirmwareUpdateState.Idle)
    val state: StateFlow<FirmwareUpdateState> = _state.asStateFlow()

    private val _selectedReleaseType = MutableStateFlow(FirmwareReleaseType.STABLE)
    val selectedReleaseType: StateFlow<FirmwareReleaseType> = _selectedReleaseType.asStateFlow()

    private var updateJob: Job? = null
    private val fileHandler = FirmwareFileHandler(context, client)
    private var tempFirmwareFile: File? = null

    init {
        // Cleanup potential leftovers from previous crashes
        tempFirmwareFile = cleanupTemporaryFiles(fileHandler, tempFirmwareFile)
        checkForUpdates()

        // Start listening to DFU events immediately
        viewModelScope.launch { observeDfuProgress() }
    }

    override fun onCleared() {
        super.onCleared()
        tempFirmwareFile = cleanupTemporaryFiles(fileHandler, tempFirmwareFile)
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
                            _state.value =
                                FirmwareUpdateState.Ready(
                                    release = release,
                                    deviceHardware = deviceHardware,
                                    address = address,
                                    showBootloaderWarning =
                                    deviceHardware.requiresBootloaderUpgradeForOta == true && !dismissed,
                                )
                        }
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
        val release = currentState.release
        val hardware = currentState.deviceHardware
        val address = currentState.address

        if (release == null) return

        updateJob?.cancel()
        updateJob =
            viewModelScope.launch {
                if (radioPrefs.isSerial()) {
                    startUsbDfuUpdate(release, hardware)
                } else if (radioPrefs.isBle()) {
                    startOtaUpdate(release, hardware, address)
                }
            }
    }

    private suspend fun startOtaUpdate(release: FirmwareRelease, hardware: DeviceHardware, address: String) {
        try {
            // 1. Download
            _state.value = FirmwareUpdateState.Downloading(0f)

            var firmwareFile: File? = null

            // Try direct download of the specific device firmware
            val version = release.id.removePrefix("v")
            val target = hardware.platformioTarget.ifEmpty { hardware.hwModelSlug }
            val filename = "firmware-$target-$version-ota.zip"
            val directUrl =
                "https://raw.githubusercontent.com/meshtastic/meshtastic.github.io/master/firmware-$version/$filename"

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
                _state.value = FirmwareUpdateState.Processing(getString(Res.string.firmware_update_extracting))
                val extracted = fileHandler.extractFirmware(downloadedZip, hardware)

                if (extracted == null) {
                    val msg = getString(Res.string.firmware_update_not_found_in_release, hardware.displayName)
                    _state.value = FirmwareUpdateState.Error(msg)
                    return
                }
                firmwareFile = extracted
            }

            tempFirmwareFile = firmwareFile
            initiateDfu(address, hardware, firmwareFile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e)
            _state.value = FirmwareUpdateState.Error(e.message ?: getString(Res.string.firmware_update_failed))
        }
    }

    private suspend fun startUsbDfuUpdate(release: FirmwareRelease, hardware: DeviceHardware) {
        try {
            _state.value = FirmwareUpdateState.Downloading(0f)

            val target = hardware.platformioTarget.ifEmpty { hardware.hwModelSlug }
            val version = release.id.removePrefix("v")
            val filename = "firmware-$target-$version.uf2"
            val directUrl =
                "https://raw.githubusercontent.com/meshtastic/meshtastic.github.io/master/firmware-$version/$filename"

            val firmwareFile =
                fileHandler.downloadFile(directUrl, filename) { progress ->
                    _state.value = FirmwareUpdateState.Downloading(progress)
                }
            tempFirmwareFile = firmwareFile

            _state.value = FirmwareUpdateState.Processing(getString(Res.string.firmware_update_rebooting))
            serviceRepository.meshService?.rebootToDfu()
            delay(REBOOT_DELAY)

            _state.value = FirmwareUpdateState.AwaitingFileSave(firmwareFile, filename)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e)
            _state.value = FirmwareUpdateState.Error(e.message ?: getString(Res.string.firmware_update_failed))
        }
    }

    /** Saves the downloaded DFU file to the URI chosen by the user. */
    fun saveDfuFile(uri: Uri) {
        val currentState = _state.value as? FirmwareUpdateState.AwaitingFileSave ?: return
        val firmwareFile = currentState.uf2File

        viewModelScope.launch {
            try {
                _state.value = FirmwareUpdateState.Processing(getString(Res.string.firmware_update_copying))
                fileHandler.copyFileToUri(firmwareFile, uri)

                _state.value = FirmwareUpdateState.Processing(getString(Res.string.firmware_update_flashing))

                withTimeoutOrNull(DEVICE_DETACH_TIMEOUT) { waitForDeviceDetach(context).first() }
                    ?: Timber.w("Timed out waiting for device to detach, assuming success")

                _state.value = FirmwareUpdateState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e)
                _state.value = FirmwareUpdateState.Error(e.message ?: getString(Res.string.firmware_update_failed))
            } finally {
                cleanupTemporaryFiles(fileHandler, tempFirmwareFile)
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
        val hardware = currentState.deviceHardware
        val address = currentState.address

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

    /** Persists dismissal of the bootloader warning for the current device and updates state accordingly. */
    fun dismissBootloaderWarningForCurrentDevice() {
        val currentState = _state.value as? FirmwareUpdateState.Ready ?: return
        val address = currentState.address

        viewModelScope.launch {
            runCatching { bootloaderWarningDataSource.dismiss(address) }
                .onFailure { e ->
                    Timber.w(e, "Failed to persist bootloader warning dismissal for address=%s", address)
                }

            _state.value = currentState.copy(showBootloaderWarning = false)
        }
    }

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

    private suspend fun observeDfuProgress() {
        dfuProgressFlow(context).flowOn(Dispatchers.Main).collect { dfuState ->
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
    val knownArchs = listOf("esp32-s3", "esp32-c3", "esp32-c6", "nrf52840", "rp2040", "stm32", "esp32")

    for (arch in knownArchs) {
        if (url.contains(arch, ignoreCase = true)) {
            return url.replace(arch, targetArch.lowercase(), ignoreCase = true)
        }
    }

    return url
}

private fun cleanupTemporaryFiles(fileHandler: FirmwareFileHandler, tempFirmwareFile: File?): File? {
    runCatching {
        tempFirmwareFile?.takeIf { it.exists() }?.delete()
        fileHandler.cleanupAllTemporaryFiles()
    }
        .onFailure { e -> Timber.w(e, "Failed to cleanup temp files") }
    return null
}

private fun waitForDeviceDetach(context: Context): Flow<Unit> = callbackFlow {
    val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                    trySend(Unit).isSuccess
                    close()
                }
            }
        }
    val filter = IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
        @Suppress("UnspecifiedRegisterReceiverFlag")
        context.registerReceiver(receiver, filter)
    }
    awaitClose { context.unregisterReceiver(receiver) }
}

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

private class FirmwareFileHandler(private val context: Context, private val client: OkHttpClient) {
    private val tempDir = File(context.cacheDir, "firmware_update")

    fun cleanupAllTemporaryFiles() {
        runCatching {
            if (tempDir.exists()) {
                tempDir.deleteRecursively()
            }
            tempDir.mkdirs()
        }
            .onFailure { e -> Timber.w(e, "Failed to cleanup temp directory") }
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

        if (!tempDir.exists()) tempDir.mkdirs()

        val targetFile = File(tempDir, "local_update.zip")

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

            if (!tempDir.exists()) tempDir.mkdirs()

            val targetFile = File(tempDir, fileName)

            body.byteStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                    var bytesRead: Int
                    var totalBytesRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (!isActive) throw CancellationException("Download cancelled")

                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead

                        if (contentLength > 0) {
                            onProgress(totalBytesRead.toFloat() / contentLength)
                        }
                    }
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

        if (!tempDir.exists()) tempDir.mkdirs()

        ZipInputStream(zipFile.inputStream()).use { zipInput ->
            var entry = zipInput.nextEntry
            while (entry != null) {
                val name = entry.name.lowercase()
                if (!entry.isDirectory && isValidFirmwareFile(name, targetLowerCase)) {
                    val outFile = File(tempDir, File(name).name)
                    FileOutputStream(outFile).use { output -> zipInput.copyTo(output) }
                    matchingEntries.add(entry to outFile)
                }
                entry = zipInput.nextEntry
            }
        }
        matchingEntries.minByOrNull { it.first.name.length }?.second
    }

    private fun isValidFirmwareFile(filename: String, target: String): Boolean {
        val regex = Regex(".*[\\-_]${Regex.escape(target)}[\\-_\\.].*")
        return filename.endsWith(".zip") &&
            filename.contains(target) &&
            (regex.matches(filename) || filename.startsWith("$target-") || filename.startsWith("$target."))
    }

    suspend fun copyFileToUri(sourceFile: File, destinationUri: Uri) = withContext(Dispatchers.IO) {
        val inputStream = FileInputStream(sourceFile)
        val outputStream =
            context.contentResolver.openOutputStream(destinationUri)
                ?: throw IOException("Cannot open content URI for writing")

        inputStream.use { input -> outputStream.use { output -> input.copyTo(output) } }
    }
}
