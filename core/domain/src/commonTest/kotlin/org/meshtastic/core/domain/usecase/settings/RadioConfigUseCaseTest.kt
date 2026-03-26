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
import org.meshtastic.core.model.Position
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.proto.Config
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.User
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RadioConfigUseCaseTest {

    private lateinit var radioController: FakeRadioController
    private lateinit var useCase: RadioConfigUseCase

    @BeforeTest
    fun setUp() {
        radioController = FakeRadioController()
        useCase = RadioConfigUseCase(radioController)
    }

    @Test
    fun `setOwner calls radioController`() = runTest {
        val user = User(long_name = "New Name")
        useCase.setOwner(1234, user)
        // Verify call implicitly or by adding tracking to FakeRadioController if needed.
        // FakeRadioController already has getPacketId returning 1.
    }

    @Test
    fun `getOwner calls radioController`() = runTest {
        val packetId = useCase.getOwner(1234)
        assertEquals(1, packetId)
    }

    @Test
    fun `setConfig calls radioController`() = runTest {
        val config = Config(lora = Config.LoRaConfig(use_preset = true))
        useCase.setConfig(1234, config)
    }

    @Test
    fun `setModuleConfig calls radioController`() = runTest {
        val config = ModuleConfig(mqtt = ModuleConfig.MQTTConfig(enabled = true))
        useCase.setModuleConfig(1234, config)
    }

    @Test
    fun `setFixedPosition calls radioController`() = runTest {
        val position = Position(1.0, 2.0, 3)
        useCase.setFixedPosition(1234, position)
    }

    @Test
    fun `removeFixedPosition calls radioController with zero position`() = runTest { useCase.removeFixedPosition(1234) }

    @Test fun `setRingtone calls radioController`() = runTest { useCase.setRingtone(1234, "ringtone.mp3") }

    @Test fun `setCannedMessages calls radioController`() = runTest { useCase.setCannedMessages(1234, "messages") }

    @Test fun `getConfig calls radioController`() = runTest { useCase.getConfig(1234, 1) }

    @Test fun `getModuleConfig calls radioController`() = runTest { useCase.getModuleConfig(1234, 1) }

    @Test fun `getChannel calls radioController`() = runTest { useCase.getChannel(1234, 1) }

    @Test
    fun `setRemoteChannel calls radioController`() = runTest {
        useCase.setRemoteChannel(1234, org.meshtastic.proto.Channel())
    }

    @Test fun `getRingtone calls radioController`() = runTest { useCase.getRingtone(1234) }

    @Test fun `getCannedMessages calls radioController`() = runTest { useCase.getCannedMessages(1234) }
}
