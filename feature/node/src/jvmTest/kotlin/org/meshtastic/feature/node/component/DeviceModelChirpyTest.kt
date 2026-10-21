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
package org.meshtastic.feature.node.component

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.repository.DeviceModelRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The viewer's easter egg: Chirpy comes from the app's own resources, never the network. */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceModelChirpyTest {

    private val noNetwork =
        object : DeviceModelRepository {
            override suspend fun hasModel(imageFile: String) = error("Chirpy must not be fetched")

            override suspend fun getModel(imageFile: String) = error("Chirpy must not be fetched")
        }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the bundled Chirpy loads as a framable model with his own notice`() = runTest {
        val viewModel = DeviceModelViewModel(noNetwork)

        viewModel.showChirpy()

        val ready = assertIs<DeviceModelState.Ready>(viewModel.state.first { it != DeviceModelState.Loading })
        assertEquals("chirpy", ready.imageFile)
        assertTrue(ready.copyright.orEmpty().contains("Chirpy"), "notice was ${ready.copyright}")
    }

    @Test
    fun `Chirpy comes back after the viewer fell back to the illustration`() = runTest {
        val viewModel = DeviceModelViewModel(noNetwork)
        viewModel.showChirpy()
        viewModel.state.first { it is DeviceModelState.Ready }

        viewModel.markFailed()
        viewModel.showChirpy()

        assertIs<DeviceModelState.Ready>(viewModel.state.first { it != DeviceModelState.Loading })
    }
}
