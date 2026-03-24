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
package org.meshtastic.feature.node.compass

import app.cash.turbine.test
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.Node
import org.meshtastic.proto.Config
import org.meshtastic.proto.User
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CompassViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = CoroutineDispatchers(main = testDispatcher, io = testDispatcher, default = testDispatcher)

    private lateinit var viewModel: CompassViewModel
    private val headingProvider: CompassHeadingProvider = mock()
    private val phoneLocationProvider: PhoneLocationProvider = mock()
    private val magneticFieldProvider: MagneticFieldProvider = mock()

    private val headingFlow = MutableStateFlow(HeadingState())
    private val locationFlow = MutableStateFlow(PhoneLocationState(permissionGranted = true, providerEnabled = true))

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { headingProvider.headingUpdates() } returns headingFlow
        every { phoneLocationProvider.locationUpdates() } returns locationFlow
        every { magneticFieldProvider.getDeclination(any(), any(), any(), any()) } returns 0f

        viewModel =
            CompassViewModel(
                headingProvider = headingProvider,
                phoneLocationProvider = phoneLocationProvider,
                magneticFieldProvider = magneticFieldProvider,
                dispatchers = dispatchers,
            )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialization() {
        assertNotNull(viewModel)
    }

    @Test
    fun `uiState reflects target node info after start`() = runTest {
        val node = Node(num = 1234, user = User(id = "!1234", long_name = "Target Node"))

        viewModel.start(node, Config.DisplayConfig.DisplayUnits.METRIC)

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("Target Node", state.targetName)
            assertEquals(Config.DisplayConfig.DisplayUnits.METRIC, state.displayUnits)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `uiState updates when heading and location change`() = runTest {
        val node =
            Node(
                num = 1234,
                user = User(id = "!1234"),
                position =
                org.meshtastic.proto.Position(
                    latitude_i = 10000000,
                    longitude_i = 10000000,
                ), // 1 deg North, 1 deg East
            )

        viewModel.start(node, Config.DisplayConfig.DisplayUnits.METRIC)

        viewModel.uiState.test {
            // Skip initial states
            awaitItem()

            // Update location and heading
            locationFlow.value =
                PhoneLocationState(
                    permissionGranted = true,
                    providerEnabled = true,
                    location = PhoneLocation(0.0, 0.0, 0.0, 1000L),
                )
            headingFlow.value = HeadingState(heading = 0f)

            // Wait for state with both bearing and heading
            var state = awaitItem()
            while (state.bearing == null || state.heading == null) {
                state = awaitItem()
            }

            // Bearing from (0,0) to (1,1) is approx 45 degrees
            assertEquals(45f, state.bearing, 0.5f)
            assertEquals(0f, state.heading, 0.1f)
            assertTrue(state.hasTargetPosition)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
