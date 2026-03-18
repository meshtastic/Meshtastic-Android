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

class InstallProfileUseCaseTest {
    /*


    private lateinit var radioController: RadioController
    private lateinit var useCase: InstallProfileUseCase

    @BeforeTest
    fun setUp() {
        useCase = InstallProfileUseCase(radioController)
        every { radioController.getPacketId() } returns 1
    }

    @Test
    fun `invoke with names updates owner`() = runTest {
        // Arrange
        val profile = DeviceProfile(long_name = "New Long", short_name = "NL")
        val currentUser = User(long_name = "Old Long", short_name = "OL")

        // Act
        useCase(123, profile, currentUser)

        // Assert
        verifySuspend { radioController.beginEditSettings(123) }
        verifySuspend { radioController.commitEditSettings(123) }
    }

    @Test
    fun `invoke with config sets config`() = runTest {
        // Arrange
        val loraConfig = Config.LoRaConfig(region = Config.LoRaConfig.RegionCode.US)
        val profile = DeviceProfile(config = LocalConfig(lora = loraConfig))

        // Act
        useCase(456, profile, null)

        // Assert
    }

    @Test
    fun `invoke with module_config sets module config`() = runTest {
        // Arrange
        val mqttConfig = ModuleConfig.MQTTConfig(enabled = true, address = "broker.local")
        val profile = DeviceProfile(module_config = LocalModuleConfig(mqtt = mqttConfig))

        // Act
        useCase(789, profile, null)

        // Assert
    }

    @Test
    fun `invoke with module_config part 2 sets module config`() = runTest {
        // Arrange
        val neighborInfoConfig = ModuleConfig.NeighborInfoConfig(enabled = true)
        val profile = DeviceProfile(module_config = LocalModuleConfig(neighbor_info = neighborInfoConfig))

        // Act
        useCase(789, profile, null)

        // Assert
    }

     */
}
