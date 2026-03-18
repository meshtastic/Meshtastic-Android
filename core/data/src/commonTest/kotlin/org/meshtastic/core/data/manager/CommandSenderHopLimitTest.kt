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
package org.meshtastic.core.data.manager

class CommandSenderHopLimitTest {
    /*



    private val localConfigFlow = MutableStateFlow(LocalConfig())
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = CoroutineScope(testDispatcher)

    private lateinit var commandSender: CommandSender

    @Before
    fun setUp() {
        val myNum = 123
        val myNode = Node(num = myNum, user = User(id = "!id", long_name = "long", short_name = "shrt"))
        every { radioConfigRepository.localConfigFlow } returns localConfigFlow
        every { nodeManager.myNodeNum } returns myNum
        every { nodeManager.nodeDBbyNodeNum } returns mapOf(myNum to myNode)

        commandSender = CommandSenderImpl(packetHandler, nodeManager, radioConfigRepository)
        commandSender.start(testScope)
    }

    @Test
    fun `sendData uses default hop limit when config hop limit is zero`() = runTest(testDispatcher) {
        val packet =
            DataPacket(
                to = DataPacket.ID_BROADCAST,
                bytes = byteArrayOf(1, 2, 3).toByteString(),
                dataType = 1, // PortNum.TEXT_MESSAGE_APP
            )

        val meshPacketSlot = Capture.slot<MeshPacket>()

        // Ensure localConfig has lora.hop_limit = 0
        localConfigFlow.value = LocalConfig(lora = Config.LoRaConfig(hop_limit = 0))

        commandSender.sendData(packet)


        val capturedHopLimit = meshPacketSlot.captured.hop_limit ?: 0
        assertTrue("Hop limit should be greater than 0, but was $capturedHopLimit", capturedHopLimit > 0)
        assertEquals(3, capturedHopLimit)
        assertEquals(3, meshPacketSlot.captured.hop_start)
    }

    @Test
    fun `sendData respects non-zero hop limit from config`() = runTest(testDispatcher) {
        val packet =
            DataPacket(to = DataPacket.ID_BROADCAST, bytes = byteArrayOf(1, 2, 3).toByteString(), dataType = 1)

        val meshPacketSlot = Capture.slot<MeshPacket>()

        localConfigFlow.value = LocalConfig(lora = Config.LoRaConfig(hop_limit = 7))

        commandSender.sendData(packet)

        assertEquals(7, meshPacketSlot.captured.hop_limit)
        assertEquals(7, meshPacketSlot.captured.hop_start)
    }

    @Test
    fun `requestUserInfo sets hopStart equal to hopLimit`() = runTest(testDispatcher) {
        val destNum = 12345
        val meshPacketSlot = Capture.slot<MeshPacket>()

        localConfigFlow.value = LocalConfig(lora = Config.LoRaConfig(hop_limit = 6))

        // Mock node manager interactions
        // Note: we need to keep myNode in the map for requestUserInfo to not return early
        val myNum = 123
        val myNode = Node(num = myNum, user = User(id = "!id", long_name = "long", short_name = "shrt"))
        every { nodeManager.nodeDBbyNodeNum } returns mapOf(myNum to myNode)

        commandSender.requestUserInfo(destNum)

        assertEquals("Hop Limit should be 6", 6, meshPacketSlot.captured.hop_limit)
        assertEquals("Hop Start should be 6", 6, meshPacketSlot.captured.hop_start)
    }

     */
}
