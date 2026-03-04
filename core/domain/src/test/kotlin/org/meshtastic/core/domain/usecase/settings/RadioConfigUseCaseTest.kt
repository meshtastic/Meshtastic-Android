/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.RadioController
import org.meshtastic.proto.Channel
import org.meshtastic.proto.Config
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.User

class RadioConfigUseCaseTest {

    private lateinit var radioController: RadioController
    private lateinit var useCase: RadioConfigUseCase

    @Before
    fun setUp() {
        radioController = mockk(relaxed = true)
        useCase = RadioConfigUseCase(radioController)
        every { radioController.getPacketId() } returns 42
    }

    @Test
    fun `setOwner calls radioController and returns packetId`() = runTest {
        val user = User(long_name = "New Name")
        val result = useCase.setOwner(123, user)

        coVerify { radioController.setOwner(123, user, 42) }
        assertEquals(42, result)
    }

    @Test
    fun `getOwner calls radioController and returns packetId`() = runTest {
        val result = useCase.getOwner(123)

        coVerify { radioController.getOwner(123, 42) }
        assertEquals(42, result)
    }

    @Test
    fun `setConfig calls radioController and returns packetId`() = runTest {
        val config = Config(device = Config.DeviceConfig(role = Config.DeviceConfig.Role.CLIENT))
        val result = useCase.setConfig(123, config)

        coVerify { radioController.setConfig(123, config, 42) }
        assertEquals(42, result)
    }

    @Test
    fun `getConfig calls radioController and returns packetId`() = runTest {
        val result = useCase.getConfig(123, 1)

        coVerify { radioController.getConfig(123, 1, 42) }
        assertEquals(42, result)
    }

    @Test
    fun `setModuleConfig calls radioController and returns packetId`() = runTest {
        val config = ModuleConfig(mqtt = ModuleConfig.MQTTConfig(enabled = true))
        val result = useCase.setModuleConfig(123, config)

        coVerify { radioController.setModuleConfig(123, config, 42) }
        assertEquals(42, result)
    }

    @Test
    fun `getModuleConfig calls radioController and returns packetId`() = runTest {
        val result = useCase.getModuleConfig(123, 2)

        coVerify { radioController.getModuleConfig(123, 2, 42) }
        assertEquals(42, result)
    }

    @Test
    fun `getChannel calls radioController and returns packetId`() = runTest {
        val result = useCase.getChannel(123, 0)

        coVerify { radioController.getChannel(123, 0, 42) }
        assertEquals(42, result)
    }

    @Test
    fun `setRemoteChannel calls radioController and returns packetId`() = runTest {
        val channel = Channel(index = 0)
        val result = useCase.setRemoteChannel(123, channel)

        coVerify { radioController.setRemoteChannel(123, channel, 42) }
        assertEquals(42, result)
    }

    @Test
    fun `setFixedPosition calls radioController`() = runTest {
        val pos = Position(1.0, 2.0, 3)
        useCase.setFixedPosition(123, pos)

        coVerify { radioController.setFixedPosition(123, pos) }
    }

    @Test
    fun `removeFixedPosition calls radioController with zero position`() = runTest {
        useCase.removeFixedPosition(123)

        coVerify { radioController.setFixedPosition(123, any()) }
    }

    @Test
    fun `setRingtone calls radioController`() = runTest {
        useCase.setRingtone(123, "ring")
        coVerify { radioController.setRingtone(123, "ring") }
    }

    @Test
    fun `getRingtone calls radioController and returns packetId`() = runTest {
        val result = useCase.getRingtone(123)
        coVerify { radioController.getRingtone(123, 42) }
        assertEquals(42, result)
    }

    @Test
    fun `setCannedMessages calls radioController`() = runTest {
        useCase.setCannedMessages(123, "msg")
        coVerify { radioController.setCannedMessages(123, "msg") }
    }

    @Test
    fun `getCannedMessages calls radioController and returns packetId`() = runTest {
        val result = useCase.getCannedMessages(123)
        coVerify { radioController.getCannedMessages(123, 42) }
        assertEquals(42, result)
    }

    @Test
    fun `getDeviceConnectionStatus calls radioController and returns packetId`() = runTest {
        val result = useCase.getDeviceConnectionStatus(123)
        coVerify { radioController.getDeviceConnectionStatus(123, 42) }
        assertEquals(42, result)
    }
}
