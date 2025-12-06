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

package org.meshtastic.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.meshtastic.core.common.BuildConfigProvider
import org.meshtastic.core.data.repository.DeviceHardwareRepository
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.data.repository.RadioConfigRepository
import org.meshtastic.core.database.DatabaseConstants
import org.meshtastic.core.database.DatabaseManager
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.datastore.UiPreferencesDataSource
import org.meshtastic.core.domain.SavePacketLogsUseCase
import org.meshtastic.core.prefs.radio.RadioPrefs
import org.meshtastic.core.prefs.radio.isBle
import org.meshtastic.core.prefs.radio.isSerial
import org.meshtastic.core.prefs.ui.UiPrefs
import org.meshtastic.core.service.IMeshService
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import org.meshtastic.proto.LocalOnlyProtos.LocalConfig
import javax.inject.Inject

@Suppress("LongParameterList")
@HiltViewModel
class SettingsViewModel
@Inject
constructor(
    radioConfigRepository: RadioConfigRepository,
    private val serviceRepository: ServiceRepository,
    private val nodeRepository: NodeRepository,
    private val uiPrefs: UiPrefs,
    private val uiPreferencesDataSource: UiPreferencesDataSource,
    private val buildConfigProvider: BuildConfigProvider,
    private val databaseManager: DatabaseManager,
    private val deviceHardwareRepository: DeviceHardwareRepository,
    private val radioPrefs: RadioPrefs,
    private val savePacketLogsUseCase: SavePacketLogsUseCase,
) : ViewModel() {
    val myNodeInfo: StateFlow<MyNodeEntity?> = nodeRepository.myNodeInfo

    val myNodeNum
        get() = myNodeInfo.value?.myNodeNum

    val ourNodeInfo: StateFlow<Node?> = nodeRepository.ourNodeInfo

    val isConnected =
        serviceRepository.connectionState.map { it.isConnected() }.stateInWhileSubscribed(initialValue = false)

    val localConfig: StateFlow<LocalConfig> =
        radioConfigRepository.localConfigFlow.stateInWhileSubscribed(initialValue = LocalConfig.getDefaultInstance())

    val meshService: IMeshService?
        get() = serviceRepository.meshService

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

    private val _excludedModulesUnlocked = MutableStateFlow(false)
    val excludedModulesUnlocked: StateFlow<Boolean> = _excludedModulesUnlocked.asStateFlow()

    val appVersionName
        get() = buildConfigProvider.versionName

    val isDfuCapable: StateFlow<Boolean> =
        combine(ourNodeInfo, serviceRepository.connectionState) { node, connectionState -> Pair(node, connectionState) }
            .flatMapLatest { (node, connectionState) ->
                if (node == null || !connectionState.isConnected()) {
                    flowOf(false)
                } else if (radioPrefs.isBle() || radioPrefs.isSerial()) {
                    val hwModel = node.user.hwModel.number
                    val hw = deviceHardwareRepository.getDeviceHardwareByModel(hwModel).getOrNull()
                    flow { emit(hw?.requiresDfu == true) }
                } else {
                    flowOf(false)
                }
            }
            .stateInWhileSubscribed(initialValue = false)

    // Device DB cache limit (bounded by DatabaseConstants)
    val dbCacheLimit: StateFlow<Int> = databaseManager.cacheLimit

    fun setDbCacheLimit(limit: Int) {
        val clamped = limit.coerceIn(DatabaseConstants.MIN_CACHE_LIMIT, DatabaseConstants.MAX_CACHE_LIMIT)
        databaseManager.setCacheLimit(clamped)
    }

    fun setProvideLocation(value: Boolean) {
        myNodeNum?.let { uiPrefs.setShouldProvideNodeLocation(it, value) }
    }

    fun setTheme(theme: Int) {
        uiPreferencesDataSource.setTheme(theme)
    }

    fun showAppIntro() {
        uiPreferencesDataSource.setAppIntroCompleted(false)
    }

    fun unlockExcludedModules() {
        _excludedModulesUnlocked.update { true }
    }

    /**
     * Export all persisted packet data to a CSV file at the given URI.
     *
     * The CSV will include all packets, or only those matching the given port number if specified. Each row contains:
     * date, time, sender node number, sender name, sender latitude, sender longitude, receiver latitude, receiver
     * longitude, receiver elevation, received SNR, distance, hop limit, and payload.
     *
     * @param uri The destination URI for the CSV file.
     * @param filterPortnum If provided, only packets with this port number will be exported.
     */
    fun saveDataCsv(uri: Uri, filterPortnum: Int? = null) {
        viewModelScope.launch(Dispatchers.IO) { savePacketLogsUseCase(uri, filterPortnum) }
    }
}
