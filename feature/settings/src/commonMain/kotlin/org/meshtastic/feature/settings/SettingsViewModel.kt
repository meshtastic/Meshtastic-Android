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
package org.meshtastic.feature.settings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import okio.BufferedSink
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.common.BuildConfigProvider
import org.meshtastic.core.common.database.DatabaseManager
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.domain.usecase.settings.ExportDataUseCase
import org.meshtastic.core.domain.usecase.settings.IsOtaCapableUseCase
import org.meshtastic.core.domain.usecase.settings.MeshLocationUseCase
import org.meshtastic.core.domain.usecase.settings.SetAppIntroCompletedUseCase
import org.meshtastic.core.domain.usecase.settings.SetContrastLevelUseCase
import org.meshtastic.core.domain.usecase.settings.SetDatabaseCacheLimitUseCase
import org.meshtastic.core.domain.usecase.settings.SetLocaleUseCase
import org.meshtastic.core.domain.usecase.settings.SetMeshLogSettingsUseCase
import org.meshtastic.core.domain.usecase.settings.SetNotificationSettingsUseCase
import org.meshtastic.core.domain.usecase.settings.SetProvideLocationUseCase
import org.meshtastic.core.domain.usecase.settings.SetThemeUseCase
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.repository.FileService
import org.meshtastic.core.repository.MeshLogPrefs
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.NotificationPrefs
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.core.ui.viewmodel.safeLaunch
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import org.meshtastic.proto.LocalConfig

