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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.DeviceModelRepository
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioConfigRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.DisplayInfo
import org.meshtastic.proto.LocalConfig
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Arming the mirror is an admin write the firmware answers only when its metadata reports a display. */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceModelViewModelMirrorTest {

    private val radioController = FakeRadioController()
    private val nodeRepository = FakeNodeRepository()
    private val radioConfigRepository = FakeRadioConfigRepository()
    private val mirrorManager = FakeDisplayMirrorManager()

    private val noModels =
        object : DeviceModelRepository {
            override suspend fun hasModel(imageFile: String) = false

            override suspend fun getModel(imageFile: String): ByteArray? = null
        }

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = DeviceModelViewModel(
        repository = noModels,
        displayMirrorManager = mirrorManager,
        adminController = radioController,
        nodeRepository = nodeRepository,
        radioConfigRepository = radioConfigRepository,
        dispatchers = CoroutineDispatchers(io = testDispatcher, main = testDispatcher, default = testDispatcher),
    )

    private fun ourNode(display: DisplayInfo?) =
        Node(num = 1, metadata = DeviceMetadata.Builder().also { wb -> wb.display = display }.build())

    @Test
    fun `firmware without display info never arms the mirror`() {
        nodeRepository.setOurNode(ourNode(display = null))
        val viewModel = viewModel()
        assertFalse(viewModel.supported.value)

        viewModel.setMirror(true)

        assertTrue(radioController.displayMirrorCalls.isEmpty())
        assertEquals(0, mirrorManager.resets)
    }

    @Test
    fun `firmware with display info arms the mirror once`() {
        nodeRepository.setOurNode(ourNode(display = DisplayInfo.Builder().build()))
        val viewModel = viewModel()
        assertTrue(viewModel.supported.value)

        viewModel.setMirror(true)
        viewModel.setMirror(true)

        assertEquals(listOf(true), radioController.displayMirrorCalls)
        assertEquals(1, mirrorManager.resets)
    }

    @Test
    fun `display info arriving after the handshake arms a pending mirror`() {
        nodeRepository.setOurNode(ourNode(display = null))
        val viewModel = viewModel()
        viewModel.setMirror(true)
        assertTrue(radioController.displayMirrorCalls.isEmpty())

        nodeRepository.setOurNode(ourNode(display = DisplayInfo.Builder().build()))

        assertEquals(listOf(true), radioController.displayMirrorCalls)
    }

    @Test
    fun `display info arriving after the dialog closed arms nothing`() {
        nodeRepository.setOurNode(ourNode(display = null))
        val viewModel = viewModel()
        viewModel.setMirror(true)
        viewModel.setMirror(false)

        nodeRepository.setOurNode(ourNode(display = DisplayInfo.Builder().build()))

        assertTrue(radioController.displayMirrorCalls.isEmpty())
    }

    @Test
    fun `managed mode blocks arming even with display info`() {
        nodeRepository.setOurNode(ourNode(display = DisplayInfo.Builder().build()))
        radioConfigRepository.setLocalConfigDirect(
            LocalConfig.Builder()
                .also { wb -> wb.security = Config.SecurityConfig.Builder().also { s -> s.is_managed = true }.build() }
                .build(),
        )
        val viewModel = viewModel()

        viewModel.setMirror(true)

        assertTrue(radioController.displayMirrorCalls.isEmpty())
    }
}
