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
package org.meshtastic.core.ui.viewmodel

import app.cash.turbine.test
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeServiceRepository
import org.meshtastic.proto.LocalConfig
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: ConnectionsViewModel
    private val radioConfigRepository: RadioConfigRepository = mock(MockMode.autofill)
    private val serviceRepository = FakeServiceRepository()
    private val nodeRepository = FakeNodeRepository()
    private val uiPrefs: UiPrefs = mock(MockMode.autofill)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { radioConfigRepository.localConfigFlow } returns MutableStateFlow(LocalConfig())
        every { uiPrefs.hasShownNotPairedWarning } returns MutableStateFlow(false)

        viewModel =
            ConnectionsViewModel(
                radioConfigRepository = radioConfigRepository,
                serviceRepository = serviceRepository,
                nodeRepository = nodeRepository,
                uiPrefs = uiPrefs,
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
    fun `suppressNoPairedWarning updates state and prefs`() {
        every { uiPrefs.setHasShownNotPairedWarning(any()) } returns Unit

        viewModel.suppressNoPairedWarning()

        assertEquals(true, viewModel.hasShownNotPairedWarning.value)
        verify { uiPrefs.setHasShownNotPairedWarning(true) }
    }

    @Test
    fun `Disconnected with Reconnecting progress maps to RECONNECTING`() = runTest {
        viewModel.connectionStatus.test {
            // Initial value from stateInWhileSubscribed.
            assertEquals(ConnectionStatus.NOT_CONNECTED, awaitItem())

            // Track A contract: progress is set BEFORE the Disconnected transition.
            serviceRepository.setConnectionProgress(ServiceRepository.RECONNECTING_PROGRESS_TEXT)
            assertEquals(ConnectionStatus.RECONNECTING, awaitItem())

            // State catch-up: the Disconnected transition is a no-op because FakeServiceRepository
            // starts Disconnected, and distinctUntilChanged suppresses the re-emission.
            serviceRepository.setConnectionState(ConnectionState.Disconnected)
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Connecting state maps to CONNECTING regardless of progress text`() = runTest {
        viewModel.connectionStatus.test {
            assertEquals(ConnectionStatus.NOT_CONNECTED, awaitItem())

            // Progress set first would transiently surface RECONNECTING while state is still Disconnected.
            serviceRepository.setConnectionProgress(ServiceRepository.RECONNECTING_PROGRESS_TEXT)
            assertEquals(ConnectionStatus.RECONNECTING, awaitItem())

            // The Connecting transition overrides progress: Connecting always maps to CONNECTING.
            serviceRepository.setConnectionState(ConnectionState.Connecting)
            assertEquals(ConnectionStatus.CONNECTING, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Disconnected without Reconnecting progress stays NOT_CONNECTED`() = runTest {
        viewModel.connectionStatus.test {
            assertEquals(ConnectionStatus.NOT_CONNECTED, awaitItem())

            serviceRepository.setConnectionProgress("Downloading Node DB...")
            serviceRepository.setConnectionState(ConnectionState.Disconnected)
            // No emission: state and progress resolve to NOT_CONNECTED, equal to the current value.
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Cross-track contract pin: Track A (MeshConnectionManagerImpl.runSiblingHandshakeRecovery) writes the literal
     * "Reconnecting…" (with U+2026) to ServiceRepository.connectionProgress. This constant is what Track C compares
     * against. If either side changes (e.g., localization, ASCII normalization), the UI would silently fall back to
     * NOT_CONNECTED instead of RECONNECTING. This test pins the canonical constant in [ServiceRepository]; combined
     * with the existing ordering test in MeshConnectionManagerImplTest that asserts the same literal is set, this
     * transitively enforces the contract via the shared constant.
     */
    @Test
    fun `RECONNECTING_PROGRESS_TEXT pins the cross-track literal value`() {
        assertEquals("Reconnecting\u2026", ServiceRepository.RECONNECTING_PROGRESS_TEXT)
    }
}
