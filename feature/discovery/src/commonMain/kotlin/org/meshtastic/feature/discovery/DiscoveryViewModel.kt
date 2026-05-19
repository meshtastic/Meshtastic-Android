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
package org.meshtastic.feature.discovery

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.database.dao.DiscoveryDao
import org.meshtastic.core.database.entity.DiscoverySessionEntity
import org.meshtastic.core.model.ChannelOption
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.repository.DiscoveryPrefs
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.ui.viewmodel.safeLaunch
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed

@KoinViewModel
class DiscoveryViewModel(
    private val scanEngine: DiscoveryScanEngine,
    private val serviceRepository: ServiceRepository,
    private val discoveryPrefs: DiscoveryPrefs,
    radioConfigRepository: RadioConfigRepository,
    discoveryDao: DiscoveryDao,
) : ViewModel() {

    val scanState: StateFlow<DiscoveryScanState> = scanEngine.scanState
    val currentSession: StateFlow<DiscoverySessionEntity?> = scanEngine.currentSession
    val connectionState: StateFlow<ConnectionState> = serviceRepository.connectionState

    val homePreset: StateFlow<ChannelOption> =
        radioConfigRepository.localConfigFlow
            .map { localConfig ->
                val presetEnum = localConfig.lora?.modem_preset
                ChannelOption.entries.firstOrNull { it.modemPreset == presetEnum } ?: ChannelOption.DEFAULT
            }
            .stateInWhileSubscribed(initialValue = ChannelOption.DEFAULT)

    private val _selectedPresets = MutableStateFlow<Set<ChannelOption>>(restoreSelectedPresets())
    val selectedPresets: StateFlow<Set<ChannelOption>> = _selectedPresets.asStateFlow()

    private val _dwellDurationMinutes = MutableStateFlow(discoveryPrefs.dwellMinutes.value)
    val dwellDurationMinutes: StateFlow<Int> = _dwellDurationMinutes.asStateFlow()

    val isConnected: StateFlow<Boolean> =
        serviceRepository.connectionState
            .map { it is ConnectionState.Connected }
            .stateInWhileSubscribed(initialValue = false)

    /** True when the primary channel uses the default (well-known) PSK — scanning is unsafe. */
    val usesDefaultKey: StateFlow<Boolean> =
        radioConfigRepository.channelSetFlow
            .map { channelSet ->
                val primaryPsk = channelSet.settings.firstOrNull()?.psk
                primaryPsk == null || primaryPsk.size == 0 || (primaryPsk.size == 1 && primaryPsk[0].toInt() <= 1)
            }
            .stateInWhileSubscribed(initialValue = true)

    val sessions: StateFlow<List<DiscoverySessionEntity>> =
        discoveryDao.getAllSessions().stateInWhileSubscribed(initialValue = emptyList())

    init {
        safeLaunch(tag = "markInterruptedSessions") { discoveryDao.markInterruptedSessions() }
    }

    fun togglePreset(preset: ChannelOption) {
        _selectedPresets.update { current ->
            val updated = if (preset in current) current - preset else current + preset
            discoveryPrefs.setSelectedPresets(updated.map { it.name }.toSet())
            updated
        }
    }

    fun setDwellDuration(minutes: Int) {
        _dwellDurationMinutes.value = minutes
        discoveryPrefs.setDwellMinutes(minutes)
    }

    fun startScan() {
        safeLaunch(tag = "startScan") {
            scanEngine.startScan(
                presets = selectedPresets.value.toList(),
                dwellDurationSeconds = dwellDurationMinutes.value.toLong() * SECONDS_PER_MINUTE,
            )
        }
    }

    fun stopScan() {
        safeLaunch(tag = "stopScan") { scanEngine.stopScan() }
    }

    fun reset() {
        scanEngine.reset()
    }

    private fun restoreSelectedPresets(): Set<ChannelOption> = discoveryPrefs.selectedPresets.value
        .mapNotNull { name -> ChannelOption.entries.firstOrNull { it.name == name } }
        .toSet()

    companion object {
        private const val SECONDS_PER_MINUTE = 60L
    }
}
