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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import okio.BufferedSink
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.common.BuildConfigProvider
import org.meshtastic.core.common.database.DatabaseManager
import org.meshtastic.core.common.state.ExcludedModulesUnlock
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.domain.usecase.settings.ExportDataUseCase
import org.meshtastic.core.domain.usecase.settings.IsOtaCapableUseCase
import org.meshtastic.core.domain.usecase.settings.SetMeshLogSettingsUseCase
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeListDensity
import org.meshtastic.core.repository.FileService
import org.meshtastic.core.repository.MeshLogPrefs
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.NotificationPrefs
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.RadioController
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
    private val setMeshLogSettingsUseCase: SetMeshLogSettingsUseCase,
    private val exportDataUseCase: ExportDataUseCase,
    private val isOtaCapableUseCase: IsOtaCapableUseCase,
    private val fileService: FileService,
    private val excludedModulesUnlock: ExcludedModulesUnlock,
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
        radioController.startProvideLocation()
    }

    fun stopProvidingLocation() {
        radioController.stopProvideLocation()
    }

    // Process-scoped shared state so other features (e.g. the nightly firmware channel) see the same unlock.
    val excludedModulesUnlocked: StateFlow<Boolean> = excludedModulesUnlock.unlocked

    val appVersionName
        get() = buildConfigProvider.versionName

    val isOtaCapable: StateFlow<Boolean> = isOtaCapableUseCase().stateInWhileSubscribed(initialValue = false)

    // Device DB cache limit (bounded by DatabaseConstants)
    val dbCacheLimit: StateFlow<Int> = databaseManager.cacheLimit

    fun setDbCacheLimit(limit: Int) {
        databaseManager.setCacheLimit(limit)
    }

    // Notifications
    val messagesEnabled = notificationPrefs.messagesEnabled
    val nodeEventsEnabled = notificationPrefs.nodeEventsEnabled
    val lowBatteryEnabled = notificationPrefs.lowBatteryEnabled

    fun setMessagesEnabled(enabled: Boolean) = notificationPrefs.setMessagesEnabled(enabled)

    fun setNodeEventsEnabled(enabled: Boolean) = notificationPrefs.setNodeEventsEnabled(enabled)

    fun setLowBatteryEnabled(enabled: Boolean) = notificationPrefs.setLowBatteryEnabled(enabled)

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
        myNodeNum?.let { uiPrefs.setShouldProvideNodeLocation(it, value) }
    }

    fun setTheme(theme: Int) {
        uiPrefs.setTheme(theme)
    }

    /** Set the application locale. Empty string means system default. */
    fun setLocale(languageTag: String) {
        uiPrefs.setLocale(languageTag)
    }

    fun showAppIntro() {
        uiPrefs.setAppIntroCompleted(false)
    }

    fun unlockExcludedModules() {
        excludedModulesUnlock.unlock()
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

    // Node list layout preferences
    val nodeListDensity = uiPrefs.nodeListDensity
    val shouldShowPower = uiPrefs.shouldShowPower
    val shouldShowLastHeard = uiPrefs.shouldShowLastHeard
    val lastHeardIsRelative = uiPrefs.lastHeardIsRelative
    val shouldShowLocation = uiPrefs.shouldShowLocation
    val shouldShowHops = uiPrefs.shouldShowHops
    val shouldShowSignal = uiPrefs.shouldShowSignal
    val shouldShowChannel = uiPrefs.shouldShowChannel
    val shouldShowRole = uiPrefs.shouldShowRole
    val shouldShowTelemetry = uiPrefs.shouldShowTelemetry

    fun setNodeListDensity(value: String) = uiPrefs.setNodeListDensity(value)

    fun setShouldShowPower(value: Boolean) = uiPrefs.setShouldShowPower(value)

    fun setShouldShowLastHeard(value: Boolean) = uiPrefs.setShouldShowLastHeard(value)

    fun setLastHeardIsRelative(value: Boolean) = uiPrefs.setLastHeardIsRelative(value)

    fun setShouldShowLocation(value: Boolean) = uiPrefs.setShouldShowLocation(value)

    fun setShouldShowHops(value: Boolean) = uiPrefs.setShouldShowHops(value)

    fun setShouldShowSignal(value: Boolean) = uiPrefs.setShouldShowSignal(value)

    fun setShouldShowChannel(value: Boolean) = uiPrefs.setShouldShowChannel(value)

    fun setShouldShowRole(value: Boolean) = uiPrefs.setShouldShowRole(value)

    fun setShouldShowTelemetry(value: Boolean) = uiPrefs.setShouldShowTelemetry(value)

    // Aggregated node list settings — nested combines because typed overloads max at 5 args
    val nodeListSettings =
        combine(
            combine(
                nodeListDensity,
                shouldShowPower,
                shouldShowLastHeard,
                lastHeardIsRelative,
                shouldShowLocation,
            ) { density, power, lastHeard, heardRelative, location ->
                NodeListSettingsState(
                    density = NodeListDensity.fromName(density),
                    showPower = power,
                    showLastHeard = lastHeard,
                    lastHeardIsRelative = heardRelative,
                    showLocation = location,
                )
            },
            combine(shouldShowHops, shouldShowSignal, shouldShowChannel, shouldShowRole, shouldShowTelemetry) {
                    hops,
                    signal,
                    channel,
                    role,
                    telemetry,
                ->
                NodeListSettingsState(
                    showHops = hops,
                    showSignal = signal,
                    showChannel = channel,
                    showRole = role,
                    showTelemetry = telemetry,
                )
            },
        ) { first, second ->
            first.copy(
                showHops = second.showHops,
                showSignal = second.showSignal,
                showChannel = second.showChannel,
                showRole = second.showRole,
                showTelemetry = second.showTelemetry,
            )
        }
            .stateInWhileSubscribed(initialValue = NodeListSettingsState())
}

/** Aggregated state for node list display settings to reduce recomposition overhead. */
data class NodeListSettingsState(
    val density: NodeListDensity = NodeListDensity.COMPLETE,
    val showPower: Boolean = false,
    val showLastHeard: Boolean = false,
    val lastHeardIsRelative: Boolean = false,
    val showLocation: Boolean = false,
    val showHops: Boolean = false,
    val showSignal: Boolean = false,
    val showChannel: Boolean = false,
    val showRole: Boolean = false,
    val showTelemetry: Boolean = false,
)
