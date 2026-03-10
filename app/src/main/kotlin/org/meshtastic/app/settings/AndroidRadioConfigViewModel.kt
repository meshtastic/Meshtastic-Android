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
package org.meshtastic.app.settings

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.buffer
import okio.sink
import okio.source
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.domain.usecase.settings.AdminActionsUseCase
import org.meshtastic.core.domain.usecase.settings.ExportProfileUseCase
import org.meshtastic.core.domain.usecase.settings.ExportSecurityConfigUseCase
import org.meshtastic.core.domain.usecase.settings.ImportProfileUseCase
import org.meshtastic.core.domain.usecase.settings.InstallProfileUseCase
import org.meshtastic.core.domain.usecase.settings.ProcessRadioResponseUseCase
import org.meshtastic.core.domain.usecase.settings.RadioConfigUseCase
import org.meshtastic.core.domain.usecase.settings.ToggleAnalyticsUseCase
import org.meshtastic.core.domain.usecase.settings.ToggleHomoglyphEncodingUseCase
import org.meshtastic.core.repository.AnalyticsPrefs
import org.meshtastic.core.repository.HomoglyphPrefs
import org.meshtastic.core.repository.LocationRepository
import org.meshtastic.core.repository.MapConsentPrefs
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceProfile
import java.io.FileOutputStream

@KoinViewModel
class AndroidRadioConfigViewModel(
    savedStateHandle: SavedStateHandle,
    private val app: Application,
    radioConfigRepository: RadioConfigRepository,
    packetRepository: PacketRepository,
    serviceRepository: ServiceRepository,
    nodeRepository: NodeRepository,
    private val locationRepository: LocationRepository,
    mapConsentPrefs: MapConsentPrefs,
    analyticsPrefs: AnalyticsPrefs,
    homoglyphEncodingPrefs: HomoglyphPrefs,
    toggleAnalyticsUseCase: ToggleAnalyticsUseCase,
    toggleHomoglyphEncodingUseCase: ToggleHomoglyphEncodingUseCase,
    importProfileUseCase: ImportProfileUseCase,
    exportProfileUseCase: ExportProfileUseCase,
    exportSecurityConfigUseCase: ExportSecurityConfigUseCase,
    installProfileUseCase: InstallProfileUseCase,
    radioConfigUseCase: RadioConfigUseCase,
    adminActionsUseCase: AdminActionsUseCase,
    processRadioResponseUseCase: ProcessRadioResponseUseCase,
) : RadioConfigViewModel(
    savedStateHandle,
    radioConfigRepository,
    packetRepository,
    serviceRepository,
    nodeRepository,
    locationRepository,
    mapConsentPrefs,
    analyticsPrefs,
    homoglyphEncodingPrefs,
    toggleAnalyticsUseCase,
    toggleHomoglyphEncodingUseCase,
    importProfileUseCase,
    exportProfileUseCase,
    exportSecurityConfigUseCase,
    installProfileUseCase,
    radioConfigUseCase,
    adminActionsUseCase,
    processRadioResponseUseCase,
) {
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    override suspend fun getCurrentLocation(): Location? = if (
        ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    ) {
        locationRepository.getLocations().firstOrNull()
    } else {
        null
    }

    override fun importProfile(uri: Any, onResult: (DeviceProfile) -> Unit) {
        if (uri is Uri) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    app.contentResolver.openInputStream(uri)?.source()?.buffer()?.use { inputStream ->
                        importProfileUseCase(inputStream).onSuccess(onResult).onFailure { throw it }
                    }
                } catch (ex: Exception) {
                    Logger.e { "Import DeviceProfile error: ${ex.message}" }
                    // Error handling simplified for this example
                }
            }
        }
    }

    override fun exportProfile(uri: Any, profile: DeviceProfile) {
        if (uri is Uri) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    try {
                        app.contentResolver.openFileDescriptor(uri, "wt")?.use { parcelFileDescriptor ->
                            FileOutputStream(parcelFileDescriptor.fileDescriptor).sink().buffer().use { outputStream ->
                                exportProfileUseCase(outputStream, profile)
                                    .onSuccess { /* Success */ }
                                    .onFailure { throw it }
                            }
                        }
                    } catch (ex: Exception) {
                        Logger.e { "Can't write file error: ${ex.message}" }
                    }
                }
            }
        }
    }

    override fun exportSecurityConfig(uri: Any, securityConfig: Config.SecurityConfig) {
        if (uri is Uri) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    try {
                        app.contentResolver.openFileDescriptor(uri, "wt")?.use { parcelFileDescriptor ->
                            FileOutputStream(parcelFileDescriptor.fileDescriptor).sink().buffer().use { outputStream ->
                                exportSecurityConfigUseCase(outputStream, securityConfig)
                                    .onSuccess { /* Success */ }
                                    .onFailure { throw it }
                            }
                        }
                    } catch (ex: Exception) {
                        Logger.e { "Can't write security keys JSON error: ${ex.message}" }
                    }
                }
            }
        }
    }
}
