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
package org.meshtastic.feature.settings.radio.component

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.network.mqtt.LatLon
import org.meshtastic.core.network.mqtt.MqttRootDirectory
import org.meshtastic.core.network.mqtt.MqttTopicSampler
import org.meshtastic.core.network.mqtt.TopicPlan
import org.meshtastic.core.repository.LocationService
import org.meshtastic.core.repository.ReverseGeocoder
import org.meshtastic.proto.ModuleConfig
import kotlin.time.Duration.Companion.seconds

/**
 * Samples the broker for the root topics in use and builds the suggestions for where the user is. The radio's own fix
 * comes first and the phone's location is the fallback, read only when permission is already granted.
 */
@KoinViewModel
class MqttTopicFinderViewModel(
    private val reverseGeocoder: ReverseGeocoder,
    private val locationService: LocationService,
) : ViewModel() {
    data class UiState(
        val regionName: String? = null,
        val plan: TopicPlan? = null,
        val location: LatLon? = null,
        val locating: Boolean = false,
        val geocodeFailed: Boolean = false,
        val sample: MqttTopicSampler.State = MqttTopicSampler.State(),
    )

    private val sampler = MqttTopicSampler()
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var sampleJob: Job? = null
    private var planJob: Job? = null

    init {
        viewModelScope.launch { sampler.state.collect { sample -> _state.update { it.copy(sample = sample) } } }
    }

    /**
     * Starts a fresh sample; a null region shows nothing but the custom field, as there is no default root to probe.
     */
    fun start(config: ModuleConfig.MQTTConfig, regionName: String?, radioPosition: LatLon?) {
        stop()
        _state.update { UiState(regionName = regionName, sample = it.sample) }
        if (regionName == null) return
        planJob = viewModelScope.launch { plan(regionName, radioPosition) }
        sampleJob = viewModelScope.launch { sampler.run(config, "msh/$regionName") }
    }

    /** Ends the sample and keeps what was heard. */
    fun stop() {
        sampleJob?.cancel()
        planJob?.cancel()
    }

    private suspend fun plan(regionName: String, radioPosition: LatLon?) {
        val directory = MqttRootDirectory.SEED
        _state.update { it.copy(plan = TopicPlan.build(regionName, null, null, directory), locating = true) }
        val location =
            radioPosition
                ?: withTimeoutOrNull(LOCATION_WAIT) { locationService.getCurrentLocation() }
                    ?.let { LatLon(it.latitude, it.longitude) }
        if (location == null) {
            _state.update { it.copy(locating = false) }
            return
        }
        val place = reverseGeocoder.place(location.latitude, location.longitude)
        _state.update {
            it.copy(
                plan = TopicPlan.build(regionName, place, location, directory),
                location = location,
                locating = false,
                geocodeFailed = place == null,
            )
        }
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    private companion object {
        val LOCATION_WAIT = 5.seconds
    }
}
