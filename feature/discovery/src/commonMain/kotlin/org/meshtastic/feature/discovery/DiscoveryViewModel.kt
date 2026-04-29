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
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.ui.viewmodel.safeLaunch
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed

@Suppress("MagicNumber")
@KoinViewModel
class DiscoveryViewModel(
    private val scanEngine: DiscoveryScanEngine,
    private val serviceRepository: ServiceRepository,
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

    private val _selectedPresets = MutableStateFlow<Set<ChannelOption>>(emptySet())
    val selectedPresets: StateFlow<Set<ChannelOption>> = _selectedPresets.asStateFlow()

    private val _dwellDurationMinutes = MutableStateFlow(DEFAULT_DWELL_MINUTES)
    val dwellDurationMinutes: StateFlow<Int> = _dwellDurationMinutes.asStateFlow()

    val isConnected: StateFlow<Boolean> =
        serviceRepository.connectionState
            .map { it is ConnectionState.Connected }
            .stateInWhileSubscribed(initialValue = false)

    val sessions: StateFlow<List<DiscoverySessionEntity>> =
        discoveryDao.getAllSessions().stateInWhileSubscribed(initialValue = emptyList())

    fun togglePreset(preset: ChannelOption) {
        _selectedPresets.update { current -> if (preset in current) current - preset else current + preset }
    }

    fun setDwellDuration(minutes: Int) {
        _dwellDurationMinutes.value = minutes
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

    companion object {
        private const val DEFAULT_DWELL_MINUTES = 15
        private const val SECONDS_PER_MINUTE = 60L
    }
}
