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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DeviceAdmin
import org.meshtastic.core.model.DeviceAdminEdit
import org.meshtastic.core.testing.FakeRadioConfigRepository
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ScannedQrCodeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var radioConfigRepository: FakeRadioConfigRepository
    private lateinit var deviceAdmin: TestDeviceAdmin
    private lateinit var viewModel: ScannedQrCodeViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        radioConfigRepository = FakeRadioConfigRepository()
        deviceAdmin = TestDeviceAdmin()
        viewModel = ScannedQrCodeViewModel(radioConfigRepository, deviceAdmin)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun channels_emitsFromRadioConfigRepository() = runTest {
        val channelSet = ChannelSet(settings = listOf(ChannelSettings(name = "Primary")))
        radioConfigRepository.setChannelSet(channelSet)
        viewModel = ScannedQrCodeViewModel(radioConfigRepository, deviceAdmin)

        viewModel.channels.test {
            advanceUntilIdle()
            assertEquals(channelSet, expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun setChannels_updatesChannelsViaRadioController() = runTest {
        val first = ChannelSettings(name = "Primary")
        val second = ChannelSettings(name = "Secondary")
        val channelSet = ChannelSet(settings = listOf(first, second))

        viewModel.setChannels(channelSet)
        advanceUntilIdle()

        assertEquals(
            listOf(
                Channel(role = Channel.Role.PRIMARY, index = 0, settings = first),
                Channel(role = Channel.Role.SECONDARY, index = 1, settings = second),
            ),
            deviceAdmin.setLocalChannelCalls,
        )
    }

    @Test
    fun setChannels_updatesLoraConfig_whenDifferent() = runTest {
        val loraConfig = Config.LoRaConfig(hop_limit = 5)
        val channelSet = ChannelSet(settings = listOf(ChannelSettings(name = "Primary")), lora_config = loraConfig)

        viewModel.setChannels(channelSet)
        advanceUntilIdle()

        assertEquals(listOf(Config(lora = loraConfig)), deviceAdmin.setLocalConfigCalls)
    }

    @Test
    fun setChannels_skipsLoraConfig_whenSame() = runTest {
        val channelSet = ChannelSet(settings = listOf(ChannelSettings(name = "Primary")))

        viewModel.setChannels(channelSet)
        advanceUntilIdle()

        assertTrue(deviceAdmin.setLocalConfigCalls.isEmpty())
    }

    @Test
    fun setChannels_replacesAllSettings() = runTest {
        val settings = listOf(ChannelSettings(name = "Primary"), ChannelSettings(name = "Secondary"))

        viewModel.setChannels(ChannelSet(settings = settings))
        advanceUntilIdle()

        assertEquals(settings, radioConfigRepository.currentChannelSet.settings)
    }

    private class TestDeviceAdmin : DeviceAdmin {
        override val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Connected)
        val setLocalChannelCalls = mutableListOf<Channel>()
        val setLocalConfigCalls = mutableListOf<Config>()

        override suspend fun setLocalConfig(config: Config) {
            setLocalConfigCalls += config
        }

        override suspend fun setLocalChannel(channel: Channel) {
            setLocalChannelCalls += channel
        }

        override suspend fun editSettings(destNum: Int, block: suspend DeviceAdminEdit.() -> Unit) = Unit
    }
}