@KoinViewModel
@Suppress("LongParameterList", "TooManyFunctions")
class SettingsViewModel(
    radioConfigRepository: RadioConfigRepository,
    private val radioController: RadioController,
    private val nodeRepository: NodeRepository,
    private val uiPrefs: UiPrefs,
    private val buildConfigProvider: BuildConfigProvider,
    private val databaseManager: DatabaseManager,
    private val meshLogPrefs: MeshLogPrefs,
    private val notificationPrefs: NotificationPrefs,
    private val setThemeUseCase: SetThemeUseCase,
    private val setContrastLevelUseCase: SetContrastLevelUseCase,
    private val setLocaleUseCase: SetLocaleUseCase,
    private val setAppIntroCompletedUseCase: SetAppIntroCompletedUseCase,
    private val setProvideLocationUseCase: SetProvideLocationUseCase,
    private val setDatabaseCacheLimitUseCase: SetDatabaseCacheLimitUseCase,
    private val setMeshLogSettingsUseCase: SetMeshLogSettingsUseCase,
    private val setNotificationSettingsUseCase: SetNotificationSettingsUseCase,
    private val meshLocationUseCase: MeshLocationUseCase,
    private val exportDataUseCase: ExportDataUseCase,
    private val isOtaCapableUseCase: IsOtaCapableUseCase,
    private val fileService: FileService,
) : ViewModel() {
    val myNodeInfo: StateFlow<MyNodeInfo?> = nodeRepository.myNodeInfo

    val myNodeNum
        get() = myNodeInfo.value?.myNodeNum

    val ourNodeInfo: StateFlow<Node?> = nodeRepository.ourNodeInfo

    val isConnected =
        radioController.connectionState
            .map { it is ConnectionState.Connected }
            .stateInWhileSubscribed(initialValue = false)

    val localConfig: StateFlow<LocalConfig> =
        radioConfigRepository.localConfigFlow.stateInWhileSubscribed(initialValue = LocalConfig())

    val provideLocation: StateFlow<Boolean> =
        myNodeInfo
            .flatMapLatest { myNodeEntity ->
                // When myNodeInfo changes, set up emissions for the "provide-location-nodeNum" pref.
                if (myNodeEntity == null) {
                    flowOf(false)
                } else {
                    uiPrefs.shouldProvideNodeLocation(myNodeEntity.myNodeNum)
                }
            }
            .stateInWhileSubscribed(initialValue = false)

    fun startProvidingLocation() {
        meshLocationUseCase.startProvidingLocation()
    }

    fun stopProvidingLocation() {
        meshLocationUseCase.stopProvidingLocation()
    }

    private val _excludedModulesUnlocked = MutableStateFlow(false)
    val excludedModulesUnlocked: StateFlow<Boolean> = _excludedModulesUnlocked.asStateFlow()

    val appVersionName
        get() = buildConfigProvider.versionName

    val isOtaCapable: StateFlow<Boolean> = isOtaCapableUseCase().stateInWhileSubscribed(initialValue = false)

    // Device DB cache limit (bounded by DatabaseConstants)
    val dbCacheLimit: StateFlow<Int> = databaseManager.cacheLimit

    fun setDbCacheLimit(limit: Int) {
        setDatabaseCacheLimitUseCase(limit)
    }

    // Notifications
    val messagesEnabled = notificationPrefs.messagesEnabled
    val nodeEventsEnabled = notificationPrefs.nodeEventsEnabled
    val lowBatteryEnabled = notificationPrefs.lowBatteryEnabled

    fun setMessagesEnabled(enabled: Boolean) = setNotificationSettingsUseCase.setMessagesEnabled(enabled)

    fun setNodeEventsEnabled(enabled: Boolean) = setNotificationSettingsUseCase.setNodeEventsEnabled(enabled)

    fun setLowBatteryEnabled(enabled: Boolean) = setNotificationSettingsUseCase.setLowBatteryEnabled(enabled)

    // MeshLog retention period (bounded by MeshLogPrefsImpl constants)
    private val _meshLogRetentionDays = MutableStateFlow(meshLogPrefs.retentionDays.value)
    val meshLogRetentionDays: StateFlow<Int> = _meshLogRetentionDays.asStateFlow()

    private val _meshLogLoggingEnabled = MutableStateFlow(meshLogPrefs.loggingEnabled.value)
    val meshLogLoggingEnabled: StateFlow<Boolean> = _meshLogLoggingEnabled.asStateFlow()

    fun setMeshLogRetentionDays(days: Int) {
        safeLaunch(tag = "setMeshLogRetentionDays") { setMeshLogSettingsUseCase.setRetentionDays(days) }
        _meshLogRetentionDays.value = days.coerceIn(MeshLogPrefs.MIN_RETENTION_DAYS, MeshLogPrefs.MAX_RETENTION_DAYS)
    }

    fun setMeshLogLoggingEnabled(enabled: Boolean) {
        safeLaunch(tag = "setMeshLogLoggingEnabled") { setMeshLogSettingsUseCase.setLoggingEnabled(enabled) }
        _meshLogLoggingEnabled.value = enabled
    }

    fun setProvideLocation(value: Boolean) {
        myNodeNum?.let { setProvideLocationUseCase(it, value) }
    }

    fun setTheme(theme: Int) {
        setThemeUseCase(theme)
    }

    fun setContrastLevel(level: Int) {
        setContrastLevelUseCase(level)
    }

    /** Set the application locale. Empty string means system default. */
    fun setLocale(languageTag: String) {
        setLocaleUseCase(languageTag)
    }

    fun showAppIntro() {
        setAppIntroCompletedUseCase(false)
    }

    fun unlockExcludedModules() {
        _excludedModulesUnlocked.update { true }
    }

    /**
     * Export all persisted packet data to a CSV file at the given URI.
     *
     * @param uri The destination URI for the CSV file.
     * @param filterPortnum If provided, only packets with this port number will be exported.
     */
    fun saveDataCsv(uri: CommonUri, filterPortnum: Int? = null) {
        safeLaunch(tag = "saveDataCsv") {
            fileService.write(uri) { writer -> performDataExport(writer, filterPortnum) }
        }
    }

    private suspend fun performDataExport(writer: BufferedSink, filterPortnum: Int?) {
        val myNodeNum = myNodeNum ?: return
        exportDataUseCase(writer, myNodeNum, filterPortnum)
    }
}
