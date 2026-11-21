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
import org.meshtastic.core.testing.FakeRadioController.AdminRequest
import org.meshtastic.proto.Channel
import org.meshtastic.proto.Config
import org.meshtastic.proto.HamParameters
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.User
import org.meshtastic.sdk.AdminResult
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RadioConfigUseCaseTest {

    private lateinit var radioController: FakeRadioController
    private lateinit var useCase: RadioConfigUseCase

    @BeforeTest
    fun setUp() {
        radioController = FakeRadioController()
        useCase = RadioConfigUseCase(radioController)
    }

    /** Asserts [call] sent exactly [expected]. */
    private suspend fun assertSends(expected: AdminRequest, call: suspend () -> Any?) {
        call()

        assertEquals(listOf(expected), radioController.adminRequests)
    }

    @Test
    fun `setOwner sends the user to the node`() = runTest {
        val user = User.Builder().also { wb -> wb.long_name = "New Name" }.build()

        assertSends(AdminRequest("setOwner", DEST, user, null)) { useCase.setOwner(DEST, user) }
    }

    @Test
    fun `setHamMode sends the ham parameters to the node`() = runTest {
        val ham =
            HamParameters.Builder()
                .also { wb ->
                    wb.call_sign = "KK7ABC"
                    wb.short_name = "KK7A"
                }
                .build()

        assertSends(AdminRequest("setHamMode", DEST, ham, null)) { useCase.setHamMode(DEST, ham) }
    }

    @Test
    fun `getOwner requests the owner`() = runTest {
        assertSends(AdminRequest("getOwner", DEST, null, null)) { useCase.getOwner(DEST) }
    }

    @Test
    fun `setConfig sends the config to the node`() = runTest {
        val config =
            Config.Builder()
                .also { wb -> wb.lora = Config.LoRaConfig.Builder().also { wb -> wb.use_preset = true }.build() }
                .build()

        assertSends(AdminRequest("setConfig", DEST, config, null)) { useCase.setConfig(DEST, config) }
    }

    @Test
    fun `setModuleConfig sends the module config to the node`() = runTest {
        val config =
            ModuleConfig.Builder()
                .also { wb -> wb.mqtt = ModuleConfig.MQTTConfig.Builder().also { wb -> wb.enabled = true }.build() }
                .build()

        assertSends(AdminRequest("setModuleConfig", DEST, config, null)) { useCase.setModuleConfig(DEST, config) }
    }

    @Test
    fun `setFixedPosition sends the position unchanged`() = runTest {
        val position = Position(1.0, 2.0, 3)

        assertSends(AdminRequest("setFixedPosition", DEST, position, null)) {
            useCase.setFixedPosition(DEST, position)
            null
        }
    }

    @Test
    fun `removeFixedPosition sends the removal sentinel`() = runTest {
        useCase.removeFixedPosition(DEST)

        val request = radioController.adminRequests.single()
        assertEquals(AdminRequest("setFixedPosition", DEST, request.payload, null), request)
        assertTrue(assertIs<Position>(request.payload).isFixedPositionRemoval())
    }

    @Test
    fun `setRingtone sends the ringtone`() = runTest {
        assertSends(AdminRequest("setRingtone", DEST, "ringtone.mp3", null)) {
            useCase.setRingtone(DEST, "ringtone.mp3")
            null
        }
    }

    @Test
    fun `playRingtone asks the node to play its ringtone`() = runTest {
        assertSends(AdminRequest("playRingtone", DEST, null, null)) {
            useCase.playRingtone(DEST)
            null
        }
    }

    @Test
    fun `setCannedMessages sends the messages`() = runTest {
        assertSends(AdminRequest("setCannedMessages", DEST, "messages", null)) {
            useCase.setCannedMessages(DEST, "messages")
            null
        }
    }

    @Test
    fun `getConfig requests the config type`() = runTest {
        assertSends(AdminRequest("getConfig", DEST, 1, null)) { useCase.getConfig(DEST, 1) }
    }

    @Test
    fun `getModuleConfig requests the module config type`() = runTest {
        assertSends(AdminRequest("getModuleConfig", DEST, 1, null)) { useCase.getModuleConfig(DEST, 1) }
    }

    @Test
    fun `getChannel requests the channel index`() = runTest {
        assertSends(AdminRequest("getChannel", DEST, 1, null)) { useCase.getChannel(DEST, 1) }
    }

    @Test
    fun `setRemoteChannel sends the channel to the node`() = runTest {
        val channel = Channel.Builder().also { wb -> wb.index = 2 }.build()

        assertSends(AdminRequest("setRemoteChannel", DEST, channel, null)) {
            useCase.setRemoteChannel(DEST, channel)
        }
    }

    @Test
    fun `getRingtone requests the ringtone`() = runTest {
        assertSends(AdminRequest("getRingtone", DEST, null, null)) { useCase.getRingtone(DEST) }
    }

    @Test
    fun `getCannedMessages requests the canned messages`() = runTest {
        assertSends(AdminRequest("getCannedMessages", DEST, null, null)) { useCase.getCannedMessages(DEST) }
    }

    @Test
    fun `getDeviceConnectionStatus requests the connection status`() = runTest {
        assertSends(AdminRequest("getDeviceConnectionStatus", DEST, null, null)) {
            useCase.getDeviceConnectionStatus(DEST)
        }
    }

    @Test
    fun `a read returns the radio answer`() = runTest {
        val owner = User.Builder().also { wb -> wb.long_name = "Remote" }.build()
        radioController.readResults["getOwner"] = AdminResult.Success(owner)

        assertEquals(AdminResult.Success(owner), useCase.getOwner(DEST))
    }

    private companion object {
        const val DEST = 1234
    }
}
