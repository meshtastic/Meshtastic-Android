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
import org.meshtastic.core.repository.RadioController
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.core.testing.FakeRadioController.AdminRequest
import org.meshtastic.proto.Channel
import org.meshtastic.proto.Config
import org.meshtastic.proto.HamParameters
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
        radioController.nextPacketId = PACKET_ID
        useCase = RadioConfigUseCase(radioController)
    }

    /** Asserts [call] sent exactly [expected] and, for requests that carry one, returned the same packet ID. */
    private suspend fun assertSends(expected: AdminRequest, call: suspend () -> Int?) {
        val returned = call()

        assertEquals(listOf(expected), radioController.adminRequests)
        if (expected.packetId != null) assertEquals(expected.packetId, returned)
    }

    @Test
    fun `getConfig invokes onRequestId with the packet id before issuing the send`() = runTest {
        // Guards against the response/registration race: for the locally connected node the firmware
        // (2.8+) delivers the admin response before the QueueStatus ack that completes the send, so a
        // caller registering the request id only after the send returns would drop the response.
        val events = mutableListOf<String>()
        val recordingController =
            object : RadioController by radioController {
                override suspend fun getConfig(destNum: Int, configType: Int, packetId: Int) {
                    events.add("send:$packetId")
                }
            }
        val orderedUseCase = RadioConfigUseCase(recordingController)

        val packetId = orderedUseCase.getConfig(1234, 5) { events.add("registered:$it") }

        assertEquals(listOf("registered:$packetId", "send:$packetId"), events)
    }

    @Test
    fun `setOwner sends the user to the node`() = runTest {
        val user = User.Builder().also { wb -> wb.long_name = "New Name" }.build()

        assertSends(AdminRequest("setOwner", DEST, user, PACKET_ID)) { useCase.setOwner(DEST, user) }
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

        assertSends(AdminRequest("setHamMode", DEST, ham, PACKET_ID)) { useCase.setHamMode(DEST, ham) }
    }

    @Test
    fun `getOwner requests the owner`() = runTest {
        assertSends(AdminRequest("getOwner", DEST, null, PACKET_ID)) { useCase.getOwner(DEST) }
    }

    @Test
    fun `setConfig sends the config to the node`() = runTest {
        val config =
            Config.Builder()
                .also { wb -> wb.lora = Config.LoRaConfig.Builder().also { wb -> wb.use_preset = true }.build() }
                .build()

        assertSends(AdminRequest("setConfig", DEST, config, PACKET_ID)) { useCase.setConfig(DEST, config) }
    }

    @Test
    fun `setModuleConfig sends the module config to the node`() = runTest {
        val config =
            ModuleConfig.Builder()
                .also { wb -> wb.mqtt = ModuleConfig.MQTTConfig.Builder().also { wb -> wb.enabled = true }.build() }
                .build()

        assertSends(AdminRequest("setModuleConfig", DEST, config, PACKET_ID)) { useCase.setModuleConfig(DEST, config) }
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
        assertSends(AdminRequest("setFixedPosition", DEST, Position(0.0, 0.0, 0), null)) {
            useCase.removeFixedPosition(DEST)
            null
        }
        assertEquals(true, radioController.fixedPositions.single().isFixedPositionRemoval())
    }

    @Test
    fun `setRingtone sends the ringtone`() = runTest {
        assertSends(AdminRequest("setRingtone", DEST, "ringtone.mp3", null)) {
            useCase.setRingtone(DEST, "ringtone.mp3")
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
        assertSends(AdminRequest("getConfig", DEST, 1, PACKET_ID)) { useCase.getConfig(DEST, 1) }
    }

    @Test
    fun `getModuleConfig requests the module config type`() = runTest {
        assertSends(AdminRequest("getModuleConfig", DEST, 1, PACKET_ID)) { useCase.getModuleConfig(DEST, 1) }
    }

    @Test
    fun `getChannel requests the channel index`() = runTest {
        assertSends(AdminRequest("getChannel", DEST, 1, PACKET_ID)) { useCase.getChannel(DEST, 1) }
    }

    @Test
    fun `setRemoteChannel sends the channel to the node`() = runTest {
        val channel = Channel.Builder().also { wb -> wb.index = 2 }.build()

        assertSends(AdminRequest("setRemoteChannel", DEST, channel, PACKET_ID)) {
            useCase.setRemoteChannel(DEST, channel)
        }
    }

    @Test
    fun `getRingtone requests the ringtone`() = runTest {
        assertSends(AdminRequest("getRingtone", DEST, null, PACKET_ID)) { useCase.getRingtone(DEST) }
    }

    @Test
    fun `getCannedMessages requests the canned messages`() = runTest {
        assertSends(AdminRequest("getCannedMessages", DEST, null, PACKET_ID)) { useCase.getCannedMessages(DEST) }
    }

    @Test
    fun `getDeviceConnectionStatus requests the connection status`() = runTest {
        assertSends(AdminRequest("getDeviceConnectionStatus", DEST, null, PACKET_ID)) {
            useCase.getDeviceConnectionStatus(DEST)
        }
    }

    @Test
    fun `onRequestId receives the packet id the request carries`() = runTest {
        var registered: Int? = null

        useCase.getOwner(DEST) { registered = it }

        assertEquals(PACKET_ID, registered)
        assertEquals(PACKET_ID, radioController.adminRequests.single().packetId)
    }

    private companion object {
        const val DEST = 1234
        const val PACKET_ID = 4242
    }
}
