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

import dev.mokkery.MockMode
import dev.mokkery.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.model.MqttRootsEntry
import org.meshtastic.core.model.MqttRootsResponse
import org.meshtastic.core.network.mqtt.LatLon
import org.meshtastic.core.network.mqtt.TopicSuggestion
import org.meshtastic.core.repository.GeocodedPlace
import org.meshtastic.core.repository.MqttRootsRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MqttTopicFinderViewModelTest {
    private val seed = MqttRootsResponse(subdivisionSegments = mapOf("US" to mapOf("illinois" to "IL")))
    private val fetched =
        seed.copy(
            entries =
            listOf(MqttRootsEntry("msh/US/MeshSTL", "MeshSTL", center = listOf(38.581, -90.374), radiusKm = 50.0)),
        )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun fetchedEntriesReachThePlan() {
        val viewModel = viewModel(FakeMqttRootsRepository(seed, fetched))

        val plan =
            viewModel.state.value
                .copy(
                    regionName = "US",
                    place = GeocodedPlace("US", "Illinois", "Belleville"),
                    location = LatLon(38.52, -89.98),
                )
                .plan

        assertNotNull(plan)
        assertTrue(plan.suggestions.any { it.kind == TopicSuggestion.Kind.COMMUNITY && it.topic == "msh/US/MeshSTL" })
    }

    @Test
    fun aFailedFetchKeepsTheSeed() {
        val viewModel = viewModel(FakeMqttRootsRepository(seed, fetched = null))

        val directory = viewModel.state.value.directory

        assertEquals(emptyList(), directory.entries)
        assertEquals("IL", directory.subdivisionSegment("US", "Illinois"))
    }

    private fun viewModel(repository: MqttRootsRepository) = MqttTopicFinderViewModel(
        reverseGeocoder = mock(MockMode.autofill),
        locationService = mock(MockMode.autofill),
        mqttRootsRepository = repository,
    )

    /** Serves [seed] until a [reconcile] lands [fetched]; a null [fetched] is a fetch that failed. */
    private class FakeMqttRootsRepository(
        private var snapshot: MqttRootsResponse,
        private val fetched: MqttRootsResponse?,
    ) : MqttRootsRepository {
        override suspend fun getSnapshot(): MqttRootsResponse = snapshot

        override suspend fun reconcile() {
            fetched?.let { snapshot = it }
        }
    }
}
