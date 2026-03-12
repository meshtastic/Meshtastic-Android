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

import android.app.Application
import android.net.Uri
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.BufferedSink
import okio.buffer
import okio.sink
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.common.BuildConfigProvider
import org.meshtastic.core.common.database.DatabaseManager
import org.meshtastic.core.domain.usecase.settings.ExportDataUseCase
import org.meshtastic.core.domain.usecase.settings.IsOtaCapableUseCase
import org.meshtastic.core.domain.usecase.settings.MeshLocationUseCase
import org.meshtastic.core.domain.usecase.settings.SetAppIntroCompletedUseCase
import org.meshtastic.core.domain.usecase.settings.SetDatabaseCacheLimitUseCase
import org.meshtastic.core.domain.usecase.settings.SetLocaleUseCase
import org.meshtastic.core.domain.usecase.settings.SetMeshLogSettingsUseCase
import org.meshtastic.core.domain.usecase.settings.SetProvideLocationUseCase
import org.meshtastic.core.domain.usecase.settings.SetThemeUseCase
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.repository.MeshLogPrefs
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.feature.settings.SettingsViewModel
import java.io.FileNotFoundException
import java.io.FileOutputStream

@KoinViewModel
@Suppress("LongParameterList")
class AndroidSettingsViewModel(
    private val app: Application,
    radioConfigRepository: RadioConfigRepository,
    radioController: RadioController,
    nodeRepository: NodeRepository,
    uiPrefs: UiPrefs,
    buildConfigProvider: BuildConfigProvider,
    databaseManager: DatabaseManager,
    meshLogPrefs: MeshLogPrefs,
    setThemeUseCase: SetThemeUseCase,
    setLocaleUseCase: SetLocaleUseCase,
    setAppIntroCompletedUseCase: SetAppIntroCompletedUseCase,
    setProvideLocationUseCase: SetProvideLocationUseCase,
    setDatabaseCacheLimitUseCase: SetDatabaseCacheLimitUseCase,
    setMeshLogSettingsUseCase: SetMeshLogSettingsUseCase,
    meshLocationUseCase: MeshLocationUseCase,
    exportDataUseCase: ExportDataUseCase,
    isOtaCapableUseCase: IsOtaCapableUseCase,
) : SettingsViewModel(
    radioConfigRepository,
    radioController,
    nodeRepository,
    uiPrefs,
    buildConfigProvider,
    databaseManager,
    meshLogPrefs,
    setThemeUseCase,
    setLocaleUseCase,
    setAppIntroCompletedUseCase,
    setProvideLocationUseCase,
    setDatabaseCacheLimitUseCase,
    setMeshLogSettingsUseCase,
    meshLocationUseCase,
    exportDataUseCase,
    isOtaCapableUseCase,
) {
    override fun saveDataCsv(uri: Any, filterPortnum: Int?) {
        if (uri is Uri) {
            viewModelScope.launch { writeToUri(uri) { writer -> performDataExport(writer, filterPortnum) } }
        }
    }

    private suspend inline fun writeToUri(uri: Uri, crossinline block: suspend (BufferedSink) -> Unit) {
        withContext(Dispatchers.IO) {
            try {
                app.contentResolver.openFileDescriptor(uri, "wt")?.use { parcelFileDescriptor ->
                    FileOutputStream(parcelFileDescriptor.fileDescriptor).sink().buffer().use { writer ->
                        block.invoke(writer)
                    }
                }
            } catch (ex: FileNotFoundException) {
                Logger.e { "Can't write file error: ${ex.message}" }
            }
        }
    }
}
