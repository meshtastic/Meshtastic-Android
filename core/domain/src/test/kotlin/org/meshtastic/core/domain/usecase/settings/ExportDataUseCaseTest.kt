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

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.encodeUtf8
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.data.repository.MeshLogRepository
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.database.entity.MeshLog
import org.meshtastic.core.database.model.Node
import org.meshtastic.proto.Data
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.robolectric.RobolectricTestRunner
import java.io.BufferedWriter
import java.io.StringWriter

@RunWith(RobolectricTestRunner::class)
class ExportDataUseCaseTest {

    private lateinit var nodeRepository: NodeRepository
    private lateinit var meshLogRepository: MeshLogRepository
    private lateinit var useCase: ExportDataUseCase

    @Before
    fun setUp() {
        nodeRepository = mockk(relaxed = true)
        meshLogRepository = mockk(relaxed = true)
        useCase = ExportDataUseCase(nodeRepository, meshLogRepository)
    }

    @Test
    fun `invoke writes header and log data`() = runTest {
        // Arrange
        val myNodeNum = 123
        val senderNodeNum = 456
        val senderNode = mockk<Node>(relaxed = true)
        every { senderNode.user.long_name } returns "Sender Name"
        every { senderNode.num } returns senderNodeNum
        
        val nodes = mapOf(senderNodeNum to senderNode)
        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(nodes)

        val meshPacket = MeshPacket(
            from = senderNodeNum,
            rx_snr = 5.5f,
            decoded = Data(portnum = PortNum.TEXT_MESSAGE_APP, payload = "Hello".encodeUtf8())
        )
        val meshLog = MeshLog(
            uuid = "uuid-1",
            message_type = "Packet",
            received_date = 1700000000000L,
            raw_message = "",
            fromNum = senderNodeNum,
            portNum = PortNum.TEXT_MESSAGE_APP.value,
            fromRadio = FromRadio(packet = meshPacket)
        )
        every { meshLogRepository.getAllLogsInReceiveOrder(any()) } returns flowOf(listOf(meshLog))

        val stringWriter = StringWriter()
        val bufferedWriter = BufferedWriter(stringWriter)

        // Act
        useCase(bufferedWriter, myNodeNum)
        bufferedWriter.flush()

        // Assert
        val output = stringWriter.toString()
        assertTrue("Header should be present", output.contains("\"date\",\"time\",\"from\",\"sender name\""))
        assertTrue("Sender name should be present", output.contains("Sender Name"))
        assertTrue("Payload should be present", output.contains("Hello"))
    }
}
