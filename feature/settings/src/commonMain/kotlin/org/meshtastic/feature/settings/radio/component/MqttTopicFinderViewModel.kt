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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.network.mqtt.LatLon
import org.meshtastic.core.network.mqtt.MqttRootDirectory
import org.meshtastic.core.network.mqtt.MqttTopicSampler
import org.meshtastic.core.network.mqtt.TopicPlan
import org.meshtastic.core.repository.GeocodedPlace
import org.meshtastic.core.repository.LocationService
import org.meshtastic.core.repository.MqttRootsRepository
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
    private val mqttRootsRepository: MqttRootsRepository,
) : ViewModel() {
    data class UiState(
        val regionName: String? = null,
        val place: GeocodedPlace? = null,
        val location: LatLon? = null,
        val locating: Boolean = false,
        val geocodeFailed: Boolean = false,
        val sample: MqttTopicSampler.State = MqttTopicSampler.State(),
        val directory: MqttRootDirectory = MqttRootDirectory.EMPTY,
    ) {
        /** Rebuilt from each snapshot, since whether the country level is offered depends on what has been heard. */
        val plan: TopicPlan? by lazy {
            regionName?.let { TopicPlan.build(it, place, location, directory, sample.activity) }
        }
    }

    private val sampler = MqttTopicSampler()
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var sampleJob: Job? = null
    private var planJob: Job? = null

    init {
        viewModelScope.launch { sampler.state.collect { sample -> _state.update { it.copy(sample = sample) } } }
        viewModelScope.launch {
            // The seed shows at once; the fetch replaces it when it lands.
            loadDirectory()
            mqttRootsRepository.reconcile()
            loadDirectory()
        }
    }

    private suspend fun loadDirectory() {
        val directory = MqttRootDirectory.from(mqttRootsRepository.getSnapshot())
        _state.update { it.copy(directory = directory) }
    }

    private var openedSession: Int? = null

    /**
     * Starts a sample once per opening of the sheet: a recomposition or rotation repeats [session] and keeps the
     * running sample, a reopened sheet passes a new one.
     */
    fun open(session: Int, config: ModuleConfig.MQTTConfig, regionName: String?, radioPosition: LatLon?) {
        if (session == openedSession) return
        openedSession = session
        start(config, regionName, radioPosition)
    }

    /**
     * Starts a fresh sample; a null region shows nothing but the custom field, as there is no default root to probe.
     */
    fun start(config: ModuleConfig.MQTTConfig, regionName: String?, radioPosition: LatLon?) {
        stop()
        _state.update { UiState(regionName = regionName, sample = it.sample, directory = it.directory) }
        if (regionName == null) return
        planJob = viewModelScope.launch { locate(radioPosition) }
        val previous = sampleJob
        sampleJob = viewModelScope.launch {
            // A cancelled run's cleanup still writes sampler state, so it must end before the next run starts.
            withContext(NonCancellable) { previous?.cancelAndJoin() }
            sampler.run(config, "msh/$regionName")
        }
    }

    /** Ends the sample and keeps what was heard. */
    fun stop() {
        sampleJob?.cancel()
        planJob?.cancel()
    }

    private suspend fun locate(radioPosition: LatLon?) {
        _state.update { it.copy(locating = true) }
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
                place = place,
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
