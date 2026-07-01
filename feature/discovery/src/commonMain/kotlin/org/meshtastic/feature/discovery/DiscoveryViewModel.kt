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
import org.meshtastic.core.model.MeshBeaconOffer
import org.meshtastic.core.repository.DiscoveryPrefs
import org.meshtastic.core.repository.MeshBeaconRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.ui.viewmodel.safeLaunch
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import org.meshtastic.feature.discovery.scan.Check24GhzCapability
import org.meshtastic.feature.discovery.scan.HardwareCapabilityResult
import org.meshtastic.proto.Config.LoRaConfig.RegionCode

@KoinViewModel
class DiscoveryViewModel(
    private val scanEngine: DiscoveryScanEngine,
    private val serviceRepository: ServiceRepository,
    private val discoveryPrefs: DiscoveryPrefs,
    private val check24GhzCapability: Check24GhzCapability,
    private val meshBeaconRepository: MeshBeaconRepository,
    radioConfigRepository: RadioConfigRepository,
    discoveryDao: DiscoveryDao,
) : ViewModel() {

    val scanState: StateFlow<DiscoveryScanState> = scanEngine.scanState
    val currentSession: StateFlow<DiscoverySessionEntity?> = scanEngine.currentSession
    val connectionState: StateFlow<ConnectionState> = serviceRepository.connectionState

    /** Mesh Beacon invitations received from other meshes, newest first. */
    val beaconOffers: StateFlow<List<MeshBeaconOffer>> = meshBeaconRepository.offers

    val homePreset: StateFlow<ChannelOption> =
        radioConfigRepository.localConfigFlow
            .map { localConfig -> ChannelOption.from(localConfig.lora?.modem_preset) ?: ChannelOption.DEFAULT }
            .stateInWhileSubscribed(initialValue = ChannelOption.DEFAULT)

    /** True when the radio is configured for LORA_24 region but hardware doesn't support 2.4 GHz. */
    private val _is24GhzBlocked = MutableStateFlow(false)
    val is24GhzBlocked: StateFlow<Boolean> = _is24GhzBlocked.asStateFlow()

    /** True when the radio is on the LORA_24 region. */
    val isLora24Region: StateFlow<Boolean> =
        radioConfigRepository.localConfigFlow
            .map { it.lora?.region == RegionCode.LORA_24 }
            .stateInWhileSubscribed(initialValue = false)

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
        safeLaunch(tag = "check24GhzCapability") {
            val result = check24GhzCapability()
            _is24GhzBlocked.value =
                result is HardwareCapabilityResult.Unsupported || result is HardwareCapabilityResult.Unknown
        }
    }

    fun togglePreset(preset: ChannelOption) {
        _selectedPresets.update { current ->
            val updated = if (preset in current) current - preset else current + preset
            discoveryPrefs.setSelectedPresets(updated.map { it.name }.toSet())
            updated
        }
    }

    /**
     * Seeds the scan with just the preset an invitation advertised, so the user can survey the advertised mesh before
     * joining. Persists like [togglePreset] (not a bare [_selectedPresets] write) so the shown selection and saved
     * prefs never diverge — otherwise the next [togglePreset] would silently persist prefs derived from this seed.
     * No-op if the offer carries no preset, or a preset with no matching [ChannelOption].
     */
    fun discoverOffer(offer: MeshBeaconOffer) {
        val preset = ChannelOption.from(offer.beacon.offer_preset) ?: return
        _selectedPresets.value = setOf(preset)
        discoveryPrefs.setSelectedPresets(setOf(preset.name))
    }

    fun dismissOffer(offer: MeshBeaconOffer) {
        meshBeaconRepository.dismiss(offer.key)
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
