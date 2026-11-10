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
package org.meshtastic.feature.settings.debugging

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.DisplayMirrorManager
import org.meshtastic.core.repository.MirrorFrame
import org.meshtastic.core.repository.MirrorPalette
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioConfigRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.DisplayFrame
import org.meshtastic.proto.DisplayInfo
import org.meshtastic.proto.DisplayPalette
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DisplayMirrorViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val radioController = FakeRadioController()
    private val nodeRepository = FakeNodeRepository()
    private val radioConfigRepository = FakeRadioConfigRepository()
    private val mirrorManager = FakeDisplayMirrorManager()

    private class FakeDisplayMirrorManager : DisplayMirrorManager {
        override val frame: StateFlow<MirrorFrame?> = MutableStateFlow(null)
        override val palette: StateFlow<MirrorPalette?> = MutableStateFlow(null)

        override fun handleIncomingFrame(chunk: DisplayFrame) {}

        override fun handleIncomingPalette(chunk: DisplayPalette) {}

        override fun reset() {}
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        radioController.setConnectionState(ConnectionState.Connected)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = DisplayMirrorViewModel(
        displayMirrorManager = mirrorManager,
        connectionStateProvider = radioController,
        nodeRepository = nodeRepository,
        adminController = radioController,
        radioConfigRepository = radioConfigRepository,
    )

    private fun ourNode(display: DisplayInfo?) =
        Node(num = 1, metadata = DeviceMetadata.Builder().also { wb -> wb.display = display }.build())

    @Test
    fun `firmware without display info cannot arm the mirror or request a frame`() {
        nodeRepository.setOurNode(ourNode(display = null))
        val viewModel = viewModel()
        assertFalse(viewModel.supported.value)

        viewModel.setMirror(true)
        viewModel.requestFrame()

        assertTrue(radioController.displayMirrorCalls.isEmpty())
        assertEquals(0, radioController.displayFrameRequests)
        assertFalse(viewModel.mirroring.value)
    }

    @Test
    fun `firmware with display info arms the mirror and requests frames`() {
        nodeRepository.setOurNode(ourNode(display = DisplayInfo.Builder().build()))
        val viewModel = viewModel()
        assertTrue(viewModel.supported.value)

        viewModel.setMirror(true)
        viewModel.requestFrame()

        assertEquals(listOf(true), radioController.displayMirrorCalls)
        assertEquals(1, radioController.displayFrameRequests)
        assertTrue(viewModel.mirroring.value)
    }

    @Test
    fun `keys still reach firmware without display info`() {
        nodeRepository.setOurNode(ourNode(display = null))
        val viewModel = viewModel()

        viewModel.sendKey(INPUT_DOWN)

        assertEquals(listOf(INPUT_DOWN), radioController.inputEvents)
    }

    @Test
    fun `display info arriving after the handshake unlocks the mirror`() {
        nodeRepository.setOurNode(ourNode(display = null))
        val viewModel = viewModel()
        assertFalse(viewModel.supported.value)

        nodeRepository.setOurNode(ourNode(display = DisplayInfo.Builder().build()))

        assertTrue(viewModel.supported.value)
    }
}
