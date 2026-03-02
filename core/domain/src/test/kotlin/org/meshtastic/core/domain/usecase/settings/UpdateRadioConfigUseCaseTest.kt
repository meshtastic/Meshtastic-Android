/*
 * Copyright (c) 2025 Meshtastic LLC
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
import org.meshtastic.core.model.RadioController
import org.meshtastic.proto.Config
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.User

class UpdateRadioConfigUseCaseTest {

    private lateinit var radioController: RadioController
    private lateinit var useCase: UpdateRadioConfigUseCase

    @Before
    fun setUp() {
        radioController = mockk(relaxed = true)
        useCase = UpdateRadioConfigUseCase(radioController)
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
    fun `setConfig calls radioController and returns packetId`() = runTest {
        val config = Config(device = Config.DeviceConfig(role = Config.DeviceConfig.Role.CLIENT))
        val result = useCase.setConfig(123, config)
        
        coVerify { radioController.setConfig(123, config, 42) }
        assertEquals(42, result)
    }

    @Test
    fun `setModuleConfig calls radioController and returns packetId`() = runTest {
        val config = ModuleConfig(mqtt = ModuleConfig.MQTTConfig(enabled = true))
        val result = useCase.setModuleConfig(123, config)
        
        coVerify { radioController.setModuleConfig(123, config, 42) }
        assertEquals(42, result)
    }
}
