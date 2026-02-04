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
package com.geeksville.mesh.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.meshtastic.core.data.repository.RadioConfigRepository
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.service.ConnectionState
import org.meshtastic.proto.Config
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.MeshPacket

class MeshCommandSenderHopLimitTest {

    private val packetHandler: PacketHandler = mockk(relaxed = true)
    private val nodeManager = MeshNodeManager()
    private val connectionStateHolder: ConnectionStateHandler = mockk(relaxed = true)
    private val radioConfigRepository: RadioConfigRepository = mockk(relaxed = true)

    private val localConfigFlow = MutableStateFlow(LocalConfig())
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = CoroutineScope(testDispatcher)

    private lateinit var commandSender: MeshCommandSender

    @Before
    fun setUp() {
        val connectedFlow = MutableStateFlow(ConnectionState.Connected)
        every { connectionStateHolder.connectionState } returns connectedFlow
        every { radioConfigRepository.localConfigFlow } returns localConfigFlow

        commandSender = MeshCommandSender(packetHandler, nodeManager, connectionStateHolder, radioConfigRepository)
        commandSender.start(testScope)
        nodeManager.myNodeNum = 123
    }

    @Test
    fun `sendData uses default hop limit when config hop limit is zero`() = runTest(testDispatcher) {
        val packet =
            DataPacket(
                to = DataPacket.ID_BROADCAST,
                bytes = byteArrayOf(1, 2, 3).toByteString(),
                dataType = 1, // PortNum.TEXT_MESSAGE_APP
            )

        val meshPacketSlot = slot<MeshPacket>()
        every { packetHandler.sendToRadio(capture(meshPacketSlot)) } returns Unit

        // Ensure localConfig has lora.hop_limit = 0
        localConfigFlow.value = LocalConfig(lora = Config.LoRaConfig(hop_limit = 0))

        commandSender.sendData(packet)

        verify(exactly = 1) { packetHandler.sendToRadio(any<MeshPacket>()) }

        val capturedHopLimit = meshPacketSlot.captured.hop_limit ?: 0
        assertTrue("Hop limit should be greater than 0, but was $capturedHopLimit", capturedHopLimit > 0)
        assertEquals(3, capturedHopLimit)
        assertEquals(3, meshPacketSlot.captured.hop_start)
    }

    @Test
    fun `sendData respects non-zero hop limit from config`() = runTest(testDispatcher) {
        val packet =
            DataPacket(to = DataPacket.ID_BROADCAST, bytes = byteArrayOf(1, 2, 3).toByteString(), dataType = 1)

        val meshPacketSlot = slot<MeshPacket>()
        every { packetHandler.sendToRadio(capture(meshPacketSlot)) } returns Unit

        localConfigFlow.value = LocalConfig(lora = Config.LoRaConfig(hop_limit = 7))

        commandSender.sendData(packet)

        verify { packetHandler.sendToRadio(any<MeshPacket>()) }
        assertEquals(7, meshPacketSlot.captured.hop_limit)
        assertEquals(7, meshPacketSlot.captured.hop_start)
    }

    @Test
    fun `requestUserInfo sets hopStart equal to hopLimit`() = runTest(testDispatcher) {
        val destNum = 12345
        val meshPacketSlot = slot<MeshPacket>()
        every { packetHandler.sendToRadio(capture(meshPacketSlot)) } returns Unit

        localConfigFlow.value = LocalConfig(lora = Config.LoRaConfig(hop_limit = 6))

        // Mock node manager interactions
        nodeManager.nodeDBbyNodeNum.remove(destNum)

        commandSender.requestUserInfo(destNum)

        verify { packetHandler.sendToRadio(any<MeshPacket>()) }
        assertEquals("Hop Limit should be 6", 6, meshPacketSlot.captured.hop_limit)
        assertEquals("Hop Start should be 6", 6, meshPacketSlot.captured.hop_start)
    }
}
