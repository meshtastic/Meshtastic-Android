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
package org.meshtastic.core.ui.qr

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioConfigRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.core.ui.util.SnackbarManager
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ScannedQrCodeViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val radioController = FakeRadioController()
    private val radioConfigRepository = FakeRadioConfigRepository()
    private val snackbarManager = SnackbarManager()

    private val imported =
        ChannelSet.Builder()
            .also { wb ->
                wb.settings =
                    listOf("Imported", "Private").map { ChannelSettings.Builder().also { s -> s.name = it }.build() }
            }
            .build()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() =
        ScannedQrCodeViewModel(radioConfigRepository, radioController, FakeNodeRepository(), snackbarManager)

    @Test
    fun a_failed_import_offers_a_retry_that_imports_the_same_channels() = runTest(dispatcher) {
        radioController.failEditSettingsBegin = true
        val viewModel = viewModel()

        viewModel.setChannels(imported)
        val failure = snackbarManager.events.first()
        assertEquals("Couldn't update the channels on your radio.", failure.message)
        assertEquals("Retry", failure.actionLabel)

        radioController.failEditSettingsBegin = false
        failure.onAction?.invoke()

        // The failure resumed this test from inside its own unconfined frame, so the retry finishes once the test
        // suspends rather than before invoke returns.
        val written = radioConfigRepository.channelSetFlow.first { it.settings.isNotEmpty() }
        assertEquals(listOf("Imported", "Private"), written.settings.map { it.name })
        snackbarManager.events.test { expectNoEvents() }
    }

    @Test
    fun a_successful_import_shows_no_snackbar() = runTest(dispatcher) {
        val viewModel = viewModel()

        snackbarManager.events.test {
            viewModel.setChannels(imported)

            assertEquals(
                listOf("Imported", "Private"),
                radioConfigRepository.currentChannelSet.settings.map { it.name },
            )
            expectNoEvents()
        }
    }
}
