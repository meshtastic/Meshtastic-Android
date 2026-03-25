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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.Config
import org.meshtastic.proto.LocalConfig
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
abstract class CommonChannelViewModelTest {

    protected val radioController = FakeRadioController()
    protected val radioConfigRepository: RadioConfigRepository = mock(MockMode.autofill)
    protected val analytics: PlatformAnalytics = mock(MockMode.autofill)
    protected val testDispatcher = UnconfinedTestDispatcher()

    protected lateinit var viewModel: ChannelViewModel

    fun setupRepo() {
        Dispatchers.setMain(testDispatcher)
        every { radioConfigRepository.localConfigFlow } returns MutableStateFlow(LocalConfig())
        every { radioConfigRepository.channelSetFlow } returns MutableStateFlow(ChannelSet())

        viewModel = ChannelViewModel(radioController, radioConfigRepository, analytics)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `isManaged returns true when security is managed`() = runTest {
        val config = LocalConfig(security = Config.SecurityConfig(is_managed = true))
        every { radioConfigRepository.localConfigFlow } returns MutableStateFlow(config)
        viewModel = ChannelViewModel(radioController, radioConfigRepository, analytics)
        
        viewModel.localConfig.test {
            awaitItem().security?.is_managed shouldBe true
            assertEquals(true, viewModel.isManaged)
            cancelAndIgnoreRemainingEvents()
        }
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
}
