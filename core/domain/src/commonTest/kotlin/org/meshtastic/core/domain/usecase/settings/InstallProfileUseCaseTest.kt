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
import org.meshtastic.core.testing.FakeRadioController
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
import org.meshtastic.proto.ModuleConfig.TrafficManagementConfig
import org.meshtastic.proto.User
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class InstallProfileUseCaseTest {

    private lateinit var radioController: FakeRadioController
    private lateinit var useCase: InstallProfileUseCase

    @BeforeTest
    fun setUp() {
        radioController = FakeRadioController()
        useCase = InstallProfileUseCase(radioController)
    }

    @Test
    fun `invoke calls begin and commit edit settings`() = runTest {
        useCase(1234, DeviceProfile(), User())

        assertTrue(radioController.beginEditSettingsCalled)
        assertTrue(radioController.commitEditSettingsCalled)
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
                    traffic_management = TrafficManagementConfig(),
                    tak = TAKConfig(),
                ),
                fixed_position = org.meshtastic.proto.Position(),
            )

        useCase(1234, profile, org.meshtastic.proto.User(long_name = "Old"))

        assertTrue(radioController.beginEditSettingsCalled)
        assertTrue(radioController.commitEditSettingsCalled)
    }
}
