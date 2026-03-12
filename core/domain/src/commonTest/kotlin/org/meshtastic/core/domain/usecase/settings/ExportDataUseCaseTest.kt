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
import okio.Buffer
import okio.ByteString.Companion.encodeUtf8
import org.meshtastic.core.model.MeshLog
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.MeshLogRepository
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.proto.Data
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.User
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class ExportDataUseCaseTest {

    private lateinit var nodeRepository: NodeRepository
    private lateinit var meshLogRepository: MeshLogRepository
    private lateinit var useCase: ExportDataUseCase

    @BeforeTest
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
        val senderNode = Node(num = senderNodeNum, user = User(long_name = "Sender Name"))

        val nodes = mapOf(senderNodeNum to senderNode)
        val stateFlow = MutableStateFlow(nodes)
        every { nodeRepository.nodeDBbyNum } returns stateFlow

        val meshPacket =
            MeshPacket(
                from = senderNodeNum,
                rx_snr = 5.5f,
                decoded = Data(portnum = PortNum.TEXT_MESSAGE_APP, payload = "Hello".encodeUtf8()),
            )
        val meshLog =
            MeshLog(
                uuid = "uuid-1",
                message_type = "Packet",
                received_date = 1700000000000L,
                raw_message = "",
                fromNum = senderNodeNum,
                portNum = PortNum.TEXT_MESSAGE_APP.value,
                fromRadio = FromRadio(packet = meshPacket),
            )
        every { meshLogRepository.getAllLogsInReceiveOrder(any()) } returns flowOf(listOf(meshLog))

        val buffer = Buffer()

        // Act
        useCase(buffer, myNodeNum)

        // Assert
        val output = buffer.readUtf8()
        assertTrue(output.contains("\"date\",\"time\",\"from\",\"sender name\""), "Header should be present")
        assertTrue(output.contains("Sender Name"), "Sender name should be present")
        assertTrue(output.contains("Hello"), "Payload should be present")
    }
}
