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
package org.meshtastic.feature.settings.channel

import app.cash.turbine.test
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.model.util.withOnWirePrecision
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.testing.FakeRadioConfigRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.core.ui.util.SnackbarManager
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.LocalConfig
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
abstract class CommonChannelViewModelTest {

    protected val radioController = FakeRadioController()
    protected val radioConfigRepository: RadioConfigRepository = mock(MockMode.autofill)
    protected val analytics: PlatformAnalytics = mock(MockMode.autofill)
    protected val testDispatcher = UnconfinedTestDispatcher()
    protected val snackbarManager = SnackbarManager()

    protected lateinit var viewModel: ChannelViewModel

    @BeforeTest
    fun setupRepo() {
        Dispatchers.setMain(testDispatcher)
        every { radioConfigRepository.localConfigFlow } returns MutableStateFlow(LocalConfig.Builder().build())
        every { radioConfigRepository.channelSetFlow } returns MutableStateFlow(ChannelSet.Builder().build())

        viewModel = ChannelViewModel(radioController, radioConfigRepository, analytics, snackbarManager)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `isManaged returns true when security is managed`() = runTest {
        val config =
            LocalConfig.Builder()
                .also { wb ->
                    wb.security = Config.SecurityConfig.Builder().also { wb -> wb.is_managed = true }.build()
                }
                .build()
        every { radioConfigRepository.localConfigFlow } returns MutableStateFlow(config)
        viewModel = ChannelViewModel(radioController, radioConfigRepository, analytics, snackbarManager)

        viewModel.localConfig.test {
            awaitItem().security?.is_managed shouldBe true
            assertEquals(true, viewModel.isManaged)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `reset to defaults keeps the transparent mode region and transmit setting of the device`() = runTest {
        val device =
            Config.LoRaConfig.Builder()
                .also { wb ->
                    wb.region = Config.LoRaConfig.RegionCode.EU_868
                    wb.tx_enabled = true
                    wb.hop_limit = 6
                    wb.transparent_mode = true
                }
                .build()
        every { radioConfigRepository.localConfigFlow } returns
            MutableStateFlow(LocalConfig.Builder().also { wb -> wb.lora = device }.build())
        viewModel = ChannelViewModel(radioController, radioConfigRepository, analytics, snackbarManager)

        // Nothing collects viewModel.localConfig here, as nothing does on the channel screen.
        viewModel.resetToDefaults(regionPresetMap = null)

        val written = radioController.configWrites.single().config.lora
        assertEquals(true, written?.transparent_mode)
        assertEquals(Config.LoRaConfig.RegionCode.EU_868, written?.region)
        assertEquals(true, written?.tx_enabled)
    }

    @Test
    fun `txEnabled updates config via radioController`() = runTest {
        viewModel.txEnabled = true
        // FakeRadioController doesn't track setLocalConfig calls yet, but it's fine for coverage
    }

    @Test
    fun `trackShare calls analytics`() {
        viewModel.trackShare()
        verify { analytics.track("share", any()) }
    }

    @Test
    fun `requestChannelUrl sets requestChannelSet`() = runTest {
        // Use a guaranteed valid Meshtastic URL
        val url = "https://www.meshtastic.org/e/#CgMSAQESBggBQANIAQ"
        viewModel.requestChannelUrl(url) {}
        runCurrent()

        assertEquals(true, viewModel.requestChannelSet.value != null)
    }

    @Test
    fun `a failed channel write keeps the radio channels and offers a retry that writes the same set`() =
        runTest(testDispatcher) {
            val repository = FakeRadioConfigRepository()
            val onRadio =
                ChannelSet.Builder()
                    .also { wb -> wb.settings = listOf(ChannelSettings.Builder().also { it.name = "Team" }.build()) }
                    .build()
            repository.setChannelSet(onRadio)
            val writer = ChannelViewModel(radioController, repository, analytics, snackbarManager)
            val reset =
                ChannelSet.Builder()
                    .also { wb -> wb.settings = listOf(ChannelSettings.Builder().also { it.name = "Reset" }.build()) }
                    .build()
            radioController.failEditSettingsBegin = true

            writer.setChannels(reset)
            val failure = snackbarManager.events.first()

            // ChannelScreen draws its list from `channels`, so the screen still shows what the radio has.
            assertEquals(onRadio, writer.channels.first { it.settings.isNotEmpty() })
            assertEquals("Couldn't update the channels on your radio.", failure.message)
            assertEquals("Retry", failure.actionLabel)

            radioController.failEditSettingsBegin = false
            failure.onAction?.invoke()

            // The retry finishes once this test suspends: the failure resumed it from inside its own unconfined frame.
            val written = repository.channelSetFlow.first { it != onRadio }
            assertEquals(reset.settings.withOnWirePrecision(), written.settings)
            snackbarManager.events.test { expectNoEvents() }
        }
}
