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
package org.meshtastic.core.domain.usecase.settings

import kotlinx.coroutines.test.runTest
import org.meshtastic.core.common.log.expectedConditionLabel
import org.meshtastic.core.model.util.MalformedMeshtasticUrlException
import org.meshtastic.core.model.util.getChannelUrl
import org.meshtastic.core.repository.PacketQueueRejectedException
import org.meshtastic.core.testing.FakeRadioConfigRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.core.testing.FakeRadioController.SettingsOperation
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.Config.BluetoothConfig
import org.meshtastic.proto.Config.DeviceConfig
import org.meshtastic.proto.Config.DisplayConfig
import org.meshtastic.proto.Config.LoRaConfig
import org.meshtastic.proto.Config.NetworkConfig
import org.meshtastic.proto.Config.PositionConfig
import org.meshtastic.proto.Config.PowerConfig
import org.meshtastic.proto.Config.SecurityConfig
import org.meshtastic.proto.DeviceProfile
import org.meshtastic.proto.ModuleConfig.AmbientLightingConfig
import org.meshtastic.proto.ModuleConfig.AudioConfig
import org.meshtastic.proto.ModuleConfig.CannedMessageConfig
import org.meshtastic.proto.ModuleConfig.DetectionSensorConfig
import org.meshtastic.proto.ModuleConfig.ExternalNotificationConfig
import org.meshtastic.proto.ModuleConfig.MQTTConfig
import org.meshtastic.proto.ModuleConfig.NeighborInfoConfig
import org.meshtastic.proto.ModuleConfig.PaxcounterConfig
import org.meshtastic.proto.ModuleConfig.RangeTestConfig
import org.meshtastic.proto.ModuleConfig.RemoteHardwareConfig
import org.meshtastic.proto.ModuleConfig.SerialConfig
import org.meshtastic.proto.ModuleConfig.StatusMessageConfig
import org.meshtastic.proto.ModuleConfig.StoreForwardConfig
import org.meshtastic.proto.ModuleConfig.TAKConfig
import org.meshtastic.proto.ModuleConfig.TelemetryConfig
import org.meshtastic.proto.User
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InstallProfileUseCaseTest {

    private lateinit var radioController: FakeRadioController
    private lateinit var radioConfigRepository: FakeRadioConfigRepository
    private lateinit var useCase: InstallProfileUseCase

    @BeforeTest
    fun setUp() {
        radioController = FakeRadioController()
        radioConfigRepository = FakeRadioConfigRepository()
        useCase = InstallProfileUseCase(radioController, radioConfigRepository)
    }

    @Test
    fun `invoke calls begin and commit edit settings`() = runTest {
        useCase(1234, DeviceProfile(), User(), currentLoraConfig = null, isLocal = false)

        assertTrue(radioController.editSettingsCalled)
    }

    @Test
    fun `invoke installs all sections of a full profile`() = runTest {
        val profile =
            DeviceProfile(
                long_name = "Full Node",
                short_name = "FULL",
                config =
                org.meshtastic.proto.LocalConfig(
                    device = DeviceConfig(),
                    position = PositionConfig(),
                    power = PowerConfig(),
                    network = NetworkConfig(),
                    display = DisplayConfig(),
                    lora = LoRaConfig(),
                    bluetooth = BluetoothConfig(),
                    security = SecurityConfig(),
                ),
                module_config =
                org.meshtastic.proto.LocalModuleConfig(
                    mqtt = MQTTConfig(),
                    serial = SerialConfig(),
                    external_notification = ExternalNotificationConfig(),
                    store_forward = StoreForwardConfig(),
                    range_test = RangeTestConfig(),
                    telemetry = TelemetryConfig(),
                    canned_message = CannedMessageConfig(),
                    audio = AudioConfig(),
                    remote_hardware = RemoteHardwareConfig(),
                    neighbor_info = NeighborInfoConfig(),
                    ambient_lighting = AmbientLightingConfig(),
                    detection_sensor = DetectionSensorConfig(),
                    paxcounter = PaxcounterConfig(),
                    statusmessage = StatusMessageConfig(),
                    tak = TAKConfig(),
                ),
                fixed_position = org.meshtastic.proto.Position(),
            )

        useCase(1234, profile, org.meshtastic.proto.User(long_name = "Old"), currentLoraConfig = null, isLocal = false)

        assertTrue(radioController.editSettingsCalled)
    }

    @Test
    fun `fixed position queue rejection aborts profile installation after closing the edit transaction`() = runTest {
        val rejection = PacketQueueRejectedException("Fixed position")
        radioController.onSetFixedPosition = { _, _ -> throw rejection }
        val profile = DeviceProfile(fixed_position = org.meshtastic.proto.Position(latitude_i = 1, longitude_i = 1))

        val failure =
            assertFailsWith<PacketQueueRejectedException> {
                useCase(
                    destNum = 1234,
                    profile = profile,
                    currentUser = User(),
                    currentLoraConfig = null,
                    isLocal = false,
                )
            }

        assertEquals(rejection, failure)
        assertEquals(listOf("begin", "commit"), radioController.adminOperations)
        assertTrue(radioController.fixedPositions.isEmpty())
    }

    @Test
    fun `invoke installs is_unmessagable but never auto-installs is_licensed`() = runTest {
        val profile = DeviceProfile(is_unmessagable = true, is_licensed = true)

        useCase(1234, profile, User(long_name = "Old"), currentLoraConfig = null, isLocal = false)

        assertEquals(true, radioController.lastSetOwnerUser?.is_unmessagable)
        assertEquals(false, radioController.lastSetOwnerUser?.is_licensed)
    }

    @Test
    fun `invoke normalizes channels refreshes local cache and writes URL LoRa once`() = runTest {
        val primary = ChannelSettings(name = "Node A Primary")
        val secondary = ChannelSettings(name = "Node A Secondary")
        val urlLoraConfig =
            LoRaConfig(
                use_preset = true,
                modem_preset = LoRaConfig.ModemPreset.MEDIUM_FAST,
                region = LoRaConfig.RegionCode.US,
            )
        val profileLoraConfig =
            LoRaConfig(
                use_preset = true,
                modem_preset = LoRaConfig.ModemPreset.LONG_SLOW,
                region = LoRaConfig.RegionCode.EU_868,
            )
        val currentLoraConfig = LoRaConfig(region = LoRaConfig.RegionCode.ANZ)
        val oldSettings = listOf(ChannelSettings(name = "Node B Primary"))
        val cachedLoraConfig = LoRaConfig(region = LoRaConfig.RegionCode.EU_433)
        radioConfigRepository.setChannelSet(ChannelSet(settings = oldSettings, lora_config = cachedLoraConfig))
        val exportedProfile =
            DeviceProfile(
                config = org.meshtastic.proto.LocalConfig(lora = profileLoraConfig),
                channel_url =
                ChannelSet(
                    settings = listOf(primary, ChannelSettings(), primary, secondary),
                    lora_config = urlLoraConfig,
                )
                    .getChannelUrl()
                    .toString(),
            )

        useCase(
            4321,
            exportedProfile,
            User(long_name = "Node B"),
            currentLoraConfig = currentLoraConfig,
            isLocal = true,
        )

        assertTrue(radioController.editSettingsCalled, "profile install transaction did not run")
        val channelWrites = radioController.channelWrites
        assertEquals(List(8) { 4321 }, channelWrites.map(FakeRadioController.ChannelWrite::destination))
        assertEquals((0..7).toList(), channelWrites.map { it.channel.index })
        assertEquals(
            listOf(
                Channel.Role.PRIMARY,
                Channel.Role.SECONDARY,
                Channel.Role.DISABLED,
                Channel.Role.DISABLED,
                Channel.Role.DISABLED,
                Channel.Role.DISABLED,
                Channel.Role.DISABLED,
                Channel.Role.DISABLED,
            ),
            channelWrites.map { it.channel.role },
        )
        assertEquals(listOf(primary, secondary), channelWrites.take(2).map { it.channel.settings })
        assertEquals(listOf(primary, secondary), radioConfigRepository.currentChannelSet.settings)
        assertEquals(urlLoraConfig, radioConfigRepository.currentChannelSet.lora_config)
        assertEquals(
            listOf(FakeRadioController.ConfigWrite(destination = 4321, config = Config(lora = urlLoraConfig))),
            radioController.configWrites,
        )
        assertEquals(
            channelWrites.map { SettingsOperation.SetChannel(it.channel) } +
                SettingsOperation.SetConfig(Config(lora = urlLoraConfig)),
            radioController.settingsOperations,
        )
    }

    @Test
    fun `invoke treats blank channel URL as absent and installs profile LoRa once`() = runTest {
        val oldSettings = listOf(ChannelSettings(name = "Keep Me"))
        val currentLoraConfig = LoRaConfig(region = LoRaConfig.RegionCode.EU_868)
        val profileLoraConfig = LoRaConfig(region = LoRaConfig.RegionCode.US)
        val cachedLoraConfig = LoRaConfig(region = LoRaConfig.RegionCode.ANZ)
        radioConfigRepository.setChannelSet(ChannelSet(settings = oldSettings, lora_config = cachedLoraConfig))
        val profile =
            DeviceProfile(channel_url = " \t\n", config = org.meshtastic.proto.LocalConfig(lora = profileLoraConfig))

        useCase(4321, profile, User(long_name = "Node B"), currentLoraConfig = currentLoraConfig, isLocal = true)

        assertTrue(radioController.editSettingsCalled)
        assertTrue(radioController.localChannels.isEmpty())
        assertEquals(
            listOf(FakeRadioController.ConfigWrite(destination = 4321, config = Config(lora = profileLoraConfig))),
            radioController.configWrites,
        )
        assertEquals(
            listOf(FakeRadioConfigRepository.ChannelSetUpdate(settingsList = null, loraConfig = profileLoraConfig)),
            radioConfigRepository.channelSetUpdates,
        )
        assertEquals(oldSettings, radioConfigRepository.currentChannelSet.settings)
        assertEquals(profileLoraConfig, radioConfigRepository.currentChannelSet.lora_config)
    }

    @Test
    fun `invoke replaces local channels and preserves cached LoRa when no LoRa write is needed`() = runTest {
        val cachedLoraConfig = LoRaConfig(region = LoRaConfig.RegionCode.ANZ)
        val importedPrimary = ChannelSettings(name = "Imported Primary")
        radioConfigRepository.setChannelSet(
            ChannelSet(settings = listOf(ChannelSettings(name = "Old Primary")), lora_config = cachedLoraConfig),
        )
        val profile =
            DeviceProfile(channel_url = ChannelSet(settings = listOf(importedPrimary)).getChannelUrl().toString())

        useCase(4321, profile, User(long_name = "Node B"), currentLoraConfig = cachedLoraConfig, isLocal = true)

        assertTrue(radioController.localConfigs.isEmpty())
        assertEquals(listOf(importedPrimary), radioConfigRepository.currentChannelSet.settings)
        assertEquals(cachedLoraConfig, radioConfigRepository.currentChannelSet.lora_config)
    }

    @Test
    fun `invoke skips redundant LoRa write when desired config is already active`() = runTest {
        val currentLoraConfig = LoRaConfig(region = LoRaConfig.RegionCode.US)
        val profile = DeviceProfile(config = org.meshtastic.proto.LocalConfig(lora = currentLoraConfig))

        useCase(4321, profile, User(long_name = "Node B"), currentLoraConfig = currentLoraConfig, isLocal = false)

        assertTrue(radioController.localConfigs.isEmpty())
    }

    @Test
    fun `invoke does not replace local cache for a remote profile install`() = runTest {
        val oldSettings = listOf(ChannelSettings(name = "Local Primary"))
        val remotePrimary = ChannelSettings(name = "Remote Primary")
        val cachedChannelSet =
            ChannelSet(settings = oldSettings, lora_config = LoRaConfig(region = LoRaConfig.RegionCode.ANZ))
        radioConfigRepository.setChannelSet(cachedChannelSet)
        val profile =
            DeviceProfile(
                channel_url =
                ChannelSet(
                    settings = listOf(remotePrimary),
                    lora_config = LoRaConfig(region = LoRaConfig.RegionCode.US),
                )
                    .getChannelUrl()
                    .toString(),
            )

        useCase(4321, profile, User(long_name = "Remote"), currentLoraConfig = null, isLocal = false)

        assertEquals(
            FakeRadioController.ChannelWrite(
                4321,
                Channel(index = 0, role = Channel.Role.PRIMARY, settings = remotePrimary),
            ),
            radioController.channelWrites.first(),
        )
        assertTrue(radioController.localChannels.isEmpty())
        assertEquals(cachedChannelSet, radioConfigRepository.currentChannelSet)
    }

    @Test
    fun `invoke leaves local cache unchanged when a channel write fails`() = runTest {
        val oldSettings = listOf(ChannelSettings(name = "Local Primary"))
        val cachedChannelSet =
            ChannelSet(settings = oldSettings, lora_config = LoRaConfig(region = LoRaConfig.RegionCode.ANZ))
        radioConfigRepository.setChannelSet(cachedChannelSet)
        radioController.failChannelWriteAfter = 2
        val profile =
            DeviceProfile(
                channel_url =
                ChannelSet(
                    settings = listOf(ChannelSettings(name = "Imported Primary")),
                    lora_config = LoRaConfig(region = LoRaConfig.RegionCode.US),
                )
                    .getChannelUrl()
                    .toString(),
            )

        assertFailsWith<IllegalStateException> {
            useCase(4321, profile, User(long_name = "Local"), currentLoraConfig = null, isLocal = true)
        }

        assertEquals(cachedChannelSet, radioConfigRepository.currentChannelSet)
    }

    @Test
    fun `invoke rejects an empty channel set before opening the transaction`() = runTest {
        val destinationPrimary =
            Channel(role = Channel.Role.PRIMARY, index = 0, settings = ChannelSettings(name = "Node B Primary"))
        radioController.channelWrites.add(
            FakeRadioController.ChannelWrite(destination = null, channel = destinationPrimary),
        )
        val profile =
            DeviceProfile(
                long_name = "Must Not Apply",
                channel_url =
                ChannelSet(
                    settings = emptyList(),
                    lora_config = LoRaConfig(use_preset = true, region = LoRaConfig.RegionCode.US),
                )
                    .getChannelUrl()
                    .toString(),
            )

        assertFailsWith<MalformedMeshtasticUrlException> {
            useCase(4321, profile, User(long_name = "Node B"), currentLoraConfig = null, isLocal = true)
        }

        assertFalse(radioController.editSettingsCalled)
        assertEquals(listOf(destinationPrimary), radioController.localChannels)
        assertTrue(radioController.localConfigs.isEmpty())
        assertTrue(radioController.settingsOperations.isEmpty())
    }

    @Test
    fun `invoke rejects a malformed channel URL before opening the transaction`() = runTest {
        val profile = DeviceProfile(long_name = "Must Not Apply", channel_url = "https://example.com/not-a-channel")

        val error =
            assertFailsWith<MalformedMeshtasticUrlException> {
                useCase(4321, profile, User(long_name = "Node B"), currentLoraConfig = null, isLocal = true)
            }

        assertFalse(radioController.editSettingsCalled)
        assertTrue(radioController.localChannels.isEmpty())
        assertTrue(radioController.localConfigs.isEmpty())
        assertEquals("malformed-meshtastic-url", error.expectedConditionLabel())
    }
}
