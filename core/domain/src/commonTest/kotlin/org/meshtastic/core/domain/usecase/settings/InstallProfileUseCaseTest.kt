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
        useCase(1234, DeviceProfile.Builder().build(), User.Builder().build(), currentLoraConfig = null, isLocal = false)

        assertTrue(radioController.editSettingsCalled)
    }

    @Test
    fun `invoke installs all sections of a full profile`() = runTest {
        val profile =
            DeviceProfile.Builder().also { wb ->
            wb.long_name = "Full Node"
            wb.short_name = "FULL"
            wb.config = org.meshtastic.proto.LocalConfig.Builder().also { wb ->
                            wb.device = DeviceConfig.Builder().build()
                            wb.position = PositionConfig.Builder().build()
                            wb.power = PowerConfig.Builder().build()
                            wb.network = NetworkConfig.Builder().build()
                            wb.display = DisplayConfig.Builder().build()
                            wb.lora = LoRaConfig.Builder().build()
                            wb.bluetooth = BluetoothConfig.Builder().build()
                            wb.security = SecurityConfig.Builder().build()
                            }.build()
            wb.module_config = org.meshtastic.proto.LocalModuleConfig.Builder().also { wb ->
                            wb.mqtt = MQTTConfig.Builder().build()
                            wb.serial = SerialConfig.Builder().build()
                            wb.external_notification = ExternalNotificationConfig.Builder().build()
                            wb.store_forward = StoreForwardConfig.Builder().build()
                            wb.range_test = RangeTestConfig.Builder().build()
                            wb.telemetry = TelemetryConfig.Builder().build()
                            wb.canned_message = CannedMessageConfig.Builder().build()
                            wb.audio = AudioConfig.Builder().build()
                            wb.remote_hardware = RemoteHardwareConfig.Builder().build()
                            wb.neighbor_info = NeighborInfoConfig.Builder().build()
                            wb.ambient_lighting = AmbientLightingConfig.Builder().build()
                            wb.detection_sensor = DetectionSensorConfig.Builder().build()
                            wb.paxcounter = PaxcounterConfig.Builder().build()
                            wb.statusmessage = StatusMessageConfig.Builder().build()
                            wb.tak = TAKConfig.Builder().build()
                            }.build()
            wb.fixed_position = org.meshtastic.proto.Position.Builder().build()
            }.build()

        useCase(1234, profile, org.meshtastic.proto.User.Builder().also { wb ->wb.long_name = "Old"}.build(), currentLoraConfig = null, isLocal = false)

        assertTrue(radioController.editSettingsCalled)
    }

    @Test
    fun `fixed position queue rejection aborts profile installation after closing the edit transaction`() = runTest {
        val rejection = PacketQueueRejectedException("Fixed position")
        radioController.onSetFixedPosition = { _, _ -> throw rejection }
        val profile = DeviceProfile.Builder().also { wb ->wb.fixed_position = org.meshtastic.proto.Position.Builder().also { wb ->wb.latitude_i = 1; wb.longitude_i = 1}.build()}.build()

        val failure =
            assertFailsWith<PacketQueueRejectedException> {
                useCase(
                    destNum = 1234,
                    profile = profile,
                    currentUser = User.Builder().build(),
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
        val profile = DeviceProfile.Builder().also { wb ->wb.is_unmessagable = true; wb.is_licensed = true}.build()

        useCase(1234, profile, User.Builder().also { wb ->wb.long_name = "Old"}.build(), currentLoraConfig = null, isLocal = false)

        assertEquals(true, radioController.lastSetOwnerUser?.is_unmessagable)
        assertEquals(false, radioController.lastSetOwnerUser?.is_licensed)
    }

    @Test
    fun `invoke normalizes channels refreshes local cache and writes URL LoRa once`() = runTest {
        val primary = ChannelSettings.Builder().also { wb ->wb.name = "Node A Primary"}.build()
        val secondary = ChannelSettings.Builder().also { wb ->wb.name = "Node A Secondary"}.build()
        val urlLoraConfig =
            LoRaConfig.Builder()
                .also { wb ->
                    wb.use_preset = true
                    wb.modem_preset = LoRaConfig.ModemPreset.MEDIUM_FAST
                    wb.region = LoRaConfig.RegionCode.US
                }
                .build()
        val profileLoraConfig =
            LoRaConfig.Builder()
                .also { wb ->
                    wb.use_preset = true
                    wb.modem_preset = LoRaConfig.ModemPreset.LONG_SLOW
                    wb.region = LoRaConfig.RegionCode.EU_868
                }
                .build()
        val currentLoraConfig = LoRaConfig.Builder().also { wb -> wb.region = LoRaConfig.RegionCode.ANZ }.build()
        val oldSettings = listOf(ChannelSettings.Builder().also { wb ->wb.name = "Node B Primary"}.build())
        val cachedLoraConfig = LoRaConfig.Builder().also { wb -> wb.region = LoRaConfig.RegionCode.EU_433 }.build()
        radioConfigRepository.setChannelSet(ChannelSet.Builder().also { wb ->wb.settings = oldSettings; wb.lora_config = cachedLoraConfig}.build())
        val exportedProfile =
            DeviceProfile.Builder().also { wb ->
            wb.config = org.meshtastic.proto.LocalConfig.Builder().also { wb ->wb.lora = profileLoraConfig}.build()
            wb.channel_url = ChannelSet.Builder().also { wb ->
                            wb.settings = listOf(primary, ChannelSettings.Builder().build(), primary, secondary)
                            wb.lora_config = urlLoraConfig
                            }.build()
                                .getChannelUrl()
                                .toString()
            }.build()

        useCase(
            4321,
            exportedProfile,
            User.Builder().also { wb ->wb.long_name = "Node B"}.build(),
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
            listOf(FakeRadioController.ConfigWrite(destination = 4321, config = Config.Builder().also { wb ->wb.lora = urlLoraConfig}.build())),
            radioController.configWrites,
        )
        assertEquals(
            channelWrites.map { SettingsOperation.SetChannel(it.channel) } +
                SettingsOperation.SetConfig(Config.Builder().also { wb ->wb.lora = urlLoraConfig}.build()),
            radioController.settingsOperations,
        )
    }

    @Test
    fun `invoke treats blank channel URL as absent and installs profile LoRa once`() = runTest {
        val oldSettings = listOf(ChannelSettings.Builder().also { wb ->wb.name = "Keep Me"}.build())
        val currentLoraConfig = LoRaConfig.Builder().also { wb -> wb.region = LoRaConfig.RegionCode.EU_868 }.build()
        val profileLoraConfig = LoRaConfig.Builder().also { wb -> wb.region = LoRaConfig.RegionCode.US }.build()
        val cachedLoraConfig = LoRaConfig.Builder().also { wb -> wb.region = LoRaConfig.RegionCode.ANZ }.build()
        radioConfigRepository.setChannelSet(ChannelSet.Builder().also { wb ->wb.settings = oldSettings; wb.lora_config = cachedLoraConfig}.build())
        val profile =
            DeviceProfile.Builder().also { wb ->wb.channel_url = " \t\n"; wb.config = org.meshtastic.proto.LocalConfig.Builder().also { wb ->wb.lora = profileLoraConfig}.build()}.build()

        useCase(4321, profile, User.Builder().also { wb ->wb.long_name = "Node B"}.build(), currentLoraConfig = currentLoraConfig, isLocal = true)

        assertTrue(radioController.editSettingsCalled)
        assertTrue(radioController.localChannels.isEmpty())
        assertEquals(
            listOf(FakeRadioController.ConfigWrite(destination = 4321, config = Config.Builder().also { wb ->wb.lora = profileLoraConfig}.build())),
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
        val cachedLoraConfig = LoRaConfig.Builder().also { wb -> wb.region = LoRaConfig.RegionCode.ANZ }.build()
        val importedPrimary = ChannelSettings.Builder().also { wb ->wb.name = "Imported Primary"}.build()
        radioConfigRepository.setChannelSet(
            ChannelSet.Builder().also { wb ->wb.settings = listOf(ChannelSettings.Builder().also { wb ->wb.name = "Old Primary"}.build()); wb.lora_config = cachedLoraConfig}.build(),
        )
        val profile =
            DeviceProfile.Builder().also { wb ->wb.channel_url = ChannelSet.Builder().also { wb ->wb.settings = listOf(importedPrimary)}.build().getChannelUrl().toString()}.build()

        useCase(4321, profile, User.Builder().also { wb ->wb.long_name = "Node B"}.build(), currentLoraConfig = cachedLoraConfig, isLocal = true)

        assertTrue(radioController.localConfigs.isEmpty())
        assertEquals(listOf(importedPrimary), radioConfigRepository.currentChannelSet.settings)
        assertEquals(cachedLoraConfig, radioConfigRepository.currentChannelSet.lora_config)
    }

    @Test
    fun `invoke skips redundant LoRa write when desired config is already active`() = runTest {
        val currentLoraConfig = LoRaConfig.Builder().also { wb -> wb.region = LoRaConfig.RegionCode.US }.build()
        val profile = DeviceProfile.Builder().also { wb ->wb.config = org.meshtastic.proto.LocalConfig.Builder().also { wb ->wb.lora = currentLoraConfig}.build()}.build()

        useCase(4321, profile, User.Builder().also { wb ->wb.long_name = "Node B"}.build(), currentLoraConfig = currentLoraConfig, isLocal = false)

        assertTrue(radioController.localConfigs.isEmpty())
    }

    @Test
    fun `invoke does not replace local cache for a remote profile install`() = runTest {
        val oldSettings = listOf(ChannelSettings.Builder().also { wb ->wb.name = "Local Primary"}.build())
        val remotePrimary = ChannelSettings.Builder().also { wb ->wb.name = "Remote Primary"}.build()
        val cachedChannelSet =
            ChannelSet.Builder().also { wb ->wb.settings = oldSettings; wb.lora_config = LoRaConfig.Builder().also { wb -> wb.region = LoRaConfig.RegionCode.ANZ }.build()}.build()
        radioConfigRepository.setChannelSet(cachedChannelSet)
        val profile =
            DeviceProfile.Builder().also { wb ->
            wb.channel_url = ChannelSet.Builder().also { wb ->
                            wb.settings = listOf(remotePrimary)
                            wb.lora_config = LoRaConfig.Builder().also { wb -> wb.region = LoRaConfig.RegionCode.US }.build()
                            }.build()
                                .getChannelUrl()
                                .toString()
            }.build()

        useCase(4321, profile, User.Builder().also { wb ->wb.long_name = "Remote"}.build(), currentLoraConfig = null, isLocal = false)

        assertEquals(
            FakeRadioController.ChannelWrite(
                4321,
                Channel.Builder().also { wb ->wb.index = 0; wb.role = Channel.Role.PRIMARY; wb.settings = remotePrimary}.build(),
            ),
            radioController.channelWrites.first(),
        )
        assertTrue(radioController.localChannels.isEmpty())
        assertEquals(cachedChannelSet, radioConfigRepository.currentChannelSet)
    }

    @Test
    fun `invoke leaves local cache unchanged when a channel write fails`() = runTest {
        val oldSettings = listOf(ChannelSettings.Builder().also { wb ->wb.name = "Local Primary"}.build())
        val cachedChannelSet =
            ChannelSet.Builder().also { wb ->wb.settings = oldSettings; wb.lora_config = LoRaConfig.Builder().also { wb -> wb.region = LoRaConfig.RegionCode.ANZ }.build()}.build()
        radioConfigRepository.setChannelSet(cachedChannelSet)
        radioController.failChannelWriteAfter = 2
        val profile =
            DeviceProfile.Builder().also { wb ->
            wb.channel_url = ChannelSet.Builder().also { wb ->
                            wb.settings = listOf(ChannelSettings.Builder().also { wb ->wb.name = "Imported Primary"}.build())
                            wb.lora_config = LoRaConfig.Builder().also { wb -> wb.region = LoRaConfig.RegionCode.US }.build()
                            }.build()
                                .getChannelUrl()
                                .toString()
            }.build()

        assertFailsWith<IllegalStateException> {
            useCase(4321, profile, User.Builder().also { wb ->wb.long_name = "Local"}.build(), currentLoraConfig = null, isLocal = true)
        }

        assertEquals(cachedChannelSet, radioConfigRepository.currentChannelSet)
    }

    @Test
    fun `invoke rejects an empty channel set before opening the transaction`() = runTest {
        val destinationPrimary =
            Channel.Builder().also { wb ->wb.role = Channel.Role.PRIMARY; wb.index = 0; wb.settings = ChannelSettings.Builder().also { wb ->wb.name = "Node B Primary"}.build()}.build()
        radioController.channelWrites.add(
            FakeRadioController.ChannelWrite(destination = null, channel = destinationPrimary),
        )
        val profile =
            DeviceProfile.Builder().also { wb ->
            wb.long_name = "Must Not Apply"
            wb.channel_url = ChannelSet.Builder().also { wb ->
                            wb.settings = emptyList()
                            wb.lora_config =
                                LoRaConfig.Builder()
                                    .also { wb ->
                                        wb.use_preset = true
                                        wb.region = LoRaConfig.RegionCode.US
                                    }
                                    .build()
                            }.build()
                                .getChannelUrl()
                                .toString()
            }.build()

        assertFailsWith<MalformedMeshtasticUrlException> {
            useCase(4321, profile, User.Builder().also { wb ->wb.long_name = "Node B"}.build(), currentLoraConfig = null, isLocal = true)
        }

        assertFalse(radioController.editSettingsCalled)
        assertEquals(listOf(destinationPrimary), radioController.localChannels)
        assertTrue(radioController.localConfigs.isEmpty())
        assertTrue(radioController.settingsOperations.isEmpty())
    }

    @Test
    fun `invoke rejects a malformed channel URL before opening the transaction`() = runTest {
        val profile = DeviceProfile.Builder().also { wb ->wb.long_name = "Must Not Apply"; wb.channel_url = "https://example.com/not-a-channel"}.build()

        val error =
            assertFailsWith<MalformedMeshtasticUrlException> {
                useCase(4321, profile, User.Builder().also { wb ->wb.long_name = "Node B"}.build(), currentLoraConfig = null, isLocal = true)
            }

        assertFalse(radioController.editSettingsCalled)
        assertTrue(radioController.localChannels.isEmpty())
        assertTrue(radioController.localConfigs.isEmpty())
        assertEquals("malformed-meshtastic-url", error.expectedConditionLabel())
    }
}
